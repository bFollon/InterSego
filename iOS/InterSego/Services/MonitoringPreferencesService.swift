/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
