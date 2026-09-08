/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Menu screen reached from Landing's "Más opciones" card — shows whichever pool actions
/// aren't currently pinned to one of Landing's 4 configurable slots, in the same square-card
/// style as Landing's own grid.
///
/// "Consultar otro día" asks for the target date here, before route/stop selection: a route's
/// stops and views can differ completely by day type (e.g. M1's circularA/B, M6's 7 variants),
/// so the date must be known before a correct stop list can be shown for it.
struct OtrasOpcionesView: View {
    let onShowReminders: () -> Void
    let onCheckAnotherDay: (Date) -> Void
    let onPlanJourney: () -> Void
    let onNavigateToRouteList: () -> Void
    let onShowFavorites: () -> Void
    let onBoardBus: () -> Void
    let pinnedActions: Set<MainLandingAction>
    var isBoardingBus: Bool = false
    var boardingBusConfirmed: Bool = false

    @State private var showDatePicker = false
    @State private var pickedDate = Date()

    private let columns = [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)]

    // Whichever pool actions aren't pinned to one of Landing's 4 slots land here, so every
    // action stays reachable regardless of how the user configured their Landing screen.
    private var visibleActions: [MainLandingAction] {
        MainLandingAction.allCases.filter { !pinnedActions.contains($0) }
    }

    private var callbacks: LandingActionCallbacks {
        LandingActionCallbacks(
            onNavigateToRouteList: onNavigateToRouteList,
            onPlanJourney: onPlanJourney,
            onShowReminders: onShowReminders,
            onOpenAnotherDay: { showDatePicker = true },
            onShowFavorites: onShowFavorites,
            onBoardBus: onBoardBus,
        )
    }

    var body: some View {
        LazyVGrid(columns: columns, spacing: 16) {
            ForEach(visibleActions, id: \.self) { action in
                LandingActionSquare(
                    action: action,
                    callbacks: callbacks,
                    isBoardingBus: isBoardingBus,
                    boardingBusConfirmed: boardingBusConfirmed,
                )
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Más opciones")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showDatePicker) {
            NavigationStack {
                DatePicker(
                    "Fecha",
                    selection: $pickedDate,
                    in: Date() ... (Calendar.current.date(byAdding: .day, value: 90, to: Date()) ?? Date()),
                    displayedComponents: .date,
                )
                .datePickerStyle(.graphical)
                .padding()
                .navigationTitle("Consultar otro día")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancelar") { showDatePicker = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Aceptar") {
                            showDatePicker = false
                            AnalyticsService.shared.track("check_another_day")
                            onCheckAnotherDay(pickedDate)
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
    }
}
