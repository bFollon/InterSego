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
                        Text(stop.name)
                            .font(.headline)
                            .fontWeight(.bold)
                    }

                    ForEach(directionGroups) { group in
                        Section(header: Text("Línea \(group.route.number)")) {
                            ForEach(group.directions) { option in
                                Button(action: {
                                    GuidedModePrefs.saveLastViewId(option.viewId, stopId: stop.id, routeId: option.routeId)
                                    onSelected(option.routeId, option.viewId)
                                }) {
                                    HStack {
                                        Text(option.direction)
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
        .navigationTitle("Selecciona dirección")
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

        do {
            let departuresService = DeparturesService.shared
            let allRoutes = BusRouteRegistry.knownRoutes()
            let departuresData = await departuresService.loadDepartures(stop: stop, allRoutes: allRoutes, primaryRouteId: primaryRouteId)

            // Build direction groups from the loaded routes
            var groups: [RouteDirectionGroup] = []
            for routeData in departuresData.routes {
                // Collect all distinct directions for this route that serve this stop
                var validDirections = Set<String>()
                var directionViewMap: [String: String] = [:]

                for t in routeData.timetables where t.stopId == stop.id {
                    guard let direction = t.direction else { continue }
                    validDirections.insert(direction)
                }

                // Map directions to viewIds
                for direction in validDirections {
                    if let view = routeData.views.first(where: { $0.direction == direction }) {
                        directionViewMap[direction] = view.id
                    }
                }

                if !validDirections.isEmpty {
                    let dirOptions = validDirections
                        .compactMap { direction in
                            if let viewId = directionViewMap[direction] {
                                DirectionOption(
                                    routeId: routeData.route.id,
                                    route: routeData.route,
                                    direction: direction,
                                    viewId: viewId
                                )
                            } else {
                                nil
                            }
                        }
                        .sorted { (lhs: DirectionOption, rhs: DirectionOption) in lhs.direction < rhs.direction }

                    if !dirOptions.isEmpty {
                        groups.append(RouteDirectionGroup(route: routeData.route, directions: dirOptions))
                    }
                }
            }

            directionGroups = groups

            // Auto-advance if only one direction
            if groups.count == 1, groups[0].directions.count == 1 {
                let option = groups[0].directions[0]
                GuidedModePrefs.saveLastViewId(option.viewId, stopId: stop.id, routeId: option.routeId)
                onSelected(option.routeId, option.viewId)
                return
            }

            if groups.isEmpty {
                error = "No hay salidas disponibles para esta parada."
            }
        } catch {
            self.error = "No se pudieron cargar las direcciones disponibles."
        }

        loading = false
    }
}
