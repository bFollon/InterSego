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

/// Owns the async search + stop-lookup state for a `JourneyResultsSelection`, so
/// `JourneyResultsView` itself stays a plain, previewable display component.
struct JourneyResultsContainer: View {
    let selection: JourneyResultsSelection
    let supportedRouteIds: [String]
    let routes: [BusRoute]
    let onJourneySelected: (_ journey: Journey, _ stops: [String: BusStop]) -> Void

    @State private var journeys: [Journey] = []
    @State private var tightTransferJourneys: [Journey] = []
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
            tightTransferJourneys: tightTransferJourneys,
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
            let result = await JourneySearchCoordinator.search(
                originPhysicalStopId: selection.originId,
                destinationPhysicalStopId: selection.destinationId,
                date: selection.date,
                departAfterMin: selection.departAfterMin,
                arriveBeforeMin: selection.arriveBeforeMin,
            )
            journeys = result.journeys
            tightTransferJourneys = result.tightTransferJourneys
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

/// Top 3 ranked journeys for the current query, plus (below them, in their own section) up to
/// `JourneyPlannerService.maxTightTransferResults` additional options whose transfer margin falls
/// below the configured buffer — surfaced rather than silently dropped, so a user knows they
/// exist. See docs/JOURNEY_PLANNER.md.
struct JourneyResultsView: View {
    let originName: String
    let destinationName: String
    let date: Date
    let departAfterMin: Int
    let arriveBeforeMin: Int?
    let journeys: [Journey]
    var tightTransferJourneys: [Journey] = []
    let isLoading: Bool
    let routesById: [String: BusRoute]
    let stopsById: [String: BusStop]
    let onJourneySelected: (Journey) -> Void

    @State private var reminderKeys: Set<String> = []
    @State private var reminderContexts: [Journey: JourneyReminderHelper.Context] = [:]
    @State private var showTightMarginInfo = false

    private func reminderContext(for journey: Journey) -> JourneyReminderHelper.Context? {
        reminderContexts[journey]
    }

    private func loadReminderContexts() async {
        for journey in journeys + tightTransferJourneys where reminderContexts[journey] == nil {
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
                    // Doesn't claim there are no journeys at all - a tight-transfer section with
                    // real options can still render right below this (see the sibling `if` after
                    // this whole if/else-if/else chain), so "no journeys" here would contradict
                    // what's on screen.
                    Text("No hay viajes disponibles con el margen configurado.")
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
                        journeyRow(journey)
                    } header: {
                        if index == 0 {
                            Text("Opciones")
                        }
                    }
                }
            }
            // Sibling to the if/else-if/else above, not nested inside its `else` - so this still
            // renders when `journeys` is empty but `tightTransferJourneys` isn't (exactly the case
            // this whole feature exists for: no comfortable-margin option, but tighter ones exist).
            // Matches JourneyResultsScreen.kt's structure on Android.
            if !isLoading && !tightTransferJourneys.isEmpty {
                Section {
                    TightTransferSectionBanner(onInfoTap: { showTightMarginInfo = true })
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                } header: {
                    Text("Viajes con transbordos ajustados")
                }
                ForEach(Array(tightTransferJourneys.enumerated()), id: \.offset) { _, journey in
                    Section {
                        journeyRow(journey)
                    }
                }
            }
        }
        .listSectionSpacing(.compact)
        .navigationTitle("Resultados")
        .navigationBarTitleDisplayMode(.inline)
        .task { reminderKeys = await ReminderService.shared.activeMatchKeys() }
        .task(id: journeys + tightTransferJourneys) { await loadReminderContexts() }
        .sheet(isPresented: $showTightMarginInfo) {
            TightMarginInfoSheet()
        }
    }

    @ViewBuilder
    private func journeyRow(_ journey: Journey) -> some View {
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
            onReminderToggle: { toggleReminder(for: journey) },
            onTightMarginInfoTap: { showTightMarginInfo = true }
        )
        // Opt this row out of the system inset-grouped row background/insets/
        // separator (same bypass ItineraryMapView's row uses in JourneyDetailView)
        // so JourneyCard can draw its own card chrome and let the tight-transfer
        // banner overflow above the card's top edge, tucked behind it.
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }
}

