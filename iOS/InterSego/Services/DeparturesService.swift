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
    let direction: String
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
        let today = Date()
        let currentDayTypes = TimetableQuery.dayTypesForDate(today)
        let currentDayType: DayType = currentDayTypes.contains(.saturday) ? .saturday :
                             currentDayTypes.contains(.sunday) ? .sunday :
                             .weekday
        let currentWeekday = Calendar.current.component(.weekday, from: today)

        for routeId in routeIds {
            guard let route = allRoutes.first(where: { $0.id == routeId }) else { continue }
            let timetables = await timetableService.loadTimetables(routeId: routeId)
            guard !timetables.isEmpty else { continue }
            let views = await pdfService.getRouteViews(routeId: routeId, dayType: currentDayType)
            loadedRoutes.append(RouteLoadedData(route: route, views: views, timetables: timetables))
        }

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

    /// Returns (routeId, viewId) if there is exactly one direction option for this stop,
    /// otherwise nil (the direction picker should be shown to let the user choose).
    func resolveDirectionIfUnambiguous(
        stop: BusStop,
        allRoutes: [BusRoute],
        primaryRouteId: String?
    ) async -> (String, String)? {
        let data = await loadDepartures(stop: stop, allRoutes: allRoutes, primaryRouteId: primaryRouteId)
        var count = 0
        var resolved: (String, String)? = nil

        for routeData in data.routes {
            if let mergedView = routeData.views.first(where: { $0.mergedDirectionLabel != nil }) {
                count += 1
                resolved = (routeData.route.id, mergedView.id)
                continue
            }
            var seen = Set<String>()
            for t in routeData.timetables where t.stopId == stop.id {
                guard let dir = t.direction, seen.insert(dir).inserted else { continue }
                if let view = routeData.views.first(where: { $0.direction == dir }) {
                    count += 1
                    resolved = (routeData.route.id, view.id)
                }
            }
        }

        return count == 1 ? resolved : nil
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
            let matching = routeData.timetables.filter { dayTypes.contains($0.dayType) && $0.stopId == stop.id }
            for timetable in matching {
                for dep in timetable.seasonalDepartures(weekday: weekday).sorted() {
                    result.append(TaggedDeparture(
                        departure: dep, routeId: routeData.route.id,
                        routeNumber: routeData.route.number,
                        direction: timetable.direction ?? "",
                    ))
                }
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

            var tagged: [TaggedDeparture] = []
            for routeData in loadedRoutes {
                let matching = TimetableQuery.filterTimetables(routeData.timetables, date: futureDate, stopId: stop.id)
                for timetable in matching {
                    for dep in timetable.seasonalDepartures(weekday: futureWeekday).sorted() {
                        tagged.append(TaggedDeparture(
                            departure: dep, routeId: routeData.route.id,
                            routeNumber: routeData.route.number,
                            direction: timetable.direction ?? "",
                        ))
                    }
                }
            }
            let sorted = tagged.sorted { $0.departure < $1.departure }
            if !sorted.isEmpty {
                result[daysAhead] = sorted
            }
        }

        return result
    }

}
