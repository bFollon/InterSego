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
import Aptabase

/// Service for product analytics via Aptabase (self-hosted).
///
/// Initialization is gated on user consent. If the user has not opted in,
/// the SDK is never started and track() calls are no-ops.
/// Equivalent to Android AnalyticsService.
class AnalyticsService {
    static let shared = AnalyticsService()

    private init() {}

    /// Initialize the Aptabase SDK.
    /// Must be called only if the user has opted in to analytics.
    func initialize() {
        guard MonitoringPreferencesService.shared.hasUserOptedInToAnalytics() else {
            DebugConfig.debugPrint("AnalyticsService: skipping init (user has not opted in)")
            return
        }

        Aptabase.shared.initialize(appKey: AppConfig.aptabaseKey, with: InitOptions(host: AppConfig.aptabaseHost))
        DebugConfig.debugPrint("AnalyticsService: Aptabase initialized")
    }

    /// Track a named event with optional properties.
    /// Safe to call even if Aptabase is not initialized (SDK no-ops when uninitialized).
    func track(_ eventName: String, with props: [String: Any] = [:]) {
        if props.isEmpty {
            Aptabase.shared.trackEvent(eventName)
        } else {
            Aptabase.shared.trackEvent(eventName, with: props)
        }
        DebugConfig.debugPrint("AnalyticsService: tracked '\(eventName)'")
    }
}
