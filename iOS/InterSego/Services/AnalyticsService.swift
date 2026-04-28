/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
