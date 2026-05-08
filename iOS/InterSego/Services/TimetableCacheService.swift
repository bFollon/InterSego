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

    private let routes = ["m1", "m2", "m3", "m4", "m5", "m6", "m7", "m7-ave", "m8"]

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

    /// Fetches all routes in parallel. Updated routes are flagged via `hasPendingUpdate(_:)`.
    func fetchAllRoutes() async {
        DebugConfig.debugPrint("TimetableCacheService: Starting fetch for \(routes.count) routes")
        await withTaskGroup(of: Void.self) { group in
            for routeId in routes {
                group.addTask { await self.fetchRoute(routeId) }
            }
        }
        DebugConfig.debugPrint("TimetableCacheService: Fetch complete, \(pendingUpdates.count) route(s) updated")
    }
}
