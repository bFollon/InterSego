/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Menu screen reached from Landing's "Más opciones" card — groups secondary features
/// (reminders, trip planning, and future entries like "Cómo llegar") behind one grid, the
/// same style as Landing's own square-card grid.
///
/// "Consultar otro día" asks for the target date here, before route/stop selection: a route's
/// stops and views can differ completely by day type (e.g. M1's circularA/B, M6's 7 variants),
/// so the date must be known before a correct stop list can be shown for it.
struct OtrasOpcionesView: View {
    let onShowReminders: () -> Void
    let onCheckAnotherDay: (Date) -> Void
    let onPlanJourney: () -> Void
    let onNavigateToRouteList: () -> Void
    var mainAction: MainLandingAction = .routes

    @State private var showDatePicker = false
    @State private var pickedDate = Date()

    private let columns = [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)]

    // The action bound to Landing's main card is dropped here so every action stays reachable
    // regardless of which one the user picked as their main card.
    private var visibleActions: [MainLandingAction] {
        MainLandingAction.allCases.filter { $0 != mainAction }
    }

    private func onTapFor(_ action: MainLandingAction) -> () -> Void {
        switch action {
        case .routes: return onNavigateToRouteList
        case .routePlanner: return onPlanJourney
        case .reminders: return onShowReminders
        case .anotherDay: return { showDatePicker = true }
        }
    }

    var body: some View {
        LazyVGrid(columns: columns, spacing: 16) {
            ForEach(visibleActions, id: \.self) { action in
                Button(action: onTapFor(action)) {
                    SquareCard(label: action.label) {
                        MainLandingActionIcon(action: action)
                    }
                }
                .buttonStyle(.plain)
                .aspectRatio(1, contentMode: .fit)
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
