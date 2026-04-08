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

// MARK: - Day Type Matching

func dayTypesForCalendarDay(_ weekday: Int) -> Set<DayType> {
    switch weekday {
    case 7: [.saturday, .weekend] // Saturday
    case 1: [.sunday, .weekend, .holiday] // Sunday
    default: [.weekday]
    }
}

// MARK: - Time of Day

private enum TimeOfDay {
    case morning // 6:00 - 12:59
    case afternoon // 13:00 - 19:59
    case evening // 20:00 - 5:59

    var label: String {
        switch self {
        case .morning: "Mañana"
        case .afternoon: "Tarde"
        case .evening: "Noche"
        }
    }

    var icon: String {
        switch self {
        case .morning: "sun.max.fill"
        case .afternoon: "sun.haze.fill"
        case .evening: "moon.fill"
        }
    }

    var color: Color {
        switch self {
        case .morning: .orange
        case .afternoon: .blue
        case .evening: .indigo
        }
    }
}

private func getTimeOfDay(_ hour: Int) -> TimeOfDay {
    switch hour {
    case 6 ... 12: .morning
    case 13 ... 19: .afternoon
    default: .evening
    }
}

// MARK: - Tagged Departure

private struct TaggedDeparture: Identifiable {
    let id = UUID()
    let departure: DepartureTime
    let routeId: String
    let routeNumber: String
}

// MARK: - Route Loaded Data

private struct RouteLoadedData {
    let route: BusRoute
    let views: [RouteView]
    let timetables: [BusTimetable]
}

// MARK: - Departure Info

private struct DepartureInfo {
    let departure: TaggedDeparture?
    let following: [TaggedDeparture]
    let daysAhead: Int // 0 = today, 1 = tomorrow, 2+ = future
}

// MARK: - Next Departure View

struct NextDepartureView: View {
    let stop: BusStop
    let primaryRouteId: String?
    let primaryViewId: String?

    @State private var direction: String = ""
    @State private var routesData: [RouteLoadedData] = []
    @State private var selectedRouteId: String? = nil
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var currentTime = Date()
    @State private var reminderKeys: Set<String> = []
    @State private var dailyReminderKeys: Set<String> = []
    @State private var reminderErrorMessage: String?
    @State private var showReminderAlert = false
    @AppStorage("bellTutorialShown") private var bellTutorialShown = false
    @State private var showBellTip = false

    // Boarding state
    @State private var boardingConfirmed = false
    @State private var boardingSubmitting = false
    @State private var boardingError: String? = nil
    @State private var showBoardingError = false
    @State private var showBoardingWindowTooltip = false
    @State private var activeBoardings: [BoardingEvent] = []

    init(stop: BusStop, primaryRouteId: String?, primaryViewId: String?) {
        self.stop = stop
        self.primaryRouteId = primaryRouteId
        self.primaryViewId = primaryViewId
    }

    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var currentWeekday: Int {
        Calendar.current.component(.weekday, from: Date())
    }

    private var currentDayTypes: Set<DayType> {
        dayTypesForCalendarDay(currentWeekday)
    }

    private var currentDayType: DayType {
        switch currentWeekday {
        case 7: return .saturday
        case 1: return .sunday
        default: return .weekday
        }
    }

    private var activeRoutesData: [RouteLoadedData] {
        if let id = selectedRouteId {
            return routesData.filter { $0.route.id == id }
        }
        return routesData
    }

    private var availableDirections: [String] {
        var seen = Set<String>()
        var result: [String] = []
        for routeData in activeRoutesData {
            for t in routeData.timetables {
                guard let dir = t.direction, t.stopId == stop.id else { continue }
                if seen.insert(dir).inserted { result.append(dir) }
            }
        }
        return result
    }

    private var swapDirection: String? {
        availableDirections.first { $0 != direction }
    }

    /// True when the displayed departure is within ±20 minutes of now.
    private var isWithinBoardingWindow: Bool {
        guard let next = departureInfo.departure, departureInfo.daysAhead == 0 else { return false }
        let h = Calendar.current.component(.hour, from: currentTime)
        let m = Calendar.current.component(.minute, from: currentTime)
        let currentMinutes = h * 60 + m
        return abs(next.departure.minutesSinceMidnight - currentMinutes) <= 20
    }

