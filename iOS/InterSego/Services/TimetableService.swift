/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

actor TimetableService {
    static let shared = TimetableService()

    private var cachedTimetables: [String: [BusTimetable]] = [:]

    private init() {}

    func loadTimetables(routeId: String, forceRefresh: Bool = false) async -> [BusTimetable] {
        let isDebugParser = await RouteDataService.shared.isDebugParser(routeId: routeId)
        if await TimetableCacheService.shared.hasPendingUpdate(routeId) {
            await TimetableCacheService.shared.clearPendingUpdate(routeId)
            cachedTimetables.removeValue(forKey: routeId)
            DebugConfig.debugPrint("TimetableService: Disk update detected for \(routeId) — clearing memory cache")
        }
        let shouldForceRefresh = forceRefresh || isDebugParser

        if isDebugParser {
            DebugConfig.debugPrint("TimetableService: DEBUG parser detected for \(routeId) - bypassing cache")
        }

        DebugConfig.debugPrint("TimetableService: Loading timetables for route \(routeId) (forceRefresh: \(shouldForceRefresh))")

        if !shouldForceRefresh, let cached = cachedTimetables[routeId] {
            DebugConfig.debugPrint("TimetableService: Using memory cache for route \(routeId)")
            return cached
        }

        DebugConfig.debugPrint("TimetableService: Loading JSON for route \(routeId)")

        do {
            let timetables = try await RouteDataService.shared.parseTimetables(routeId: routeId)
            cachedTimetables[routeId] = timetables
            DebugConfig.debugPrint("TimetableService: Successfully loaded \(timetables.count) timetables for \(routeId)")
            return timetables
        } catch {
            DebugConfig.debugError("TimetableService: Failed to load JSON for \(routeId)", error: error)
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

    func clearCache() {
        cachedTimetables.removeAll()
        DebugConfig.debugPrint("TimetableService: Cache cleared")
    }

    func clearCacheForRoute(routeId: String) {
        cachedTimetables.removeValue(forKey: routeId)
        DebugConfig.debugPrint("TimetableService: Cleared cache for route \(routeId)")
    }

    nonisolated func getCurrentDayType() -> DayType {
        TimetableQuery.primaryDayType()
    }

    nonisolated func getCurrentTime() -> (hour: Int, minute: Int) {
        let hour = Calendar.current.component(.hour, from: Date())
        let minute = Calendar.current.component(.minute, from: Date())
        return (hour, minute)
    }
}
