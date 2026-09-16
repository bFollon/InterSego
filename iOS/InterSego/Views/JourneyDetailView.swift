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
    let routes: [BusRoute]
    let date: Date
    let originName: String
    let destinationName: String
    let onLegSelected: (_ routeId: String, _ stopId: String) -> Void

    @State private var reminderKeys: Set<String> = []
    @State private var reminderContext: JourneyReminderHelper.Context?
    @State private var reminderErrorMessage: String?
    @State private var showReminderAlert = false

    private var hasEstimatedLeg: Bool {
        journey.legs.contains { if case .ride(let r) = $0 { return r.isEstimated } else { return false } }
    }

    /// The journey's tight transfer, if any — actual margin below the recommended safety
    /// threshold, independent of whatever buffer the search itself required, so this still flags
    /// a risky connection even if the user has lowered their own buffer setting. At most one:
    /// the planner never returns more than one mid-journey transfer (see JOURNEY_PLANNER.md).
    private var tightMargin: Journey.TransferMargin? {
        journey.transferMargins().first { $0.marginMin < TripPlannerPrefs.recommendedMinBuffer }
    }

    private func loadReminderContext() async {
        reminderContext = await JourneyReminderHelper.build(
            journey: journey, date: date, originName: originName, destinationName: destinationName,
            routesById: Dictionary(uniqueKeysWithValues: routes.map { ($0.id, $0) }), stopsById: stops
        )
    }

    private func toggleReminder() {
        guard let context = reminderContext else { return }
        let key = JourneyReminderHelper.matchKey(context)
        reminderErrorMessage = nil
        Task {
            if reminderKeys.contains(key) {
                await ReminderService.shared.cancelReminder(
                    routeId: context.route.id, stopId: context.stop.id, direction: context.direction,
                    hour: context.departure.hour, minute: context.departure.minute
                )
            } else {
                do {
                    try await ReminderService.shared.scheduleReminder(
                        departure: context.departure, stop: context.stop, route: context.route,
                        direction: context.direction, dayType: context.dayType, journeyLabel: context.journeyLabel
                    )
                    AnalyticsService.shared.track("reminder_set", with: ["type": "journey"])
                } catch {
                    reminderErrorMessage = error.localizedDescription
                    showReminderAlert = true
                }
            }
            reminderKeys = await ReminderService.shared.activeMatchKeys()
        }
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
                // A single self-drawn card (bypassing the system row chrome, same trick
                // JourneyResultsView's JourneyCard uses) rather than plain List rows, so the tight
                // transfer warning can peek out above it as a banner tucked behind - same style as
                // the results list, rather than the light-tint rows this used to show under the map.
                PasosCard(
                    journey: journey,
                    stopName: stopName,
                    hasEstimatedLeg: hasEstimatedLeg,
                    tightMargin: tightMargin,
                    onLegSelected: onLegSelected
                )
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            } header: {
                Text("Pasos")
            }
        }
        .navigationTitle("\(formatDuration(journey.arrivalMin - journey.departureMin)) · \(journey.transferCount == 0 ? "Directo" : "\(journey.transferCount) transbordo(s)")")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if reminderContext != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    let isSet = reminderContext.map { reminderKeys.contains(JourneyReminderHelper.matchKey($0)) } ?? false
                    Button(action: toggleReminder) {
                        Image(systemName: isSet ? "bell.fill" : "bell")
                    }
                }
            }
        }
        .task {
            reminderKeys = await ReminderService.shared.activeMatchKeys()
            await loadReminderContext()
        }
        .alert("No se pudo programar el recordatorio", isPresented: $showReminderAlert, presenting: reminderErrorMessage) { _ in
            Button("OK", role: .cancel) {}
        } message: { message in
            Text(message)
        }
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

}

/// The "Pasos" leg-by-leg breakdown, drawn as a self-contained card (see the `.listRowInsets`/
/// `.listRowBackground` bypass at the call site) so a tight-transfer warning can be layered as a
/// banner tucked behind it, peeking out above its top edge - same pattern and same
/// `TightTransferPeekBanner` as JourneyResultsView's `JourneyCard`.
private struct PasosCard: View {
    let journey: Journey
    let stopName: (String) -> String
    let hasEstimatedLeg: Bool
    let tightMargin: Journey.TransferMargin?
    let onLegSelected: (_ routeId: String, _ stopId: String) -> Void

    private let cardCornerRadius: CGFloat = 14
    /// Taller than JourneyResultsView's JourneyCard (32) - this banner is two lines (stop name +
    /// margin) instead of one, so more of it needs to peek above the card to stay fully visible.
    private let peekHeight: CGFloat = 48

    var body: some View {
        ZStack(alignment: .top) {
            if let tightMargin {
                TightTransferPeekBanner(
                    title: "Transbordo ajustado en \(stopName(tightMargin.stopId))",
                    subtitle: "\(tightMargin.marginMin) min de margen"
                )
            }
            card
                .padding(.top, tightMargin != nil ? peekHeight : 0)
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(journey.stepsWithWaits().enumerated()), id: \.offset) { index, step in
                if index > 0 { Divider() }
                switch step {
                case .leg(.ride(let ride)):
                    Button(action: {
                        AnalyticsService.shared.track("journey_leg_tapped", with: ["route": ride.routeId])
                        onLegSelected(ride.routeId, ride.fromStop)
                    }) {
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
                    .padding(.top, 8)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: cardCornerRadius))
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
