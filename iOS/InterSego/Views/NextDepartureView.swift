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
import MapKit

// MARK: - Day Type Matching

private func dayTypesForCalendarDay(_ weekday: Int) -> Set<DayType> {
    switch weekday {
    case 7: return [.saturday, .weekend]   // Saturday
    case 1: return [.sunday, .weekend, .holiday] // Sunday
    default: return [.weekday]
    }
}

// MARK: - Time of Day

private enum TimeOfDay {
    case morning    // 6:00 - 12:59
    case afternoon  // 13:00 - 19:59
    case evening    // 20:00 - 5:59

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

private func getTimeOfDay(_ hour: Int) -> TimeOfDay {
    switch hour {
    case 6...12: return .morning
    case 13...19: return .afternoon
    default: return .evening
    }
}

// MARK: - Departure Info

private struct DepartureInfo {
    let departure: DepartureTime?
    let following: [DepartureTime]
    let daysAhead: Int  // 0 = today, 1 = tomorrow, 2+ = future
}

// MARK: - Next Departure View

struct NextDepartureView: View {
    let route: BusRoute
    let stop: BusStop
    let direction: String
    let selectedVariantLabel: String?

    @State private var timetables: [BusTimetable] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var currentTime = Date()
    @Environment(\.dismiss) private var dismiss

    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var currentDayTypes: Set<DayType> {
        let weekday = Calendar.current.component(.weekday, from: Date())
        return dayTypesForCalendarDay(weekday)
    }

    private var todayDepartures: [DepartureTime] {
        let matching = timetables.filter { timetable in
            currentDayTypes.contains(timetable.dayType)
            && timetable.stopId == stop.name
            && timetable.direction == direction
        }
        return matching.flatMap { $0.seasonalDepartures() }.sorted()
    }

    private var departureInfo: DepartureInfo {
        let now = Calendar.current
        let currentHour = now.component(.hour, from: currentTime)
        let currentMinute = now.component(.minute, from: currentTime)

        let today = todayDepartures
        let upcoming = today.filter { $0.isFuture(currentHour: currentHour, currentMinute: currentMinute) }

        if !upcoming.isEmpty {
            return DepartureInfo(
                departure: upcoming.first,
                following: Array(upcoming.dropFirst().prefix(5)),
                daysAhead: 0
            )
        }

        // Search up to 7 days ahead
        for daysAhead in 1...7 {
            guard let futureDate = Calendar.current.date(byAdding: .day, value: daysAhead, to: Date()) else { continue }
            let futureWeekday = Calendar.current.component(.weekday, from: futureDate)
            let futureDayTypes = dayTypesForCalendarDay(futureWeekday)

            let departures = timetables
                .filter { futureDayTypes.contains($0.dayType) && $0.stopId == stop.name && $0.direction == direction }
                .flatMap { $0.seasonalDepartures() }
                .sorted()

            if !departures.isEmpty {
                return DepartureInfo(
                    departure: departures.first,
                    following: Array(departures.dropFirst().prefix(5)),
                    daysAhead: daysAhead
                )
            }
        }

        return DepartureInfo(departure: nil, following: [], daysAhead: 0)
    }

    var body: some View {
        content
            .navigationTitle("Línea \(route.number)")
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
        } else if departureInfo.departure == nil {
            Text("No hay horarios disponibles para esta parada")
                .font(.title3)
                .multilineTextAlignment(.center)
                .padding()
        } else {
            departureScrollView
        }
    }

    private var departureScrollView: some View {
        let info = departureInfo
        return ScrollView {
            VStack(spacing: 0) {
                StopHeroHeader(route: route, stop: stop)

                if info.daysAhead > 0 {
                    FutureDayWarningCard(daysAhead: info.daysAhead)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                }

                // "Ver horario completo" button — only for today's schedule
                if !todayDepartures.isEmpty {
                    NavigationLink(value: DayScheduleSelection(
                        route: route,
                        stop: stop,
                        direction: direction,
                        departureLabel: selectedVariantLabel
                    )) {
                        HStack(spacing: 8) {
                            Image(systemName: "clock.arrow.2.circlepath")
                                .font(.subheadline)
                            Text("Ver horario completo")
                                .font(.subheadline)
                                .fontWeight(.medium)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                }

                if let next = info.departure {
                    NextDepartureCard(
                        departure: next,
                        currentTime: currentTime,
                        selectedVariantLabel: selectedVariantLabel,
                        daysAhead: info.daysAhead
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }

                if !info.following.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Siguientes salidas")
                            .font(.title2)
                            .fontWeight(.bold)
                            .padding(.horizontal, 20)

                        DepartureTimeline(
                            departures: info.following,
                            selectedVariantLabel: selectedVariantLabel
                        )
                        .padding(.horizontal, 16)
                    }
                    .padding(.top, 24)
                }

                Spacer(minLength: 32)
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

// MARK: - Stop Hero Header

private struct StopHeroHeader: View {
    let route: BusRoute
    let stop: BusStop

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Route badge
            Text("Línea \(route.number)")
                .font(.caption)
                .fontWeight(.bold)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.accentColor)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: 6))

            // Stop name
            Text(stop.name)
                .font(.title)
                .fontWeight(.bold)

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
                    span: MKCoordinateSpan(latitudeDelta: 0.005, longitudeDelta: 0.005)
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
                endPoint: .bottom
            )
        )
    }
}

// MARK: - Future Day Warning Card

private struct FutureDayWarningCard: View {
    let daysAhead: Int

    private var message: String {
        switch daysAhead {
        case 1: return "No hay más autobuses hoy. Mostrando horario de mañana."
        case 2: return "No hay más autobuses hoy ni mañana. Mostrando horario de pasado mañana."
        default: return "No hay más autobuses en los próximos días. Mostrando próximo horario disponible."
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

// MARK: - Next Departure Card

private struct NextDepartureCard: View {
    let departure: DepartureTime
    let currentTime: Date
    let selectedVariantLabel: String?
    let daysAhead: Int

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

    private var countdownText: String {
        let mins = minutesUntil
        switch mins {
        case ..<1: return "Saliendo ahora"
        case 1: return "Sale en 1 minuto"
        case 2..<60: return "Sale en \(mins) minutos"
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
                Text("Próxima salida:")
                    .font(.headline)

                DepartureTimeBadge(time: departure.displayString)

                if showVariantLabel, let label = departure.variantLabel {
                    Text(label)
                        .font(.caption2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(.systemGray5))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
            }

            // Countdown
            Text(countdownText)
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
    let departures: [DepartureTime]
    let selectedVariantLabel: String?

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(departures.enumerated()), id: \.offset) { index, departure in
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

                        TimeOfDayIndicator(timeOfDay: timeOfDay)
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
