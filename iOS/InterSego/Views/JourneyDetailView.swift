/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

private func formatMin(_ minutesOfDay: Int) -> String {
    let h = (minutesOfDay / 60) % 24
    let m = minutesOfDay % 60
    return String(format: "%02d:%02d", h, m)
}

private func formatDuration(_ minutes: Int) -> String {
    minutes < 60 ? "\(minutes) min" : "\(minutes / 60) h \(minutes % 60) min"
}

/// Leg-by-leg breakdown of one journey. See docs/JOURNEY_PLANNER.md.
struct JourneyDetailView: View {
    let journey: Journey
    let stopName: (String) -> String
    let stops: [String: BusStop]
    let onLegSelected: (_ routeId: String, _ stopId: String) -> Void

    private var hasEstimatedLeg: Bool {
        journey.legs.contains { if case .ride(let r) = $0 { return r.isEstimated } else { return false } }
    }

    var body: some View {
        List {
            Section {
                tripSummaryRow(
                    icon: "circle.fill",
                    iconColor: .accentColor,
                    label: "Salida",
                    stopName: stopName(journey.legs.first!.fromStop),
                    time: formatMin(journey.departureMin)
                )
                tripSummaryRow(
                    icon: "mappin",
                    iconColor: .red,
                    label: "Llegada",
                    stopName: stopName(journey.legs.last!.toStop),
                    time: formatMin(journey.arrivalMin)
                )
                ItineraryMapView(journey: journey, stops: stops)
                    .frame(height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .listRowInsets(EdgeInsets())
                    .listRowSeparator(.hidden)
            } header: {
                Text("Itinerario")
            }
            Section {
                ForEach(Array(journey.stepsWithWaits().enumerated()), id: \.offset) { _, step in
                    switch step {
                    case .leg(.ride(let ride)):
                        Button(action: { onLegSelected(ride.routeId, ride.fromStop) }) {
                            legRow(
                                systemImage: "bus",
                                iconColor: .accentColor,
                                title: "\(ride.routeId) · \(stopName(ride.fromStop)) → \(stopName(ride.toStop))",
                                subtitle: "\(formatMin(ride.depMin)) — \(formatMin(ride.arrMin))"
                            )
                        }
                        .foregroundStyle(.primary)
                    case .leg(.walk(let walk)):
                        // Walk legs aren't tappable - there's no route/stop screen for a walking segment.
                        legRow(
                            systemImage: "figure.walk",
                            iconColor: .gray,
                            title: "Caminar · \(stopName(walk.fromStop)) → \(stopName(walk.toStop))",
                            subtitle: "\(walk.minutes) min (\(walk.meters) m)"
                        )
                    case .wait(let minutes):
                        legRow(
                            systemImage: "clock",
                            iconColor: .gray,
                            title: "Espera",
                            subtitle: "\(minutes) min"
                        )
                    }
                }
                if hasEstimatedLeg {
                    // Shown once for the whole journey, not per-leg — riding on just the one leg
                    // that happens to be estimated read like a note about that specific bus.
                    Text("Horarios orientativos")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                        .listRowSeparator(.hidden)
                }
            } header: {
                Text("Pasos")
            }
        }
        .navigationTitle("\(formatDuration(journey.arrivalMin - journey.departureMin)) · \(journey.transferCount == 0 ? "Directo" : "\(journey.transferCount) transbordo(s)")")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func tripSummaryRow(icon: String, iconColor: Color, label: String, stopName: String, time: String) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundStyle(iconColor)
                .font(.system(size: 10))
                .frame(width: 16)
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Text(stopName).fontWeight(.medium)
            }
            Spacer()
            Text(time).font(.headline)
        }
        .padding(.vertical, 4)
    }

    private func legRow(systemImage: String, iconColor: Color, title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .foregroundStyle(iconColor)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).fontWeight(.medium)
                Text(subtitle).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
