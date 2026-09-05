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
    let onJourneySelected: (Journey) -> Void

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
                        Button(action: {
                            AnalyticsService.shared.track("journey_result_selected", with: [
                                "duration_min": journey.arrivalMin - journey.departureMin,
                                "transfers": journey.transferCount,
                            ])
                            onJourneySelected(journey)
                        }) {
                            JourneyRow(journey: journey)
                        }
                        .foregroundStyle(.primary)
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
