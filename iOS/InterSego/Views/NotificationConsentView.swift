/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI
import UserNotifications

struct NotificationConsentView: View {
    @Binding var isPresented: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header
            VStack(spacing: 8) {
                Image(systemName: "bell.fill")
                    .font(.system(size: 36))
                    .foregroundColor(.blue)

                Text("Notificaciones")
                    .font(.title3)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text("InterSego puede avisarte sobre recordatorios de autobús y alertas de servicio como huelgas o desvíos.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity)

            Divider()

            Button(action: activate) {
                Text("Activar notificaciones")
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.blue)
                    .cornerRadius(10)
            }
            .buttonStyle(PlainButtonStyle())

            Button(action: dismiss) {
                Text("Ahora no")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(PlainButtonStyle())

            Text("Puedes personalizar qué alertas recibir en Ajustes")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
        .padding(20)
    }

    private func activate() {
        Task {
            let center = UNUserNotificationCenter.current()
            let status = await center.notificationSettings().authorizationStatus
            var effectiveSeverity = "info"
            if status == .notDetermined {
                let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
                if !granted { effectiveSeverity = "none" }
            } else if status == .denied {
                effectiveSeverity = "none"
            }
            saveAndRegister(minSeverity: effectiveSeverity)
        }
    }

    private func dismiss() {
        saveAndRegister(minSeverity: "none")
    }

    private func saveAndRegister(minSeverity: String) {
        NotificationPreferencesService.shared.saveChoice(minSeverity: minSeverity)
        if let token = UserDefaults.standard.string(forKey: "apnsDeviceToken") {
            Task { await DeviceTokenService.shared.registerToken(token, platform: "ios", minSeverity: minSeverity) }
        }
        isPresented = false
    }
}

#Preview {
    NotificationConsentView(isPresented: .constant(true))
}
