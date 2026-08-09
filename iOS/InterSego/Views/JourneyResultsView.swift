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
    let journeys: [Journey]
    let isLoading: Bool
    let onJourneySelected: (Journey) -> Void

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
            } else if journeys.isEmpty {
                ContentUnavailableView(
                    "Sin viajes disponibles",
                    systemImage: "bus",
                    description: Text("No hay viajes con margen suficiente para esta búsqueda. Prueba con otra hora.")
                )
            } else {
                // Each journey gets its own Section so it renders as its own separate pill
                // (matching Android's per-journey card) rather than one pill with dividers.
                List(journeys, id: \.self) { journey in
                    Section {
                        Button(action: { onJourneySelected(journey) }) {
                            JourneyRow(journey: journey)
                        }
                        .foregroundStyle(.primary)
                    }
                }
                .listSectionSpacing(.compact)
            }
        }
        .navigationTitle("\(originName) → \(destinationName)")
        .navigationBarTitleDisplayMode(.inline)
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
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Capsule().fill(Color.accentColor.opacity(0.15)))
                    case .walk(let walk):
                        Label("\(walk.minutes) min", systemImage: "figure.walk")
                            .font(.caption)
                            .padding(.horizontal, 8).padding(.vertical, 4)
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
