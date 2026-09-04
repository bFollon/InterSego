/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import MapKit
import SwiftUI

/// Cycled per ride leg (in leg order, ignoring walk legs) so consecutive rides are visually distinct.
private let legColors: [Color] = [
    Color(red: 0.11, green: 0.455, blue: 0.827), // blue
    .orange,
    .green,
    .purple,
    .red,
    .teal,
]

/// Overview map for a whole `Journey`: one colored polyline per ride leg (loaded via
/// `PolylineLoader`, same road-following data as `RouteMapView`) plus a dashed straight line for
/// any walk legs, with markers at the origin, destination, and any transfer points in between.
struct ItineraryMapView: View {
    let journey: Journey
    let stops: [String: BusStop]

    @State private var cameraPosition: MapCameraPosition = .automatic
    @State private var ridePolylines: [Int: [CLLocationCoordinate2D]] = [:]
    @State private var hasLoaded = false

    private struct Waypoint: Identifiable {
        let id: String
        let role: String
    }

    private func coordinate(for stopId: String) -> CLLocationCoordinate2D? {
        guard let stop = stops[stopId], let lat = stop.resolvedLatitude, let lon = stop.resolvedLongitude else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }

    /// Prefers `routingLatitude`/`routingLongitude` since those track the road the polyline
    /// follows more closely than the physical stop location (see `BusStop.routingCoordinates`).
    private func routingCoordinate(for stopId: String) -> CLLocationCoordinate2D? {
        guard let stop = stops[stopId], let lat = stop.routingLatitude, let lon = stop.routingLongitude else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lon)
    }

    private func nearestIndex(in polyline: [CLLocationCoordinate2D], to point: CLLocationCoordinate2D) -> Int {
        var bestIndex = 0
        var bestDist = Double.greatestFiniteMagnitude
        for (index, p) in polyline.enumerated() {
            let dLat = p.latitude - point.latitude
            let dLon = p.longitude - point.longitude
            let dist = dLat * dLat + dLon * dLon
            if dist < bestDist {
                bestDist = dist
                bestIndex = index
            }
        }
        return bestIndex
    }

    /// A ride leg only covers part of its route's full polyline - trims it down to the stretch
    /// between the boarding and alighting stops (by nearest-point matching), rather than drawing
    /// the whole route.
    private func trimPolyline(_ polyline: [CLLocationCoordinate2D], from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) -> [CLLocationCoordinate2D] {
        let fromIndex = nearestIndex(in: polyline, to: from)
        let toIndex = nearestIndex(in: polyline, to: to)
        if fromIndex == toIndex { return [] }
        if fromIndex < toIndex {
            return Array(polyline[fromIndex...toIndex])
        } else {
            return Array(polyline[toIndex...fromIndex].reversed())
        }
    }

    private var waypoints: [Waypoint] {
        var ids: [String] = []
        for (index, leg) in journey.legs.enumerated() {
            if index == 0 { ids.append(leg.fromStop) }
            ids.append(leg.toStop)
        }
        var unique: [String] = []
        for id in ids where !unique.contains(id) { unique.append(id) }
        return unique.enumerated().map { idx, id in
            let role = idx == 0 ? "Origen" : (idx == unique.count - 1 ? "Destino" : "Transbordo")
            return Waypoint(id: id, role: role)
        }
    }

    var body: some View {
        Map(position: $cameraPosition) {
            ForEach(Array(journey.legs.enumerated()), id: \.offset) { index, leg in
                switch leg {
                case .ride:
                    if let coords = ridePolylines[index], coords.count >= 2 {
                        MapPolyline(coordinates: coords)
                            .stroke(legColors[rideColorIndex(upTo: index) % legColors.count], lineWidth: 4)
                    }
                case .walk(let walk):
                    if let from = coordinate(for: walk.fromStop), let to = coordinate(for: walk.toStop) {
                        MapPolyline(coordinates: [from, to])
                            .stroke(Color.gray, style: StrokeStyle(lineWidth: 3, dash: [8, 6]))
                    }
                }
            }
            ForEach(waypoints) { waypoint in
                if let coordinate = coordinate(for: waypoint.id) {
                    Annotation(stops[waypoint.id]?.name ?? "", coordinate: coordinate) {
                        ItineraryWaypointMarker(role: waypoint.role)
                    }
                }
            }
        }
        .task {
            guard !hasLoaded else { return }
            hasLoaded = true
            var polylines: [Int: [CLLocationCoordinate2D]] = [:]
            for (index, leg) in journey.legs.enumerated() {
                guard case .ride(let ride) = leg else { continue }
                let loaded = PolylineLoader().load(routeId: ride.routeId, viewId: ride.variantId)
                let trimmed: [CLLocationCoordinate2D]
                if loaded.count >= 2, let fromRouting = routingCoordinate(for: ride.fromStop), let toRouting = routingCoordinate(for: ride.toStop) {
                    trimmed = trimPolyline(loaded, from: fromRouting, to: toRouting)
                } else {
                    trimmed = []
                }
                if !trimmed.isEmpty {
                    polylines[index] = trimmed
                } else if let from = coordinate(for: ride.fromStop), let to = coordinate(for: ride.toStop) {
                    polylines[index] = [from, to]
                }
            }
            ridePolylines = polylines
            fitCamera()
        }
    }

    /// Ride legs are colored independently of walk legs, so a walk in between doesn't shift the palette.
    private func rideColorIndex(upTo index: Int) -> Int {
        journey.legs[0...index].filter { if case .ride = $0 { return true } else { return false } }.count - 1
    }

    private func fitCamera() {
        var coords: [CLLocationCoordinate2D] = ridePolylines.values.flatMap { $0 }
        for leg in journey.legs {
            if case .walk = leg, let from = coordinate(for: leg.fromStop), let to = coordinate(for: leg.toStop) {
                coords.append(from)
                coords.append(to)
            }
        }
        guard !coords.isEmpty else { return }

        if coords.count == 1 {
            cameraPosition = .region(MKCoordinateRegion(
                center: coords[0],
                span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01),
            ))
            return
        }

        let lats = coords.map(\.latitude)
        let lons = coords.map(\.longitude)
        let minLat = lats.min()!
        let maxLat = lats.max()!
        let minLon = lons.min()!
        let maxLon = lons.max()!

        let padding = 0.3
        let center = CLLocationCoordinate2D(
            latitude: (minLat + maxLat) / 2,
            longitude: (minLon + maxLon) / 2,
        )
        let span = MKCoordinateSpan(
            latitudeDelta: max((maxLat - minLat) * (1 + padding), 0.01),
            longitudeDelta: max((maxLon - minLon) * (1 + padding), 0.01),
        )
        withAnimation(.easeInOut(duration: 0.4)) {
            cameraPosition = .region(MKCoordinateRegion(center: center, span: span))
        }
    }
}

// MARK: - Waypoint marker

private struct ItineraryWaypointMarker: View {
    let role: String

    private var color: Color {
        switch role {
        case "Origen": .green
        case "Destino": .red
        default: .accentColor
        }
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(color)
                .frame(width: 20, height: 20)
            Circle()
                .fill(Color.white)
                .frame(width: 8, height: 8)
        }
    }
}
