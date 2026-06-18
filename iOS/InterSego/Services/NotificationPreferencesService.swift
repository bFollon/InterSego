/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

final class NotificationPreferencesService {
    static let shared = NotificationPreferencesService()
    private init() {}

    private let choiceMadeKey  = "notificationChoiceMade"
    private let minSeverityKey = "alertMinSeverity"

    func hasUserMadeNotificationChoice() -> Bool {
        UserDefaults.standard.bool(forKey: choiceMadeKey)
    }

    func getAlertMinSeverity() -> String {
        UserDefaults.standard.string(forKey: minSeverityKey) ?? "info"
    }

    func saveChoice(minSeverity: String) {
        UserDefaults.standard.set(true, forKey: choiceMadeKey)
        UserDefaults.standard.set(minSeverity, forKey: minSeverityKey)
    }
}
