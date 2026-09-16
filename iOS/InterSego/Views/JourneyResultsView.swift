/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Owns the async search + stop-lookup state for a `JourneyResultsSelection`, so
/// `JourneyResultsView` itself stays a plain, previewable display component.
struct JourneyResultsContainer: View {
    let selection: JourneyResultsSelection
    let supportedRouteIds: [String]
    let routes: [BusRoute]
    let onJourneySelected: (_ journey: Journey, _ stops: [String: BusStop]) -> Void

    @State private var journeys: [Journey] = []
    @State private var isLoading = true
    @State private var stopsById: [String: BusStop] = [:]
    // `.task` reruns every time this view re-appears - including popping back from a pushed
    // JourneyDetailView, not just on first load - so without this guard the search re-ran on
    // every return trip. Harmless in principle (same query, should give the same answer) but
    // wasteful, and visibly not always the same answer: ties between candidates with identical
    // arrival/transfers/departure can land in a different order between two separate CSA runs
    // (Swift's `[String: Journey]` dictionary used internally has no ordering guarantee), so the
    // top-3 list could silently reshuffle each time you came back. Searching once per selection
    // fixes both.
    @State private var hasSearched = false

    var body: some View {
        JourneyResultsView(
            originName: selection.originName,
            destinationName: selection.destinationName,
            date: selection.date,
            departAfterMin: selection.departAfterMin,
            arriveBeforeMin: selection.arriveBeforeMin,
            journeys: journeys,
            isLoading: isLoading,
            routesById: Dictionary(uniqueKeysWithValues: routes.map { ($0.id, $0) }),
            stopsById: stopsById,
            onJourneySelected: { journey in onJourneySelected(journey, stopsById) },
        )
        .task {
            guard !hasSearched else { return }
            hasSearched = true
            let entries = StopDirectoryService.buildDirectory(routeIds: supportedRouteIds)
            stopsById = Dictionary(uniqueKeysWithValues: entries.map { ($0.physicalStopId, $0.stop) })
            journeys = await JourneySearchCoordinator.search(
                originPhysicalStopId: selection.originId,
                destinationPhysicalStopId: selection.destinationId,
                date: selection.date,
                departAfterMin: selection.departAfterMin,
                arriveBeforeMin: selection.arriveBeforeMin,
            )
            isLoading = false
        }
    }
}

private func formatMin(_ minutesOfDay: Int) -> String {
    let h = (minutesOfDay / 60) % 24
    let m = minutesOfDay % 60
    return String(format: "%02d:%02d", h, m)
}

private func formatDuration(_ minutes: Int) -> String {
    minutes < 60 ? "\(minutes) min" : "\(minutes / 60) h \(minutes % 60) min"
}

/// Top 3 ranked journeys for the current query. Below-buffer transfers are already filtered out
/// by the planner — what's shown here is always usable. See docs/JOURNEY_PLANNER.md.
struct JourneyResultsView: View {
    let originName: String
    let destinationName: String
    let date: Date
    let departAfterMin: Int
    let arriveBeforeMin: Int?
    let journeys: [Journey]
    let isLoading: Bool
    let routesById: [String: BusRoute]
    let stopsById: [String: BusStop]
    let onJourneySelected: (Journey) -> Void

    @State private var reminderKeys: Set<String> = []
    @State private var reminderContexts: [Journey: JourneyReminderHelper.Context] = [:]

    private func reminderContext(for journey: Journey) -> JourneyReminderHelper.Context? {
        reminderContexts[journey]
    }

    private func loadReminderContexts() async {
        for journey in journeys where reminderContexts[journey] == nil {
            if let context = await JourneyReminderHelper.build(
                journey: journey, date: date, originName: originName, destinationName: destinationName,
                routesById: routesById, stopsById: stopsById
            ) {
                reminderContexts[journey] = context
            }
        }
    }

