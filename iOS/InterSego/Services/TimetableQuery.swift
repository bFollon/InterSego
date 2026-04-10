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
