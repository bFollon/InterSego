/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// User-tunable parameters for `JourneyPlannerService`'s connection search (max transfer wait,
/// transfer buffers). Uses UserDefaults for storage. Mirrors Android TripPlannerPrefs API.
struct TripPlannerPrefs {
    private static let keyMaxWaitMin = "tripPlannerMaxWaitMin"
    private static let keyBufferSameStopTranscribed = "tripPlannerBufferSameStopTranscribed"
    private static let keyBufferSameStopEstimated = "tripPlannerBufferSameStopEstimated"
    private static let keyBufferWalkTranscribed = "tripPlannerBufferWalkTranscribed"
    private static let keyBufferWalkEstimated = "tripPlannerBufferWalkEstimated"

    static let defaultMaxWaitMin = 90
    static let defaultBufferSameStopTranscribed = 15
    static let defaultBufferSameStopEstimated = 15
    static let defaultBufferWalkTranscribed = 15
    static let defaultBufferWalkEstimated = 15

    /// Below this, a transfer buffer is shown with a "tight margin" warning.
    static let recommendedMinBuffer = 15

    static func getMaxWaitMin() -> Int {
        UserDefaults.standard.object(forKey: keyMaxWaitMin) as? Int ?? defaultMaxWaitMin
    }
    static func setMaxWaitMin(_ value: Int) {
        UserDefaults.standard.set(value, forKey: keyMaxWaitMin)
    }

    static func getBufferSameStopTranscribed() -> Int {
        UserDefaults.standard.object(forKey: keyBufferSameStopTranscribed) as? Int ?? defaultBufferSameStopTranscribed
    }
    static func setBufferSameStopTranscribed(_ value: Int) {
        UserDefaults.standard.set(value, forKey: keyBufferSameStopTranscribed)
    }

    static func getBufferSameStopEstimated() -> Int {
        UserDefaults.standard.object(forKey: keyBufferSameStopEstimated) as? Int ?? defaultBufferSameStopEstimated
    }
    static func setBufferSameStopEstimated(_ value: Int) {
        UserDefaults.standard.set(value, forKey: keyBufferSameStopEstimated)
    }

    static func getBufferWalkTranscribed() -> Int {
        UserDefaults.standard.object(forKey: keyBufferWalkTranscribed) as? Int ?? defaultBufferWalkTranscribed
    }
    static func setBufferWalkTranscribed(_ value: Int) {
        UserDefaults.standard.set(value, forKey: keyBufferWalkTranscribed)
    }

    static func getBufferWalkEstimated() -> Int {
        UserDefaults.standard.object(forKey: keyBufferWalkEstimated) as? Int ?? defaultBufferWalkEstimated
    }
    static func setBufferWalkEstimated(_ value: Int) {
        UserDefaults.standard.set(value, forKey: keyBufferWalkEstimated)
    }

    /// Resets all trip-planner tuning parameters to their app defaults.
    static func resetToDefaults() {
        setMaxWaitMin(defaultMaxWaitMin)
        setBufferSameStopTranscribed(defaultBufferSameStopTranscribed)
        setBufferSameStopEstimated(defaultBufferSameStopEstimated)
        setBufferWalkTranscribed(defaultBufferWalkTranscribed)
        setBufferWalkEstimated(defaultBufferWalkEstimated)
    }
}
