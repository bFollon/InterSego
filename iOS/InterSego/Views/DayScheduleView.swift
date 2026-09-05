/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

// MARK: - Day Schedule Selection

struct DayScheduleSelection: Hashable {
    let route: BusRoute
    let stop: BusStop
    let direction: String
    let departureLabel: String?
    let overrideDayType: DayType?
    let mergedDirectionLabel: String?
    let allowDateSelection: Bool
    let initialDate: Date

    init(route: BusRoute, stop: BusStop, direction: String, departureLabel: String?, overrideDayType: DayType? = nil, mergedDirectionLabel: String? = nil, allowDateSelection: Bool = false, initialDate: Date = Date()) {
        self.route = route
        self.stop = stop
        self.direction = direction
        self.departureLabel = departureLabel
        self.overrideDayType = overrideDayType
        self.mergedDirectionLabel = mergedDirectionLabel
        self.allowDateSelection = allowDateSelection
        self.initialDate = initialDate
    }
}

// MARK: - Day Schedule View

struct DayScheduleView: View {
    let route: BusRoute
    let stop: BusStop
    let selectedVariantLabel: String?
    let overrideDayType: DayType?
    let mergedDirectionLabel: String?
    let allowDateSelection: Bool

    // Direction can be swapped in-place (mirrors NextDepartureView's swap button) — starts from
    // the direction the caller resolved, but the user can flip to the other direction this
    // route serves at this stop without leaving the screen.
    @State private var currentDirection: String

    init(route: BusRoute, stop: BusStop, direction: String, selectedVariantLabel: String?, overrideDayType: DayType? = nil, mergedDirectionLabel: String? = nil, allowDateSelection: Bool = false, initialDate: Date = Date()) {
        self.route = route
        self.stop = stop
        self.selectedVariantLabel = selectedVariantLabel
        self.overrideDayType = overrideDayType
        self.mergedDirectionLabel = mergedDirectionLabel
        self.allowDateSelection = allowDateSelection
        _currentDirection = State(initialValue: direction)
        _selectedDate = State(initialValue: initialDate)
    }

    @State private var timetables: [BusTimetable] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var currentTime = Date()
    @State private var reminderKeys: Set<String> = []
    @State private var dailyReminderKeys: Set<String> = []
    @State private var reminderErrorMessage: String?
    @State private var showReminderAlert = false

    // "Consultar otro día" flow only — the pre-existing overrideDayType path (from
    // NextDepartureView's "Ver horario completo") keeps its existing today-anchored
    // weekday/seasonal resolution below, unchanged.
    @State private var selectedDate: Date
    @State private var showDatePicker = false

    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var isToday: Bool {
        !allowDateSelection || Calendar.current.isDateInToday(selectedDate)
    }

    private var currentWeekday: Int {
        let date = allowDateSelection ? selectedDate : Date()
        return Calendar.current.component(.weekday, from: date)
    }

    private var currentMonth: Int {
        let date = allowDateSelection ? selectedDate : Date()
        return Calendar.current.component(.month, from: date)
    }

    private var currentDayTypes: Set<DayType> {
        if allowDateSelection { return TimetableQuery.dayTypesForDate(selectedDate) }
        if let override = overrideDayType { return dayTypesFor(override) }
        return TimetableQuery.dayTypesForDate(Date())
    }

    private var holidayName: String? {
        allowDateSelection ? HolidayService.holidayName(selectedDate) : nil
    }

    // Each item is (departure, effectiveDirection) — direction may vary per-departure in merged mode.
    private var todayItems: [(DepartureTime, String)] {
        let matching = timetables.filter { timetable in
            currentDayTypes.contains(timetable.dayType)
                && timetable.stopId == stop.id
                && (mergedDirectionLabel != nil || timetable.direction == currentDirection)
        }
        return matching.flatMap { timetable in
            timetable.seasonalDepartures(month: currentMonth, weekday: currentWeekday)
                .map { ($0, timetable.direction ?? currentDirection) }
        }.sorted { $0.0 < $1.0 }
    }

    // Other directions this route serves at this stop (for the swap button) — irrelevant in
    // merged-direction mode (e.g. M4 circular), where there's only one logical direction.
    private var availableDirections: [String] {
        var seen = Set<String>()
        var result: [String] = []
        for t in timetables where currentDayTypes.contains(t.dayType) && t.stopId == stop.id {
            guard let dir = t.direction, seen.insert(dir).inserted else { continue }
            result.append(dir)
        }
        return result
    }

    private var swapDirection: String? {
        mergedDirectionLabel == nil ? availableDirections.first { $0 != currentDirection } : nil
    }

