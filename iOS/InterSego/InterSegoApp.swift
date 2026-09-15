/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI
import UIKit

enum HomeDestination: Hashable {
    case routeList
}

/// "Más opciones" hub — separate from `HomeDestination.routeList` so its "Consultar otro
/// día" route/stop picker (below) can always land on `DayScheduleSelection` instead of
/// `StopSelection`. The target date is picked on the hub screen itself, before route/stop
/// selection: a route's stops and views can differ completely by day type (e.g. M1's
/// circularA/B, M6's 7 variants), so the date must be known before a correct stop list can
/// be shown for it.
enum OtrasOpcionesDestination: Hashable {
    case home
    case routeList(Date)
    case journeyPlanner
    case favorites
}

struct OtrasOpcionesRouteSelection: Hashable {
    let route: BusRoute
    let date: Date
}

/// Query params for a journey search (Epic 2) — the results themselves are computed inside the
/// destination view's `.task`, not carried on the nav path, so re-pushing the same selection
/// (e.g. via Back then forward) re-runs the search against current data rather than caching a
/// stale result.
struct JourneyResultsSelection: Hashable {
    let originId: String
    let originName: String
    let destinationId: String
    let destinationName: String
    let date: Date
    let departAfterMin: Int
    let arriveBeforeMin: Int?
}

struct JourneyDetailSelection: Hashable {
    let journey: Journey
    let stops: [String: BusStop]
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
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @State private var isInitialized = false
    @State private var showSplash = true
    @State private var showMonitoringConsent = false
    @State private var showNotificationConsent = false
    @State private var showWhatsNew = false
    @State private var showAbout = false
    @State private var showReminders = false
    @State private var showSettings = false
    @State private var routes: [BusRoute] = []
    @State private var supportedRoutes: Set<String> = []
    @State private var navigationPath = NavigationPath()
    @State private var activeAlerts: [ServiceAlert] = []
    @State private var showAlertDetail = false
    @State private var laLigaBlockingSuspected = false
    @State private var showLaLigaDetail = false
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
    @State private var landingSlots = LandingLayoutPrefs.getSlots()