    private func toggleReminder(for journey: Journey) {
        guard let context = reminderContext(for: journey) else { return }
        let key = JourneyReminderHelper.matchKey(context)
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
                    // Best-effort — the bell simply stays unset; the user can retry the tap.
                }
            }
            reminderKeys = await ReminderService.shared.activeMatchKeys()
        }
    }

    var body: some View {
        List {
            Section {
                SearchSummaryCard(
                    originName: originName,
                    destinationName: destinationName,
                    date: date,
                    departAfterMin: departAfterMin,
                    arriveBeforeMin: arriveBeforeMin,
                )
            } header: {
                Text("Resumen")
            }
            if isLoading {
                Section {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                } header: {
                    Text("Opciones")
                }
            } else if journeys.isEmpty {
                Section {
                    Text("No hay viajes con margen suficiente para esta búsqueda. Prueba con otra hora.")
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Opciones")
                }
            } else {
                // Each journey gets its own Section so it renders as its own separate pill
                // (matching Android's per-journey card) rather than one pill with dividers - the
                // "Opciones" header is only attached to the first one so it reads as one heading
                // over the whole group, not repeated per card.
                ForEach(Array(journeys.enumerated()), id: \.offset) { index, journey in
                    Section {
                        JourneyCard(
                            journey: journey,
                            reminderContext: reminderContext(for: journey),
                            isReminderSet: reminderContext(for: journey).map { reminderKeys.contains(JourneyReminderHelper.matchKey($0)) } ?? false,
                            onSelect: {
                                AnalyticsService.shared.track("journey_result_selected", with: [
                                    "duration_min": journey.arrivalMin - journey.departureMin,
                                    "transfers": journey.transferCount,
                                ])
                                onJourneySelected(journey)
                            },
                            onReminderToggle: { toggleReminder(for: journey) }
                        )
                        // Opt this row out of the system inset-grouped row background/insets/
                        // separator (same bypass ItineraryMapView's row uses in JourneyDetailView)
                        // so JourneyCard can draw its own card chrome and let the tight-transfer
                        // banner overflow above the card's top edge, tucked behind it.
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    } header: {
                        if index == 0 {
                            Text("Opciones")
                        }
                    }
                }
            }
        }
        .listSectionSpacing(.compact)
        .navigationTitle("Resultados")
        .navigationBarTitleDisplayMode(.inline)
        .task { reminderKeys = await ReminderService.shared.activeMatchKeys() }
        .task(id: journeys) { await loadReminderContexts() }
    }
}

private struct SearchSummaryCard: View {
    let originName: String
    let destinationName: String
    let date: Date
    let departAfterMin: Int
    let arriveBeforeMin: Int?

    private var dateLabel: String {
        Calendar.current.isDateInToday(date) ? "Hoy" : date.formatted(.dateTime.day().month(.abbreviated))
    }

    private var searchLabel: String {
        var label = "\(dateLabel) · Desde las \(formatMin(departAfterMin))"
        if let arriveBeforeMin {
            label += " · Antes de las \(formatMin(arriveBeforeMin))"
        }
        return label
    }

    var body: some View {
        VStack(spacing: 0) {
            summaryRow(icon: "circle.fill", iconColor: .accentColor, label: "Origen", value: originName)
            Divider()
            summaryRow(icon: "mappin", iconColor: .red, label: "Destino", value: destinationName)
            Divider()
            summaryRow(icon: "clock", iconColor: .secondary, label: "Búsqueda", value: searchLabel)
        }
    }

    private func summaryRow(icon: String, iconColor: Color, label: String, value: String) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundStyle(iconColor)
                .font(.system(size: 10))
                .frame(width: 16)
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Text(value).fontWeight(.medium)
            }
            Spacer()
        }
        .padding(.vertical, 4)
    }
}

/// One journey result: the tappable summary row plus its optional reminder row, drawn as a
/// self-contained card (rather than relying on the List's system inset-grouped row chrome — see
/// the `.listRowInsets`/`.listRowBackground` bypass at the call site) so a tight-transfer warning
/// can be layered as a banner tucked behind the card, peeking out above its top edge.
private struct JourneyCard: View {
    let journey: Journey
    let reminderContext: JourneyReminderHelper.Context?
    let isReminderSet: Bool
    let onSelect: () -> Void
    let onReminderToggle: () -> Void

    private let cardCornerRadius: CGFloat = 14

    /// The smallest transfer margin below the safety threshold, if any — `nil` for a direct
    /// journey or one where every transfer already has a comfortable margin.
    private var tightestMargin: Int? {
        journey.transferMargins()
            .map(\.marginMin)
            .filter { $0 < TripPlannerPrefs.recommendedMinBuffer }
            .min()
    }