    private var boardingWindowTooltipText: String {
        guard let next = departureInfo.departure else {
            return "No hay salidas próximas para este trayecto."
        }
        let h = Calendar.current.component(.hour, from: currentTime)
        let m = Calendar.current.component(.minute, from: currentTime)
        let currentMinutes = h * 60 + m
        let depMinutes = next.departure.minutesSinceMidnight
        let diff = depMinutes - currentMinutes
        if diff > 20 {
            let mins = diff - 20
            return "Disponible en \(mins) min, cuando el bus esté más cerca."
        } else {
            return "La ventana de confirmación ha pasado. Espera al siguiente bus."
        }
    }

    private var isMultiRoute: Bool { activeRoutesData.count > 1 }

    private var singleActiveRoute: BusRoute? {
        activeRoutesData.count == 1 ? activeRoutesData.first?.route : nil
    }

    private var currentTripKey: String? {
        guard let next = departureInfo.departure else { return nil }
        return "\(next.routeId)|\(direction)|\(currentDayType.rawValue)|\(next.departure.displayString)"
    }

    private var matchingBoardings: [BoardingEvent] {
        guard let next = departureInfo.departure else { return [] }
        let myMinutes = next.departure.hour * 60 + next.departure.minute
        return activeBoardings.filter { boarding in
            let parts = boarding.tripKey.split(separator: "|", maxSplits: 3).map(String.init)
            guard parts.count == 4 else { return false }
            let timeParts = parts[3].split(separator: ":").map(String.init)
            guard timeParts.count == 2,
                  let bHour = Int(timeParts[0]),
                  let bMin = Int(timeParts[1]) else { return false }
            let bMinutes = bHour * 60 + bMin
            return parts[0] == next.routeId
                && parts[1] == direction
                && parts[2] == currentDayType.rawValue
                && bMinutes <= myMinutes
                && myMinutes - bMinutes <= 90
        }
    }

    private var adjustedETA: String? {
        guard let latest = matchingBoardings.max(by: { $0.boardedAt < $1.boardedAt }),
              let scheduled = latest.scheduledDepartureDate,
              let boarded = latest.boardedAtDate,
              let next = departureInfo.departure else { return nil }
        let latenessMinutes = Int(boarded.timeIntervalSince(scheduled) / 60)
        let myMinutes = next.departure.hour * 60 + next.departure.minute
        let adjusted = ((myMinutes + latenessMinutes) % 1440 + 1440) % 1440
        return String(format: "%02d:%02d", adjusted / 60, adjusted % 60)
    }

    private var todayDepartures: [TaggedDeparture] {
        var result: [TaggedDeparture] = []
        for routeData in activeRoutesData {
            let matching = routeData.timetables.filter { t in
                currentDayTypes.contains(t.dayType)
                    && t.stopId == stop.id
                    && t.direction == direction
            }
            for dep in matching.flatMap({ $0.seasonalDepartures(weekday: currentWeekday) }).sorted() {
                result.append(TaggedDeparture(
                    departure: dep, routeId: routeData.route.id,
                    routeNumber: routeData.route.number,
                ))
            }
        }
        return result.sorted { $0.departure < $1.departure }
    }

    private var departureInfo: DepartureInfo {
        let cal = Calendar.current
        let currentHour = cal.component(.hour, from: currentTime)
        let currentMinute = cal.component(.minute, from: currentTime)
        let currentMinutes = currentHour * 60 + currentMinute

        let today = todayDepartures
        let upcoming = today.filter { $0.departure.isFuture(currentHour: currentHour, currentMinute: currentMinute) }

        // If a bus departed within the last 20 minutes, show it as primary so
        // the boarding button always refers to the trip the user can see on screen.
        let justDeparted = today.last(where: {
            let diff = currentMinutes - $0.departure.minutesSinceMidnight
            return diff > 0 && diff <= 20
        })
        if let recent = justDeparted {
            return DepartureInfo(departure: recent, following: Array(upcoming.prefix(5)), daysAhead: 0)
        }

        if !upcoming.isEmpty {
            return DepartureInfo(
                departure: upcoming.first,
                following: Array(upcoming.dropFirst().prefix(5)),
                daysAhead: 0,
            )
        }

        for daysAhead in 1 ... 7 {
            guard let futureDate = Calendar.current.date(byAdding: .day, value: daysAhead, to: Date()) else { continue }
            let futureWeekday = Calendar.current.component(.weekday, from: futureDate)
            let futureDayTypes = dayTypesForCalendarDay(futureWeekday)

            var tagged: [TaggedDeparture] = []
            for routeData in activeRoutesData {
                let deps = routeData.timetables
                    .filter { futureDayTypes.contains($0.dayType) && $0.stopId == stop.id && $0.direction == direction }
                    .flatMap { $0.seasonalDepartures(weekday: futureWeekday) }
                    .sorted()
                for dep in deps {
                    tagged.append(TaggedDeparture(
                        departure: dep, routeId: routeData.route.id,
                        routeNumber: routeData.route.number,
                    ))
                }
            }
            let sorted = tagged.sorted { $0.departure < $1.departure }
            if !sorted.isEmpty {
                return DepartureInfo(
                    departure: sorted.first,
                    following: Array(sorted.dropFirst().prefix(5)),
                    daysAhead: daysAhead,
                )
            }
        }

        return DepartureInfo(departure: nil, following: [], daysAhead: 0)
    }

