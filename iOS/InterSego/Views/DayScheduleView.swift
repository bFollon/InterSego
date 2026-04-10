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

// MARK: - Day Schedule Selection

struct DayScheduleSelection: Hashable {
    let route: BusRoute
    let stop: BusStop
    let direction: String
    let departureLabel: String?
    let overrideDayType: DayType?

    init(route: BusRoute, stop: BusStop, direction: String, departureLabel: String?, overrideDayType: DayType? = nil) {
        self.route = route
        self.stop = stop
        self.direction = direction
        self.departureLabel = departureLabel
        self.overrideDayType = overrideDayType
    }
}

// MARK: - Day Schedule View

struct DayScheduleView: View {
    let route: BusRoute
    let stop: BusStop
    let direction: String
    let selectedVariantLabel: String?
    let overrideDayType: DayType?

    init(route: BusRoute, stop: BusStop, direction: String, selectedVariantLabel: String?, overrideDayType: DayType? = nil) {
        self.route = route
        self.stop = stop
        self.direction = direction
        self.selectedVariantLabel = selectedVariantLabel
        self.overrideDayType = overrideDayType
    }

    @State private var timetables: [BusTimetable] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var currentTime = Date()
    @State private var reminderKeys: Set<String> = []
    @State private var dailyReminderKeys: Set<String> = []
    @State private var reminderErrorMessage: String?
    @State private var showReminderAlert = false

    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var currentWeekday: Int {
        Calendar.current.component(.weekday, from: Date())
    }

    private var currentDayTypes: Set<DayType> {
        if let override = overrideDayType { return dayTypesFor(override) }
        return dayTypesForToday(currentWeekday)
    }

    private var todayDepartures: [DepartureTime] {
        let matching = timetables.filter { timetable in
            currentDayTypes.contains(timetable.dayType)
                && timetable.stopId == stop.id
                && timetable.direction == direction
        }
        return matching.flatMap { $0.seasonalDepartures(weekday: currentWeekday) }.sorted()
    }

    private var dayTypeLabel: String {
        switch overrideDayType ?? (currentWeekday == 7 ? .saturday : currentWeekday == 1 ? .sunday : .weekday) {
        case .saturday, .weekend: "Sábado"
        case .sunday, .holiday: "Domingo"
        default: "Lunes a Viernes"
        }
    }

    private var nowMarkerIndex: Int {
        let cal = Calendar.current
        let currentHour = cal.component(.hour, from: currentTime)
        let currentMinute = cal.component(.minute, from: currentTime)

        let departures = todayDepartures
        for (index, dep) in departures.enumerated() {
            if dep.isFuture(currentHour: currentHour, currentMinute: currentMinute) {
                return index
            }
        }
        return departures.count
    }

    private var currentTimeString: String {
        let cal = Calendar.current
        let hour = cal.component(.hour, from: currentTime)
        let minute = cal.component(.minute, from: currentTime)
        return String(format: "%d:%02d", hour, minute)
    }

