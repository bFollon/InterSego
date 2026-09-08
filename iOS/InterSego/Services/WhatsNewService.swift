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
    /// The version this entry shipped in, e.g. "3.9.0" — see `MARKETING_VERSION` in project.pbxproj.
    let version: String
}

/// Version-*range*-gated "what's new" notice, shown once per app update.
///
/// Unlike the per-feature tutorials (`RemindersTutorial`, `LiveUpdateTutorialView`), which key
/// off "has this screen been visited before", this keys off "has this app version been seen
/// before" — so it surfaces on first launch after an update, before the user has to go looking
/// for whatever changed (e.g. a feature moving to a different screen).
///
/// `entries` is append-only: each release adds new version-tagged entries, older ones are never
/// removed. A user who skips versions sees everything they missed (`entriesToShow()`), not just
/// whatever shipped in the version they happen to update to — capped at `maxEntriesToShow` so
/// someone who hasn't updated in a very long time doesn't get a wall of old announcements.
enum WhatsNewService {
    private static let lastSeenVersionKey = "whats_new_last_seen_version"

    /// Upper bound on how many past entries to show at once, oldest-missed dropped first.
    private static let maxEntriesToShow = 5

    static var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    /// All announcements ever shipped, oldest first. Append new ones here on every release that
    /// warrants an announcement — never remove or overwrite past entries.
    static let entries: [WhatsNewEntry] = [
        WhatsNewEntry(
            icon: "point.topleft.down.curvedto.point.bottomright.up",
            title: "Planifica tu viaje",
            body: "Nuevo en \u{201c}Más opciones\u{201d}: dinos de dónde a dónde quieres ir y te mostramos las mejores combinaciones de autobuses, con transbordos incluidos. Elige salir a una hora concreta o llegar antes de una hora límite.",
            version: "3.9.0"
        ),
        WhatsNewEntry(
            icon: "slider.horizontal.3",
            title: "Ajusta tu margen de conexión",
            body: "En Configuración → \u{201c}Planifica tu viaje\u{201d} puedes personalizar la espera máxima en un transbordo y los márgenes de seguridad que usa el planificador de rutas al buscar combinaciones.",
            version: "3.11.0"
        ),
        WhatsNewEntry(
            icon: "square.grid.2x2",
            title: "Personaliza tu pantalla de inicio",
            body: "En Configuración → \u{201c}Acción principal\u{201d} puedes elegir qué opción aparece en la tarjeta principal de inicio: Líneas de bus, Planificar viaje, Mis recordatorios o Consultar otro día. Las demás siguen disponibles en \u{201c}Más opciones\u{201d}.",
            version: "3.12.0"
        ),
        WhatsNewEntry(
            icon: "map",
            title: "Mapa en tu itinerario",
            body: "Al elegir una combinación en \u{201c}Planifica tu viaje\u{201d} ahora verás un mapa con el recorrido completo: cada trayecto en autobús con su propio color y los transbordos marcados sobre el plano.",
            version: "3.13.0"
        ),
        WhatsNewEntry(
            icon: "star",
            title: "Paradas favoritas",
            body: "Marca con la estrella tus paradas de uso frecuente desde la pantalla de horarios. Accede a ellas al instante desde \u{201c}Más opciones\u{201d} → \u{201c}Favoritos\u{201d}.",
            version: "3.14.0"
        ),
    ]

    /// Entries the user hasn't seen yet: `version > lastSeenVersion` and `version <= currentVersion`,
    /// sorted ascending and capped to the most recent `maxEntriesToShow`.
    static func entriesToShow() -> [WhatsNewEntry] {
        let lastSeen = UserDefaults.standard.string(forKey: lastSeenVersionKey) ?? "0.0.0"
        let inRange = entries.filter {
            compareSemVer($0.version, lastSeen) == .orderedDescending
                && compareSemVer($0.version, currentVersion) != .orderedDescending
        }
        let sorted = inRange.sorted { compareSemVer($0.version, $1.version) == .orderedAscending }
        return Array(sorted.suffix(maxEntriesToShow))
    }

    /// True if there's at least one unseen entry to announce on this device.
    ///
    /// No last-seen version recorded means one of two things: a genuinely fresh install (in
    /// which case there's nothing to announce — the user never saw the old layout), or an
    /// existing install upgrading into the very first version that ships this mechanism (in
    /// which case they *should* see whatever they missed). `MonitoringPreferencesService.hasUserMadeAnalyticsChoice`
    /// distinguishes the two: that choice is only ever made once, on a screen every install has
    /// gone through since long before this feature existed, so its presence means the app has
    /// run on this device before.
    static func shouldShow() -> Bool {
        guard UserDefaults.standard.string(forKey: lastSeenVersionKey) != nil else {
            let isExistingInstall = MonitoringPreferencesService.shared.hasUserMadeAnalyticsChoice()
            if !isExistingInstall {
                markAsSeen()
                return false
            }
            return !entriesToShow().isEmpty
        }
        return !entriesToShow().isEmpty
    }

    static func markAsSeen() {
        UserDefaults.standard.set(currentVersion, forKey: lastSeenVersionKey)
    }
}

/// Compares two "MAJOR.MINOR.PATCH" SemVer strings numerically, not lexicographically
/// (`"3.10.0" > "3.9.0"`, which plain string comparison would get wrong). Missing or
/// non-numeric components are treated as 0.
private func compareSemVer(_ lhs: String, _ rhs: String) -> ComparisonResult {
    let l = semVerComponents(lhs)
    let r = semVerComponents(rhs)
    if l != r {
        return (l.0, l.1, l.2) < (r.0, r.1, r.2) ? .orderedAscending : .orderedDescending
    }
    return .orderedSame
}

private func semVerComponents(_ version: String) -> (Int, Int, Int) {
    let parts = version.split(separator: ".").map { Int($0) ?? 0 }
    return (parts.count > 0 ? parts[0] : 0, parts.count > 1 ? parts[1] : 0, parts.count > 2 ? parts[2] : 0)
}
