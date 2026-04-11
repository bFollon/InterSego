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

/// Manages user consent preferences for error reporting and analytics.
/// Equivalent to Android MonitoringPreferencesService.
class MonitoringPreferencesService {
    static let shared = MonitoringPreferencesService()

    private init() {}

    private enum Keys {
        static let monitoringEnabled = "monitoring_enabled"
        static let choiceMade = "monitoring_choice_made"
        static let analyticsEnabled = "analytics_enabled"
        static let analyticsChoiceMade = "analytics_choice_made"
    }

    private let defaults = UserDefaults.standard

    // MARK: - Error Reporting

    func hasUserOptedIn() -> Bool {
        guard hasUserMadeChoice() else { return false }
        return defaults.bool(forKey: Keys.monitoringEnabled)
    }

    func hasUserMadeChoice() -> Bool {
        return defaults.bool(forKey: Keys.choiceMade)
    }

    func setMonitoringEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: Keys.monitoringEnabled)
        defaults.set(true, forKey: Keys.choiceMade)
        DebugConfig.debugPrint("MonitoringPreferencesService: error reporting \(enabled ? "enabled" : "disabled")")
    }

    // MARK: - Analytics

    func hasUserOptedInToAnalytics() -> Bool {
        guard hasUserMadeAnalyticsChoice() else { return false }
        return defaults.bool(forKey: Keys.analyticsEnabled)
    }

    func hasUserMadeAnalyticsChoice() -> Bool {
        return defaults.bool(forKey: Keys.analyticsChoiceMade)
    }

    func setAnalyticsEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: Keys.analyticsEnabled)
        defaults.set(true, forKey: Keys.analyticsChoiceMade)
        DebugConfig.debugPrint("MonitoringPreferencesService: analytics \(enabled ? "enabled" : "disabled")")
    }
}