    var body: some View {
        ZStack {
            NavigationStack(path: $navigationPath) {
                Group {
                    if isInitialized {
                        LandingView(
                            landingSlots: landingSlots,
                            onFindClosestStop: {
                                Task {
                                    isSearchingClosestStop = true
                                    closestStopError = nil
                                    do {
                                        let selection = try await ClosestStopService.shared.findClosest()
                                        AnalyticsService.shared.track("closest_stop_used", with: ["stop": selection.stop.id])
                                        if GuidedModePrefs.isGuidedModeEnabled() {
                                            let allRoutes = BusRouteRegistry.knownRoutes()
                                            if let resolved = await DeparturesService.shared.resolveDirectionIfUnambiguous(stop: selection.stop, allRoutes: allRoutes, primaryRouteId: nil) {
                                                navigationPath.append(StopSelection(stop: selection.stop, primaryRouteId: resolved.0, primaryViewId: resolved.1))
                                            } else {
                                                navigationPath.append(DirectionPickerSelection(stop: selection.stop, primaryRouteId: nil, primaryViewId: nil))
                                            }
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
                            onShowSettings: {
                                showSettings = true
                            },
                            onShowOtrasOpciones: {
                                navigationPath.append(OtrasOpcionesDestination.home)
                            },
                            onNavigateToRouteList: {
                                navigationPath.append(HomeDestination.routeList)
                            },
                            onPlanJourney: {
                                navigationPath.append(OtrasOpcionesDestination.journeyPlanner)
                            },
                            onShowReminders: {
                                showReminders = true
                            },
                            onOpenAnotherDay: {
                                navigationPath.append(OtrasOpcionesDestination.home)
                            },
                            onShowFavorites: {
                                navigationPath.append(OtrasOpcionesDestination.favorites)
                            },
                            onBoardBus: {
                                Task {
                                    isSearchingBoardingStop = true
                                    landingBoardingError = nil
                                    do {
                                        let selection = try await ClosestStopService.shared.findClosest()
                                        let stop = selection.stop
                                        let routeIds = await RouteDataService.shared.getRoutesForStop(stopId: stop.id)
                                        let allRoutes = BusRouteRegistry.knownRoutes()
                                        let now = Date()
                                        let weekday = Calendar.current.component(.weekday, from: now)
                                        let todayDayTypes = TimetableQuery.dayTypesForDate(now)
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
                            activeAlerts: activeAlerts,
                            onShowAlertDetail: { showAlertDetail = true },
                            laLigaBlockingSuspected: laLigaBlockingSuspected,
                            onShowLaLigaDetail: { showLaLigaDetail = true },
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
                .navigationDestination(for: OtrasOpcionesDestination.self) { destination in
                    switch destination {
                    case .home:
                        OtrasOpcionesView(
                            onShowReminders: {
                                showReminders = true
                            },
                            onCheckAnotherDay: { date in
                                navigationPath.append(OtrasOpcionesDestination.routeList(date))
                            },
                            onPlanJourney: {
                                navigationPath.append(OtrasOpcionesDestination.journeyPlanner)
                            },
                            onNavigateToRouteList: {
                                navigationPath.append(HomeDestination.routeList)
                            },
                            onShowFavorites: {
                                navigationPath.append(OtrasOpcionesDestination.favorites)
                            },
                            onBoardBus: {
                                Task {
                                    isSearchingBoardingStop = true
                                    landingBoardingError = nil
                                    do {
                                        let selection = try await ClosestStopService.shared.findClosest()
                                        let stop = selection.stop
                                        let routeIds = await RouteDataService.shared.getRoutesForStop(stopId: stop.id)
                                        let allRoutes = BusRouteRegistry.knownRoutes()
                                        let now = Date()
                                        let weekday = Calendar.current.component(.weekday, from: now)
                                        let todayDayTypes = TimetableQuery.dayTypesForDate(now)
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
                            pinnedActions: Set(landingSlots.values),
                            isBoardingBus: isSearchingBoardingStop || landingBoardingSubmitting,
                            boardingBusConfirmed: landingBoardingConfirmed,
                        )
                    case .favorites:
                        FavoriteStopsView(
                            onSelectFavorite: { favorite in
                                let stopsById = try? TimetableLoader().loadBusStopsById(favorite.routeId)
                                // Falls back to a minimal stop built from the favorite's own snapshot
                                // if the route's stop data can no longer be found (e.g. a route was
                                // dropped from the bundle) — a tap should always navigate somewhere
                                // rather than silently doing nothing.
                                let stop = stopsById?[favorite.stopId]
                                    ?? BusStop(id: favorite.stopId, name: favorite.stopName, coordinates: "0, 0")
                                navigationPath.append(StopSelection(stop: stop, primaryRouteId: favorite.routeId, primaryViewId: favorite.viewId))
                            },
                        )
                    case .routeList(let date):
                        RouteSelectionView(
                            routes: routes,
                            supportedRoutes: supportedRoutes,
                            onRouteSelected: { route in
                                navigationPath.append(OtrasOpcionesRouteSelection(route: route, date: date))
                            },
                        )
                    case .journeyPlanner:
                        JourneyPlannerView(
                            supportedRouteIds: routes.map(\.id),
                            onSearch: { originId, originName, destinationId, destinationName, date, departAfterMin, arriveBeforeMin in
                                navigationPath.append(JourneyResultsSelection(
                                    originId: originId, originName: originName,
                                    destinationId: destinationId, destinationName: destinationName,
                                    date: date, departAfterMin: departAfterMin, arriveBeforeMin: arriveBeforeMin
                                ))
                            },
                        )
                    }
                }
                .navigationDestination(for: OtrasOpcionesRouteSelection.self) { selection in
                    OtrasOpcionesRouteStopsContainer(route: selection.route, date: selection.date, navigationPath: $navigationPath)
                }
                .navigationDestination(for: JourneyResultsSelection.self) { selection in
                    JourneyResultsContainer(
                        selection: selection,
                        supportedRouteIds: routes.map(\.id),
                        onJourneySelected: { journey, stops in
                            navigationPath.append(JourneyDetailSelection(journey: journey, stops: stops))
                        },
                    )
                }
                .navigationDestination(for: JourneyDetailSelection.self) { selection in
                    JourneyDetailView(
                        journey: selection.journey,
                        stopName: { stopId in selection.stops[stopId]?.name ?? stopId },
                        stops: selection.stops,
                        onLegSelected: { routeId, stopId in
                            if let stop = selection.stops[stopId] {
                                navigationPath.append(StopSelection(stop: stop, primaryRouteId: routeId, primaryViewId: nil))
                            }
                        },
                    )
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
                        referenceDate: selection.date ?? Date(),
                        onSelected: { routeId, viewId in
                            // Replace the DirectionPickerSelection with the resolved destination
                            // so pressing Back skips past the picker entirely rather than
                            // re-triggering it (which causes an auto-advance loop). For the
                            // date-picker flow, resolve the destination BEFORE popping — popping
                            // first would briefly reveal the screen underneath the picker while
                            // the async lookup is still in flight.
                            if let date = selection.date {
                                Task {
                                    let dayType = TimetableQuery.primaryDayType(date)
                                    let resolvedViews = await RouteDataService.shared.getRouteViews(routeId: routeId, dayType: dayType)
                                    let view = resolvedViews.first { $0.id == viewId }
                                    guard let resolvedRoute = (routes.first { $0.id == routeId } ?? BusRouteRegistry.knownRoutes().first { $0.id == routeId }) else { return }
                                    navigationPath.removeLast()
                                    navigationPath.append(DayScheduleSelection(
                                        route: resolvedRoute,
                                        stop: selection.stop,
                                        direction: view?.direction ?? "",
                                        departureLabel: view?.departureLabel,
                                        allowDateSelection: true,
                                        initialDate: date,
                                    ))
                                }
                            } else {
                                navigationPath.removeLast()
                                navigationPath.append(StopSelection(
                                    stop: selection.stop,
                                    primaryRouteId: routeId,
                                    primaryViewId: viewId
                                ))
                            }
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
                        mergedDirectionLabel: selection.mergedDirectionLabel,
                        allowDateSelection: selection.allowDateSelection,
                        initialDate: selection.initialDate,
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
                .sheet(isPresented: $showAlertDetail) {
                    AlertDetailSheet(alerts: activeAlerts)
                }
                .sheet(isPresented: $showLaLigaDetail) {
                    LaLigaBlockingDetailSheet()
                }
                .sheet(isPresented: $showAbout) {
                    AboutView()
                }
                .sheet(isPresented: $showReminders) {
                    RemindersView()
                }
                .sheet(isPresented: $showSettings, onDismiss: {
                    landingSlots = LandingLayoutPrefs.getSlots()
                }) {
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
                                    let currentDayType = TimetableQuery.primaryDayType()
                                    let dayTypes = TimetableQuery.dayTypesForDate(Date())
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
                                        AnalyticsService.shared.track("boarding_confirmed", with: ["route": route.id, "stop": stop.id])
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

            if showMonitoringConsent {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()

                MonitoringConsentView(isPresented: $showMonitoringConsent)
                    .frame(maxWidth: 500)
                    .background(Color(uiColor: .systemBackground))
                    .cornerRadius(16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                    )
                    .shadow(radius: 20)
                    .padding(40)
            } else if showNotificationConsent {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()

                NotificationConsentView(isPresented: $showNotificationConsent)
                    .frame(maxWidth: 500)
                    .background(Color(uiColor: .systemBackground))
                    .cornerRadius(16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                    )
                    .shadow(radius: 20)
                    .padding(40)
            } else if showWhatsNew {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()

                WhatsNewView(isPresented: $showWhatsNew)
                    .frame(maxWidth: 500)
                    .background(Color(uiColor: .systemBackground))
                    .cornerRadius(16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                    )
                    .shadow(radius: 20)
                    .padding(40)
            }
        }
        .task {
            await initialize()
            withAnimation(.easeOut(duration: 0.5)) {
                showSplash = false
            }
        }
        .onAppear {
            ReviewPromptService.shared.startSession()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            ReviewPromptService.shared.startSession()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
            ReviewPromptService.shared.cancelSession()
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
                            if GuidedModePrefs.isGuidedModeEnabled() {
                                Task {
                                    let allRoutes = BusRouteRegistry.knownRoutes()
                                    if let resolved = await DeparturesService.shared.resolveDirectionIfUnambiguous(stop: stop, allRoutes: allRoutes, primaryRouteId: route.id) {
                                        navigationPath.append(StopSelection(stop: stop, primaryRouteId: resolved.0, primaryViewId: resolved.1))
                                    } else {
                                        navigationPath.append(DirectionPickerSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId))
                                    }
                                }
                            } else {
                                navigationPath.append(StopSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId))
                            }
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
                let entries = await RouteDataService.shared.getRouteEntries(routeId: route.id)
                let todayViews = await RouteDataService.shared.getRouteViews(
                    routeId: route.id, dayType: todayDayType,
                )
                showAllRoutes = entries.count > 1 || todayViews.isEmpty
                if todayViews.isEmpty {
                    // No service today — find any day type that has views so stops are still shown.
                    // NextDepartureView handles looking up to 7 days ahead for the actual departure.
                    var fallback: [RouteView] = []
                    for dayType in [DayType.weekday, .saturday, .sunday, .weekend, .holiday] {
                        let v = await RouteDataService.shared.getRouteViews(routeId: route.id, dayType: dayType)
                        if !v.isEmpty { fallback = v; break }
                    }
                    views = fallback
                } else {
                    views = todayViews
                }
                loaded = true
            }
        }
    }

    /// "Consultar otro día" variant of `RouteStopsContainer` — lands on `DayScheduleSelection`
    /// (with the date picker enabled) for the picked date instead of `StopSelection`/today,
    /// but otherwise mirrors `RouteStopsContainer`'s guided-mode direction resolution.
    private struct OtrasOpcionesRouteStopsContainer: View {
        let route: BusRoute
        let date: Date
        @Binding var navigationPath: NavigationPath
        @State private var views: [RouteView]?
        @State private var loaded = false

        private var selectedDayType: DayType {
            TimetableQuery.primaryDayType(date)
        }

        var body: some View {
            Group {
                if loaded {
                    let resolvedViews = views ?? []
                    RouteStopsView(
                        route: route,
                        views: resolvedViews,
                        onAllRoutesSelected: nil,
                        onStopSelected: { stop, viewId in
                            if GuidedModePrefs.isGuidedModeEnabled() {
                                Task {
                                    let allRoutes = BusRouteRegistry.knownRoutes()
                                    if let resolved = await DeparturesService.shared.resolveDirectionIfUnambiguous(stop: stop, allRoutes: allRoutes, primaryRouteId: route.id, referenceDate: date) {
                                        let resolvedViewsForRoute = await RouteDataService.shared.getRouteViews(routeId: resolved.0, dayType: selectedDayType)
                                        let view = resolvedViewsForRoute.first { $0.id == resolved.1 }
                                        let resolvedRoute = allRoutes.first { $0.id == resolved.0 } ?? route
                                        navigationPath.append(DayScheduleSelection(
                                            route: resolvedRoute,
                                            stop: stop,
                                            direction: view?.direction ?? "",
                                            departureLabel: view?.departureLabel,
                                            allowDateSelection: true,
                                            initialDate: date,
                                        ))
                                    } else {
                                        navigationPath.append(DirectionPickerSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId, date: date))
                                    }
                                }
                            } else {
                                let direction = resolvedViews.first { $0.id == viewId }?.direction ?? ""
                                let label = resolvedViews.first { $0.id == viewId }?.departureLabel
                                navigationPath.append(DayScheduleSelection(
                                    route: route,
                                    stop: stop,
                                    direction: direction,
                                    departureLabel: label,
                                    allowDateSelection: true,
                                    initialDate: date,
                                ))
                            }
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
                let dateViews = await RouteDataService.shared.getRouteViews(
                    routeId: route.id, dayType: selectedDayType,
                )
                if dateViews.isEmpty {
                    var fallback: [RouteView] = []
                    for dayType in [DayType.weekday, .saturday, .sunday, .weekend, .holiday] {
                        let v = await RouteDataService.shared.getRouteViews(routeId: route.id, dayType: dayType)
                        if !v.isEmpty { fallback = v; break }
                    }
                    views = fallback
                } else {
                    views = dateViews
                }
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
                    if GuidedModePrefs.isGuidedModeEnabled() {
                        Task {
                            let allRoutes = BusRouteRegistry.knownRoutes()
                            if let resolved = await DeparturesService.shared.resolveDirectionIfUnambiguous(stop: stop, allRoutes: allRoutes, primaryRouteId: route.id) {
                                navigationPath.append(StopSelection(stop: stop, primaryRouteId: resolved.0, primaryViewId: resolved.1))
                            } else {
                                navigationPath.append(DirectionPickerSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId))
                            }
                        }
                    } else {
                        navigationPath.append(StopSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId))
                    }
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
                                if GuidedModePrefs.isGuidedModeEnabled() {
                                    Task {
                                        let allRoutes = BusRouteRegistry.knownRoutes()
                                        if let resolved = await DeparturesService.shared.resolveDirectionIfUnambiguous(stop: stop, allRoutes: allRoutes, primaryRouteId: route.id) {
                                            navigationPath.append(StopSelection(stop: stop, primaryRouteId: resolved.0, primaryViewId: resolved.1))
                                        } else {
                                            navigationPath.append(DirectionPickerSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId))
                                        }
                                    }
                                } else {
                                    navigationPath.append(StopSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId))
                                }
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
                let entries = await RouteDataService.shared.getRouteEntries(routeId: route.id)
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
                        if GuidedModePrefs.isGuidedModeEnabled() {
                            Task {
                                let allRoutes = BusRouteRegistry.knownRoutes()
                                if let resolved = await DeparturesService.shared.resolveDirectionIfUnambiguous(stop: stop, allRoutes: allRoutes, primaryRouteId: route.id) {
                                    navigationPath.append(StopSelection(stop: stop, primaryRouteId: resolved.0, primaryViewId: resolved.1))
                                } else {
                                    navigationPath.append(DirectionPickerSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId))
                                }
                            }
                        } else {
                            navigationPath.append(StopSelection(stop: stop, primaryRouteId: route.id, primaryViewId: viewId))
                        }
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
                let entries = await RouteDataService.shared.getRouteEntries(routeId: route.id)
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

        await ReminderService.shared.initialize()
        await ReminderService.shared.pruneExpired()

        // Ensure network monitor is alive (starts in init)
        _ = NetworkMonitor.shared

        // Load routes
        routes = BusRouteRegistry.knownRoutes()

        // Get supported routes from RouteDataService
        let supported = await RouteDataService.shared.getSupportedRoutes()
        supportedRoutes = Set(supported)

        await HolidayService.shared.initialize()

        DebugConfig.debugPrint("InterSego: Initialization complete. \(supportedRoutes.count) routes supported.")
        isInitialized = true

        // Fetch active service alerts — detached so it can't block the splash-to-landing transition
        Task { activeAlerts = await AlertService.shared.fetchActiveAlerts() }

        // Background timetable + polyline refresh — non-blocking, uses disk cache + ETags
        if NetworkMonitor.shared.isOnline {
            Task {
                await TimetableCacheService.shared.fetchAllRoutes()
                laLigaBlockingSuspected = await LaLigaBlockingService.shared.isLikelyBlocked
            }
            Task { await PolylineCacheService.shared.fetchAllPolylines() }
            Task { await HolidayService.shared.refresh() }
        }

        // Analytics: app launch event
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        AnalyticsService.shared.track("app_launch", with: ["version": version, "platform": "ios"])

        // Show privacy consent if user hasn't made an analytics choice yet
        // (this also triggers for existing users who pre-date analytics)
        if !MonitoringPreferencesService.shared.hasUserMadeAnalyticsChoice() {
            showMonitoringConsent = true
        }
        if !NotificationPreferencesService.shared.hasUserMadeNotificationChoice() {
            showNotificationConsent = true
        }
        if WhatsNewService.shouldShow() {
            showWhatsNew = true
        }
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

// MARK: - Alert Detail Sheet

private struct AlertDetailSheet: View {
    let alerts: [ServiceAlert]

    private var sortedAlerts: [ServiceAlert] {
        let order: [String: Int] = ["critical": 0, "warning": 1, "info": 2]
        return alerts.sorted { (order[$0.severity] ?? 3) < (order[$1.severity] ?? 3) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Avisos de servicio")
                    .font(.title2)
                    .fontWeight(.bold)
                    .padding(.top, 48)

                ForEach(sortedAlerts) { alert in
                    AlertDetailCard(alert: alert)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .presentationDragIndicator(.visible)
        .presentationDetents([.medium, .large])
    }
}

private struct AlertDetailCard: View {
    let alert: ServiceAlert

    private var color: Color {
        switch alert.severity {
        case "critical": return .red
        case "warning":  return .orange
        default:         return .blue
        }
    }

    private var iconName: String {
        switch alert.severity {
        case "critical": return "exclamationmark.triangle.fill"
        case "warning":  return "exclamationmark.circle.fill"
        default:         return "info.circle.fill"
        }
    }

    private var severityLabel: String {
        switch alert.severity {
        case "critical": return "Urgente"
        case "warning":  return "Aviso"
        default:         return "Información"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: iconName)
                    .font(.caption.weight(.semibold))
                Text(severityLabel.uppercased())
                    .font(.caption.weight(.semibold))
                    .tracking(0.5)
            }
            .foregroundColor(color)

            Text(alert.title)
                .font(.headline)

            Text(alert.message)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 4) {
                Image(systemName: "calendar")
                    .font(.caption)
                Text(formatDateRange(alert.startsAt, alert.endsAt))
                    .font(.caption)
            }
            .foregroundColor(.secondary)

            if let routes = alert.affectedRoutes, !routes.isEmpty {
                HStack(spacing: 4) {
                    Text("Líneas:")
                        .font(.caption.weight(.semibold))
                    Text(routes.joined(separator: ", "))
                        .font(.caption)
                }
                .foregroundColor(.secondary)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(color.opacity(0.25), lineWidth: 1)
        )
    }

    private func formatDateRange(_ startIso: String, _ endIso: String) -> String {
        func parse(_ iso: String) -> Date? {
            let parser = ISO8601DateFormatter()
            parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            return parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        }
        guard let start = parse(startIso), let end = parse(endIso) else {
            return "\(startIso) – \(endIso)"
        }

        let madrid = TimeZone(identifier: "Europe/Madrid")!
        var calendar = Calendar.current
        calendar.timeZone = madrid

        let dateFmt = DateFormatter()
        dateFmt.locale = Locale(identifier: "es_ES")
        dateFmt.timeZone = madrid
        dateFmt.dateStyle = .medium
        dateFmt.timeStyle = .none

        let timeFmt = DateFormatter()
        timeFmt.locale = Locale(identifier: "es_ES")
        timeFmt.timeZone = madrid
        timeFmt.dateFormat = "HH:mm"

        if calendar.isDate(start, inSameDayAs: end) {
            return "\(dateFmt.string(from: start)), \(timeFmt.string(from: start)) – \(timeFmt.string(from: end))"
        } else {
            return "\(dateFmt.string(from: start)) \(timeFmt.string(from: start)) – \(dateFmt.string(from: end)) \(timeFmt.string(from: end))"
        }
    }
}

private struct LaLigaBlockingDetailSheet: View {
    private let accent = Color.orange

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 8) {
                    Image(systemName: "soccerball")
                        .foregroundColor(accent)
                    Text("¿Por qué no se actualizan los horarios?")
                        .font(.title2)
                        .fontWeight(.bold)
                }
                .padding(.top, 48)

                Text("Ahora mismo no podemos conectar con nuestro servidor. Coincide con un partido de LaLiga, y probablemente se deba al bloqueo de direcciones IP que LaLiga ordena a los operadores españoles durante los partidos para cortar las retransmisiones piratas.")
                    .font(.subheadline)

                Text("El problema es que esas direcciones IP son compartidas por Cloudflare entre miles de webs legítimas — incluida, a veces, la nuestra — que quedan bloqueadas como daño colateral sin haber hecho nada ilegal.")
                    .font(.subheadline)

                Text("Desde diciembre de 2024 la Justicia española avala esta práctica, pese a las críticas de Cloudflare y de expertos en ciberseguridad, que la consideran un ataque a la neutralidad de la red.")
                    .font(.subheadline)

                Text("Esto no es un fallo de la app: mientras dure el bloqueo verás los horarios guardados en tu propio teléfono, que pueden no reflejar cambios muy recientes.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 10) {
                    LaLigaBlockingLink(
                        text: "Seguimiento en directo de los bloqueos: hayahora.futbol",
                        url: "https://hayahora.futbol",
                        destination: "hayahora"
                    )
                    LaLigaBlockingLink(
                        text: "Comunicado de LaLiga sobre el bloqueo a clientes de Cloudflare",
                        url: "https://www.laliga.com/noticias/laliga-pone-un-buzon-a-disposicion-de-los-clientes-de-cloudflare-afectados-por-los-bloqueos",
                        destination: "laliga_statement"
                    )
                    LaLigaBlockingLink(
                        text: "Análisis técnico independiente del bloqueo (OONI)",
                        url: "https://ooni.org/post/2026-laliga-collateral/",
                        destination: "ooni_report"
                    )
                    LaLigaBlockingLink(
                        text: "El caso de Vercel, afectado por el mismo bloqueo",
                        url: "https://vercel.com/blog/update-on-spain-and-laliga-blocks-of-the-internet",
                        destination: "vercel_blog"
                    )
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .presentationDragIndicator(.visible)
        .presentationDetents([.medium, .large])
    }
}

private struct LaLigaBlockingLink: View {
    let text: String
    let url: String
    let destination: String

    var body: some View {
        Link(destination: URL(string: url)!) {
            HStack(spacing: 4) {
                Text(text)
                Image(systemName: "arrow.up.right")
            }
            .font(.subheadline.weight(.semibold))
        }
        .simultaneousGesture(TapGesture().onEnded {
            AnalyticsService.shared.track("laliga_blocking_link_tapped", with: ["destination": destination])
        })
    }
}

// MARK: - App Delegate

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Initialize error reporting and analytics if user has already opted in.
        // On first launch, these will be no-ops — the consent modal gates initialization.
        ErrorReportingService.shared.initialize()
        AnalyticsService.shared.initialize()
        ReviewPromptService.shared.recordAppLaunch()
        NSLog("🔔 Calling registerForRemoteNotifications")
        UIApplication.shared.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        Task { await ReminderService.shared.updateDeviceToken(token) }
        Task { await DeviceTokenService.shared.registerToken(token, platform: "ios") }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("APNs registration failed: \(error)")
    }
}