    var body: some View {
        content
            .navigationTitle(stop.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if let target = swapDirection {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { direction = target } label: {
                            Image(systemName: "arrow.up.arrow.down")
                        }
                    }
                }
            }
            .task { await loadTimetables() }
            .task { await refreshReminderKeys() }
            .task(id: currentTripKey) { await pollBoardings() }
            .onReceive(timer) { time in
                currentTime = time
                Task { await pollBoardings() }
            }
            .alert("Error al programar el recordatorio", isPresented: $showReminderAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(reminderErrorMessage ?? "")
            }
            .alert("Error al enviar", isPresented: $showBoardingError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(boardingError ?? "")
            }
            .sheet(isPresented: $showBoardingWindowTooltip) {
                BoardingWindowExplanationSheet(tooltipText: boardingWindowTooltipText)
            }
            .onChange(of: direction) { _, _ in boardingConfirmed = false }
            .onChange(of: currentTripKey) { _, _ in boardingConfirmed = false }
            .onAppear {
                if !bellTutorialShown {
                    bellTutorialShown = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                        withAnimation { showBellTip = true }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 4.5) {
                            withAnimation { showBellTip = false }
                        }
                    }
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            VStack(spacing: 16) {
                ProgressView().controlSize(.large)
                Text("Cargando horarios...").foregroundColor(.secondary)
            }
        } else if let error = errorMessage {
            Text(error).foregroundColor(.red).multilineTextAlignment(.center).padding()
        } else if departureInfo.departure == nil {
            Text("No hay horarios disponibles para esta parada")
                .font(.title3).multilineTextAlignment(.center).padding()
        } else {
            departureScrollView
        }
    }

    private var departureScrollView: some View {
        let info = departureInfo
        let isToday = info.daysAhead == 0
        return ScrollView {
            VStack(spacing: 0) {
                StopHeroHeader(stop: stop, direction: direction)

                if routesData.count > 1 {
                    LineFilterChips(routes: routesData.map(\.route), selectedRouteId: $selectedRouteId)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                }

                if info.daysAhead > 0 {
                    FutureDayWarningCard(daysAhead: info.daysAhead)
                        .padding(.horizontal, 16).padding(.vertical, 12)
                }

                if let next = info.departure {
                    NextDepartureCard(
                        tagged: next,
                        currentTime: currentTime,
                        selectedVariantLabel: variantLabel(for: next),
                        daysAhead: info.daysAhead,
                        showRouteBadge: isMultiRoute,
                        adjustedETA: adjustedETA,
                        boardingCount: matchingBoardings.count,
                        bellState: isToday ? bellState(for: next) : .off,
                        onBellTap: isToday ? { handleBellTap(for: next) } : nil,
                        onBellLongPress: isToday ? { handleBellLongPress(for: next) } : nil,
                    )
                    .padding(.horizontal, 16).padding(.top, 8)
                }

                TimesDisclaimerCard()
                    .padding(.horizontal, 16).padding(.top, 16)

                // "Estoy en el autobús" button — always visible for today, active within ±20 min
                if info.daysAhead == 0 {
                    BoardingButton(
                        confirmed: boardingConfirmed,
                        isLoading: boardingSubmitting,
                        isActive: isWithinBoardingWindow,
                        onTap: handleBoardingTap,
                        onInactiveTap: { showBoardingWindowTooltip = true }
                    )
                    .padding(.horizontal, 16).padding(.top, 12)
                }

                if let activeRoute = singleActiveRoute {
                    let scheduleOverrideDayType: DayType? = {
                        guard info.daysAhead > 0,
                              let futureDate = Calendar.current.date(byAdding: .day, value: info.daysAhead, to: Date())
                        else { return nil }
                        let weekday = Calendar.current.component(.weekday, from: futureDate)
                        return weekday == 7 ? .saturday : (weekday == 1 ? .sunday : .weekday)
                    }()
                    NavigationLink(value: DayScheduleSelection(
                        route: activeRoute, stop: stop, direction: direction,
                        departureLabel: viewDepartureLabel(for: activeRoute.id),
                        overrideDayType: scheduleOverrideDayType
                    )) {
                        HStack(spacing: 8) {
                            Image(systemName: "clock.arrow.2.circlepath").font(.subheadline)
                            Text("Ver horario completo").font(.subheadline).fontWeight(.medium)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 16).padding(.top, 12)
                }

                if !info.following.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Siguientes salidas")
                            .font(.title2).fontWeight(.bold).padding(.horizontal, 20)
                        DepartureTimeline(
                            departures: info.following,
                            showRouteBadge: isMultiRoute,
                            selectedVariantLabelFor: { t in variantLabel(for: t) },
                            bellStateFor: isToday ? { t in bellState(for: t) } : nil,
                            onBellTap: isToday ? { t in handleBellTap(for: t) } : nil,
                            onBellLongPress: isToday ? { t in handleBellLongPress(for: t) } : nil,
                        )
                        .padding(.horizontal, 16)
                    }
                    .padding(.top, 24)
                }

                Spacer(minLength: 32)
            }
        }
        .overlay(alignment: .bottom) {
            if showBellTip {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill").foregroundColor(.accentColor)
                    Text("Pulsa la campana para aviso puntual · Mantén pulsado para recordatorio diario")
                        .font(.caption)
                }
                .padding(12)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
                .padding(.horizontal, 16).padding(.bottom, 12)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .onTapGesture { withAnimation { showBellTip = false } }
            }
        }
    }

    private func variantLabel(for tagged: TaggedDeparture) -> String? {
        routesData.first { $0.route.id == tagged.routeId }?
            .views.first { $0.direction == direction }?.departureLabel
    }

    private func viewDepartureLabel(for routeId: String) -> String? {
        routesData.first { $0.route.id == routeId }?
            .views.first { $0.direction == direction }?.departureLabel
    }

    private func loadTimetables() async {
        isLoading = true
        errorMessage = nil

        let allRouteIds = await PDFProcessingService.shared.getRoutesForStop(stopId: stop.id)
        let allBusRoutes = BusRouteRegistry.knownRoutes()
        let dayType = TimetableService.shared.getCurrentDayType()

        var loaded: [RouteLoadedData] = []
        for routeId in allRouteIds.sorted() {
            guard let busRoute = allBusRoutes.first(where: { $0.id == routeId }) else { continue }
            let timetables = await TimetableService.shared.loadTimetables(routeId: routeId)
            guard !timetables.isEmpty else { continue }
            let views = await PDFProcessingService.shared.getRouteViews(routeId: routeId, dayType: dayType)
            loaded.append(RouteLoadedData(route: busRoute, views: views, timetables: timetables))
        }

        routesData = loaded

        if let primaryId = primaryRouteId,
           let primaryRoute = loaded.first(where: { $0.route.id == primaryId })
        {
            let targetView = primaryViewId.flatMap { vid in primaryRoute.views.first { $0.id == vid } }
                ?? primaryRoute.views.first
            direction = targetView?.direction ?? loaded.first?.views.first?.direction ?? ""
            selectedRouteId = primaryId
        } else {
            direction = loaded.first?.views.first?.direction ?? ""
        }

        if loaded.isEmpty { errorMessage = "Error al cargar horarios" }
        isLoading = false
    }

    // MARK: - Reminder helpers

    private func refreshReminderKeys() async {
        async let keys = ReminderService.shared.activeMatchKeys()
        async let dailyKeys = ReminderService.shared.dailyMatchKeys()
        reminderKeys = await keys
        dailyReminderKeys = await dailyKeys
    }

    private func bellState(for tagged: TaggedDeparture) -> BellState {
        let key = BusReminder.matchKey(
            routeId: tagged.routeId, stopId: stop.id, direction: direction,
            hour: tagged.departure.hour, minute: tagged.departure.minute
        )
        if dailyReminderKeys.contains(key) { return .daily }
        if reminderKeys.contains(key) { return .oneOff }
        return .off
    }

    private func handleBellTap(for tagged: TaggedDeparture) {
        reminderErrorMessage = nil
        let state = bellState(for: tagged)
        guard let route = routesData.first(where: { $0.route.id == tagged.routeId })?.route else { return }
        Task {
            if state != .off {
                await ReminderService.shared.cancelReminder(
                    routeId: tagged.routeId, stopId: stop.id, direction: direction,
                    hour: tagged.departure.hour, minute: tagged.departure.minute
                )
            } else {
                do {
                    try await ReminderService.shared.scheduleReminder(
                        departure: tagged.departure, stop: stop, route: route,
                        direction: direction, dayType: currentDayType
                    )
                } catch {
                    reminderErrorMessage = error.localizedDescription
                    showReminderAlert = true
                }
            }
            await refreshReminderKeys()
        }
    }

    private func handleBellLongPress(for tagged: TaggedDeparture) {
        reminderErrorMessage = nil
        let state = bellState(for: tagged)
        guard let route = routesData.first(where: { $0.route.id == tagged.routeId })?.route else { return }
        Task {
            if state == .daily {
                await ReminderService.shared.cancelReminder(
                    routeId: tagged.routeId, stopId: stop.id, direction: direction,
                    hour: tagged.departure.hour, minute: tagged.departure.minute
                )
            } else {
                if state == .oneOff {
                    await ReminderService.shared.cancelReminder(
                        routeId: tagged.routeId, stopId: stop.id, direction: direction,
                        hour: tagged.departure.hour, minute: tagged.departure.minute
                    )
                }
                do {
                    try await ReminderService.shared.scheduleReminder(
                        departure: tagged.departure, stop: stop, route: route,
                        direction: direction, isDaily: true, dayType: currentDayType
                    )
                } catch {
                    reminderErrorMessage = error.localizedDescription
                    showReminderAlert = true
                }
            }
            await refreshReminderKeys()
        }
    }

    // MARK: - Boarding helpers

    private func pollBoardings() async {
        activeBoardings = await BoardingService.shared.fetchActiveBoardings()
    }

    private func handleBoardingTap() {
        Task { await submitBoarding(direction: direction) }
    }

    private func nextDeparture(forDirection dir: String) -> DepartureTime? {
        let cal = Calendar.current
        let h = cal.component(.hour, from: currentTime)
        let m = cal.component(.minute, from: currentTime)
        return activeRoutesData
            .flatMap { routeData in
                routeData.timetables
                    .filter { currentDayTypes.contains($0.dayType) && $0.stopId == stop.id && $0.direction == dir }
                    .flatMap { $0.seasonalDepartures(weekday: currentWeekday) }
            }
            .sorted()
            .first { $0.isFuture(currentHour: h, currentMinute: m) }
    }

    private func submitBoarding(direction dir: String) async {
        guard let next = departureInfo.departure else { return }
        boardingSubmitting = true
        defer { boardingSubmitting = false }
        // Use the actual next departure for the chosen direction (may differ from the displayed one)
        let departure = (dir == direction) ? next.departure : (nextDeparture(forDirection: dir) ?? next.departure)
        let request = BoardingRequest.make(
            stop: stop,
            routeId: next.routeId,
            direction: dir,
            dayType: currentDayType,
            departure: departure
        )
        do {
            try await BoardingService.shared.postBoarding(request)
            boardingConfirmed = true
            activeBoardings = await BoardingService.shared.fetchActiveBoardings()
        } catch {
            boardingError = error.localizedDescription
            showBoardingError = true
        }
    }
}

