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

    /// Minimum wait at a transfer point before it's surfaced as an explicit step (see
    /// `stepsWithWaits()`) rather than left implicit in the leg times either side of it. Single
    /// source of truth for this value — mirrored on Android as `Journey.LONG_WAIT_THRESHOLD_MIN`.
    static let longWaitThresholdMin = 5

    /// The stop a mid-journey transfer boards at, and how many minutes elapse there between
    /// arriving (by ride or by walk) and the connecting bus's departure. One entry per `Ride` leg
    /// that isn't the journey's first leg — i.e. one per transfer, regardless of whether it's a
    /// same-stop or walking connection. See `transferMargins()`.
    struct TransferMargin: Hashable {
        let stopId: String
        let marginMin: Int
    }

    /// Computes `TransferMargin`s for every mid-journey transfer. This is the actual elapsed gap
    /// for each connection — independent of whichever (possibly user-lowered) buffer the search
    /// itself required to accept the journey — so callers can flag a transfer as tight against a
    /// fixed safety threshold (`TripPlannerPrefs.recommendedMinBuffer`) regardless of search
    /// settings. Mirrored on Android as `Journey.transferMargins()`.
    func transferMargins() -> [TransferMargin] {
        var margins: [TransferMargin] = []
        var clock = departureMin
        for (index, leg) in legs.enumerated() {
            if index > 0, case .ride(let ride) = leg {
                margins.append(TransferMargin(stopId: ride.fromStop, marginMin: ride.depMin - clock))
            }
            switch leg {
            case .ride(let ride): clock = ride.arrMin
            case .walk(let walk): clock += walk.minutes
            }
        }
        return margins
    }

    /// `legs` with a `.wait` step inserted before any Ride that follows more than
    /// `longWaitThresholdMin` minutes after the previous leg ends. Only mid-journey transfers are
    /// considered — never before the first leg, since that's simply when you start the journey,
    /// not something to "wait out" (see `departureMin`'s doc comment on why it can precede the
    /// first leg's own `depMin`).
    func stepsWithWaits() -> [JourneyStep] {
        var steps: [JourneyStep] = []
        var clock = departureMin
        for (index, leg) in legs.enumerated() {
            if index > 0, case .ride(let ride) = leg {
                let gap = ride.depMin - clock
                if gap > Journey.longWaitThresholdMin {
                    steps.append(.wait(gap))
                }
            }
            steps.append(.leg(leg))
            switch leg {
            case .ride(let ride): clock = ride.arrMin
            case .walk(let walk): clock += walk.minutes
            }
        }
        return steps
    }
}

/// One row of a journey's leg-by-leg breakdown: either an actual `Leg`, or an explicit wait
/// inserted between two legs by `Journey.stepsWithWaits()`.
enum JourneyStep: Hashable {
    case leg(Leg)
    case wait(Int)
}
