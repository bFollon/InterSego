/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Combined privacy consent modal covering both error reporting and analytics.
///
/// Shown to all users whenever the analytics choice hasn't been made yet.
/// The errors toggle is pre-filled from the existing saved preference;
/// the analytics toggle starts off by default.
struct MonitoringConsentView: View {
    @Binding var isPresented: Bool

    @State private var errorsEnabled: Bool
    @State private var analyticsEnabled: Bool = false

    init(isPresented: Binding<Bool>) {
        self._isPresented = isPresented
        self._errorsEnabled = State(initialValue: MonitoringPreferencesService.shared.hasUserOptedIn())
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header
            VStack(spacing: 8) {
                Image(systemName: "hand.raised.fill")
                    .font(.system(size: 36))
                    .foregroundColor(.blue)

                Text("Configuración de Privacidad")
                    .font(.title3)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text("Elige qué datos compartes con nosotros. Todo es anónimo y puedes cambiarlo en Ajustes.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity)

            Divider()

            Toggle(isOn: $errorsEnabled) {
                VStack(alignment: .leading, spacing: 3) {
                    Label("Monitoreo de Errores", systemImage: "exclamationmark.triangle.fill")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    Text("Datos técnicos anónimos (info del dispositivo, errores y fallos) para detectar y corregir problemas.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .tint(.blue)

            Divider()

            Toggle(isOn: $analyticsEnabled) {
                VStack(alignment: .leading, spacing: 3) {
                    Label("Analíticas de Uso", systemImage: "chart.bar.fill")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    Text("Eventos de uso anónimos (pantallas visitadas, funciones usadas) para entender cómo mejorar la app.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .tint(.blue)

            Divider()

            Button(action: confirm) {
                Text("Confirmar")
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.blue)
                    .cornerRadius(10)
            }
            .buttonStyle(PlainButtonStyle())

            Text("Cambiar en Ajustes cuando quiera")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
        .padding(20)
    }

    private func confirm() {
        MonitoringPreferencesService.shared.setMonitoringEnabled(errorsEnabled)
        MonitoringPreferencesService.shared.setAnalyticsEnabled(analyticsEnabled)
        isPresented = false
    }
}

#Preview {
    MonitoringConsentView(isPresented: .constant(true))
}
