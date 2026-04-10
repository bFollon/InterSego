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
    let stop: BusStop
    let primaryRouteId: String?
    let primaryViewId: String?
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
    @State private var showAbout = false
    @State private var showReminders = false
    @State private var showSettings = false
    @State private var routes: [BusRoute] = []
    @State private var supportedRoutes: Set<String> = []
    @State private var navigationPath = NavigationPath()
    @State private var isSearchingClosestStop = false
    @State private var closestStopError: String?
    @State private var isSearchingBoardingStop = false
    @State private var landingBoardingConfirmed = false
    @State private var landingBoardingError: String?
    @State private var showLandingBoardingPicker = false
    @State private var landingBoardingStop: BusStop? = nil
    @State private var landingBoardingOptions: [LandingBoardingOption] = []
    @State private var landingBoardingSubmitting = false
    @State private var showNoServiceSheet = false
    @State private var noServiceStop: BusStop? = nil

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
                                        if GuidedModePrefs.isGuidedModeEnabled() {
                                            navigationPath.append(DirectionPickerSelection(
                                                stop: selection.stop,
                                                primaryRouteId: nil,
                                                primaryViewId: nil
                                            ))
                                        } else {
                                            navigationPath.append(selection)
                                        }
                                    } catch let error as ClosestStopError {
                                        closestStopError = error.errorDescription
                                    } catch {
                                        closestStopError = "No se pudo encontrar la parada más cercana."
                                    }
                                    isSearchingClosestStop = false
                                }
                            },
                            onShowAbout: {
                                showAbout = true
                            },
                            onShowReminders: {
                                showReminders = true
                            },
                            onShowSettings: {
                                showSettings = true
                            },
                            onBoardBus: {
                                Task {
                                    isSearchingBoardingStop = true
                                    landingBoardingError = nil
                                    do {
                                        let selection = try await ClosestStopService.shared.findClosest()
                                        let stop = selection.stop
                                        let routeIds = await PDFProcessingService.shared.getRoutesForStop(stopId: stop.id)
                                        let allRoutes = BusRouteRegistry.knownRoutes()
                                        let now = Date()
                                        let weekday = Calendar.current.component(.weekday, from: now)
                                        let todayDayTypes = dayTypesForCalendarDay(weekday)
                                        let h = Calendar.current.component(.hour, from: now)
                                        let m = Calendar.current.component(.minute, from: now)
                                        let currentMinutes = h * 60 + m
                                        var options: [LandingBoardingOption] = []
                                        for routeId in routeIds.sorted() {
                                            guard let route = allRoutes.first(where: { $0.id == routeId }) else { continue }
                                            let timetables = await TimetableService.shared.loadTimetables(routeId: routeId)
                                            var seen = Set<String>()
                                            var dirs: [String] = []
                                            for t in timetables where t.stopId == stop.id && todayDayTypes.contains(t.dayType) {
                                                guard let d = t.direction else { continue }
                                                let hasNearbyDeparture = t.seasonalDepartures(weekday: weekday)
                                                    .contains { abs($0.minutesSinceMidnight - currentMinutes) <= 20 }
                                                if hasNearbyDeparture, seen.insert(d).inserted { dirs.append(d) }
                                            }
                                            if !dirs.isEmpty {
                                                options.append(LandingBoardingOption(route: route, directions: dirs, timetables: timetables))
                                            }
                                        }
                                        if options.isEmpty {
                                            noServiceStop = stop
                                            showNoServiceSheet = true
                                        } else {
                                            landingBoardingStop = stop
                                            landingBoardingOptions = options
                                            showLandingBoardingPicker = true
                                        }
                                    } catch let error as ClosestStopError {
                                        landingBoardingError = error.errorDescription
                                    } catch {
                                        landingBoardingError = "No se pudo encontrar la parada más cercana."
                                    }
                                    isSearchingBoardingStop = false
                                }
                            },
                            isSearchingClosestStop: isSearchingClosestStop,
                            closestStopError: closestStopError,
                            isBoardingBus: isSearchingBoardingStop || landingBoardingSubmitting,
                            boardingBusConfirmed: landingBoardingConfirmed,
                            boardingBusError: landingBoardingError,
                        )
                    }
                }
                .navigationDestination(for: HomeDestination.self) { _ in
                    RouteSelectionView(
                        routes: routes,
                        supportedRoutes: supportedRoutes,
                        onRouteSelected: { route in
                            navigationPath.append(route)
                        },
                    )
                }
                .navigationDestination(for: BusRoute.self) { route in
                    RouteStopsContainer(route: route, navigationPath: $navigationPath)
                }
                .navigationDestination(for: StopSelection.self) { selection in
                    NextDepartureView(
                        stop: selection.stop,
                        primaryRouteId: selection.primaryRouteId,
                        primaryViewId: selection.primaryViewId,
                    )
                }
                .navigationDestination(for: DirectionPickerSelection.self) { selection in
                    DirectionPickerView(
                        stop: selection.stop,
                        primaryRouteId: selection.primaryRouteId,
                        primaryViewId: selection.primaryViewId,
                        onSelected: { routeId, viewId in
                            navigationPath.append(StopSelection(
                                stop: selection.stop,
                                primaryRouteId: routeId,
                                primaryViewId: viewId
                            ))
                        }
                    )
                }
                .navigationDestination(for: DayScheduleSelection.self) { selection in
                    DayScheduleView(
                        route: selection.route,
                        stop: selection.stop,
                        direction: selection.direction,
                        selectedVariantLabel: selection.departureLabel,
                        overrideDayType: selection.overrideDayType,
                    )
                }
                .navigationDestination(for: MapSelection.self) { selection in
                    RouteMapContainer(
                        route: selection.route,
                        initialViewId: selection.initialViewId,
                        fallbackViews: selection.routeViews,
                        navigationPath: $navigationPath,
                    )
                }
                .navigationDestination(for: AllRoutesSelection.self) { selection in
                    AllRoutesContainer(
                        route: selection.route,
                        navigationPath: $navigationPath,
                    )
                }
                .navigationDestination(for: AllRoutesMapSelection.self) { selection in
                    AllRoutesMapContainer(
                        route: selection.route,
                        initialViewId: selection.initialViewId,
                        initialEntryId: selection.initialEntryId,
                        navigationPath: $navigationPath,
                    )
                }
                .sheet(isPresented: $showAbout) {
                    AboutView()
                }
                .sheet(isPresented: $showReminders) {
                    RemindersView()
                }
                .sheet(isPresented: $showSettings) {
                    SettingsView()
                }
                .sheet(isPresented: $showNoServiceSheet) {
                    NoServiceNearbySheet(stop: noServiceStop)
                }
                .sheet(isPresented: $showLandingBoardingPicker) {
                    if let stop = landingBoardingStop {
                        LandingBoardingPickerView(
                            stop: stop,
                            options: landingBoardingOptions,
                            onSubmit: { route, direction in
                                showLandingBoardingPicker = false
                                Task {
                                    landingBoardingSubmitting = true
                                    let option = landingBoardingOptions.first { $0.route.id == route.id }
                                    let weekday = Calendar.current.component(.weekday, from: Date())
                                    let currentDayType: DayType = weekday == 7 ? .saturday : (weekday == 1 ? .sunday : .weekday)
                                    let dayTypes = dayTypesForCalendarDay(weekday)
                                    let h = Calendar.current.component(.hour, from: Date())
                                    let m = Calendar.current.component(.minute, from: Date())
                                    let departure = option?.timetables
                                        .filter { dayTypes.contains($0.dayType) && $0.stopId == stop.id && $0.direction == direction }
                                        .flatMap { $0.seasonalDepartures(weekday: weekday) }
                                        .sorted()
                                        .first { $0.isFuture(currentHour: h, currentMinute: m) }
                                        ?? DepartureTime(hour: h, minute: m)
                                    let request = BoardingRequest.make(
                                        stop: stop,
                                        routeId: route.id,
                                        direction: direction,
                                        dayType: currentDayType,
                                        departure: departure
                                    )
                                    do {
                                        try await BoardingService.shared.postBoarding(request)
                                        landingBoardingConfirmed = true
                                    } catch {
                                        landingBoardingError = "No se pudo enviar. Inténtalo de nuevo."
                                    }
                                    landingBoardingSubmitting = false
                                }
                            },
                            onDismiss: { showLandingBoardingPicker = false }
                        )
                    }
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
                                stop: stop,
                                primaryRouteId: route.id,
                                primaryViewId: viewId,
                            ))
                        },
                        onMapSelected: { viewId in
                            navigationPath.append(MapSelection(
                                route: route,
                                routeViews: resolvedViews,
                                initialViewId: viewId,
                            ))
                        },
                    )
                } else {
                    ProgressView("Cargando paradas...")
                }
            }
            .task {
                let entries = await PDFProcessingService.shared.getRouteEntries(routeId: route.id)
                let todayViews = await PDFProcessingService.shared.getRouteViews(
                    routeId: route.id, dayType: todayDayType,
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
                        stop: stop,
                        primaryRouteId: route.id,
                        primaryViewId: viewId,
                    ))
                },
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
                                    stop: stop,
                                    primaryRouteId: route.id,
                                    primaryViewId: viewId,
                                ))
                            } else {
                                let direction = views.first { $0.id == viewId }?.direction ?? ""
                                navigationPath.append(DayScheduleSelection(
                                    route: route,
                                    stop: stop,
                                    direction: direction,
                                    departureLabel: entry?.label,
                                    overrideDayType: entry?.timetableDayType,
                                ))
                            }
                        },
                        onMapSelected: { viewId in
                            navigationPath.append(AllRoutesMapSelection(
                                route: route,
                                initialViewId: viewId,
                                initialEntryId: selectedEntryId,
                            ))
                        },
                    )
                } else {
                    ProgressView("Cargando rutas...")
                }
            }
            .task {
                let entries = await PDFProcessingService.shared.getRouteEntries(routeId: route.id)
                if !entries.isEmpty {
                    routeEntries = entries
                    if selectedEntryId == nil {
                        selectedEntryId = entries.first { $0.isActiveToday }?.id ?? entries.first?.id
                    }
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
                            stop: stop,
                            primaryRouteId: route.id,
                            primaryViewId: viewId,
                        ))
                    } else {
                        let direction = views.first { $0.id == viewId }?.direction ?? ""
                        navigationPath.append(DayScheduleSelection(
                            route: route,
                            stop: stop,
                            direction: direction,
                            departureLabel: entry?.label,
                            overrideDayType: entry?.timetableDayType,
                        ))
                    }
                },
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

        // Prune expired one-off reminders and replenish daily reminder batches
        await ReminderService.shared.initialize()
        await ReminderService.shared.pruneExpired()
        await ReminderService.shared.replenishDailyReminders()

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

