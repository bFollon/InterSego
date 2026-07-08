/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

struct DirectionPickerSelection: Hashable, Identifiable {
    let id = UUID()
    let stop: BusStop
    let primaryRouteId: String?
    let primaryViewId: String?

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: DirectionPickerSelection, rhs: DirectionPickerSelection) -> Bool {
        lhs.id == rhs.id
    }
}

struct DirectionOption: Identifiable, Hashable {
    let id = UUID()
    let routeId: String
    let route: BusRoute
    let direction: String
    let viewId: String

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: DirectionOption, rhs: DirectionOption) -> Bool {
        lhs.id == rhs.id
    }
}

struct RouteDirectionGroup: Identifiable {
    let id = UUID()
    let route: BusRoute
    let directions: [DirectionOption]
}

struct DirectionPickerView: View {
    let stop: BusStop
    let primaryRouteId: String?
    let primaryViewId: String?
    let onSelected: (String, String) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var loading = true
    @State private var error: String?
    @State private var directionGroups: [RouteDirectionGroup] = []
    @State private var showTutorial = false

    var body: some View {
        Group {
            if loading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error {
                VStack(spacing: 16) {
                    Text(error)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                    Button("Volver") {
                        dismiss()
                    }
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    Section {
                        Text("¿A dónde vas?")
                            .font(.title3)
                            .fontWeight(.semibold)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }

                    ForEach(directionGroups) { group in
                        Section(header: LineNamePill(routeNumber: group.route.number)) {
                            ForEach(group.directions) { option in
                                Button(action: {
                                    GuidedModePrefs.saveLastViewId(option.viewId, stopId: stop.id, routeId: option.routeId)
                                    onSelected(option.routeId, option.viewId)
                                }) {
                                    HStack {
                                        Text(destinationFromDirection(option.direction))
                                            .foregroundColor(.primary)

                                        Spacer()

                                        if GuidedModePrefs.getLastViewId(stopId: stop.id, routeId: option.routeId) == option.viewId {
                                            Label("Última", systemImage: "")
                                                .font(.caption)
                                                .foregroundColor(.accentColor)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color.accentColor.opacity(0.1))
                                                .cornerRadius(4)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(stop.name)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showTutorial) {
            GuidedModeTutorialView(onDismiss: {
                GuidedModePrefs.setTutorialShown()
                showTutorial = false
            })
        }
        .task {
            await loadDirections()
            if !GuidedModePrefs.isTutorialShown() {
                showTutorial = true
            }
        }
    }

    private func loadDirections() async {
        loading = true
        error = nil

        let departuresService = DeparturesService.shared
        let allRoutes = BusRouteRegistry.knownRoutes()
        let departuresData = await departuresService.loadDepartures(stop: stop, allRoutes: allRoutes, primaryRouteId: primaryRouteId)

        // Build direction groups from the loaded routes
        var groups: [RouteDirectionGroup] = []
        for routeData in departuresData.routes {
            // If this route uses mergedDirectionLabel (e.g. M4 circular), collapse to a
            // single option using the primary view — triggering auto-advance below.
            if let primaryMergedView = routeData.views.first(where: { $0.mergedDirectionLabel != nil }) {
                let option = DirectionOption(
                    routeId: routeData.route.id,
                    route: routeData.route,
                    direction: primaryMergedView.direction,
                    viewId: primaryMergedView.id
                )
                groups.append(RouteDirectionGroup(route: routeData.route, directions: [option]))
                continue
            }

            // Normal flow: one option per distinct direction serving this stop
            var validDirections = Set<String>()
            var directionViewMap: [String: String] = [:]

            for t in routeData.timetables where t.stopId == stop.id {
                guard let direction = t.direction else { continue }
                validDirections.insert(direction)
            }

            for direction in validDirections {
                if let view = routeData.views.first(where: { $0.direction == direction }) {
                    directionViewMap[direction] = view.id
                }
            }

            if !validDirections.isEmpty {
                let dirOptions = validDirections
                    .compactMap { direction -> DirectionOption? in
                        guard let viewId = directionViewMap[direction] else { return nil }
                        return DirectionOption(
                            routeId: routeData.route.id,
                            route: routeData.route,
                            direction: direction,
                            viewId: viewId
                        )
                    }
                    .sorted { $0.direction < $1.direction }

                if !dirOptions.isEmpty {
                    groups.append(RouteDirectionGroup(route: routeData.route, directions: dirOptions))
                }
            }
        }

        directionGroups = groups

        if groups.isEmpty {
            error = "No hay salidas disponibles para esta parada."
        }

        loading = false
    }

    private func destinationFromDirection(_ direction: String) -> String {
        direction.components(separatedBy: "→").last?.trimmingCharacters(in: .whitespaces) ?? direction
    }
}

// MARK: - Helper Views

struct LineNamePill: View {
    let routeNumber: String

    var body: some View {
        Text("Línea \(routeNumber)")
            .font(.caption)
            .fontWeight(.semibold)
            .foregroundColor(.accentColor)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color.accentColor.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}
