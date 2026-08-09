/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// A remembered origin/destination pair for the journey planner — this is a commuting app,
/// people repeat journeys.
struct RecentJourney: Codable, Hashable, Identifiable {
    let originStopId: String
    let originName: String
    let destinationStopId: String
    let destinationName: String
    var id: String { "\(originStopId)-\(destinationStopId)" }
}

/// Persists the last few origin/destination pairs searched, most-recent first. Mirrors
/// Android's RecentJourneysService API (UserDefaults instead of SharedPreferences).
enum RecentJourneysService {
    private static let key = "recentJourneys"
    private static let maxEntries = 5

    static func recent() -> [RecentJourney] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([RecentJourney].self, from: data) else { return [] }
        return decoded
    }

    static func record(_ journey: RecentJourney) {
        let existing = recent().filter {
            !($0.originStopId == journey.originStopId && $0.destinationStopId == journey.destinationStopId)
        }
        let updated = Array(([journey] + existing).prefix(maxEntries))
        if let data = try? JSONEncoder().encode(updated) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
}