// MARK: - Landing Boarding

private struct LandingBoardingOption {
    let route: BusRoute
    let directions: [String]
    let timetables: [BusTimetable]
}

private struct LandingBoardingPickerView: View {
    let stop: BusStop
    let options: [LandingBoardingOption]
    let onSubmit: (BusRoute, String) -> Void
    let onDismiss: () -> Void

    @State private var selectedRoute: BusRoute? = nil

    private var selectedOption: LandingBoardingOption? {
        guard let r = selectedRoute else { return nil }
        return options.first { $0.route.id == r.id }
    }

    var body: some View {
        VStack(spacing: 20) {
            Text(stop.name)
                .font(.headline)
                .padding(.top, 8)

            if selectedRoute == nil {
                Text("¿En qué línea estás?")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                HStack(spacing: 10) {
                    ForEach(options, id: \.route.id) { option in
                        Button(action: { selectedRoute = option.route }) {
                            Text(option.route.number)
                                .font(.callout)
                                .fontWeight(.bold)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 10)
                                .background(Color.accentColor)
                                .foregroundColor(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .buttonStyle(.plain)
                    }
                }
            } else if let option = selectedOption {
                Text("¿En qué dirección vas?")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                ForEach(option.directions, id: \.self) { dir in
                    Button(destination(from: dir)) {
                        onSubmit(option.route, dir)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                if options.count > 1 {
                    Button("← Cambiar línea") { selectedRoute = nil }
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }

            Button("Cancelar") { onDismiss() }
                .foregroundColor(.secondary)
                .padding(.bottom, 8)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
        .presentationDetents([.medium])
        .onAppear {
            if options.count == 1 { selectedRoute = options[0].route }
        }
    }

    private func destination(from direction: String) -> String {
        direction.components(separatedBy: "→").last?.trimmingCharacters(in: .whitespaces) ?? direction
    }
}

// MARK: - No Service Nearby Sheet

private struct NoServiceNearbySheet: View {
    let stop: BusStop?

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "bus.fill")
                .font(.system(size: 48))
                .foregroundStyle(.orange)

            VStack(spacing: 12) {
                Text("No hay buses próximos")
                    .font(.title3)
                    .fontWeight(.semibold)
                    .multilineTextAlignment(.center)

                if let stopName = stop?.name {
                    Text("Hemos detectado que estás en la parada **\(stopName)**.")
                        .font(.body)
                        .multilineTextAlignment(.center)
                }

                Text("Sin embargo, ninguna de sus líneas tiene salidas en los próximos 20 minutos.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                Text("Vuelve a pulsar el botón cuando estés a punto de subir al autobús.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 4)
            }
        }
        .padding(32)
        .presentationDetents([.height(400)])
        .presentationDragIndicator(.visible)
    }
}