// MARK: - Bell State

private enum BellState { case off, oneOff, daily }

// MARK: - Line Filter Chips

private struct LineFilterChips: View {
    let routes: [BusRoute]
    @Binding var selectedRouteId: String?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterChip(label: "Todas", isSelected: selectedRouteId == nil) {
                    selectedRouteId = nil
                }
                ForEach(routes, id: \.id) { route in
                    FilterChip(label: route.number, isSelected: selectedRouteId == route.id) {
                        selectedRouteId = route.id
                    }
                }
            }
        }
    }
}

private struct FilterChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption)
                .fontWeight(.semibold)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.accentColor : Color(.systemGray5))
                .foregroundColor(isSelected ? .white : .primary)
                .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Stop Hero Header

private struct StopHeroHeader: View {
    let stop: BusStop
    let direction: String

    private var directionLabel: String {
        let parts = direction.split(separator: "→")
        guard parts.count >= 2 else { return direction }
        let destination = parts.last?.trimmingCharacters(in: .whitespaces) ?? direction
        return "Dirección \(destination)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Stop name
            Text(stop.name)
                .font(.title)
                .fontWeight(.bold)

            // Direction pill
            Text(directionLabel)
                .font(.caption)
                .foregroundColor(.accentColor)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(Color.accentColor.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 6))

