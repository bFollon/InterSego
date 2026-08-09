/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// One segment of a `Journey`: either a bus ride or a walking transfer between two physical
/// stop identifiers (`physicalStopId`).
///
/// See `docs/JOURNEY_PLANNER.md` — this type mirrors Android's `Leg` sealed class field-for-field.
enum Leg: Hashable {
    case ride(Ride)
    case walk(Walk)

    /// A bus ride on a single route/variant, covering one connection extracted from the timetable JSON.
    struct Ride: Hashable {
        let routeId: String
        let variantId: String
        let fromStop: String
        let toStop: String
        let depMin: Int
        let arrMin: Int
        /// True if either endpoint's departure time is cluster-estimated (`timeIsEstimated` in the JSON).
        let isEstimated: Bool
    }

    /// A walk between two distinct physical stops, per `resources/transfers.json`.
    struct Walk: Hashable {
        let fromStop: String
        let toStop: String
        let meters: Int
        let minutes: Int
    }

    var fromStop: String {
        switch self {
        case .ride(let r): r.fromStop
        case .walk(let w): w.fromStop
        }
    }

    var toStop: String {
        switch self {
        case .ride(let r): r.toStop
        case .walk(let w): w.toStop
        }
    }
}

/// A complete A-to-B journey: an ordered sequence of `Leg`s.
///
/// `departureMin`/`arrivalMin`/`transferCount` are stored, not derived from `legs` — a journey
/// may start or end with a `Leg.walk` (e.g. walking to a better-connected nearby stop before
/// boarding), so "departure" isn't always the first leg's own depMin. The CSA builder computes
/// these directly since it already knows them when it constructs the journey.
///
/// See `docs/JOURNEY_PLANNER.md` for how journeys are computed (Connection Scan) and ranked.
struct Journey: Hashable {
    let legs: [Leg]
    let departureMin: Int
    let arrivalMin: Int
    let transferCount: Int

    init(legs: [Leg], departureMin: Int, arrivalMin: Int, transferCount: Int) {
        precondition(!legs.isEmpty, "Journey must have at least one leg")
        self.legs = legs
        self.departureMin = departureMin
        self.arrivalMin = arrivalMin
        self.transferCount = transferCount
    }
}