    var body: some View {
        ZStack(alignment: .top) {
            if let tightestMargin {
                // The banner's own height (content + extra bottom padding) is taller than what
                // ends up visible - the card on top covers its lower portion, so only a strip
                // peeks out above the card's top edge, like a tab tucked behind it. Same width
                // as the card - its bottom corners are square (see TightTransferPeekBanner), so
                // there's no rounded edge left to clash with the card's own rounded top corners.
                TightTransferPeekBanner(title: "Transbordo ajustado · \(tightestMargin) min de margen")
            }
            card
                .padding(.top, tightestMargin != nil ? 32 : 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }

    private var card: some View {
        // .leading - VStack defaults to .center, which put the reminder row's compact
        // (content-hugging) width in the middle instead of flush left.
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onSelect) {
                JourneyRow(journey: journey)
            }
            .foregroundStyle(.primary)
            if reminderContext != nil {
                Divider().padding(.top, 8)
                Button(action: onReminderToggle) {
                    HStack(spacing: 6) {
                        Image(systemName: isReminderSet ? "bell.fill" : "bell")
                        Text(isReminderSet ? "Te avisaremos para salir" : "Recuérdame salir")
                        Spacer()
                    }
                    .font(.footnote)
                    .foregroundStyle(isReminderSet ? Color.accentColor : .secondary)
                    .padding(.top, 8)
                    // .plain only makes the tight icon+text bounds tappable by default - stretch
                    // the label and claim the whole area (matching Android's full-row tap
                    // target) rather than requiring a precise tap on the text itself.
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: cardCornerRadius))
    }
}

private struct JourneyRow: View {
    let journey: Journey

    private var hasEstimatedLeg: Bool {
        journey.legs.contains { if case .ride(let r) = $0 { return r.isEstimated } else { return false } }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("\(formatMin(journey.departureMin)) — \(formatMin(journey.arrivalMin))")
                    .font(.headline)
                Spacer()
                Text(formatDuration(journey.arrivalMin - journey.departureMin))
                    .font(.subheadline)
            }
            Text(journey.transferCount == 0 ? "Directo" : "\(journey.transferCount) transbordo(s)")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            HStack(spacing: 6) {
                ForEach(Array(journey.legs.enumerated()), id: \.offset) { _, leg in
                    switch leg {
                    case .ride(let ride):
                        Label(ride.routeId, systemImage: "bus")
                            .font(.caption)
                            .padding(.horizontal, 8).padding(.vertical, 8)
                            .background(Capsule().fill(Color.accentColor.opacity(0.15)))
                    case .walk(let walk):
                        Label("\(walk.minutes) min", systemImage: "figure.walk")
                            .font(.caption)
                            .padding(.horizontal, 8).padding(.vertical, 8)
                            .background(Capsule().fill(Color.gray.opacity(0.15)))
                    }
                }
            }
            if hasEstimatedLeg {
                // Reuses the app's existing "approximate times" disclaimer language.
                Text("Horarios orientativos")
                    .font(.caption)
                    .foregroundStyle(.orange)
            }
        }
        .padding(.vertical, 4)
    }
}

/// Warning banner tucked behind a card, peeking out above its top edge - see `JourneyCard`'s
/// `ZStack` here, and the equivalent one wrapping the "Pasos" card in JourneyDetailView. Solid-
/// filled (not the light tint used elsewhere for passive notes like "Horarios orientativos")
/// since it needs to read as a distinct layer sitting behind the card, not a tint within it.
/// Not `private` - shared with JourneyDetailView.
struct TightTransferPeekBanner: View {
    let title: String
    var subtitle: String? = nil

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "exclamationmark.triangle.fill")
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                if let subtitle {
                    Text(subtitle).fontWeight(.regular)
                }
            }
            Spacer(minLength: 0)
        }
        .font(.caption)
        .fontWeight(.semibold)
        .foregroundStyle(.white)
        .padding(.horizontal, 12)
        .padding(.top, 8)
        // Extra bottom padding - this is the part that ends up hidden behind the card on top;
        // only the top strip (icon + text) shows above the card's edge. This must stay at least
        // as tall as the card's own corner radius, or the last sliver of the card's rounded
        // corner has nothing opaque behind it - a background-colored gap right at the tip of the
        // curve. The two-line case reserves more here since its caller (JourneyDetailView's
        // PasosCard) uses a taller peek (48 vs. this card's 32), which eats further into the
        // hidden portion - both cards share the same 14pt corner radius.
        .padding(.bottom, subtitle != nil ? 48 : 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange)
        // Bottom corners square, not rounded - this banner is the same width as the card sitting
        // on top of it, so a rounded bottom corner here would clash with the card's own rounded
        // top corner right where they overlap. Only the top needs rounding (it's the only part
        // that's ever actually visible, peeking above the card).
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 12, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 12))
    }
}
