/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Fetches timetable JSON files from the server and caches them to disk.
///
/// Uses ETag-based conditional requests (If-None-Match / 304) to avoid re-downloading
/// unchanged routes. Updated routes are flagged via `hasPendingUpdate(_:)` so that
/// `TimetableService` can evict its in-memory cache on the next access.
///
/// Disk layout: `Caches/Timetables/{routeId}.json`
/// ETag storage: `UserDefaults`, key `"timetable_etag_{routeId}"`
actor TimetableCacheService {
    static let shared = TimetableCacheService()

    private let session: URLSession
    private var pendingUpdates: Set<String> = []

    private var bundleRouteIds: [String] {
        guard let urls = Bundle.main.urls(forResourcesWithExtension: "json", subdirectory: "Timetables") else { return [] }
        return urls.map { $0.deletingPathExtension().lastPathComponent.lowercased() }
    }

    private struct RoutesManifest: Decodable {
        let routeIds: [String]
    }

    private struct VariantIdsFile: Decodable {
        struct Variant: Decodable { let id: String }
        let variants: [Variant]
    }

    private func diskCacheRouteIds() -> [String] {
        guard let cacheDir = Self.cacheDirectory(),
              let files = try? FileManager.default.contentsOfDirectory(at: cacheDir, includingPropertiesForKeys: nil)
        else { return [] }
        return files
            .filter { $0.pathExtension == "json" }
            .map { $0.deletingPathExtension().lastPathComponent.lowercased() }
    }

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 15
        session = URLSession(configuration: config)
    }

    // MARK: - Cache file URLs

    nonisolated static func cacheDirectory() -> URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("Timetables")
    }

    nonisolated static func cacheURL(for routeId: String) -> URL? {
        cacheDirectory()?.appendingPathComponent("\(routeId.lowercased()).json")
    }

    // MARK: - Pending update flag (checked by TimetableService)

    func hasPendingUpdate(_ routeId: String) -> Bool {
        pendingUpdates.contains(routeId.lowercased())
    }

    func clearPendingUpdate(_ routeId: String) {
        pendingUpdates.remove(routeId.lowercased())
    }

    // MARK: - ETag persistence

    private func storedEtag(for routeId: String) -> String? {
        UserDefaults.standard.string(forKey: "timetable_etag_\(routeId.lowercased())")
    }

    private func saveEtag(_ etag: String, for routeId: String) {
        UserDefaults.standard.set(etag, forKey: "timetable_etag_\(routeId.lowercased())")
    }

    // MARK: - Fetch

    @discardableResult
    private func fetchRoute(_ routeId: String) async -> Bool {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/api/timetables/\(routeId)") else {
            return false
        }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(AppConfig.serverAPIKey)", forHTTPHeaderField: "Authorization")
        if let etag = storedEtag(for: routeId) {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { return false }
            DebugConfig.debugPrint("TimetableCacheService: GET /api/timetables/\(routeId) → HTTP \(http.statusCode)")
            switch http.statusCode {
            case 200:
                guard let fileURL = Self.cacheURL(for: routeId) else { return false }
                try FileManager.default.createDirectory(
                    at: fileURL.deletingLastPathComponent(),
                    withIntermediateDirectories: true
                )
                try data.write(to: fileURL)
                if let etag = http.value(forHTTPHeaderField: "ETag") {
                    saveEtag(etag, for: routeId)
                }
                pendingUpdates.insert(routeId.lowercased())
                DebugConfig.debugPrint("TimetableCacheService: Updated \(routeId) (\(data.count) bytes)")
                return true
            case 304:
                DebugConfig.debugPrint("TimetableCacheService: \(routeId) unchanged (304)")
                return false
            default:
                DebugConfig.debugError("TimetableCacheService: \(routeId) fetch failed HTTP \(http.statusCode)")
                return false
            }
        } catch {
            DebugConfig.debugError("TimetableCacheService: \(routeId) fetch error", error: error)
            return false
        }
    }

    /// Fetches the lightweight route manifest (`GET /api/routes`), used to discover
    /// routes that exist on the server but not yet in the bundle or disk cache.
    /// Returns `nil` on any failure (offline / error) so callers can fall back to
    /// the bundle/disk-cache-only route list.
    ///
    /// Also reports reachability to `LaLigaBlockingService`, since this is our cheapest,
    /// most frequent call to our own server and the natural place to notice it's down.
    private func fetchManifest() async -> [String]? {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/api/routes") else { return nil }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(AppConfig.serverAPIKey)", forHTTPHeaderField: "Authorization")
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                await LaLigaBlockingService.shared.onServerUnreachable()
                return nil
            }
            DebugConfig.debugPrint("TimetableCacheService: GET /api/routes → HTTP \(http.statusCode)")
            guard http.statusCode == 200 else {
                await LaLigaBlockingService.shared.onServerUnreachable()
                return nil
            }
            await LaLigaBlockingService.shared.onServerReachable()
            return try JSONDecoder().decode(RoutesManifest.self, from: data).routeIds
        } catch {
            DebugConfig.debugError("TimetableCacheService: manifest fetch error", error: error)
            await LaLigaBlockingService.shared.onServerUnreachable()
            return nil
        }
    }

    /// Fetches the polyline for each of `routeId`'s variants, for a route that was just
    /// discovered via the manifest and has no bundled/cached polylines yet.
    private func fetchPolylinesForNewRoute(_ routeId: String) async {
        guard let cacheURL = Self.cacheURL(for: routeId),
              let data = try? Data(contentsOf: cacheURL),
              let file = try? JSONDecoder().decode(VariantIdsFile.self, from: data)
        else { return }
        for variant in file.variants {
            await PolylineCacheService.shared.fetchPolyline("\(routeId.uppercased())-\(variant.id)")
        }
    }

    /// Fetches all routes in parallel. Updated routes are flagged via `hasPendingUpdate(_:)`.
    ///
    /// The route ID list is the union of bundled assets, the disk cache, and the server
    /// manifest (`fetchManifest`). Routes that are new (server-only, not yet seen on this
    /// device) also have their polylines fetched once their timetable is downloaded.
    func fetchAllRoutes() async {
        let knownIds = Set(bundleRouteIds + diskCacheRouteIds())
        let manifestIds = (await fetchManifest())?.map { $0.lowercased() } ?? []
        let allIds = knownIds.union(manifestIds)

        DebugConfig.debugPrint("TimetableCacheService: Starting fetch for \(allIds.count) routes")
        await withTaskGroup(of: Void.self) { group in
            for routeId in allIds {
                group.addTask {
                    let isNew = !knownIds.contains(routeId)
                    let updated = await self.fetchRoute(routeId)
                    if isNew && updated {
                        await self.fetchPolylinesForNewRoute(routeId)
                    }
                }
            }
        }
        DebugConfig.debugPrint("TimetableCacheService: Fetch complete, \(pendingUpdates.count) route(s) updated")
    }
}