            // Area pill
            if let area = stop.area {
                HStack(spacing: 6) {
                    Image(systemName: "building.2")
                        .font(.caption2)
                    Text(area)
                        .font(.caption)
                }
                .foregroundColor(.secondary)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 6))
            }

            // Details
            if let details = stop.details {
                HStack(spacing: 4) {
                    Image(systemName: "info.circle")
                        .font(.caption2)
                    Text(details)
                        .font(.caption)
                }
                .foregroundColor(.secondary)
            }

            // Navigate link
            if let lat = stop.resolvedLatitude, let lon = stop.resolvedLongitude {
                Button {
                    let url = URL(string: "maps://?daddr=\(lat),\(lon)&dirflg=w")!
                    UIApplication.shared.open(url)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "location.fill")
                            .font(.caption)
                        Text("Cómo llegar")
                            .font(.subheadline)
                            .underline()
                    }
                }
            }

            // Map tile
            if let lat = stop.resolvedLatitude, let lon = stop.resolvedLongitude {
                Map(initialPosition: .region(MKCoordinateRegion(
                    center: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                    span: MKCoordinateSpan(latitudeDelta: 0.005, longitudeDelta: 0.005),
                ))) {
                    Marker(stop.name, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon))
                }
                .frame(height: 150)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .allowsHitTesting(false)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(
            LinearGradient(
                colors: [Color.accentColor.opacity(0.12), Color(.systemBackground)],
                startPoint: .top,
                endPoint: .bottom,
            ),
        )
    }
}

