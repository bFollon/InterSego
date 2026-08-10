/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Builds the deduplicated (by physicalStopId) directory of every stop across every route, for
/// the journey planner's origin/destination picker. A stop that carries alias ids (e.g.
/// `estacion-autobuses-circ-ret`) appears once, under its canonical physicalStopId, with the
/// union of routes serving any of its aliases.
enum StopDirectoryService {

    struct Entry: Identifiable, Hashable {
        let physicalStopId: String
        let stop: BusStop
        let routeIds: [String]
        var id: String { physicalStopId }
    }

    static func buildDirectory(routeIds: [String]) -> [Entry] {
        let loader = TimetableLoader()
        var canonicalStops: [String: BusStop] = [:]
        var routesByPhysicalId: [String: Set<String>] = [:]

        for routeId in routeIds {
            guard let stopsById = try? loader.loadBusStopsById(routeId),
                  let physicalIds = try? loader.loadPhysicalStopIds(routeId) else { continue }

            for (rawId, stop) in stopsById {
                let physicalId = physicalIds[rawId] ?? rawId
                routesByPhysicalId[physicalId, default: []].insert(routeId)
                // Prefer the entry whose own id *is* the physicalStopId (the canonical, non-alias one).
                if canonicalStops[physicalId] == nil || rawId == physicalId {
                    canonicalStops[physicalId] = stop
                }
            }
        }

        return canonicalStops.map { physicalId, stop in
            Entry(physicalStopId: physicalId, stop: stop, routeIds: (routesByPhysicalId[physicalId] ?? []).sorted())
        }.sorted { $0.stop.name < $1.stop.name }
    }
}
