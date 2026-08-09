/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Pure (no UIKit/SwiftUI dependency) trip-major view of one route's timetable JSON, as needed
/// for connection extraction. See `docs/JOURNEY_PLANNER.md`. Distinct from `BusTimetable`, which
/// is the stop-major pivot used for display.
struct JourneyStop: Hashable {
    let id: String
    let physicalStopId: String
    let isEstimated: Bool
}

struct JourneyVariant: Hashable {
    let id: String
    let stopSequence: [String]
}

/// One populated departure: a resolved minute-of-day plus its effective season.
struct JourneyDeparture: Hashable {
    let minutesOfDay: Int
    let season: SeasonalAvailability
}

/// One trip's departures, index-aligned with its variant's `stopSequence`. `nil` = stop skipped.
struct JourneyTrip: Hashable {
    let departures: [JourneyDeparture?]
}

struct JourneyTimetableSection: Hashable {
    let variantId: String
    let dayType: DayType
    let trips: [JourneyTrip]
}

struct JourneyRouteData: Hashable {
    let routeId: String
    let stops: [JourneyStop]
    let variants: [JourneyVariant]
    let timetables: [JourneyTimetableSection]
}

/// A walking edge between two distinct physical stops, from `resources/transfers.json`. Bidirectional.
struct TransferEdge: Hashable {
    let from: String
    let to: String
    let meters: Int
    let walkMinutes: Int
}
