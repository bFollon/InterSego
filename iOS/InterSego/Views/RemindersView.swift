/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import SwiftUI

struct RemindersView: View {
    @State private var reminders: [BusReminder] = []
    @State private var leadMinutes: Int = 10
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
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }
                }
            }
            .task {
                await loadData()
            }
        }
    }

    // MARK: - Sections

    private var leadTimeSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Avisar con antelación")
                    Spacer()
                    Stepper("\(leadMinutes) min", value: $leadMinutes, in: 1 ... 60, step: 5)
                        .fixedSize()
                        .onChange(of: leadMinutes) { _, newValue in
                            Task { await ReminderService.shared.setDefaultLeadMinutes(newValue) }
                        }
                }
                Text("Tiempo por defecto antes de la salida del autobús.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.vertical, 4)
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
                        }
                    }
                }
            }
        }
    }

    // MARK: - Data

    private func loadData() async {
        async let r = ReminderService.shared.getReminders()
        async let l = ReminderService.shared.getDefaultLeadMinutes()
        reminders = await r
        leadMinutes = await l
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
                }
                Text("Sale a las \(reminder.departureDisplayString)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                HStack(spacing: 4) {
                    Image(systemName: "bell.fill")
                        .font(.caption2)
                        .foregroundColor(.accentColor)
                    Text("Aviso a las \(fireTimeFormatted)")
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
