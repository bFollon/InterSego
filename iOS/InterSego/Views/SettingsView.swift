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

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var guidedModeEnabled = GuidedModePrefs.isGuidedModeEnabled()
    @State private var errorsEnabled = MonitoringPreferencesService.shared.hasUserOptedIn()
    @State private var analyticsEnabled = MonitoringPreferencesService.shared.hasUserOptedInToAnalytics()

    var body: some View {
        NavigationStack {
            List {
                Section(header: Text("Modo guiado")) {
                    Toggle(
                        "Mostrar selector de dirección",
                        isOn: Binding(
                            get: { guidedModeEnabled },
                            set: { newValue in
                                guidedModeEnabled = newValue
                                GuidedModePrefs.setGuidedModeEnabled(newValue)
                            }
                        )
                    )
                    Text("Muestra una pantalla para seleccionar la dirección del autobús antes de ver las salidas")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Section(header: Text("Privacidad")) {
                    Toggle(
                        isOn: Binding(
                            get: { errorsEnabled },
                            set: { newValue in
                                errorsEnabled = newValue
                                MonitoringPreferencesService.shared.setMonitoringEnabled(newValue)
                            }
                        )
                    ) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Monitoreo de Errores")
                            Text("Datos técnicos anónimos para detectar fallos.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    Toggle(
                        isOn: Binding(
                            get: { analyticsEnabled },
                            set: { newValue in
                                analyticsEnabled = newValue
                                MonitoringPreferencesService.shared.setAnalyticsEnabled(newValue)
                            }
                        )
                    ) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Analíticas de Uso")
                            Text("Eventos de uso anónimos para mejorar la app.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    HStack(spacing: 8) {
                        Image(systemName: "info.circle")
                            .foregroundColor(.blue)
                            .font(.subheadline)
                        Text("Los cambios se aplican al reiniciar la app.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 6)
                    .padding(.horizontal, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.blue.opacity(0.08))
                    .cornerRadius(8)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                }
            }
            .navigationTitle("Configuración")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
    }
}
