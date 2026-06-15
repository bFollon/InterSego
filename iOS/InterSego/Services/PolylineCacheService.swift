/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Fetches route polyline JSON files from the server and caches them to disk.
///
/// Uses ETag-based conditional requests (If-None-Match / 304) to avoid re-downloading
/// unchanged polylines. `PolylineLoader` checks the disk cache before falling back to
/// the bundled resource, so a successful fetch is picked up the next time a route map
/// is opened — no in-memory cache to evict, unlike `TimetableCacheService`'s pendingUpdates.
///
/// Disk layout: `Caches/RoutePolylines/{routeId}-{viewId}.json`
/// ETag storage: `UserDefaults`, key `"polyline_etag_{routeId}-{viewId}"` (lowercase)
actor PolylineCacheService {
    static let shared = PolylineCacheService()

    private let session: URLSession

    private var bundlePolylineIds: [String] {
        guard let urls = Bundle.main.urls(forResourcesWithExtension: "json", subdirectory: "RoutePolylines") else { return [] }
        return urls.map { $0.deletingPathExtension().lastPathComponent }
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
            .appendingPathComponent("RoutePolylines")
    }

    nonisolated static func cacheURL(for id: String) -> URL? {
        cacheDirectory()?.appendingPathComponent("\(id).json")
    }

    // MARK: - ETag persistence

    private func storedEtag(for id: String) -> String? {
        UserDefaults.standard.string(forKey: "polyline_etag_\(id.lowercased())")
    }

    private func saveEtag(_ etag: String, for id: String) {
        UserDefaults.standard.set(etag, forKey: "polyline_etag_\(id.lowercased())")
    }

    // MARK: - Fetch

    @discardableResult
    func fetchPolyline(_ id: String) async -> Bool {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/api/polylines/\(id)") else {
            return false
        }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(AppConfig.serverAPIKey)", forHTTPHeaderField: "Authorization")
        if let etag = storedEtag(for: id) {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { return false }
            DebugConfig.debugPrint("PolylineCacheService: GET /api/polylines/\(id) → HTTP \(http.statusCode)")
            switch http.statusCode {
            case 200:
                guard let fileURL = Self.cacheURL(for: id) else { return false }
                try FileManager.default.createDirectory(
                    at: fileURL.deletingLastPathComponent(),
                    withIntermediateDirectories: true
                )
                try data.write(to: fileURL)
                if let etag = http.value(forHTTPHeaderField: "ETag") {
                    saveEtag(etag, for: id)
                }
                DebugConfig.debugPrint("PolylineCacheService: Updated \(id) (\(data.count) bytes)")
                return true
            case 304:
                DebugConfig.debugPrint("PolylineCacheService: \(id) unchanged (304)")
                return false
            default:
                DebugConfig.debugError("PolylineCacheService: \(id) fetch failed HTTP \(http.statusCode)")
                return false
            }
        } catch {
            DebugConfig.debugError("PolylineCacheService: \(id) fetch error", error: error)
            return false
        }
    }

    /// Fetches all polylines in parallel.
    func fetchAllPolylines() async {
        let ids = bundlePolylineIds
        DebugConfig.debugPrint("PolylineCacheService: Starting fetch for \(ids.count) polylines")
        var updatedCount = 0
        await withTaskGroup(of: Bool.self) { group in
            for id in ids {
                group.addTask { await self.fetchPolyline(id) }
            }
            for await updated in group where updated {
                updatedCount += 1
            }
        }
        DebugConfig.debugPrint("PolylineCacheService: Fetch complete, \(updatedCount) polyline(s) updated")
    }
}