/// Explains the "Viajes con transbordos ajustados" section as a whole — distinct from each card's
/// own `TightTransferPeekBanner`, which names that specific journey's margin. This one sits once
/// at the top of the section so a user who'd otherwise see only the (possibly empty) main list
/// knows these extra, less comfortable options exist at all.
private struct TightTransferSectionBanner: View {
    let onInfoTap: () -> Void

    var body: some View {
        // The whole banner is the tap target (not just the trailing icon) - Button + .plain style
        // + explicit .contentShape covers the Spacer's empty area too, which a bare HStack tap
        // gesture would otherwise miss.
        Button(action: onInfoTap) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                Text("Estos viajes requieren un transbordo con menos margen del configurado en los ajustes. Se muestran igualmente como alternativa, pero el cambio de autobús puede resultar más justo de lo habitual.")
                Spacer(minLength: 0)
                // Purely decorative - the whole banner is already the tap target, so this just
                // signals "tap for more info" without being a second, redundant tap target of its own.
                Image(systemName: "info.circle")
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .font(.caption)
        .foregroundStyle(.orange)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.vertical, 6)
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
    let onTightMarginInfoTap: () -> Void

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
        PeekingBannerCard(
            bannerTitle: tightestMargin.map { "Transbordo ajustado · \($0) min de margen" },
            cornerRadius: cardCornerRadius,
            onBannerInfoTap: onTightMarginInfoTap
        ) {
            card
        }
        // No horizontal padding - the Section this row sits in already provides the
        // inset-grouped margin from the screen edges (via `.listRowInsets(EdgeInsets())` at the
        // call site, which zeroes only the row's own inset, not the section's). Adding padding
        // here on top of that doubled the margin, making the card visibly narrower than every
        // other card-style row in the app (e.g. JourneyDetailView's PasosCard, which has none).
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

/// Warning banner tucked behind a card, peeking out above its top edge - see `PeekingBannerCard`,
/// which wraps this together with the card it backs on both `JourneyCard` here and the "Pasos"
/// card in JourneyDetailView. Light orange tint matching the app's other warning surfaces
/// (`FestivoBanner`, the LaLiga blocking banner, the service-alerts pill - all `.opacity(0.12)`
/// background + full-color foreground) rather than a solid fill - the List row it sits behind is
/// itself opaque, so the tint still fully backs the card's rounded corner with no visible gap.
/// Not `private` - shared with JourneyDetailView.
struct TightTransferPeekBanner: View {
    let title: String
    var subtitle: String? = nil
    /// Shared with the card this banner backs - both the visible top strip's curve and the
    /// minimum hidden height below it (see `PeekingBannerCard`) are derived from this, so the two
    /// views can never drift out of sync the way a second hardcoded radius could.
    let cornerRadius: CGFloat
    var onInfoTap: (() -> Void)? = nil

    fileprivate static let topPadding: CGFloat = 8
    fileprivate static let lineSpacing: CGFloat = 2
    /// Breathing room between the banner's own text and wherever the card in front of it starts -
    /// purely cosmetic (the corner-radius padding below already guarantees full coverage on its
    /// own), so a small fixed constant is fine here per-platform, same as the Android version.
    fileprivate static let visibleGap: CGFloat = 6

    /// Height of the visible strip that ends up peeking above the card: this banner's own top
    /// padding plus its text content plus `visibleGap`, sized from the same Dynamic-Type-aware
    /// font metric the text itself renders with (`.caption`) - not a value hand-tuned for one
    /// specific string at one specific text size. `PeekingBannerCard` uses this (rather than
    /// measuring the banner at layout time) to offset the card - a closed-form calculation avoids
    /// the GeometryReader/PreferenceKey dance, which proved unreliable inside a `List` row (the
    /// banner intermittently failed to render, and rows lost their full width).
    ///
    /// `dynamicTypeSize` bumps the assumed line count by one at accessibility sizes (AX1+): the
    /// title (e.g. "Transbordo ajustado · 3 min de margen") fits one line at standard sizes but
    /// can wrap at those larger sizes, and without this the card in front would push down too
    /// little, clipping the wrapped line behind it.
    static func visibleHeight(hasSubtitle: Bool, dynamicTypeSize: DynamicTypeSize = .large) -> CGFloat {
        let lineHeight = UIFont.preferredFont(forTextStyle: .caption1).lineHeight
        var lines: CGFloat = hasSubtitle ? 2 : 1
        if dynamicTypeSize.isAccessibilitySize { lines += 1 }
        let textHeight = lines * lineHeight + (hasSubtitle ? lineSpacing : 0)
        return topPadding + textHeight + visibleGap
    }

