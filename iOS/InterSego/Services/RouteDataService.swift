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

/// Coordinator service for timetable and route metadata queries.
///
/// All data is sourced from bundled JSON assets via TimetableLoader.
actor RouteDataService {
    static let shared = RouteDataService()

    private let supportedRoutes = ["M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8"]
    private var stopToRoutes: [String: [String]] = [:]

    private init() {
        let loader = TimetableLoader()
        for routeId in supportedRoutes {
            let allRouteStops = (try? loader.loadRoutesForId(routeId)) ?? []
            let uniqueStopIds = Set(allRouteStops.flatMap { $0 }.map(\.id))
            for stopId in uniqueStopIds {
                stopToRoutes[stopId, default: []].append(routeId)
            }
        }
        DebugConfig.debugPrint("RouteDataService: Initialized with \(supportedRoutes.count) routes, \(stopToRoutes.count) stops indexed")
    }

    func parseTimetables(routeId: String) async throws -> [BusTimetable] {
        DebugConfig.debugPrint("RouteDataService: Loading timetables for route \(routeId)")
        return try TimetableLoader().load(routeId)
    }

    func hasParserFor(routeId: String) -> Bool {
        supportedRoutes.contains(routeId)
    }

    func getSupportedRoutes() -> [String] {
        supportedRoutes
    }

    func getParserVersion(routeId: String) -> String {
        (try? TimetableLoader().getVersion(routeId)) ?? "1.0"
    }

    func isDebugParser(routeId: String) -> Bool { false }

    func getRoutesForNavigation(routeId: String) -> [[BusStop]] {
        (try? TimetableLoader().loadRoutesForId(routeId)) ?? []
    }

    func getRouteVariants(routeId: String, dayType: DayType) -> [RouteVariant] {
        (try? TimetableLoader().loadRouteVariants(routeId, dayType: dayType)) ?? []
    }

    func getRouteEntries(routeId: String, today: Date = Date()) -> [RouteSelectorEntry] {
        (try? TimetableLoader().loadRouteEntries(routeId, today: today)) ?? []
    }

    func getRoutesForStop(stopId: String) -> [String] {
        stopToRoutes[stopId] ?? []
    }

    func getRouteViews(routeId: String, dayType: DayType) -> [RouteView] {
        (try? TimetableLoader().loadRouteViews(routeId, dayType: dayType)) ?? []
    }
}
