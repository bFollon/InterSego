/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
    private let pdfService: RouteDataService
    private let timetableService: TimetableService

    nonisolated static let shared = DeparturesService()

    private init() {
        self.pdfService = RouteDataService.shared
        self.timetableService = TimetableService.shared
    }

    /// Load all departures for a specific stop.
    ///
    /// - Parameters:
    ///   - stop: The bus stop to load departures for
    ///   - allRoutes: All available routes (used for metadata lookup)
    ///   - primaryRouteId: Optional route ID to filter to a single route
    ///   - referenceDate: The date to resolve views/day-type against; defaults to today. Pass a
    ///     specific date (e.g. from the "Consultar otro día" flow) so the returned views/directions
    ///     match that date's day type rather than today's — otherwise resolved view IDs won't be
    ///     found in a views list fetched for a different day type.
    /// - Returns: DeparturesData containing today's (or referenceDate's) and future departures
    func loadDepartures(
        stop: BusStop,
        allRoutes: [BusRoute],
        primaryRouteId: String? = nil,
        referenceDate: Date = Date()
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
        let currentDayTypes = TimetableQuery.dayTypesForDate(referenceDate)
        let currentDayType = TimetableQuery.primaryDayType(referenceDate)
        let currentWeekday = Calendar.current.component(.weekday, from: referenceDate)

        for routeId in routeIds {
            guard let route = allRoutes.first(where: { $0.id == routeId }) else { continue }
            let timetables = await timetableService.loadTimetables(routeId: routeId)
            guard !timetables.isEmpty else { continue }
            var views = await pdfService.getRouteViews(routeId: routeId, dayType: currentDayType)
            if views.isEmpty {
                for dayType in [DayType.weekday, .saturday, .sunday, .weekend, .holiday] {
                    let v = await pdfService.getRouteViews(routeId: routeId, dayType: dayType)
                    if !v.isEmpty { views = v; break }
                }
            }
            loadedRoutes.append(RouteLoadedData(route: route, views: views, timetables: timetables))
        }

        // Build today's departures for all available directions
        let todayDepartures = buildTodayDepartures(
            loadedRoutes, stop: stop, dayTypes: currentDayTypes, weekday: currentWeekday
        )

        // Build future departures (up to 7 days ahead of referenceDate) for each direction
        let nextDayDepartures = buildNextDayDepartures(loadedRoutes, stop: stop, referenceDate: referenceDate)

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
        primaryRouteId: String?,
        referenceDate: Date = Date()
    ) async -> (String, String)? {
        let data = await loadDepartures(stop: stop, allRoutes: allRoutes, primaryRouteId: primaryRouteId, referenceDate: referenceDate)
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
        stop: BusStop,
        referenceDate: Date
    ) -> [Int: [TaggedDeparture]] {
        var result: [Int: [TaggedDeparture]] = [:]

        for daysAhead in 1 ... 7 {
            guard let futureDate = Calendar.current.date(byAdding: .day, value: daysAhead, to: referenceDate) else { continue }
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
