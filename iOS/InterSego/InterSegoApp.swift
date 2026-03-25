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
}

struct AllRoutesSelection: Hashable {
    let route: BusRoute
}

struct AllRoutesMapSelection: Hashable {
    let route: BusRoute
    let initialViewId: String
    let initialEntryId: String?
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
                        fallbackViews: selection.routeViews,
                        navigationPath: $navigationPath
                    )
                }
                .navigationDestination(for: AllRoutesSelection.self) { selection in
                    AllRoutesContainer(
                        route: selection.route,
                        navigationPath: $navigationPath
                    )
                }
                .navigationDestination(for: AllRoutesMapSelection.self) { selection in
                    AllRoutesMapContainer(
                        route: selection.route,
                        initialViewId: selection.initialViewId,
                        initialEntryId: selection.initialEntryId,
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
        @State private var views: [RouteView]?
        @State private var showAllRoutes = false
        @State private var loaded = false

        private var todayDayType: DayType {
            TimetableService.shared.getCurrentDayType()
        }

        var body: some View {
            Group {
                if loaded {
                    let resolvedViews = views ?? []
                    RouteStopsView(
                        route: route,
                        views: resolvedViews,
                        onAllRoutesSelected: showAllRoutes ? {
                            navigationPath.append(AllRoutesSelection(route: route))
                        } : nil,
                        onStopSelected: { stop, viewId in
                            navigationPath.append(StopSelection(
                                route: route,
                                stop: stop,
                                routeViews: resolvedViews,
                                currentViewId: viewId
                            ))
                        },
                        onMapSelected: { viewId in
                            navigationPath.append(MapSelection(
                                route: route,
                                routeViews: resolvedViews,
                                initialViewId: viewId
                            ))
                        }
                    )
                } else {
                    ProgressView("Cargando paradas...")
                }
            }
            .task {
                let entries = await PDFProcessingService.shared.getRouteEntries(routeId: route.id)
                let todayViews = await PDFProcessingService.shared.getRouteViews(
                    routeId: route.id, dayType: todayDayType
                )
                showAllRoutes = entries.count > 1 || todayViews.isEmpty
                views = todayViews
                loaded = true
            }
        }
    }

    private struct RouteMapContainer: View {
        let route: BusRoute
        let initialViewId: String
        let fallbackViews: [RouteView]
        @Binding var navigationPath: NavigationPath

        var body: some View {
            RouteMapView(
                route: route,
                routeViews: fallbackViews,
                initialViewId: initialViewId,
                onStopSelected: { stop, viewId in
                    navigationPath.append(StopSelection(
                        route: route,
                        stop: stop,
                        routeViews: fallbackViews,
                        currentViewId: viewId
                    ))
                }
            )
        }
    }

    private struct AllRoutesContainer: View {
        let route: BusRoute
        @Binding var navigationPath: NavigationPath
        @State private var routeEntries: [RouteSelectorEntry] = []
        @State private var selectedEntryId: String?

        private var selectedEntry: RouteSelectorEntry? {
            routeEntries.first { $0.id == selectedEntryId }
        }

        private var views: [RouteView] {
            selectedEntry?.views ?? []
        }

        var body: some View {
            Group {
                if !routeEntries.isEmpty {
                    AllRoutesView(
                        route: route,
                        routeEntries: routeEntries,
                        selectedEntryId: selectedEntryId ?? "",
                        onEntrySelected: { entry in
                            selectedEntryId = entry.id
                        },
                        views: views,
                        initialViewId: selectedEntry?.initialViewId,
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
                            navigationPath.append(AllRoutesMapSelection(
                                route: route,
                                initialViewId: viewId,
                                initialEntryId: selectedEntryId
                            ))
                        }
                    )
                } else {
                    ProgressView("Cargando rutas...")
                }
            }
            .task {
                let entries = await PDFProcessingService.shared.getRouteEntries(routeId: route.id)
                if !entries.isEmpty {
                    routeEntries = entries
                    selectedEntryId = entries.first { $0.isActiveToday }?.id ?? entries.first?.id
                }
            }
        }
    }

    private struct AllRoutesMapContainer: View {
        let route: BusRoute
        let initialViewId: String
        let initialEntryId: String?
        @Binding var navigationPath: NavigationPath
        @State private var routeEntries: [RouteSelectorEntry] = []
        @State private var selectedEntryId: String?

        private var selectedEntry: RouteSelectorEntry? {
            routeEntries.first { $0.id == selectedEntryId }
        }

        private var views: [RouteView] {
            selectedEntry?.views ?? []
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
                allRoutesMode: true,
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
                if !entries.isEmpty {
                    routeEntries = entries
                    selectedEntryId = initialEntryId
                        ?? entries.first { $0.isActiveToday }?.id
                        ?? entries.first?.id
                }
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
