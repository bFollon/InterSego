/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import Foundation

actor TimetableCacheService {
    static let shared = TimetableCacheService()

    private let cacheDir: URL

    private init() {
        let documentsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        cacheDir = documentsDir.appendingPathComponent("TimetableCache")
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        DebugConfig.debugPrint("TimetableCacheService: Cache directory at \(cacheDir.path)")
    }

    func isCacheValid(routeId: String, parserVersion: String) -> Bool {
        let cacheFile = getCacheFile(routeId: routeId)
        let metadataFile = getMetadataFile(routeId: routeId)

        guard FileManager.default.fileExists(atPath: cacheFile.path),
              FileManager.default.fileExists(atPath: metadataFile.path)
        else {
            return false
        }

        do {
            let metaData = try Data(contentsOf: metadataFile)
            let metadata = try JSONDecoder().decode(CacheMetadata.self, from: metaData)

            guard metadata.parserVersion == parserVersion else {
                DebugConfig.debugPrint("TimetableCacheService: Cache invalid for route \(routeId) - parser version mismatch (cached: \(metadata.parserVersion ?? "nil"), current: \(parserVersion))")
                return false
            }

            let pdfFile = pdfFileURL(routeId: routeId)

            guard FileManager.default.fileExists(atPath: pdfFile.path) else {
                DebugConfig.debugPrint("TimetableCacheService: PDF file not found for route \(routeId), cache invalid")
                return false
            }

            let attrs = try FileManager.default.attributesOfItem(atPath: pdfFile.path)
            let pdfLastModified = (attrs[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0

            let isValid = pdfLastModified <= metadata.pdfLastModified
            if isValid {
                DebugConfig.debugPrint("TimetableCacheService: Cache valid for route \(routeId)")
            } else {
                DebugConfig.debugPrint("TimetableCacheService: Cache invalid for route \(routeId) - PDF newer than cache")
            }
            return isValid

        } catch {
            DebugConfig.debugError("TimetableCacheService: Error checking cache validity for route \(routeId)", error: error)
            return false
        }
    }

    func loadCachedTimetables(routeId: String, parserVersion: String) -> [BusTimetable]? {
        guard isCacheValid(routeId: routeId, parserVersion: parserVersion) else { return nil }

        let cacheFile = getCacheFile(routeId: routeId)

        do {
            let data = try Data(contentsOf: cacheFile)
            let cachedData = try JSONDecoder().decode(CachedTimetables.self, from: data)
            DebugConfig.debugPrint("TimetableCacheService: Loaded \(cachedData.timetables.count) cached timetables for route \(routeId)")
            return cachedData.timetables
        } catch {
            DebugConfig.debugError("TimetableCacheService: Error loading cached timetables for route \(routeId)", error: error)
            deleteCacheFiles(routeId: routeId)
            return nil
        }
    }

    func saveTimetablesToCache(routeId: String, timetables: [BusTimetable], parserVersion: String) {
        do {
            let cachedData = CachedTimetables(
                routeId: routeId,
                timetables: timetables,
                cacheTimestamp: Date().timeIntervalSince1970
            )
            let encoder = JSONEncoder()
            encoder.outputFormatting = .prettyPrinted
            let data = try encoder.encode(cachedData)
            try data.write(to: getCacheFile(routeId: routeId))

            let pdfFile = pdfFileURL(routeId: routeId)
            let pdfLastModified: TimeInterval
            if FileManager.default.fileExists(atPath: pdfFile.path) {
                let attrs = try FileManager.default.attributesOfItem(atPath: pdfFile.path)
                pdfLastModified = (attrs[.modificationDate] as? Date)?.timeIntervalSince1970 ?? Date().timeIntervalSince1970
            } else {
                pdfLastModified = Date().timeIntervalSince1970
            }

            let metadata = CacheMetadata(
                routeId: routeId,
                timetableCount: timetables.count,
                cacheTimestamp: Date().timeIntervalSince1970,
                pdfLastModified: pdfLastModified,
                parserVersion: parserVersion
            )
            let metaData = try encoder.encode(metadata)
            try metaData.write(to: getMetadataFile(routeId: routeId))

            DebugConfig.debugPrint("TimetableCacheService: Cached \(timetables.count) timetables for route \(routeId)")

        } catch {
            DebugConfig.debugError("TimetableCacheService: Error saving timetables to cache for route \(routeId)", error: error)
        }
    }

    func clearRouteCache(routeId: String) {
        deleteCacheFiles(routeId: routeId)
        DebugConfig.debugPrint("TimetableCacheService: Cleared cache for route \(routeId)")
    }

    func clearAllCache() {
        let files = try? FileManager.default.contentsOfDirectory(at: cacheDir, includingPropertiesForKeys: nil)
        files?.forEach { try? FileManager.default.removeItem(at: $0) }
        DebugConfig.debugPrint("TimetableCacheService: Cleared all timetable caches")
    }

    // MARK: - Private

    private func getCacheFile(routeId: String) -> URL {
        cacheDir.appendingPathComponent("\(routeId).json")
    }

    private func getMetadataFile(routeId: String) -> URL {
        cacheDir.appendingPathComponent("\(routeId).meta.json")
    }

    private func pdfFileURL(routeId: String) -> URL {
        AppDirectories.pdfs.appendingPathComponent("\(routeId.lowercased()).pdf")
    }

    private func deleteCacheFiles(routeId: String) {
        try? FileManager.default.removeItem(at: getCacheFile(routeId: routeId))
        try? FileManager.default.removeItem(at: getMetadataFile(routeId: routeId))
    }

    private struct CachedTimetables: Codable {
        let routeId: String
        let timetables: [BusTimetable]
        let cacheTimestamp: TimeInterval
    }

    private struct CacheMetadata: Codable {
        let routeId: String
        let timetableCount: Int
        let cacheTimestamp: TimeInterval
        let pdfLastModified: TimeInterval
        /// Parser version at write time. Nil for caches written before versioning
        /// was introduced — treated as a version mismatch (stale).
        let parserVersion: String?
    }
}
