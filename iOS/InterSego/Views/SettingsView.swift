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
    @State private var landingSlots = LandingLayoutPrefs.getSlots()
    @State private var errorsEnabled = MonitoringPreferencesService.shared.hasUserOptedIn()
    @State private var analyticsEnabled = MonitoringPreferencesService.shared.hasUserOptedInToAnalytics()
    @State private var alertMinSeverity = NotificationPreferencesService.shared.getAlertMinSeverity()
    @State private var showHowItWorks = false
    @State private var showTightMarginInfo = false
    @State private var maxWaitMin = TripPlannerPrefs.getMaxWaitMin()
    @State private var bufferSameStopTranscribed = TripPlannerPrefs.getBufferSameStopTranscribed()
    @State private var bufferSameStopEstimated = TripPlannerPrefs.getBufferSameStopEstimated()
    @State private var bufferWalkTranscribed = TripPlannerPrefs.getBufferWalkTranscribed()
    @State private var bufferWalkEstimated = TripPlannerPrefs.getBufferWalkEstimated()

    var body: some View {
        NavigationStack {
            List {
                Section(header: Text("Notificaciones")) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Configura qué notificaciones quieres recibir.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Button(action: { showHowItWorks = true }) {
                            Text("¿Cómo funciona?")
                                .font(.subheadline)
                        }
                    }
                    .padding(.vertical, 4)

                    Picker("Alertas de servicio", selection: Binding(
                        get: { alertMinSeverity },
                        set: { newValue in
                            alertMinSeverity = newValue
                            AnalyticsService.shared.track("settings_changed", with: ["field": "alert_severity"])
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
                                AnalyticsService.shared.track("settings_changed", with: ["field": "guided_mode"])
                                GuidedModePrefs.setGuidedModeEnabled(newValue)
                            }
                        )
                    )
                    Text("Muestra una pantalla para seleccionar la dirección del autobús antes de ver las salidas")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Section(header: Text("Personalizar pantalla de inicio")) {
                    Text("Elige qué acciones aparecen en la pantalla de inicio. Cada una solo puede ocupar un sitio a la vez.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .padding(.vertical, 4)

                    ForEach(LandingSlot.allCases, id: \.self) { slot in
                        Picker(slot.label, selection: Binding(
                            get: { landingSlots[slot] ?? slot.defaultAction },
                            set: { newValue in
                                LandingLayoutPrefs.setAction(slot, newValue)
                                landingSlots = LandingLayoutPrefs.getSlots()
                                AnalyticsService.shared.track("settings_changed", with: ["field": slot.rawValue])
                            }
                        )) {
                            ForEach(MainLandingAction.allCases, id: \.self) { option in
                                Text(option.label).tag(option)
                            }
                        }
                        .pickerStyle(.menu)
                    }
                }

                Section(header: Text("Planifica tu viaje")) {
                    TripPlannerNumberRow(
                        title: "Espera máxima en transbordo",
                        subtitle: "Tiempo máximo de espera para que una conexión entre autobuses se considere válida.",
                        value: $maxWaitMin,
                        minValue: 5,
                        maxValue: 240,
                        onCommit: {
                            TripPlannerPrefs.setMaxWaitMin($0)
                            AnalyticsService.shared.track("settings_changed", with: ["field": "trip_planner_tuning"])
                        }
                    )
                    TripPlannerNumberRow(
                        title: "Margen en la misma parada (horario exacto)",
                        subtitle: "Minutos mínimos entre bajar y coger el siguiente bus en la misma parada para que la conexión se considere válida, cuando el horario es oficial. Cuanto mayor, más seguras las conexiones, pero se muestran menos opciones.",
                        value: $bufferSameStopTranscribed,
                        minValue: 0,
                        maxValue: 30,
                        onCommit: {
                            TripPlannerPrefs.setBufferSameStopTranscribed($0)
                            AnalyticsService.shared.track("settings_changed", with: ["field": "trip_planner_tuning"])
                        },
                        showTightMarginWarning: bufferSameStopTranscribed < TripPlannerPrefs.recommendedMinBuffer,
                        onTightMarginWarningTap: { showTightMarginInfo = true }
                    )
                    TripPlannerNumberRow(
                        title: "Margen en la misma parada (estimado)",
                        subtitle: "Igual, pero cuando la hora de llegada es una estimación, no un horario exacto.",
                        value: $bufferSameStopEstimated,
                        minValue: 0,
                        maxValue: 30,
                        onCommit: {
                            TripPlannerPrefs.setBufferSameStopEstimated($0)
                            AnalyticsService.shared.track("settings_changed", with: ["field": "trip_planner_tuning"])
                        },
                        showTightMarginWarning: bufferSameStopEstimated < TripPlannerPrefs.recommendedMinBuffer,
                        onTightMarginWarningTap: { showTightMarginInfo = true }
                    )
                    TripPlannerNumberRow(
                        title: "Margen tras caminar (horario exacto)",
                        subtitle: "Minutos mínimos tras un transbordo caminando a otra parada para que la conexión se considere válida, cuando el horario es oficial. Cuanto mayor, más seguras las conexiones, pero se muestran menos opciones.",
                        value: $bufferWalkTranscribed,
                        minValue: 0,
                        maxValue: 30,
                        onCommit: {
                            TripPlannerPrefs.setBufferWalkTranscribed($0)
                            AnalyticsService.shared.track("settings_changed", with: ["field": "trip_planner_tuning"])
                        },
                        showTightMarginWarning: bufferWalkTranscribed < TripPlannerPrefs.recommendedMinBuffer,
                        onTightMarginWarningTap: { showTightMarginInfo = true }
                    )
                    TripPlannerNumberRow(
                        title: "Margen tras caminar (estimado)",
                        subtitle: "Igual, pero cuando la hora es una estimación, no un horario exacto.",
                        value: $bufferWalkEstimated,
                        minValue: 0,
                        maxValue: 30,
                        onCommit: {
                            TripPlannerPrefs.setBufferWalkEstimated($0)
                            AnalyticsService.shared.track("settings_changed", with: ["field": "trip_planner_tuning"])
                        },
                        showTightMarginWarning: bufferWalkEstimated < TripPlannerPrefs.recommendedMinBuffer,
                        onTightMarginWarningTap: { showTightMarginInfo = true }
                    )
                    Button("Restaurar valores") {
                        AnalyticsService.shared.track("settings_changed", with: ["field": "trip_planner_tuning"])
                        TripPlannerPrefs.resetToDefaults()
                        maxWaitMin = TripPlannerPrefs.getMaxWaitMin()
                        bufferSameStopTranscribed = TripPlannerPrefs.getBufferSameStopTranscribed()
                        bufferSameStopEstimated = TripPlannerPrefs.getBufferSameStopEstimated()
                        bufferWalkTranscribed = TripPlannerPrefs.getBufferWalkTranscribed()
                        bufferWalkEstimated = TripPlannerPrefs.getBufferWalkEstimated()
                    }
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
            .sheet(isPresented: $showTightMarginInfo) {
                TightMarginInfoSheet()
            }
        }
    }
}

private struct TripPlannerNumberRow: View {
    let title: String
    let subtitle: String
    @Binding var value: Int
    let minValue: Int
    let maxValue: Int
    let onCommit: (Int) -> Void
    var showTightMarginWarning: Bool = false
    var onTightMarginWarningTap: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
            Text(subtitle)
                .font(.caption)
                .foregroundColor(.secondary)
            HStack {
                Slider(
                    value: Binding(
                        get: { Double(value) },
                        set: { newValue in
                            let rounded = Int(newValue.rounded())
                            value = rounded
                            onCommit(rounded)
                        }
                    ),
                    in: Double(minValue)...Double(maxValue)
                )
                TextField("", value: $value, formatter: NumberFormatter())
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 50)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: value) { _, newValue in
                        let clamped = min(max(newValue, minValue), maxValue)
                        if clamped != newValue { value = clamped }
                        onCommit(clamped)
                    }
            }
            if showTightMarginWarning {
                TightMarginWarningPill(onTap: onTightMarginWarningTap)
            }
        }
        .padding(.vertical, 2)
    }
}

private struct TightMarginWarningPill: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.caption2)
                Text("Margen ajustado — toca para más información")
                    .font(.caption2)
            }
            .foregroundColor(.orange)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Color.orange.opacity(0.15))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct TightMarginInfoSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Las horas de llegada son siempre una previsión, no una posición en tiempo real: incluso los horarios oficiales de Linecar son una estimación, y el autobús puede pasar unos minutos antes o después.")
                Text("Con un margen menor de \(TripPlannerPrefs.recommendedMinBuffer) min, un pequeño retraso en el primer autobús puede hacer que pierdas el de conexión. Redúcelo solo si conoces bien la puntualidad de esa línea.")
                Spacer()
            }
            .padding()
            .navigationTitle("Margen ajustado")
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
