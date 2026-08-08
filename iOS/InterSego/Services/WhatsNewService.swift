/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

struct WhatsNewEntry {
    let icon: String
    let title: String
    let body: String
}

/// Version-gated "what's new" notice, shown once per app update.
///
/// Unlike the per-feature tutorials (`RemindersTutorial`, `LiveUpdateTutorialView`), which key
/// off "has this screen been visited before", this keys off "has this app version been seen
/// before" — so it surfaces on first launch after an update, before the user has to go looking
/// for whatever changed (e.g. a feature moving to a different screen).
///
/// `entries` should be updated (and cleared once shipped) alongside each version bump that
/// warrants an announcement — see `MARKETING_VERSION` in project.pbxproj.
enum WhatsNewService {
    private static let lastSeenVersionKey = "whats_new_last_seen_version"

    static var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    /// The entries to show for the current version. Empty once there's nothing to announce.
    static let entries: [WhatsNewEntry] = [
        WhatsNewEntry(
            icon: "square.grid.2x2",
            title: "\u{201c}Mis recordatorios\u{201d} se movió",
            body: "Ahora lo encontrarás dentro de \u{201c}Otras opciones\u{201d}, junto con las nuevas funciones."
        ),
        WhatsNewEntry(
            icon: "calendar",
            title: "Consulta otro día",
            body: "Desde \u{201c}Otras opciones\u{201d} puedes elegir una fecha futura y ver los horarios de cualquier línea para ese día, con aviso de festivos incluido."
        ),
        WhatsNewEntry(
            icon: "calendar.badge.exclamationmark",
            title: "Aviso de festivos",
            body: "Ahora cuando sea festivo y los horarios se vean afectados, lo verás claramente en el listado de salidas."
        ),
    ]

    /// True if the current version hasn't been announced yet on this device.
    ///
    /// No last-seen version recorded means one of two things: a genuinely fresh install (in
    /// which case there's nothing to announce — the user never saw the old layout), or an
    /// existing install upgrading into the very first version that ships this mechanism (in
    /// which case they *should* see it). `MonitoringPreferencesService.hasUserMadeAnalyticsChoice`
    /// distinguishes the two: that choice is only ever made once, on a screen every install has
    /// gone through since long before this feature existed, so its presence means the app has
    /// run on this device before.
    static func shouldShow() -> Bool {
        guard !entries.isEmpty else { return false }
        guard let lastSeen = UserDefaults.standard.string(forKey: lastSeenVersionKey) else {
            let isExistingInstall = MonitoringPreferencesService.shared.hasUserMadeAnalyticsChoice()
            if !isExistingInstall {
                markAsSeen()
            }
            return isExistingInstall
        }
        return lastSeen != currentVersion
    }

    static func markAsSeen() {
        UserDefaults.standard.set(currentVersion, forKey: lastSeenVersionKey)
    }
}