// MARK: - Future Day Warning Card

private struct FutureDayWarningCard: View {
    let daysAhead: Int

    private var message: String {
        switch daysAhead {
        case 1: "No hay más autobuses hoy. Mostrando horario de mañana."
        case 2: "No hay más autobuses hoy ni mañana. Mostrando horario de pasado mañana."
        default: "No hay más autobuses en los próximos días. Mostrando próximo horario disponible."
        }
    }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.subheadline)
            Text(message)
                .font(.caption)
                .lineSpacing(2)
        }
        .foregroundColor(.orange)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Times Disclaimer Card

private let warningOrangeText = Color(red: 0.55, green: 0.37, blue: 0.0)

private struct TimesDisclaimerCard: View {
    @State private var isExpanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "info.circle")
                    .font(.subheadline)
                    .foregroundColor(.orange)

                Text("Los horarios son orientativos")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(warningOrangeText)

                Spacer()

                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .font(.caption2)
                    .foregroundColor(.orange)
            }

            if isExpanded {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Se recomienda estar en la parada con 10–15 minutos de antelación.")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(warningOrangeText)

                    DisclaimerBulletPoint("Los horarios oficiales solo ofrecen una orientación del paso del autobús.")
                    DisclaimerBulletPoint("Los autobuses no disponen de GPS para estimar la hora de paso.")
                    DisclaimerBulletPoint("Se han dado casos de adelantos y retrasos respecto al horario previsto.")
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .onTapGesture {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                isExpanded.toggle()
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Los horarios son orientativos")
        .accessibilityHint(isExpanded ? "Contraer" : "Expandir")
    }
}

private struct DisclaimerBulletPoint: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text("•")
                .font(.caption)
            Text(text)
                .font(.caption)
        }
        .foregroundColor(warningOrangeText)
        .padding(.leading, 4)
    }
}

// MARK: - Bell Button

/// Bell icon with tap (one-off) and long-press (daily) gestures.
/// Shows a repeat badge overlay when the reminder is daily.
private struct BellButton: View {
    let bellState: BellState
    var onTap: (() -> Void)? = nil
    var onLongPress: (() -> Void)? = nil

    var body: some View {
        ZStack {
            if bellState == .daily {
                Image(systemName: "arrow.2.circlepath")
                    .font(.system(size: 26))
                    .foregroundColor(.accentColor)
                Image(systemName: "bell.fill")
                    .font(.system(size: 10))
                    .foregroundColor(.accentColor)
            } else {
                Image(systemName: bellState == .off ? "bell" : "bell.fill")
                    .foregroundColor(bellState == .off ? .secondary : .accentColor)
            }
        }
        .frame(width: 30, height: 30)
        .contentShape(Rectangle())
        .onTapGesture { onTap?() }
        .onLongPressGesture {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            onLongPress?()
        }
    }
}

// MARK: - Next Departure Card

private struct NextDepartureCard: View {
    let tagged: TaggedDeparture
    let currentTime: Date
    let selectedVariantLabel: String?
    let daysAhead: Int
    let showRouteBadge: Bool
    var adjustedETA: String? = nil
    var boardingCount: Int = 0
    var bellState: BellState = .off
    var onBellTap: (() -> Void)? = nil
    var onBellLongPress: (() -> Void)? = nil

    @State private var showBoardingInfo = false

    private var departure: DepartureTime { tagged.departure }

