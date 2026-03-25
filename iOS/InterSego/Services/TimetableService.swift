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

/// Service for loading and managing bus timetables.
///
/// Implements three-tier caching pattern:
/// 1. Memory cache (fastest)
/// 2. Persistent cache via TimetableCacheService (fast)
/// 3. PDF parsing via PDFProcessingService (slowest, source of truth)
actor TimetableService {
    static let shared = TimetableService()

    private var cachedTimetables: [String: [BusTimetable]] = [:]

    private init() {}

    func loadTimetables(routeId: String, forceRefresh: Bool = false) async -> [BusTimetable] {
        let isDebugParser = await PDFProcessingService.shared.isDebugParser(routeId: routeId)
        let parserVersion = await PDFProcessingService.shared.getParserVersion(routeId: routeId)
        let shouldForceRefresh = forceRefresh || isDebugParser

        if isDebugParser {
            DebugConfig.debugPrint("TimetableService: DEBUG parser detected for \(routeId) - bypassing cache")
        }

        DebugConfig.debugPrint("TimetableService: Loading timetables for route \(routeId) (forceRefresh: \(shouldForceRefresh))")

        // Tier 1: Memory cache
        if !shouldForceRefresh, let cached = cachedTimetables[routeId] {
            DebugConfig.debugPrint("TimetableService: Using memory cache for route \(routeId)")
            return cached
        }

        // Tier 2: Persistent cache
        if !shouldForceRefresh {
            if let cachedData = await TimetableCacheService.shared.loadCachedTimetables(routeId: routeId, parserVersion: parserVersion) {
                DebugConfig.debugPrint("TimetableService: Using persistent cache for route \(routeId)")
                cachedTimetables[routeId] = cachedData
                return cachedData
            }
        }

        // Tier 3: Parse PDF
        DebugConfig.debugPrint("TimetableService: Parsing PDF for route \(routeId)")

        do {
            let timetables = try await PDFProcessingService.shared.parseTimetables(routeId: routeId)

            cachedTimetables[routeId] = timetables
            await TimetableCacheService.shared.saveTimetablesToCache(routeId: routeId, timetables: timetables, parserVersion: parserVersion)

            DebugConfig.debugPrint("TimetableService: Successfully loaded and cached \(timetables.count) timetables for \(routeId)")
            return timetables

        } catch {
            DebugConfig.debugError("TimetableService: Failed to parse PDF for \(routeId)", error: error)
            return []
        }
    }

    func findTimetableForDayType(_ timetables: [BusTimetable], dayType: DayType) -> BusTimetable? {
        let result = timetables.first { $0.dayType == dayType }
        if let result {
            DebugConfig.debugPrint("TimetableService: Found timetable for \(dayType) with \(result.departures.count) departures")
        } else {
            DebugConfig.debugPrint("TimetableService: No timetable found for \(dayType)")
        }
        return result
    }

    func getNextDepartures(
        timetables: [BusTimetable],
        dayType: DayType,
        currentHour: Int,
        currentMinute: Int,
        limit: Int = 5,
    ) -> [DepartureTime] {
        guard let timetable = findTimetableForDayType(timetables, dayType: dayType) else { return [] }
        return timetable.getNextDepartures(currentHour: currentHour, currentMinute: currentMinute, limit: limit)
    }

    func isLoaded(routeId: String) -> Bool {
        cachedTimetables[routeId] != nil
    }

    func clearCache() async {
        cachedTimetables.removeAll()
        await TimetableCacheService.shared.clearAllCache()
        DebugConfig.debugPrint("TimetableService: All caches cleared")
    }

    func clearCacheForRoute(routeId: String) async {
        cachedTimetables.removeValue(forKey: routeId)
        await TimetableCacheService.shared.clearRouteCache(routeId: routeId)
        DebugConfig.debugPrint("TimetableService: Cleared cache for route \(routeId)")
    }

    nonisolated func getCurrentDayType() -> DayType {
        let weekday = Calendar.current.component(.weekday, from: Date())
        switch weekday {
        case 7: return .saturday
        case 1: return .sunday
        default: return .weekday
        }
    }

    nonisolated func getCurrentTime() -> (hour: Int, minute: Int) {
        let hour = Calendar.current.component(.hour, from: Date())
        let minute = Calendar.current.component(.minute, from: Date())
        return (hour, minute)
    }
}
