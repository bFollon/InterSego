/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Searchable stop picker for the journey planner's origin/destination selection, grouped by
/// area, each row showing which routes serve it. Includes a "Mi ubicación" entry at the top when
/// `allowMyLocation` is true — this view never requests location permission itself, the caller
/// decides via `allowMyLocation` and handles the actual lookup.
struct StopPickerView: View {
    let title: String
    let supportedRouteIds: [String]
    let allowMyLocation: Bool
    let onStopSelected: (_ physicalStopId: String, _ name: String) -> Void
    let onMyLocationSelected: () -> Void

    @State private var searchText = ""
    @State private var entries: [StopDirectoryService.Entry] = []

    private var filtered: [StopDirectoryService.Entry] {
        guard !searchText.isEmpty else { return entries }
        return entries.filter { $0.stop.name.localizedCaseInsensitiveContains(searchText) }
    }

    private var grouped: [String: [StopDirectoryService.Entry]] {
        Dictionary(grouping: filtered) { $0.stop.area ?? "Otros" }
    }

    private var sortedAreas: [String] { grouped.keys.sorted() }

    var body: some View {
        List {
            if allowMyLocation, searchText.isEmpty {
                Section {
                    Button(action: onMyLocationSelected) {
                        Label {
                            VStack(alignment: .leading) {
                                Text("Mi ubicación").fontWeight(.medium)
                                Text("Usar mi ubicación actual")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "location.fill")
                        }
                    }
                    .foregroundStyle(.primary)
                }
            }
            ForEach(sortedAreas, id: \.self) { area in
                Section(area) {
                    ForEach(grouped[area] ?? []) { entry in
                        Button(action: { onStopSelected(entry.physicalStopId, entry.stop.name) }) {
                            VStack(alignment: .leading) {
                                Text(entry.stop.name)
                                Text(entry.routeIds.joined(separator: ", "))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .foregroundStyle(.primary)
                    }
                }
            }
        }
        .searchable(text: $searchText, prompt: "Buscar parada")
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            entries = StopDirectoryService.buildDirectory(routeIds: supportedRouteIds)
        }
    }
}