    // The whole banner is the tap target (not just the trailing icon), same as
    // TightTransferSectionBanner - only the exposed top strip is actually reachable in practice,
    // since the card pushed down on top of this banner (see PeekingBannerCard) absorbs touches
    // over the hidden bottom portion.
    private var bannerContent: some View {
        HStack(spacing: 6) {
            Image(systemName: "exclamationmark.triangle.fill")
            VStack(alignment: .leading, spacing: Self.lineSpacing) {
                Text(title)
                if let subtitle {
                    Text(subtitle).fontWeight(.regular)
                }
            }
            Spacer(minLength: 0)
            if onInfoTap != nil {
                // Purely decorative - the whole banner is already the tap target, so this just
                // signals "tap for more info" without being a second, redundant tap target of its own.
                Image(systemName: "info.circle")
            }
        }
        .contentShape(Rectangle())
    }

    var body: some View {
        Group {
            if let onInfoTap {
                Button(action: onInfoTap) { bannerContent }
                    .buttonStyle(.plain)
            } else {
                bannerContent
            }
        }
        .font(.caption)
        .fontWeight(.semibold)
        .foregroundStyle(.orange)
        .padding(.horizontal, 12)
        .padding(.top, Self.topPadding)
        // Extra bottom padding - this is the part that ends up hidden behind the card on top;
        // only the top strip (icon + text + `visibleGap`) shows above the card's edge. The
        // `cornerRadius` portion is the hard minimum - the card's rounded top corner cuts away a
        // quarter-circle of that radius, so the banner must stay opaque at least that far past
        // the card's top edge or the tip of the curve has a background-colored gap behind it;
        // `visibleGap` rides along on top of that minimum so `PeekingBannerCard`'s offset
        // (`visibleHeight`, which also includes `visibleGap`) and this padding stay in lockstep.
        .padding(.bottom, cornerRadius + Self.visibleGap)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.12))
        // Bottom corners square, not rounded - this banner is the same width as the card sitting
        // on top of it, so a rounded bottom corner here would clash with the card's own rounded
        // top corner right where they overlap. Only the top needs rounding (it's the only part
        // that's ever actually visible, peeking above the card).
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: cornerRadius, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: cornerRadius))
    }
}

/// Wraps `content` (a card) with an optional `TightTransferPeekBanner` tucked behind it, peeking
/// out above the card's rounded top corners. `content` should still apply its own corner
/// clipping/radius - this view only handles the offset between the two layers.
///
/// The peek height (how far the card is pushed down) comes from `TightTransferPeekBanner.
/// visibleHeight`, a closed-form calculation from the same font metric the banner's text renders
/// with - not a value hand-tuned per caller, and not something measured at layout time (an
/// earlier GeometryReader/PreferenceKey-based version of this view proved unreliable inside a
/// `List` row). Not `private` - shared with JourneyDetailView.
struct PeekingBannerCard<Content: View>: View {
    var bannerTitle: String?
    var bannerSubtitle: String? = nil
    let cornerRadius: CGFloat
    var onBannerInfoTap: (() -> Void)? = nil
    @ViewBuilder var content: () -> Content

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        ZStack(alignment: .top) {
            if let bannerTitle {
                TightTransferPeekBanner(title: bannerTitle, subtitle: bannerSubtitle, cornerRadius: cornerRadius, onInfoTap: onBannerInfoTap)
            }
            content()
                .padding(.top, bannerTitle != nil ? TightTransferPeekBanner.visibleHeight(hasSubtitle: bannerSubtitle != nil, dynamicTypeSize: dynamicTypeSize) : 0)
        }
    }
}
