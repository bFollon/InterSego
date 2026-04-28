/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Manages preferences for guided mode (direction picker) feature.
/// Uses UserDefaults for storage. Mirrors Android GuidedModePrefs API.
struct GuidedModePrefs {
    private static let keyEnabled = "guidedModeEnabled"
    private static let keyTutorialShown = "guidedPickerTutorialShown"
    private static let keyLastViewPrefix = "guidedLastView_"

    /// Check if guided mode is enabled (default: true)
    static func isGuidedModeEnabled() -> Bool {
        UserDefaults.standard.object(forKey: keyEnabled) as? Bool ?? true
    }

    /// Set guided mode enabled/disabled
    static func setGuidedModeEnabled(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: keyEnabled)
    }

    /// Check if the direction picker tutorial has been shown
    static func isTutorialShown() -> Bool {
        UserDefaults.standard.bool(forKey: keyTutorialShown)
    }

    /// Mark the direction picker tutorial as shown
    static func setTutorialShown() {
        UserDefaults.standard.set(true, forKey: keyTutorialShown)
    }

    /// Get the last selected view ID for a given stop and route
    /// Returns nil if no previous selection exists
    static func getLastViewId(stopId: String, routeId: String) -> String? {
        let key = "\(keyLastViewPrefix)\(stopId)_\(routeId)"
        return UserDefaults.standard.string(forKey: key)
    }

    /// Save the last selected view ID for a given stop and route
    static func saveLastViewId(_ viewId: String, stopId: String, routeId: String) {
        let key = "\(keyLastViewPrefix)\(stopId)_\(routeId)"
        UserDefaults.standard.set(viewId, forKey: key)
    }
}
