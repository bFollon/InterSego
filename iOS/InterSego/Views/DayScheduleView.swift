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
}

// MARK: - Day Schedule View

struct DayScheduleView: View {
    let route: BusRoute
    let stop: BusStop
    let direction: String
    let selectedVariantLabel: String?

    @State private var timetables: [BusTimetable] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var currentTime = Date()

    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var currentDayTypes: Set<DayType> {
        let weekday = Calendar.current.component(.weekday, from: Date())
        return dayTypesForToday(weekday)
    }

    private var todayDepartures: [DepartureTime] {
        let matching = timetables.filter { timetable in
            currentDayTypes.contains(timetable.dayType)
            && timetable.stopId == stop.name
            && timetable.direction == direction
        }
        return matching.flatMap { $0.seasonalDepartures() }.sorted()
    }

    private var dayTypeLabel: String {
        let weekday = Calendar.current.component(.weekday, from: Date())
        switch weekday {
        case 7: return "Sábado"
        case 1: return "Domingo"
        default: return "Lunes a Viernes"
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
            .onReceive(timer) { time in
                currentTime = time
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

                            DayScheduleTimelineRow(
                                departure: departure,
                                selectedVariantLabel: selectedVariantLabel,
                                isLast: index == departures.count - 1 && markerIndex <= departures.count - 1
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
}

// MARK: - Day Type Helper

private func dayTypesForToday(_ weekday: Int) -> Set<DayType> {
    switch weekday {
    case 7: return [.saturday, .weekend]
    case 1: return [.sunday, .weekend, .holiday]
    default: return [.weekday]
    }
}

// MARK: - Time of Day (duplicated for access in this file)

private enum DayScheduleTimeOfDay {
    case morning
    case afternoon
    case evening

    var label: String {
        switch self {
        case .morning: return "Mañana"
        case .afternoon: return "Tarde"
        case .evening: return "Noche"
        }
    }

    var icon: String {
        switch self {
        case .morning: return "sun.max.fill"
        case .afternoon: return "sun.haze.fill"
        case .evening: return "moon.fill"
        }
    }

    var color: Color {
        switch self {
        case .morning: return .orange
        case .afternoon: return .blue
        case .evening: return .indigo
        }
    }
}

private func getDayScheduleTimeOfDay(_ hour: Int) -> DayScheduleTimeOfDay {
    switch hour {
    case 6...12: return .morning
    case 13...19: return .afternoon
    default: return .evening
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

// MARK: - Day Schedule Timeline Row

private struct DayScheduleTimelineRow: View {
    let departure: DepartureTime
    let selectedVariantLabel: String?
    let isLast: Bool

    var body: some View {
        let timeOfDay = getDayScheduleTimeOfDay(departure.hour)

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

                        let showLabel = departure.shouldShowVariantLabel(selectedVariantLabel: selectedVariantLabel)

                        if showLabel, let label = departure.variantLabel {
                            Text(label)
                                .font(.caption2)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color(.systemGray5))
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
            }
            .padding(16)
            .background(Color(.secondarySystemBackground).opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .padding(.vertical, 4)
    }
}
