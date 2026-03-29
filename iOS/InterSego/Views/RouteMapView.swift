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

import MapKit
import SwiftUI

/// Displays all stops for a route direction on an interactive map.
///
/// Shows stops as markers connected by a polyline. The direction swap button
/// mirrors the behaviour in RouteStopsView. Tapping a marker navigates to
/// NextDepartureView for that stop.
struct RouteMapView: View {
    let route: BusRoute
    let routeViews: [RouteView]
    let initialViewId: String
    var routeEntries: [RouteSelectorEntry]?
    var selectedEntryId: String?
    var onEntrySelected: (RouteSelectorEntry) -> Void = { _ in }
    var allRoutesMode: Bool = false
    let onStopSelected: (BusStop, String) -> Void

    @State private var currentViewId: String = ""
    @State private var cameraPosition: MapCameraPosition = .automatic
    @State private var routePolyline: [CLLocationCoordinate2D] = []

    private var currentView: RouteView? {
        routeViews.first { $0.id == currentViewId } ?? routeViews.first
    }

    private var stopsWithCoords: [BusStop] {
        currentView?.stops.map(\.stop).filter(\.hasCoordinates) ?? []
    }

    /// Road-following coordinates if loaded, otherwise straight lines between stops.
    private var polylineCoordinates: [CLLocationCoordinate2D] {
        if !routePolyline.isEmpty { return routePolyline }
        return stopsWithCoords.compactMap { stop in
            guard let lat = stop.resolvedLatitude, let lon = stop.resolvedLongitude else { return nil }
            return CLLocationCoordinate2D(latitude: lat, longitude: lon)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Entry dropdown in all-routes mode
            if allRoutesMode, let entries = routeEntries, let entryId = selectedEntryId {
                RouteEntryDropdown(
                    entries: entries,
                    selectedEntryId: entryId,
                    onEntrySelected: onEntrySelected,
                )
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }

            Map(position: $cameraPosition) {
                // Polyline connecting stops in order
                if polylineCoordinates.count >= 2 {
                    MapPolyline(coordinates: polylineCoordinates)
                        .stroke(Color.accentColor, lineWidth: 4)
                }

                // Marker for each stop
                ForEach(stopsWithCoords) { stop in
                    if let lat = stop.resolvedLatitude, let lon = stop.resolvedLongitude {
                        Annotation(stop.name, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)) {
                            StopMapMarker {
                                if let view = currentView {
                                    onStopSelected(stop, view.id)
                                }
                            }
                        }
                    }
                }
            }
            .task(id: "\(currentViewId)|\(selectedEntryId ?? "")") {
                guard !currentViewId.isEmpty else { return }
                routePolyline = loadBundledPolyline(routeId: route.id, viewId: currentViewId)
                fitCamera()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack {
                    Text("Línea \(route.number)")
                        .font(.headline)
                    if allRoutesMode {
                        Text("Todas las rutas")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else if let view = currentView {
                        Text(view.label)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            if currentView?.swapAction != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        if let swap = currentView?.swapAction {
                            currentViewId = swap.targetViewId
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                    }
                }
            }
        }
        .onAppear {
            if currentViewId.isEmpty {
                currentViewId = initialViewId
            }
        }
        .onChange(of: selectedEntryId) {
            currentViewId = initialViewId
        }
    }

    /// Loads a pre-computed road-following polyline from the app bundle.
    /// Returns an empty array if the file is missing or cannot be parsed.
    private func loadBundledPolyline(routeId: String, viewId: String) -> [CLLocationCoordinate2D] {
        guard let url = Bundle.main.url(
            forResource: "\(routeId)-\(viewId)",
            withExtension: "json",
            subdirectory: "RoutePolylines",
        ),
            let data = try? Data(contentsOf: url),
            let pairs = try? JSONDecoder().decode([[Double]].self, from: data)
        else { return [] }

        return pairs.compactMap { pair in
            guard pair.count >= 2 else { return nil }
            return CLLocationCoordinate2D(latitude: pair[0], longitude: pair[1])
        }
    }

    /// Adjusts the camera to fit all stops in the current direction.
    private func fitCamera() {
        let coords = stopsWithCoords.compactMap { stop -> CLLocationCoordinate2D? in
            guard let lat = stop.resolvedLatitude, let lon = stop.resolvedLongitude else { return nil }
            return CLLocationCoordinate2D(latitude: lat, longitude: lon)
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
            latitudeDelta: (maxLat - minLat) * (1 + padding),
            longitudeDelta: (maxLon - minLon) * (1 + padding),
        )
        withAnimation(.easeInOut(duration: 0.4)) {
            cameraPosition = .region(MKCoordinateRegion(center: center, span: span))
        }
    }
}

// MARK: - Stop marker

private struct StopMapMarker: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack {
                Circle()
                    .fill(Color.accentColor)
                    .frame(width: 20, height: 20)
                Circle()
                    .fill(Color.white)
                    .frame(width: 8, height: 8)
            }
        }
        .buttonStyle(.plain)
    }
}
