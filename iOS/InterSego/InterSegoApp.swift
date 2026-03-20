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

enum HomeDestination: Hashable {
    case routeList
}

struct StopSelection: Hashable {
    let route: BusRoute
    let stop: BusStop
    let routeViews: [RouteView]
    let currentViewId: String
}

struct MapSelection: Hashable {
    let route: BusRoute
    let routeViews: [RouteView]
    let initialViewId: String
    let initialEntryId: String?

    init(route: BusRoute, routeViews: [RouteView], initialViewId: String, initialEntryId: String? = nil) {
        self.route = route
        self.routeViews = routeViews
        self.initialViewId = initialViewId
        self.initialEntryId = initialEntryId
    }
}

@main
struct InterSegoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @State private var isInitialized = false
    @State private var showSplash = true
    @State private var routes: [BusRoute] = []
    @State private var supportedRoutes: Set<String> = []
    @State private var navigationPath = NavigationPath()
    @State private var isSearchingClosestStop = false
    @State private var closestStopError: String?

    var body: some View {
        ZStack {
            NavigationStack(path: $navigationPath) {
                Group {
                    if isInitialized {
                        LandingView(
                            onShowRouteList: {
                                navigationPath.append(HomeDestination.routeList)
                            },
                            onFindClosestStop: {
                                Task {
                                    isSearchingClosestStop = true
                                    closestStopError = nil
                                    do {
                                        let selection = try await ClosestStopService.shared.findClosest()
                                        navigationPath.append(selection)
                                    } catch let error as ClosestStopError {
                                        closestStopError = error.errorDescription
                                    } catch {
                                        closestStopError = "No se pudo encontrar la parada más cercana."
                                    }
                                    isSearchingClosestStop = false
                                }
                            },
                            isSearchingClosestStop: isSearchingClosestStop,
                            closestStopError: closestStopError
                        )
                    }
                }
                .navigationDestination(for: HomeDestination.self) { _ in
                    RouteSelectionView(
                        routes: routes,
                        supportedRoutes: supportedRoutes,
                        onRouteSelected: { route in
                            navigationPath.append(route)
                        }
                    )
                }
                .navigationDestination(for: BusRoute.self) { route in
                    RouteStopsContainer(route: route, navigationPath: $navigationPath)
                }
                .navigationDestination(for: StopSelection.self) { selection in
                    NextDepartureView(
                        route: selection.route,
                        stop: selection.stop,
                        routeViews: selection.routeViews,
                        currentViewId: selection.currentViewId
                    )
                }
                .navigationDestination(for: DayScheduleSelection.self) { selection in
                    DayScheduleView(
                        route: selection.route,
                        stop: selection.stop,
                        direction: selection.direction,
                        selectedVariantLabel: selection.departureLabel,
                        overrideDayType: selection.overrideDayType
                    )
                }
                .navigationDestination(for: MapSelection.self) { selection in
                    RouteMapContainer(
                        route: selection.route,
                        initialViewId: selection.initialViewId,
                        initialEntryId: selection.initialEntryId,
                        fallbackViews: selection.routeViews,
                        navigationPath: $navigationPath
                    )
                }
            }

            if showSplash {
                SplashScreenView()
                    .transition(.opacity)
            }
        }
        .task {
            await initialize()
            withAnimation(.easeOut(duration: 0.5)) {
                showSplash = false
            }
        }
    }

    private struct RouteStopsContainer: View {
        let route: BusRoute
        @Binding var navigationPath: NavigationPath
        @State private var routeEntries: [RouteSelectorEntry]?
        @State private var selectedEntryId: String?
        @State private var fallbackViews: [RouteView]?

        private var todayDayType: DayType { TimetableService.shared.getCurrentDayType() }

        private var selectedEntry: RouteSelectorEntry? {
            routeEntries?.first { $0.id == selectedEntryId }
        }

        private var views: [RouteView] {
            selectedEntry?.views ?? fallbackViews ?? []
        }

        var body: some View {
            Group {
                if !views.isEmpty {
                    RouteStopsView(
                        route: route,
                        views: views,
                        initialViewId: selectedEntry?.initialViewId,
                        routeEntries: routeEntries,
                        selectedEntryId: selectedEntryId,
                        onEntrySelected: { entry in
                            selectedEntryId = entry.id
                        },
                        onStopSelected: { stop, viewId in
                            let entry = selectedEntry
                            if entry == nil || entry!.isActiveToday {
                                navigationPath.append(StopSelection(
                                    route: route,
                                    stop: stop,
                                    routeViews: views,
                                    currentViewId: viewId
                                ))
                            } else {
                                let direction = views.first { $0.id == viewId }?.direction ?? ""
                                navigationPath.append(DayScheduleSelection(
                                    route: route,
                                    stop: stop,
                                    direction: direction,
                                    departureLabel: entry?.label,
                                    overrideDayType: entry?.timetableDayType
                                ))
                            }
                        },
                        onMapSelected: { viewId in
                            navigationPath.append(MapSelection(
                                route: route,
                                routeViews: views,
                                initialViewId: viewId,
                                initialEntryId: selectedEntryId
                            ))
                        }
                    )
                } else {
                    ProgressView("Cargando paradas...")
                }
            }
            .task {
                let entries = await PDFProcessingService.shared.getRouteEntries(routeId: route.id)
                if let entries = entries, !entries.isEmpty {
                    routeEntries = entries
                    selectedEntryId = entries.first { $0.isActiveToday }?.id ?? entries.first?.id
                } else {
                    fallbackViews = await PDFProcessingService.shared.getRouteViews(
                        routeId: route.id, dayType: todayDayType
                    )
                }
            }
        }
    }

    private struct RouteMapContainer: View {
        let route: BusRoute
        let initialViewId: String
        let initialEntryId: String?
        let fallbackViews: [RouteView]
        @Binding var navigationPath: NavigationPath
        @State private var routeEntries: [RouteSelectorEntry]?
        @State private var selectedEntryId: String?

        private var todayDayType: DayType { TimetableService.shared.getCurrentDayType() }

        private var selectedEntry: RouteSelectorEntry? {
            routeEntries?.first { $0.id == selectedEntryId }
        }

        private var views: [RouteView] {
            selectedEntry?.views ?? fallbackViews
        }

        var body: some View {
            RouteMapView(
                route: route,
                routeViews: views,
                initialViewId: selectedEntry?.initialViewId ?? initialViewId,
                routeEntries: routeEntries,
                selectedEntryId: selectedEntryId,
                onEntrySelected: { entry in
                    selectedEntryId = entry.id
                },
                onStopSelected: { stop, viewId in
                    let entry = selectedEntry
                    if entry == nil || entry!.isActiveToday {
                        navigationPath.append(StopSelection(
                            route: route,
                            stop: stop,
                            routeViews: views,
                            currentViewId: viewId
                        ))
                    } else {
                        let direction = views.first { $0.id == viewId }?.direction ?? ""
                        navigationPath.append(DayScheduleSelection(
                            route: route,
                            stop: stop,
                            direction: direction,
                            departureLabel: entry?.label,
                            overrideDayType: entry?.timetableDayType
                        ))
                    }
                }
            )
            .task {
                let entries = await PDFProcessingService.shared.getRouteEntries(routeId: route.id)
                if let entries = entries, !entries.isEmpty {
                    routeEntries = entries
                    selectedEntryId = initialEntryId
                        ?? entries.first { $0.isActiveToday }?.id
                        ?? entries.first?.id
                }
                // If no entries, fallbackViews (from caller) are used directly
            }
        }
    }

    private func initialize() async {
        DebugConfig.debugPrint("InterSego: Starting initialization...")

        // Ensure network monitor is alive (starts in init)
        _ = NetworkMonitor.shared

        // Initialize PDF URL repository
        let success = await PDFURLRepository.shared.initializeURLs()
        if success {
            DebugConfig.debugPrint("InterSego: PDF URLs initialized successfully")
        } else {
            DebugConfig.debugWarn("InterSego: PDF URL initialization failed, using fallback URLs")
        }

        // Load routes
        routes = BusRouteRegistry.knownRoutes()

        // Get supported routes from PDFProcessingService
        let supported = await PDFProcessingService.shared.getSupportedRoutes()
        supportedRoutes = Set(supported)

        DebugConfig.debugPrint("InterSego: Initialization complete. \(supportedRoutes.count) routes supported.")
        isInitialized = true
    }
}
