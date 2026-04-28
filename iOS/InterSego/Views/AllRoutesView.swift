/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Screen displaying all route variants with a dropdown selector.
///
/// Allows the user to browse stops for any operating schedule (weekday, Saturday,
/// Sunday) and direction, not just today's active one.
struct AllRoutesView: View {
    let route: BusRoute
    let routeEntries: [RouteSelectorEntry]
    let selectedEntryId: String
    let onEntrySelected: (RouteSelectorEntry) -> Void
    let views: [RouteView]
    var initialViewId: String?
    let onStopSelected: (BusStop, String) -> Void
    let onMapSelected: (String) -> Void

    @State private var currentViewId: String = ""

    private var currentView: RouteView? {
        views.first { $0.id == currentViewId } ?? views.first
    }

    var body: some View {
        if let currentView {
            mainContent(currentView: currentView)
        } else {
            Text("No hay datos disponibles").foregroundColor(.secondary)
        }
    }

    private func mainContent(currentView: RouteView) -> some View {
        VStack(spacing: 0) {
            // Entry dropdown selector
            RouteEntryDropdown(
                entries: routeEntries,
                selectedEntryId: selectedEntryId,
                onEntrySelected: onEntrySelected,
            )
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            // Stop list
            ScrollView {
                LazyVStack(spacing: 0) {
                    let stops = currentView.stops
                    let extendedLabel = currentView.extendedSectionLabel
                    let hasExtendedStops = extendedLabel != nil && stops.contains { $0.isExtendedOnly }

                    ForEach(Array(stops.enumerated()), id: \.offset) { index, viewStop in
                        if hasExtendedStops, index > 0 {
                            let prevIsExtended = stops[index - 1].isExtendedOnly
                            let currIsExtended = viewStop.isExtendedOnly
                            if prevIsExtended != currIsExtended {
                                ExtendedSectionSeparator(label: extendedLabel!)
                            }
                        }

                        StopRowView(
                            stop: viewStop.stop,
                            isExtended: viewStop.isExtendedOnly,
                            isFirst: index == 0,
                            isLast: index == stops.count - 1,
                        )
                        .contentShape(Rectangle())
                        .onTapGesture {
                            onStopSelected(viewStop.stop, currentView.id)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack {
                    Text("Línea \(route.number)")
                        .font(.headline)
                    Text(currentView.label)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    onMapSelected(currentView.id)
                } label: {
                    Image(systemName: "map")
                }
            }
            if currentView.swapAction != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        if let swap = currentView.swapAction {
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
                currentViewId = initialViewId ?? views.first?.id ?? ""
            }
        }
        .onChange(of: selectedEntryId) {
            currentViewId = initialViewId ?? views.first?.id ?? ""
        }
    }
}

// MARK: - Route Entry Dropdown

struct RouteEntryDropdown: View {
    let entries: [RouteSelectorEntry]
    let selectedEntryId: String
    let onEntrySelected: (RouteSelectorEntry) -> Void

    private var selectedEntry: RouteSelectorEntry? {
        entries.first { $0.id == selectedEntryId }
    }

    var body: some View {
        Menu {
            ForEach(entries, id: \.id) { entry in
                Button {
                    onEntrySelected(entry)
                } label: {
                    if entry.id == selectedEntryId {
                        Label(entry.label, systemImage: "checkmark")
                    } else {
                        Text(entry.label)
                    }
                }
            }
        } label: {
            HStack {
                Text(selectedEntry?.label ?? "")
                    .foregroundColor(.primary)
                Spacer()
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }
}