    private var directionLabel: String {
        if let mergedDirectionLabel { return mergedDirectionLabel }
        if route.isCircular { return currentDirection }
        let destination = currentDirection.split(separator: "→").last.map { $0.trimmingCharacters(in: .whitespaces) } ?? currentDirection
        return "Dirección \(destination)"
    }

    private var dayTypeLabel: String {
        switch effectiveDayType {
        case .saturday, .weekend: "Sábado"
        case .sunday, .holiday: "Domingo"
        default: "Lunes a Viernes"
        }
    }

    /// Nil when the schedule shown isn't today's — "Ahora" only makes sense for today.
    private var nowMarkerIndex: Int? {
        guard isToday else { return nil }
        let cal = Calendar.current
        let currentHour = cal.component(.hour, from: currentTime)
        let currentMinute = cal.component(.minute, from: currentTime)

        let items = todayItems
        for (index, item) in items.enumerated() {
            if item.0.isFuture(currentHour: currentHour, currentMinute: currentMinute) {
                return index
            }
        }
        return items.count
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
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    if let target = swapDirection {
                        Button {
                            currentDirection = target
                            AnalyticsService.shared.track("direction_swapped", with: ["screen": "day_schedule"])
                        } label: {
                            Image(systemName: "arrow.up.arrow.down")
                        }
                    }
                }
            }
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
            .sheet(isPresented: $showDatePicker) {
                NavigationStack {
                    DatePicker(
                        "Fecha",
                        selection: $selectedDate,
                        in: Date() ... (Calendar.current.date(byAdding: .day, value: 90, to: Date()) ?? Date()),
                        displayedComponents: .date
                    )
                    .datePickerStyle(.graphical)
                    .padding()
                    .navigationTitle("Elige una fecha")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Aceptar") { showDatePicker = false }
                        }
                    }
                }
                .presentationDetents([.medium])
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
        } else if todayItems.isEmpty {
            VStack(spacing: 0) {
                if allowDateSelection {
                    DateSelectionHeader(selectedDate: $selectedDate, holidayName: holidayName, showDatePicker: $showDatePicker)
                }
                HStack(spacing: 8) {
                    Image(systemName: "calendar")
                        .font(.subheadline)
                    Text(stop.name)
                        .font(.headline)
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                DirectionPill(directionLabel: directionLabel)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                Spacer()
                Text(isToday ? "No hay horarios disponibles para hoy" : "No hay horarios disponibles para este día")
                    .font(.title3)
                    .multilineTextAlignment(.center)
                    .padding()
                Spacer()
            }
        } else {
            scheduleList
        }
    }

    private var scheduleList: some View {
        let items = todayItems
        let markerIndex = nowMarkerIndex

        return ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    if allowDateSelection {
                        DateSelectionHeader(selectedDate: $selectedDate, holidayName: holidayName, showDatePicker: $showDatePicker)
                    }

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

                    DirectionPill(directionLabel: directionLabel)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 16)

                    // Timeline
                    VStack(spacing: 0) {
                        ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                            let (departure, depDir) = item
                            // Insert "Ahora" marker before first future departure
                            if index == markerIndex {
                                NowMarkerRow(timeString: currentTimeString)
                                    .id("now_marker")
                            }

                            let key = BusReminder.matchKey(
                                routeId: route.id, stopId: stop.id, direction: depDir,
                                hour: departure.hour, minute: departure.minute
                            )
                            let bellStateValue: DayScheduleBellState = {
                                if dailyReminderKeys.contains(key) { return .daily }
                                if reminderKeys.contains(key) { return .oneOff }
                                return .off
                            }()
                            DayScheduleTimelineRow(
                                departure: departure,
                                stop: stop,
                                selectedVariantLabel: selectedVariantLabel,
                                isFirst: index == 0,
                                isLast: index == items.count - 1 && (markerIndex.map { $0 <= items.count - 1 } ?? true),
                                bellState: bellStateValue,
                                showBell: canSetReminder(for: departure),
                                onBellTap: { handleBellTap(for: departure, direction: depDir) },
                                onBellLongPress: { handleBellLongPress(for: departure, direction: depDir) },
                            )
                        }

                        // Marker at the end if all departures are past
                        if markerIndex == items.count {
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
        if allowDateSelection { return TimetableQuery.primaryDayType(selectedDate) }
        if let override = overrideDayType { return override }
        return TimetableQuery.primaryDayType()
    }

    private func canSetReminder(for departure: DepartureTime) -> Bool {
        return true
    }

    private func handleBellTap(for departure: DepartureTime, direction dir: String) {
        reminderErrorMessage = nil
        let key = BusReminder.matchKey(
            routeId: route.id, stopId: stop.id, direction: dir,
            hour: departure.hour, minute: departure.minute
        )
        Task {
            if reminderKeys.contains(key) {
                await ReminderService.shared.cancelReminder(
                    routeId: route.id, stopId: stop.id, direction: dir,
                    hour: departure.hour, minute: departure.minute
                )
            } else {
                do {
                    try await ReminderService.shared.scheduleReminder(
                        departure: departure, stop: stop, route: route, direction: dir,
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

    private func handleBellLongPress(for departure: DepartureTime, direction dir: String) {
        reminderErrorMessage = nil
        let key = BusReminder.matchKey(
            routeId: route.id, stopId: stop.id, direction: dir,
            hour: departure.hour, minute: departure.minute
        )
        Task {
            if dailyReminderKeys.contains(key) {
                await ReminderService.shared.cancelReminder(
                    routeId: route.id, stopId: stop.id, direction: dir,
                    hour: departure.hour, minute: departure.minute
                )
            } else {
                if reminderKeys.contains(key) {
                    await ReminderService.shared.cancelReminder(
                        routeId: route.id, stopId: stop.id, direction: dir,
                        hour: departure.hour, minute: departure.minute
                    )
                }
                do {
                    try await ReminderService.shared.scheduleReminder(
                        departure: departure, stop: stop, route: route, direction: dir,
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

// MARK: - Direction Pill

/// Small pill showing the direction this schedule is for — mirrors NextDepartureView's
/// StopHeroHeader direction pill.
private struct DirectionPill: View {
    let directionLabel: String

    var body: some View {
        Text(directionLabel)
            .font(.caption)
            .foregroundColor(.accentColor)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Color.accentColor.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

// MARK: - Date Selection Header

/// Compact date affordance + festivo banner for the "Consultar otro día" flow.
private struct DateSelectionHeader: View {
    @Binding var selectedDate: Date
    let holidayName: String?
    @Binding var showDatePicker: Bool

    private var formattedDate: String {
        if Calendar.current.isDateInToday(selectedDate) { return "Hoy" }
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE d 'de' MMMM"
        formatter.locale = Locale(identifier: "es_ES")
        return formatter.string(from: selectedDate).prefix(1).uppercased() + formatter.string(from: selectedDate).dropFirst()
    }

    var body: some View {
        VStack(spacing: 8) {
            Button(action: { showDatePicker = true }) {
                HStack(spacing: 12) {
                    Image(systemName: "calendar")
                    Text(formattedDate)
                        .font(.headline)
                    Spacer()
                    if !Calendar.current.isDateInToday(selectedDate) {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color.accentColor.opacity(0.12))
                .foregroundColor(.accentColor)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)

            if let holidayName {
                FestivoBanner(holidayName: holidayName)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}

/// Purple banner explaining why a festivo is showing Sunday-shaped departures — shared between
/// DaySchedule's "Consultar otro día" date header and NextDeparture's "today" case.
struct FestivoBanner: View {
    let holidayName: String

    var body: some View {
        Text("Festivo: \(holidayName) · horario de domingo")
            .font(.subheadline)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.purple.opacity(0.12))
            .foregroundColor(.purple)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Day Type Helpers

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
    let stop: BusStop
    let selectedVariantLabel: String?
    let isFirst: Bool
    let isLast: Bool
    let bellState: DayScheduleBellState
    let showBell: Bool
    let onBellTap: () -> Void
    var onBellLongPress: () -> Void = {}

    private var showVariantLabel: Bool {
        departure.shouldShowVariantLabel(selectedVariantLabel: selectedVariantLabel)
    }

    var body: some View {
        let timeOfDay = getDayScheduleTimeOfDay(departure.hour)

        HStack(spacing: 16) {
            TimelineIndicator(isFirst: isFirst, isLast: isLast)

            // Wrapper for departure card (with vertical padding for timeline)
            VStack {
                // Departure row
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: 8) {
                                Text(departure.displayString)
                                    .font(.headline)
                                if showVariantLabel, let label = departure.variantLabel {
                                    Text(label)
                                        .font(.caption2)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color(.systemGray5))
                                        .clipShape(RoundedRectangle(cornerRadius: 4))
                                }
                                if let altName = departure.alternateLocationName(in: stop) {
                                    Text(altName)
                                        .font(.caption2)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.orange.opacity(0.15))
                                        .foregroundColor(.orange)
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
                            HStack(alignment: .center, spacing: 8) {
                                Text(departure.displayString)
                                    .font(.headline)
                                VStack(alignment: .leading, spacing: 4) {
                                    if showVariantLabel, let label = departure.variantLabel {
                                        Text(label)
                                            .font(.caption2)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color(.systemGray5))
                                            .clipShape(RoundedRectangle(cornerRadius: 4))
                                    }
                                    if let altName = departure.alternateLocationName(in: stop) {
                                        Text(altName)
                                            .font(.caption2)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.orange.opacity(0.15))
                                            .foregroundColor(.orange)
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