    var body: some View {
        content
            .navigationTitle("Horario del día")
            .navigationBarTitleDisplayMode(.inline)
            .task {
                await loadTimetables()
            }
            .task {
                await refreshReminderKeys()
            }
            .onReceive(timer) { time in
                currentTime = time
            }
            .alert("Error al programar el recordatorio", isPresented: $showReminderAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(reminderErrorMessage ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            VStack(spacing: 16) {
                ProgressView()
                    .controlSize(.large)
                Text("Cargando horarios...")
                    .foregroundColor(.secondary)
            }
        } else if let error = errorMessage {
            Text(error)
                .foregroundColor(.red)
                .multilineTextAlignment(.center)
                .padding()
        } else if todayDepartures.isEmpty {
            Text("No hay horarios disponibles para hoy")
                .font(.title3)
                .multilineTextAlignment(.center)
                .padding()
        } else {
            scheduleList
        }
    }

    private var scheduleList: some View {
        let departures = todayDepartures
        let markerIndex = nowMarkerIndex

        return ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    // Section header
                    HStack(spacing: 8) {
                        Image(systemName: "calendar")
                            .font(.subheadline)
                        Text(stop.name)
                            .font(.headline)
                        Spacer()
                        Text(dayTypeLabel)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color(.systemGray6))
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)

                    // Timeline
                    VStack(spacing: 0) {
                        ForEach(Array(departures.enumerated()), id: \.offset) { index, departure in
                            // Insert "Ahora" marker before first future departure
                            if index == markerIndex {
                                NowMarkerRow(timeString: currentTimeString)
                                    .id("now_marker")
                            }

                            let key = BusReminder.matchKey(
                                routeId: route.id, stopId: stop.id, direction: direction,
                                hour: departure.hour, minute: departure.minute
                            )
                            let bellStateValue: DayScheduleBellState = {
                                if dailyReminderKeys.contains(key) { return .daily }
                                if reminderKeys.contains(key) { return .oneOff }
                                return .off
                            }()
                            DayScheduleTimelineRow(
                                departure: departure,
                                selectedVariantLabel: selectedVariantLabel,
                                isFirst: index == 0,
                                isLast: index == departures.count - 1 && markerIndex <= departures.count - 1,
                                bellState: bellStateValue,
                                showBell: canSetReminder(for: departure),
                                onBellTap: { handleBellTap(for: departure) },
                                onBellLongPress: { handleBellLongPress(for: departure) },
                            )
                        }

                        // Marker at the end if all departures are past
                        if markerIndex == departures.count {
                            NowMarkerRow(timeString: currentTimeString)
                                .id("now_marker")
                        }
                    }
                    .padding(.horizontal, 16)

                    Spacer(minLength: 32)
                }
            }
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    withAnimation {
                        proxy.scrollTo("now_marker", anchor: .center)
                    }
                }
            }
        }
    }

    private func loadTimetables() async {
        isLoading = true
        errorMessage = nil
        let loaded = await TimetableService.shared.loadTimetables(routeId: route.id)
        if loaded.isEmpty {
            errorMessage = "Error al cargar horarios"
        }
        timetables = loaded
        isLoading = false
    }

    // MARK: - Reminder helpers

    private func refreshReminderKeys() async {
        async let keys = ReminderService.shared.activeMatchKeys()
        async let dailyKeys = ReminderService.shared.dailyMatchKeys()
        reminderKeys = await keys
        dailyReminderKeys = await dailyKeys
    }

    private var effectiveDayType: DayType {
        if let override = overrideDayType { return override }
        switch currentWeekday {
        case 7: return .saturday
        case 1: return .sunday
        default: return .weekday
        }
    }

    private func canSetReminder(for departure: DepartureTime) -> Bool {
        return true
    }

    private func handleBellTap(for departure: DepartureTime) {
        reminderErrorMessage = nil
        let key = BusReminder.matchKey(
            routeId: route.id, stopId: stop.id, direction: direction,
            hour: departure.hour, minute: departure.minute
        )
        Task {
            if reminderKeys.contains(key) {
                await ReminderService.shared.cancelReminder(
                    routeId: route.id, stopId: stop.id, direction: direction,
                    hour: departure.hour, minute: departure.minute
                )
            } else {
                do {
                    try await ReminderService.shared.scheduleReminder(
                        departure: departure, stop: stop, route: route, direction: direction,
                        dayType: effectiveDayType
                    )
                } catch {
                    reminderErrorMessage = error.localizedDescription
                    showReminderAlert = true
                }
            }
            await refreshReminderKeys()
        }
    }

    private func handleBellLongPress(for departure: DepartureTime) {
        reminderErrorMessage = nil
        let key = BusReminder.matchKey(
            routeId: route.id, stopId: stop.id, direction: direction,
            hour: departure.hour, minute: departure.minute
        )
        Task {
            if dailyReminderKeys.contains(key) {
                await ReminderService.shared.cancelReminder(
                    routeId: route.id, stopId: stop.id, direction: direction,
                    hour: departure.hour, minute: departure.minute
                )
            } else {
                if reminderKeys.contains(key) {
                    await ReminderService.shared.cancelReminder(
                        routeId: route.id, stopId: stop.id, direction: direction,
                        hour: departure.hour, minute: departure.minute
                    )
                }
                do {
                    try await ReminderService.shared.scheduleReminder(
                        departure: departure, stop: stop, route: route, direction: direction,
                        isDaily: true, dayType: effectiveDayType
                    )
                } catch {
                    reminderErrorMessage = error.localizedDescription
                    showReminderAlert = true
                }
            }
            await refreshReminderKeys()
        }
    }
}

// MARK: - Day Type Helpers

private func dayTypesForToday(_ weekday: Int) -> Set<DayType> {
    switch weekday {
    case 7: [.saturday, .weekend]
    case 1: [.sunday, .weekend, .holiday]
    default: [.weekday]
    }
}

private func dayTypesFor(_ dayType: DayType) -> Set<DayType> {
    switch dayType {
    case .saturday: [.saturday, .weekend]
    case .sunday: [.sunday, .weekend, .holiday]
    case .weekend: [.weekend, .saturday, .sunday]
    case .holiday: [.holiday, .sunday, .weekend]
    default: [.weekday]
    }
}