    private var minutesUntil: Int {
        let cal = Calendar.current
        let currentHour = cal.component(.hour, from: currentTime)
        let currentMinute = cal.component(.minute, from: currentTime)

        if daysAhead > 0 {
            let minutesUntilMidnight = (23 - currentHour) * 60 + (60 - currentMinute)
            let minutesFromMidnight = departure.hour * 60 + departure.minute
            let fullDaysMinutes = (daysAhead - 1) * 24 * 60
            return minutesUntilMidnight + fullDaysMinutes + minutesFromMidnight
        }

        return departure.minutesUntil(currentHour: currentHour, currentMinute: currentMinute)
    }

    private var liveMinutesUntil: Int? {
        guard let eta = adjustedETA, boardingCount > 0 else { return nil }
        let parts = eta.split(separator: ":").map(String.init)
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        let cal = Calendar.current
        let currentHour = cal.component(.hour, from: currentTime)
        let currentMinute = cal.component(.minute, from: currentTime)
        var diff = (h * 60 + m) - (currentHour * 60 + currentMinute)
        if diff < -720 { diff += 1440 }
        return diff
    }

    private var countdownText: String {
        let mins = minutesUntil
        switch mins {
        case ..<0: return "Salió hace \(-mins) min"
        case 0: return "Saliendo ahora"
        case 1: return "Sale en 1 minuto"
        case 2 ..< 60: return "Sale en \(mins) minutos"
        default:
            let hours = mins / 60
            let remainder = mins % 60
            if remainder == 0 {
                return "Sale en \(hours) hora\(hours > 1 ? "s" : "")"
            }
            return "Sale en \(hours)h \(remainder)m"
        }
    }

