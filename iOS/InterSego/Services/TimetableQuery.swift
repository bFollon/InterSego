/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Utility for querying timetables based on date and optional filters.
enum TimetableQuery {
    /// Returns the set of DayTypes applicable on the given date.
    /// - Parameter date: The calendar date to query
    /// - Returns: Set of applicable DayTypes. For example:
    ///   - Saturday → {.saturday, .weekend}
    ///   - Sunday → {.sunday, .weekend, .holiday}
    ///   - Weekday → {.weekday}
    static func dayTypesForDate(_ date: Date) -> Set<DayType> {
        let weekday = Calendar.current.component(.weekday, from: date)
        switch weekday {
        case 7:
            return Set([.saturday, .weekend])
        case 1:
            return Set([.sunday, .weekend, .holiday])
        default:
            return Set([.weekday])
        }
    }

    /// Filters timetables matching the given date and optional criteria.
    /// - Parameters:
    ///   - timetables: The list of timetables to filter
    ///   - date: The calendar date to filter for
    ///   - routeId: Optional route ID to filter by; if provided, only matching routes are returned
    ///   - stopId: Optional stop ID to filter by; if provided, only matching stops are returned
    ///   - direction: Optional direction to filter by; if provided, only matching directions are returned
    /// - Returns: BusTimetable records matching the criteria. Caller should call `.seasonalDepartures(month:weekday:)`
    ///   on each record to further filter by seasonal availability (school-only, summer-only, etc.)
    static func filterTimetables(
        _ timetables: [BusTimetable],
        date: Date,
        routeId: String? = nil,
        stopId: String? = nil,
        direction: String? = nil
    ) -> [BusTimetable] {
        let dayTypes = dayTypesForDate(date)
        return timetables.filter { timetable in
            dayTypes.contains(timetable.dayType)
                && (routeId == nil || timetable.routeId == routeId)
                && (stopId == nil || timetable.stopId == stopId)
                && (direction == nil || timetable.direction == direction)
        }
    }
}
