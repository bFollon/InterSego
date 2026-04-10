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
import SwiftUI

// MARK: - Data Structures

/// A departure time tagged with the route it belongs to.
/// Used when displaying merged departures from multiple lines.
struct TaggedDeparture: Identifiable {
    let id = UUID()
    let departure: DepartureTime
    let routeId: String
    let routeNumber: String
}

/// Loaded data for a single route: route metadata, route views, and timetables.
struct RouteLoadedData {
    let route: BusRoute
    let views: [RouteView]
    let timetables: [BusTimetable]
}

/// All departures for a given stop, organized by day.
/// todayTaggedDepartures: departures for today, filtered by stop, dayType, and season
/// nextDayTaggedDepartures: dictionary of daysAhead (1-7) to departures for that day
/// routes: all loaded route data
struct DeparturesData {
    let todayTaggedDepartures: [TaggedDeparture]
    let nextDayTaggedDepartures: [Int: [TaggedDeparture]]
    let routes: [RouteLoadedData]
}

// MARK: - Service

/// Service for loading and organizing departure data for a specific stop.
/// Encapsulates all the logic for fetching timetables, filtering by day type/season,
/// and preparing departures for display.
///
/// Used by both NextDepartureView (for live departures) and DirectionPickerView
/// (for listing available directions).
actor DeparturesService {
    private let pdfService: PDFProcessingService
    private let timetableService: TimetableService

    nonisolated static let shared = DeparturesService()

    private init() {
        self.pdfService = PDFProcessingService.shared
        self.timetableService = TimetableService.shared
    }

    /// Load all departures for a specific stop.
    ///
    /// - Parameters:
    ///   - stop: The bus stop to load departures for
    ///   - allRoutes: All available routes (used for metadata lookup)
    ///   - primaryRouteId: Optional route ID to filter to a single route
    /// - Returns: DeparturesData containing today's and future departures
    func loadDepartures(
        stop: BusStop,
        allRoutes: [BusRoute],
        primaryRouteId: String? = nil
    ) async -> DeparturesData {
        // Determine which routes to load
        let routeIds: [String]
        if let primaryRouteId, primaryRouteId != "none" {
            routeIds = [primaryRouteId]
        } else {
            let all = await pdfService.getRoutesForStop(stopId: stop.id)
            routeIds = all.sorted()
        }

        // Load route data for all routes serving this stop
        var loadedRoutes: [RouteLoadedData] = []
        let currentDayType = getCurrentDayType()

        for routeId in routeIds {
            guard let route = allRoutes.first(where: { $0.id == routeId }) else { continue }
            let timetables = await timetableService.loadTimetables(routeId: routeId)
            guard !timetables.isEmpty else { continue }
            let views = await pdfService.getRouteViews(routeId: routeId, dayType: currentDayType)
            loadedRoutes.append(RouteLoadedData(route: route, views: views, timetables: timetables))
        }

        // Get today's day types and current day of week
        let currentWeekday = Calendar.current.component(.weekday, from: Date())
        let currentDayTypes = dayTypesForCalendarDay(currentWeekday)

        // Build today's departures for all available directions
        let todayDepartures = buildTodayDepartures(
            loadedRoutes, stop: stop, dayTypes: currentDayTypes, weekday: currentWeekday
        )

        // Build future departures (up to 7 days ahead) for each direction
        let nextDayDepartures = buildNextDayDepartures(loadedRoutes, stop: stop)

        return DeparturesData(
            todayTaggedDepartures: todayDepartures,
            nextDayTaggedDepartures: nextDayDepartures,
            routes: loadedRoutes
        )
    }

    /// Build departures for all available directions on today.
    /// Returns a flat list organized by direction.
    private func buildTodayDepartures(
        _ loadedRoutes: [RouteLoadedData],
        stop: BusStop,
        dayTypes: Set<DayType>,
        weekday: Int
    ) -> [TaggedDeparture] {
        var result: [TaggedDeparture] = []
        for routeData in loadedRoutes {
            let matching = routeData.timetables.filter { t in
                dayTypes.contains(t.dayType) && t.stopId == stop.id
            }
            for dep in matching.flatMap({ $0.seasonalDepartures(weekday: weekday) }).sorted() {
                result.append(TaggedDeparture(
                    departure: dep, routeId: routeData.route.id,
                    routeNumber: routeData.route.number,
                ))
            }
        }
        return result.sorted { $0.departure < $1.departure }
    }

    /// Build future departures (1-7 days ahead) for each available direction.
    /// Returns a dictionary keyed by daysAhead, containing departures for that day.
    private func buildNextDayDepartures(
        _ loadedRoutes: [RouteLoadedData],
        stop: BusStop
    ) -> [Int: [TaggedDeparture]] {
        var result: [Int: [TaggedDeparture]] = [:]

        for daysAhead in 1 ... 7 {
            guard let futureDate = Calendar.current.date(byAdding: .day, value: daysAhead, to: Date()) else { continue }
            let futureWeekday = Calendar.current.component(.weekday, from: futureDate)
            let futureDayTypes = dayTypesForCalendarDay(futureWeekday)

            var tagged: [TaggedDeparture] = []
            for routeData in loadedRoutes {
                let deps = routeData.timetables
                    .filter { futureDayTypes.contains($0.dayType) && $0.stopId == stop.id }
                    .flatMap { $0.seasonalDepartures(weekday: futureWeekday) }
                    .sorted()
                for dep in deps {
                    tagged.append(TaggedDeparture(
                        departure: dep, routeId: routeData.route.id,
                        routeNumber: routeData.route.number,
                    ))
                }
            }
            let sorted = tagged.sorted { $0.departure < $1.departure }
            if !sorted.isEmpty {
                result[daysAhead] = sorted
            }
        }

        return result
    }

    /// Map a Calendar weekday to the set of DayType values that might match.
    /// Saturday = 7, Sunday = 1 in Calendar.
    /// M4 uses WEEKEND for Saturday; M6 uses SATURDAY and SUNDAY separately.
    private func dayTypesForCalendarDay(_ weekday: Int) -> Set<DayType> {
        switch weekday {
        case 7: [.saturday, .weekend]
        case 1: [.sunday, .weekend, .holiday]
        default: [.weekday]
        }
    }

    /// Get the current day type based on today's date.
    private func getCurrentDayType() -> DayType {
        let weekday = Calendar.current.component(.weekday, from: Date())
        switch weekday {
        case 7: return .saturday
        case 1: return .sunday
        default: return .weekday
        }
    }
}