    private var showVariantLabel: Bool {
        departure.shouldShowVariantLabel(selectedVariantLabel: selectedVariantLabel)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Top row: label + time badge
            HStack(spacing: 8) {
                Text(minutesUntil < 0 ? "Última salida:" : "Próxima salida:")
                    .font(.headline)

                DepartureTimeBadge(time: departure.displayString)

                if showRouteBadge {
                    RouteBadge(number: tagged.routeNumber)
                }

                if showVariantLabel, let label = departure.variantLabel {
                    Text(label)
                        .font(.caption2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(.systemGray5))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }

                if let seasonalLabel = departure.seasonalAvailability.displayLabel {
                    Text(seasonalLabel)
                        .font(.caption2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.accentColor.opacity(0.12))
                        .foregroundColor(.accentColor)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }

                if onBellTap != nil || onBellLongPress != nil {
                    Spacer()
                    BellButton(bellState: bellState, onTap: onBellTap, onLongPress: onBellLongPress)
                        .font(.headline)
                }
            }

            // Tiempo real (subtle, right under Próxima salida)
            if let eta = adjustedETA, boardingCount > 0 {
                HStack(spacing: 4) {
                    Text("Tiempo real: \(eta)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Button {
                        showBoardingInfo = true
                    } label: {
                        Image(systemName: "info.circle")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                    .sheet(isPresented: $showBoardingInfo) {
                        let who = boardingCount == 1 ? "1 usuario confirmó" : "\(boardingCount) usuarios confirmaron"
                        VStack(spacing: 16) {
                            Image(systemName: "bus.fill")
                                .font(.largeTitle)
                                .foregroundColor(.accentColor)
                            Text("Tiempo real")
                                .font(.headline)
                            Text("\(who) que están en este autobús, lo que nos permite ajustar el tiempo estimado de llegada a \(eta).")
                                .font(.body)
                                .multilineTextAlignment(.center)
                                .foregroundColor(.secondary)
                            Button("Entendido") { showBoardingInfo = false }
                                .buttonStyle(.borderedProminent)
                        }
                        .padding(32)
                        .presentationDetents([.fraction(0.35)])
                    }
                }
            }

            // Countdown (with live suffix when ETA available)
            let liveSuffix: String = {
                guard let m = liveMinutesUntil else { return "" }
                let str = m < 60 ? "\(m)m" : "\(m / 60)h \(m % 60)m"
                return " (\(str) 🔴)"
            }()
            Text(countdownText + liveSuffix)
                .font(.body)
                .foregroundColor(.secondary)

            // Notes
            if let notes = departure.notes, !notes.isEmpty {
                Text(notes)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Route Badge

private struct RouteBadge: View {
    let number: String

    var body: some View {
        Text(number)
            .font(.caption2)
            .fontWeight(.bold)
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(Color.accentColor)
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

// MARK: - Departure Time Badge

private struct DepartureTimeBadge: View {
    let time: String

    var body: some View {
        Text(time)
            .font(.title2)
            .fontWeight(.bold)
            .foregroundColor(.accentColor)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(Color.accentColor.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Time of Day Indicator

private struct TimeOfDayIndicator: View {
    let timeOfDay: TimeOfDay

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: timeOfDay.icon)
                .font(.caption2)
            Text(timeOfDay.label)
                .font(.caption2)
                .fontWeight(.medium)
        }
        .foregroundColor(timeOfDay.color)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(timeOfDay.color.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

// MARK: - Departure Timeline

private struct DepartureTimeline: View {
    let departures: [TaggedDeparture]
    let showRouteBadge: Bool
    var selectedVariantLabelFor: ((TaggedDeparture) -> String?)? = nil
    var bellStateFor: ((TaggedDeparture) -> BellState)? = nil
    var onBellTap: ((TaggedDeparture) -> Void)? = nil
    var onBellLongPress: ((TaggedDeparture) -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(departures.enumerated()), id: \.offset) { index, tagged in
                let departure = tagged.departure
                let timeOfDay = getTimeOfDay(departure.hour)
                let isLast = index == departures.count - 1

                HStack(spacing: 16) {
                    // Timeline dot + line
                    VStack(spacing: 0) {
                        Circle()
                            .fill(Color.accentColor)
                            .frame(width: 12, height: 12)

                        if !isLast {
                            Rectangle()
                                .fill(Color(.separator))
                                .frame(width: 2)
                                .frame(maxHeight: .infinity)
                        }
                    }
                    .frame(width: 32)

                    // Departure row
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 8) {
                                Text(departure.displayString)
                                    .font(.headline)

                                if showRouteBadge {
                                    RouteBadge(number: tagged.routeNumber)
                                }

                                let variantLabel = selectedVariantLabelFor?(tagged)
                                let showLabel = departure.shouldShowVariantLabel(selectedVariantLabel: variantLabel)

                                if showLabel, let label = departure.variantLabel {
                                    Text(label)
                                        .font(.caption2)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color(.systemGray5))
                                        .clipShape(RoundedRectangle(cornerRadius: 4))
                                }

                                if let seasonalLabel = departure.seasonalAvailability.displayLabel {
                                    Text(seasonalLabel)
                                        .font(.caption2)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.accentColor.opacity(0.12))
                                        .foregroundColor(.accentColor)
                                        .clipShape(RoundedRectangle(cornerRadius: 4))
                                }
                            }

                            if let notes = departure.notes, !notes.isEmpty {
                                Text(notes)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }

                        Spacer()

                        TimeOfDayIndicator(timeOfDay: timeOfDay)

                        if bellStateFor != nil || onBellTap != nil {
                            let state = bellStateFor?(tagged) ?? .off
                            BellButton(
                                bellState: state,
                                onTap: onBellTap.map { tap in { tap(tagged) } },
                                onLongPress: onBellLongPress.map { lp in { lp(tagged) } }
                            )
                            .font(.subheadline)
                        }
                    }
                    .padding(16)
                    .background(Color(.secondarySystemBackground).opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.vertical, 4)
            }
        }
    }
}

// MARK: - Boarding Button

private struct BoardingButton: View {
    let confirmed: Bool
    let isLoading: Bool
    let isActive: Bool
    let onTap: () -> Void
    let onInactiveTap: () -> Void

    var body: some View {
        Button(action: { if isActive { onTap() } else { onInactiveTap() } }) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                } else {
                    Image(systemName: confirmed ? "checkmark.circle.fill" : "bus")
                }
                Text(confirmed ? "¡Gracias por confirmar!" : "Estoy en el autobús")
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
        }
        .buttonStyle(.borderedProminent)
        .disabled(confirmed || isLoading)
        .opacity(!isActive && !confirmed ? 0.5 : 1.0)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - Boarding Window Explanation Sheet

private struct BoardingWindowExplanationSheet: View {
    let tooltipText: String

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "clock.badge.exclamationmark")
                .font(.system(size: 48))
                .foregroundStyle(.orange)

            VStack(spacing: 12) {
                Text("Confirmación no disponible")
                    .font(.title3)
                    .fontWeight(.semibold)
                    .multilineTextAlignment(.center)

                Text(tooltipText)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                Text("El botón se activa en los 20 minutos antes y después de cada salida, para que tu confirmación sea útil para otros viajeros.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 4)
            }
        }
        .padding(32)
        .presentationDetents([.height(360)])
        .presentationDragIndicator(.visible)
    }
}
