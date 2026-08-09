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

    @State private var showDatePicker = false
    @State private var pickedDate = Date()

    var body: some View {
        HStack(spacing: 16) {
            Button(action: onShowReminders) {
                SquareCard(label: "Mis recordatorios") {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.accentColor)
                        .frame(width: 40, height: 40)
                }
            }
            .buttonStyle(.plain)
            .aspectRatio(1, contentMode: .fit)

            Button(action: { showDatePicker = true }) {
                SquareCard(label: "Consultar otro día") {
                    Image(systemName: "calendar")
                        .font(.system(size: 24))
                        .foregroundColor(.accentColor)
                        .frame(width: 40, height: 40)
                }
            }
            .buttonStyle(.plain)
            .aspectRatio(1, contentMode: .fit)

            Button(action: onPlanJourney) {
                SquareCard(label: "Planificar viaje") {
                    Image(systemName: "point.topleft.down.curvedto.point.bottomright.up")
                        .font(.system(size: 24))
                        .foregroundColor(.accentColor)
                        .frame(width: 40, height: 40)
                }
            }
            .buttonStyle(.plain)
            .aspectRatio(1, contentMode: .fit)
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
