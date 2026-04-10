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
