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
    /// - Saturday → {.saturday, .weekend}
    /// - Sunday → {.sunday, .weekend, .holiday}
    /// - A festivo (per `isHoliday`) on any other weekday → {.sunday, .weekend, .holiday}, same as Sunday
    /// - Weekday → {.weekday}
    ///
    /// `isHoliday` defaults to `HolidayService.isHoliday` but is overridable so this stays a
    /// pure, testable function — it never reaches into the actor itself. If no calendar has
    /// been loaded (offline first launch), `isHoliday` returns false for every date and this
    /// falls back to plain weekday/Saturday/Sunday resolution.
    /// - Parameters:
    ///   - date: The calendar date to query
    ///   - isHoliday: Festivo lookup, defaulting to `HolidayService.isHoliday`
    static func dayTypesForDate(_ date: Date, isHoliday: (Date) -> Bool = HolidayService.isHoliday) -> Set<DayType> {
        let weekday = Calendar.current.component(.weekday, from: date)
        if weekday != 1 && isHoliday(date) {
            return Set([.sunday, .weekend, .holiday])
        }
        switch weekday {
        case 7:
            return Set([.saturday, .weekend])
        case 1:
            return Set([.sunday, .weekend, .holiday])
        default:
            return Set([.weekday])
        }
    }

    /// Single-value day type for the given date — `.saturday`/`.sunday`/`.weekday` only.
    /// `.holiday` is folded into `.sunday` here since a festivo already means Sunday service;
    /// convenience for callers that need one value rather than the filterable set from
    /// `dayTypesForDate`.
    static func primaryDayType(_ date: Date = Date(), isHoliday: (Date) -> Bool = HolidayService.isHoliday) -> DayType {
        let types = dayTypesForDate(date, isHoliday: isHoliday)
        if types.contains(.sunday) { return .sunday }
        if types.contains(.saturday) { return .saturday }
        return .weekday
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
