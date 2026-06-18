/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

private let alertSeverityOptions: [(value: String, label: String, description: String)] = [
    ("info",     "Todas",            "Informativas, advertencias e interrupciones graves"),
    ("warning",  "Solo importantes", "Advertencias y alertas críticas"),
    ("critical", "Solo críticas",    "Únicamente interrupciones graves del servicio"),
    ("none",     "Desactivadas",     "Sin notificaciones de alertas"),
]

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var guidedModeEnabled = GuidedModePrefs.isGuidedModeEnabled()
    @State private var errorsEnabled = MonitoringPreferencesService.shared.hasUserOptedIn()
    @State private var analyticsEnabled = MonitoringPreferencesService.shared.hasUserOptedInToAnalytics()
    @State private var alertMinSeverity = NotificationPreferencesService.shared.getAlertMinSeverity()
    @State private var showHowItWorks = false

    var body: some View {
        NavigationStack {
            List {
                Section(header: Text("Notificaciones")) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Configura qué notificaciones quieres recibir.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Button(action: { showHowItWorks = true }) {
                            Label("¿Cómo funciona?", systemImage: "questionmark.circle")
                                .font(.subheadline)
                        }
                    }
                    .padding(.vertical, 4)

                    Picker("Alertas de servicio", selection: Binding(
                        get: { alertMinSeverity },
                        set: { newValue in
                            alertMinSeverity = newValue
                            NotificationPreferencesService.shared.saveChoice(minSeverity: newValue)
                            if let token = UserDefaults.standard.string(forKey: "apnsDeviceToken") {
                                Task { await DeviceTokenService.shared.registerToken(token, platform: "ios", minSeverity: newValue) }
                            }
                        }
                    )) {
                        ForEach(alertSeverityOptions, id: \.value) { option in
                            Text(option.label).tag(option.value)
                        }
                    }
                    .pickerStyle(.menu)
                }

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
            .sheet(isPresented: $showHowItWorks) {
                AlertLevelsSheet()
            }
        }
    }
}

private struct AlertLevelsSheet: View {
    @Environment(\.dismiss) private var dismiss

    private let levels: [(icon: String, color: Color, title: String, description: String)] = [
        ("bell.fill",                     .blue,   "Todas",            "Informativas, advertencias e interrupciones graves"),
        ("exclamationmark.triangle.fill", .orange, "Solo importantes", "Advertencias y alertas críticas"),
        ("exclamationmark.octagon.fill",  .red,    "Solo críticas",    "Únicamente interrupciones graves del servicio"),
        ("bell.slash",                    .gray,   "Desactivadas",     "Sin notificaciones de alertas"),
    ]

    var body: some View {
        NavigationStack {
            List {
                ForEach(levels, id: \.title) { level in
                    HStack(spacing: 14) {
                        Image(systemName: level.icon)
                            .foregroundColor(level.color)
                            .font(.title3)
                            .frame(width: 28)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(level.title)
                                .fontWeight(.semibold)
                            Text(level.description)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
            .navigationTitle("Niveles de alerta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
