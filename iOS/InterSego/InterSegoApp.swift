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

    var body: some View {
        ZStack {
            NavigationStack(path: $navigationPath) {
                Group {
                    if isInitialized {
                        LandingView(onShowRouteList: {
                            navigationPath.append(HomeDestination.routeList)
                        })
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
                        selectedVariantLabel: selection.departureLabel
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
        @State private var routeViews: [RouteView]?

        var body: some View {
            Group {
                if let views = routeViews, !views.isEmpty {
                    RouteStopsView(
                        route: route,
                        views: views,
                        onStopSelected: { stop, viewId in
                            let selection = StopSelection(
                                route: route,
                                stop: stop,
                                routeViews: views,
                                currentViewId: viewId
                            )
                            navigationPath.append(selection)
                        }
                    )
                } else {
                    ProgressView("Cargando paradas...")
                }
            }
            .task {
                let dayType = TimetableService.shared.getCurrentDayType()
                let views = await PDFProcessingService.shared.getRouteViews(
                    routeId: route.id, dayType: dayType
                )
                routeViews = views
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