// MARK: - Time of Day (duplicated for access in this file)

private enum DayScheduleTimeOfDay {
    case morning
    case afternoon
    case evening

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

private func getDayScheduleTimeOfDay(_ hour: Int) -> DayScheduleTimeOfDay {
    switch hour {
    case 6 ... 12: .morning
    case 13 ... 19: .afternoon
    default: .evening
    }
}

// MARK: - Timeline Indicator

/// Reusable timeline indicator component with centered dot and connecting lines above and below.
/// Uses Canvas to draw lines and dots without affecting layout (inspired by RouteLineIndicator).
/// Draws lines above and below the dot so they connect across rows.
private struct TimelineIndicator: View {
    let isFirst: Bool
    let isLast: Bool
    var dotColor: Color = .accentColor
    var lineColor: Color = Color(.separator)
    var dotSize: CGFloat = 12

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            let midX = size.width / 2
            let midY = size.height / 2
            let dotRadius = dotSize / 2

            Canvas { context, canvasSize in
                // Line above dot (from row top to dot top)
                if !isFirst {
                    let lineStartY = 0.0
                    let lineEndY = midY - dotRadius
                    let lineHeight = max(0.0, lineEndY - lineStartY)

                    let rect = CGRect(x: midX - 1, y: lineStartY, width: 2, height: lineHeight)
                    context.fill(Path(rect), with: .color(lineColor))
                }

                // Line below dot (from dot bottom to row bottom)
                if !isLast {
                    let lineStartY = midY + dotRadius
                    let lineEndY = canvasSize.height
                    let lineHeight = max(0.0, lineEndY - lineStartY)

                    let rect = CGRect(x: midX - 1, y: lineStartY, width: 2, height: lineHeight)
                    context.fill(Path(rect), with: .color(lineColor))
                }

                // Centered dot
                let dotRect = CGRect(x: midX - dotRadius, y: midY - dotRadius, width: dotSize, height: dotSize)
                let circle = Path(ellipseIn: dotRect)
                context.fill(circle, with: .color(dotColor))
            }
        }
        .frame(width: 32)
        .frame(maxHeight: .infinity)  // Allow vertical expansion to connect lines across rows
    }
}

// MARK: - Now Marker Row

private struct NowMarkerRow: View {
    let timeString: String

    var body: some View {
        HStack(spacing: 0) {
            // Timeline connector (line through marker)
            VStack(spacing: 0) {
                Rectangle()
                    .fill(Color.accentColor)
                    .frame(width: 2)
                    .frame(maxHeight: .infinity)
            }
            .frame(width: 32)

            // Pill with "Ahora" text
            HStack(spacing: 6) {
                Image(systemName: "clock.fill")
                    .font(.caption2)
                Text("Ahora · \(timeString)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
            }
            .foregroundColor(.accentColor)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity)
            .background(Color.accentColor.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .padding(.leading, 16)
        }
        .frame(height: 44)
        .padding(.vertical, 4)
    }
}

// MARK: - Bell State (local alias matching NextDepartureView)

private enum DayScheduleBellState { case off, oneOff, daily }

// MARK: - Bell Button

private struct DayScheduleBellButton: View {
    let bellState: DayScheduleBellState
    let onTap: () -> Void
    let onLongPress: () -> Void

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
        .onTapGesture { onTap() }
        .onLongPressGesture {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            onLongPress()
        }
    }
}

// MARK: - Day Schedule Timeline Row

private struct DayScheduleTimelineRow: View {
    let departure: DepartureTime
    let selectedVariantLabel: String?
    let isFirst: Bool
    let isLast: Bool
    let bellState: DayScheduleBellState
    let showBell: Bool
    let onBellTap: () -> Void
    var onBellLongPress: () -> Void = {}

    var body: some View {
        let timeOfDay = getDayScheduleTimeOfDay(departure.hour)

        HStack(spacing: 16) {
            TimelineIndicator(isFirst: isFirst, isLast: isLast)

            // Wrapper for departure card (with vertical padding for timeline)
            VStack {
                // Departure row
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 8) {
                            Text(departure.displayString)
                                .font(.headline)

                            let showLabel = departure.shouldShowVariantLabel(selectedVariantLabel: selectedVariantLabel)

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

                    // Time of day indicator
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

                    if showBell {
                        DayScheduleBellButton(
                            bellState: bellState,
                            onTap: onBellTap,
                            onLongPress: onBellLongPress
                        )
                        .font(.subheadline)
                    }
                }
                .padding(16)
                .background(Color(.secondarySystemBackground).opacity(0.5))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(.vertical, 8)
        }
    }
}
