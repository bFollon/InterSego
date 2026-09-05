/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

struct RemindersView: View {
    @State private var reminders: [BusReminder] = []
    @State private var leadMinutes: Int = 10
    @State private var dailyLeadMinutes: Int = 15
    /// Mirrors of the last-saved values — used to detect unsaved changes.
    @State private var savedLeadMinutes: Int = 10
    @State private var savedDailyLeadMinutes: Int = 15
    @State private var showTutorial = false

    private var hasUnsavedChanges: Bool {
        leadMinutes != savedLeadMinutes || dailyLeadMinutes != savedDailyLeadMinutes
    }
    @AppStorage("reminderTutorialShown") private var tutorialShown = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                leadTimeSection
                remindersSection
            }
            .navigationTitle("Mis recordatorios")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showTutorial = true
                        AnalyticsService.shared.track("tutorial_reopened", with: ["tutorial": "reminders"])
                    } label: {
                        Image(systemName: "questionmark.circle")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }
                }
            }
            .task {
                await loadData()
                if !tutorialShown {
                    tutorialShown = true
                    showTutorial = true
                }
            }
            .sheet(isPresented: $showTutorial) {
                ReminderTutorialView { showTutorial = false }
            }
        }
    }

    // MARK: - Sections

    private var leadTimeSection: some View {
        Section {
            // One-off lead time
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Aviso puntual")
                    Spacer()
                    Stepper("\(leadMinutes) min", value: $leadMinutes, in: 1 ... 60, step: 5)
                        .fixedSize()
                }
                Text("Antelación para recordatorios puntuales (campana rápida).")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.vertical, 4)

            // Daily lead time
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    HStack(spacing: 6) {
                        Text("Aviso diario")
                        Image(systemName: "arrow.clockwise")
                            .font(.caption)
                            .foregroundColor(.accentColor)
                    }
                    Spacer()
                    Stepper("\(dailyLeadMinutes) min", value: $dailyLeadMinutes, in: 1 ... 60, step: 5)
                        .fixedSize()
                }
                Text("Antelación para recordatorios diarios (campana mantenida).")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.vertical, 4)

            // Save button — always visible, disabled when nothing has changed
            HStack {
                Spacer()
                Button("Guardar") {
                    Task { await saveLeadTimes() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!hasUnsavedChanges)
            }
        } header: {
            Text("Configuración")
        }
    }

    @ViewBuilder
    private var remindersSection: some View {
        if reminders.isEmpty {
            Section {
                HStack {
                    Spacer()
                    VStack(spacing: 10) {
                        Image(systemName: "bell.slash")
                            .font(.system(size: 36))
                            .foregroundColor(.secondary)
                        Text("Sin recordatorios activos")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text("Pulsa la campana para un aviso puntual o mantenla para un recordatorio diario.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    Spacer()
                }
                .padding(.vertical, 24)
            }
        } else {
            Section("Recordatorios activos") {
                ForEach(reminders) { reminder in
                    ReminderRow(reminder: reminder) {
                        Task {
                            await ReminderService.shared.cancelReminder(id: reminder.id)
                            reminders = await ReminderService.shared.getReminders()
                            AnalyticsService.shared.track("reminder_cancelled", with: ["type": reminder.isDaily ? "daily" : "one_off"])
                        }
                    }
                }
            }
        }
    }

    // MARK: - Data

    private func loadData() async {
        await ReminderService.shared.refreshFromServer()
        async let r = ReminderService.shared.getReminders()
        async let l = ReminderService.shared.getDefaultLeadMinutes()
        async let dl = ReminderService.shared.getDailyLeadMinutes()
        reminders = await r
        leadMinutes = await l
        dailyLeadMinutes = await dl
        savedLeadMinutes = leadMinutes
        savedDailyLeadMinutes = dailyLeadMinutes
    }

    private func saveLeadTimes() async {
        if leadMinutes != savedLeadMinutes {
            await ReminderService.shared.setDefaultLeadMinutes(leadMinutes)
            await ReminderService.shared.rescheduleOneOff(newLeadMinutes: leadMinutes)
        }
        if dailyLeadMinutes != savedDailyLeadMinutes {
            await ReminderService.shared.setDailyLeadMinutes(dailyLeadMinutes)
            await ReminderService.shared.rescheduleDaily(newLeadMinutes: dailyLeadMinutes)
        }
        savedLeadMinutes = leadMinutes
        savedDailyLeadMinutes = dailyLeadMinutes
        reminders = await ReminderService.shared.getReminders()
    }
}

// MARK: - Reminder Row

private struct ReminderRow: View {
    let reminder: BusReminder
    let onCancel: () -> Void

    private var fireTimeFormatted: String {
        let f = DateFormatter()
        f.timeStyle = .short
        f.dateStyle = .none
        return f.string(from: reminder.fireDate)
    }

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    Text("Línea \(reminder.routeNumber)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.accentColor)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    Text(reminder.stopName)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    if reminder.isDaily {
                        Image(systemName: "arrow.clockwise")
                            .font(.caption2)
                            .foregroundColor(.accentColor)
                    }
                }
                Text("Sale a las \(reminder.departureDisplayString)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                HStack(spacing: 4) {
                    Image(systemName: reminder.isDaily ? "bell.fill" : "bell.fill")
                        .font(.caption2)
                        .foregroundColor(.accentColor)
                    Text(reminder.isDaily
                         ? "Cada día a las \(fireTimeFormatted)"
                         : "Aviso a las \(fireTimeFormatted)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                if let note = reminder.seasonalNote {
                    Text(note)
                        .font(.caption2)
                        .foregroundColor(.orange)
                }
            }

            Spacer()

            Button(role: .destructive, action: onCancel) {
                Image(systemName: "bell.slash")
            }
            .buttonStyle(.plain)
            .foregroundColor(.red)
        }
        .padding(.vertical, 2)
    }
}
