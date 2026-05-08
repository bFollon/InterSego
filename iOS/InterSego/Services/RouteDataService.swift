/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Coordinator service for timetable and route metadata queries.
///
/// All data is sourced from bundled JSON assets via TimetableLoader.
actor RouteDataService {
    static let shared = RouteDataService()

    private let loadedRouteIds: Set<String>
    private var stopToRoutes: [String: [String]] = [:]

    private init() {
        let loader = TimetableLoader()
        let allRoutes = loader.loadAllRoutes()
        loadedRouteIds = Set(allRoutes.map(\.id))
        for routeId in loadedRouteIds {
            let allRouteStops = (try? loader.loadRoutesForId(routeId)) ?? []
            let uniqueStopIds = Set(allRouteStops.flatMap { $0 }.map(\.id))
            for stopId in uniqueStopIds {
                stopToRoutes[stopId, default: []].append(routeId)
            }
        }
        DebugConfig.debugPrint("RouteDataService: Initialized with \(loadedRouteIds.count) routes, \(stopToRoutes.count) stops indexed")
    }

    func parseTimetables(routeId: String) async throws -> [BusTimetable] {
        DebugConfig.debugPrint("RouteDataService: Loading timetables for route \(routeId)")
        return try TimetableLoader().load(routeId)
    }

    func hasParserFor(routeId: String) -> Bool {
        loadedRouteIds.contains(routeId)
    }

    func getSupportedRoutes() -> [String] {
        Array(loadedRouteIds)
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
