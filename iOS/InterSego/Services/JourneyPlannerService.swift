/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// A journey search request. `dayTypes`, `month` and `weekday` are resolved by the caller
/// (via `TimetableQueryUtils`-equivalent day-type resolution and the query date) rather than
/// computed here, so this whole service stays pure Swift with no Foundation-Calendar coupling
/// baked into its logic and is trivially unit-testable.
///
/// Exactly one of two search modes applies: if `arriveBeforeMin` is set, it takes over — the
/// whole day is searched (regardless of `departAfterMin`) for journeys arriving at or before it,
/// ranked by closeness to that deadline first. Otherwise `departAfterMin` applies as before,
/// ranked by earliest arrival first. See "Arrive-before mode" in `docs/JOURNEY_PLANNER.md`.
///
/// - Parameters:
///   - origin: physicalStopId
///   - destination: physicalStopId
///   - weekday: Calendar.current.component(.weekday) value (Sun=1…Sat=7), for MON_FRI_ONLY/FRI_ONLY seasonal filtering
///   - departAfterMin: minutes-of-day; only journeys departing at or after this are returned (ignored when `arriveBeforeMin` is set)
///   - arriveBeforeMin: minutes-of-day; when set, switches to arrive-before mode (see above)
struct JourneyQuery {
    let origin: String
    let destination: String
    let dayTypes: Set<DayType>
    let month: Int
    let weekday: Int
    let departAfterMin: Int
    var arriveBeforeMin: Int? = nil
}

/// Journey search, per `docs/JOURNEY_PLANNER.md`. Pure logic, no UIKit/SwiftUI dependency —
/// takes already-loaded route/transfer data and returns a ranked, deduplicated list of journeys.
///
/// Implements a 2-round bounded Connection Scan (round 0 = direct, round 1 = one transfer),
/// justified by the Epic 0 spike's "never more than 1 transfer" result (see the spec doc).
///
/// Behavioral mirror of Android's `JourneyPlannerService.kt` — keep the two in lockstep; see
/// `docs/JOURNEY_PLANNER.md` for the shared spec both implement.
enum JourneyPlannerService {

    private static let maxWaitMin = 90 // ignore boarding opportunities requiring an unreasonably long wait
    private static let bufferSameStopTranscribed = 3
    private static let bufferSameStopEstimated = 8
    private static let bufferWalkTranscribed = 3
    private static let bufferWalkEstimated = 8

    /// One physically contiguous, time-monotonic run of stops within a single trip. See `buildRideSegments`.
    private struct RideSegment {
        let routeId: String
        let variantId: String
        let tripKey: String // unique per (routeId, variantId, dayType, trip index) — identifies "the same vehicle"
        let stops: [String]
        let minutesOfDay: [Int]
        let isEstimated: [Bool]
    }

    /// A stop reached during the search, with enough context to compute the next leg's buffer and to reconstruct legs.
    private struct Reached {
        let stop: String
        let timeMin: Int
        let isEstimatedArrival: Bool
        let legsFromOrigin: [Leg]
        let boardedTripKeys: Set<String> // trips already ridden on this path — a round-1 transfer must use a different one
    }

    static func findJourneys(
        routes: [JourneyRouteData],
        transfers: [TransferEdge],
        query: JourneyQuery
    ) -> [Journey] {
        let segments = buildRideSegments(routes: routes, dayTypes: query.dayTypes, month: query.month, weekday: query.weekday)
        let walkNeighbors = buildWalkIndex(transfers)

        // Arrive-before mode searches the whole day (any bus, however early, is a candidate) and
        // ranks by closeness to the deadline afterwards - departAfterMin doesn't apply.
        let searchStartMin = query.arriveBeforeMin != nil ? 0 : query.departAfterMin

        // Round 0: origin (+ its immediate walk neighbors) as boarding points, no buffer (nothing to transfer from yet).
        let origin = Reached(stop: query.origin, timeMin: searchStartMin, isEstimatedArrival: false, legsFromOrigin: [], boardedTripKeys: [])
        let round0Starts: [Reached] = [origin] + (walkNeighbors[query.origin] ?? []).map { edge in
            let other = edge.from == query.origin ? edge.to : edge.from
            return Reached(
                stop: other,
                timeMin: searchStartMin + edge.walkMinutes,
                isEstimatedArrival: false,
                legsFromOrigin: [.walk(Leg.Walk(fromStop: query.origin, toStop: other, meters: edge.meters, minutes: edge.walkMinutes))],
                boardedTripKeys: []
            )
        }

        var journeys: [Journey] = []
        var round0RideReach: [Reached] = []

        for start in round0Starts {
            for reached in ridesFrom(start, segments: segments, buffer: 0, requireDifferentTripThan: nil) {
                round0RideReach.append(reached)
                if reached.stop == query.destination {
                    journeys.append(toJourney(reached))
                } else {
                    for edge in walkNeighbors[reached.stop] ?? [] {
                        let other = edge.from == reached.stop ? edge.to : edge.from
                        if other == query.destination {
                            let walkLeg = Leg.Walk(fromStop: reached.stop, toStop: other, meters: edge.meters, minutes: edge.walkMinutes)
                            let extended = Reached(stop: other, timeMin: reached.timeMin + edge.walkMinutes, isEstimatedArrival: reached.isEstimatedArrival, legsFromOrigin: reached.legsFromOrigin + [.walk(walkLeg)], boardedTripKeys: reached.boardedTripKeys)
                            journeys.append(toJourney(extended))
                        }
                    }
                }
            }
        }

        // Round 1: transfer at every round-0 ride-reachable stop, or a walk from it, onto a *different* trip.
        // The walk itself adds no buffer to the arrival time here — the transfer buffer is applied at the
        // *next boarding* check inside ridesFrom (see its cameFromWalk branch).
        var round1StartsRaw = round0RideReach
        for reached in round0RideReach {
            for edge in walkNeighbors[reached.stop] ?? [] {
                let other = edge.from == reached.stop ? edge.to : edge.from
                let walkLeg = Leg.Walk(fromStop: reached.stop, toStop: other, meters: edge.meters, minutes: edge.walkMinutes)
                round1StartsRaw.append(Reached(stop: other, timeMin: reached.timeMin + edge.walkMinutes, isEstimatedArrival: reached.isEstimatedArrival, legsFromOrigin: reached.legsFromOrigin + [.walk(walkLeg)], boardedTripKeys: reached.boardedTripKeys))
            }
        }
        // In arrive-before mode, a stop already reached past the deadline can only produce
        // further journeys that are also past it (time only moves forward) - prune it here.
        let round1Starts: [Reached]
        if let deadline = query.arriveBeforeMin {
            round1Starts = round1StartsRaw.filter { $0.timeMin <= deadline }
        } else {
            round1Starts = round1StartsRaw
        }

        for start in round1Starts {
            let buffer = start.isEstimatedArrival ? bufferSameStopEstimated : bufferSameStopTranscribed
            for reached in ridesFrom(start, segments: segments, buffer: buffer, requireDifferentTripThan: start.boardedTripKeys) {
                if reached.stop == query.destination {
                    journeys.append(toJourney(reached))
                } else {
                    for edge in walkNeighbors[reached.stop] ?? [] {
                        let other = edge.from == reached.stop ? edge.to : edge.from
                        if other == query.destination {
                            let walkLeg = Leg.Walk(fromStop: reached.stop, toStop: other, meters: edge.meters, minutes: edge.walkMinutes)
                            let extended = Reached(stop: other, timeMin: reached.timeMin + edge.walkMinutes, isEstimatedArrival: reached.isEstimatedArrival, legsFromOrigin: reached.legsFromOrigin + [.walk(walkLeg)], boardedTripKeys: reached.boardedTripKeys)
                            journeys.append(toJourney(extended))
                        }
                    }
                }
            }
        }

        if let deadline = query.arriveBeforeMin {
            return rankAndDedupe(journeys.filter { $0.arrivalMin <= deadline }) { deadline - $0.arrivalMin }
        } else {
            return rankAndDedupe(journeys) { $0.arrivalMin }
        }
    }

    /// From `start` (already at `Reached.stop` at `Reached.timeMin`), board every eligible trip
    /// segment and ride it forward, yielding one `Reached` per stop reachable this way (multiple
    /// boarding trips considered, not just the earliest). `buffer` is the minimum same-stop
    /// transfer buffer already in minutes (0 for round 0 / boarding from origin); when the
    /// previous leg was a `Leg.walk`, the walk-specific buffer is used instead (computed inline).
    /// `requireDifferentTripThan`, when non-nil, excludes segments whose tripKey was already
    /// ridden earlier on this path — this is what makes a round-1 transfer an actual transfer
    /// rather than a same-trip continuation, and (as a side effect) is exactly what prevents a
    /// circular route from "teleporting": every ride is a single trip's own ordered stop
    /// sequence, walked strictly forward from a real boarding index, never re-entered out of order.
    private static func ridesFrom(
        _ start: Reached,
        segments: [RideSegment],
        buffer: Int,
        requireDifferentTripThan: Set<String>?
    ) -> [Reached] {
        let cameFromWalk: Bool = {
            if case .walk = start.legsFromOrigin.last { return true }
            return false
        }()
        var results: [Reached] = []
        for segment in segments {
            if let exclude = requireDifferentTripThan, exclude.contains(segment.tripKey) { continue }
            for i in segment.stops.indices {
                guard segment.stops[i] == start.stop else { continue }
                let effectiveBuffer: Int
                if requireDifferentTripThan == nil {
                    effectiveBuffer = 0 // round 0 boarding from origin/its walk neighbor: no prior ride to buffer against
                } else if cameFromWalk {
                    effectiveBuffer = (start.isEstimatedArrival || segment.isEstimated[i]) ? bufferWalkEstimated : bufferWalkTranscribed
                } else {
                    effectiveBuffer = buffer
                }
                if segment.minutesOfDay[i] < start.timeMin + effectiveBuffer { continue }
                // maxWaitMin caps genuine mid-journey transfer waits, not the gap between the
                // query's departAfterMin and the first bus of the day.
                if requireDifferentTripThan != nil && segment.minutesOfDay[i] - start.timeMin > maxWaitMin { continue }
                guard i + 1 < segment.stops.count else { continue }
                for j in (i + 1)..<segment.stops.count {
                    let leg = Leg.Ride(
                        routeId: segment.routeId,
                        variantId: segment.variantId,
                        fromStop: segment.stops[i],
                        toStop: segment.stops[j],
                        depMin: segment.minutesOfDay[i],
                        arrMin: segment.minutesOfDay[j],
                        isEstimated: segment.isEstimated[i] || segment.isEstimated[j]
                    )
                    results.append(Reached(
                        stop: segment.stops[j],
                        timeMin: segment.minutesOfDay[j],
                        isEstimatedArrival: segment.isEstimated[j],
                        legsFromOrigin: start.legsFromOrigin + [.ride(leg)],
                        boardedTripKeys: start.boardedTripKeys.union([segment.tripKey])
                    ))
                }
            }
        }
        return results
    }

    private static func toJourney(_ reached: Reached) -> Journey {
        let legs = reached.legsFromOrigin
        guard let firstRide = legs.compactMap({ leg -> Leg.Ride? in if case .ride(let r) = leg { return r }; return nil }).first else {
            preconditionFailure("a Reached path must contain at least one Ride leg")
        }
        let departureMin: Int
        if case .ride(let first) = legs.first! {
            departureMin = first.depMin
        } else {
            departureMin = firstRide.depMin - walkMinutesBefore(legs, firstRide: firstRide)
        }
        let transferCount = legs.filter { if case .ride = $0 { return true }; return false }.count - 1
        return Journey(legs: legs, departureMin: departureMin, arrivalMin: reached.timeMin, transferCount: transferCount)
    }

    private static func walkMinutesBefore(_ legs: [Leg], firstRide: Leg.Ride) -> Int {
        var total = 0
        for leg in legs {
            if case .ride(let r) = leg, r == firstRide { break }
            if case .walk(let w) = leg { total += w.minutes }
        }
        return total
    }

    private static func buildWalkIndex(_ transfers: [TransferEdge]) -> [String: [TransferEdge]] {
        var index: [String: [TransferEdge]] = [:]
        for edge in transfers {
            index[edge.from, default: []].append(edge)
            index[edge.to, default: []].append(edge)
        }
        return index
    }

    /// Extracts, per trip, the maximal contiguous (no gaps) runs of adjacent, time-monotonic
    /// populated departures — see "Connection extraction" and "Non-monotonic departures" in
    /// `docs/JOURNEY_PLANNER.md`. A trip with a non-monotonic hop in the middle (13 cluster-noise
    /// cases + 1 genuine mid-route-origin case in the current data) simply produces two shorter
    /// segments instead of one continuous one; a rider is conservatively assumed unable to ride
    /// straight through the break.
    private static func buildRideSegments(
        routes: [JourneyRouteData],
        dayTypes: Set<DayType>,
        month: Int,
        weekday: Int
    ) -> [RideSegment] {
        var segments: [RideSegment] = []
        for route in routes {
            let stopsById = Dictionary(uniqueKeysWithValues: route.stops.map { ($0.id, $0) })
            let variantsById = Dictionary(uniqueKeysWithValues: route.variants.map { ($0.id, $0) })
            for section in route.timetables {
                guard dayTypes.contains(section.dayType) else { continue }
                guard let variant = variantsById[section.variantId] else { continue }
                let physicalSeq = variant.stopSequence.map { stopsById[$0]?.physicalStopId ?? $0 }
                let estimatedSeq = variant.stopSequence.map { stopsById[$0]?.isEstimated ?? false }

                for (tripIndex, trip) in section.trips.enumerated() {
                    let tripKey = "\(route.routeId):\(section.variantId):\(section.dayType.rawValue):\(tripIndex)"
                    var runStops: [String] = []
                    var runMinutes: [Int] = []
                    var runEstimated: [Bool] = []

                    func flush() {
                        if runStops.count >= 2 {
                            segments.append(RideSegment(routeId: route.routeId, variantId: section.variantId, tripKey: tripKey, stops: runStops, minutesOfDay: runMinutes, isEstimated: runEstimated))
                        }
                        runStops = []
                        runMinutes = []
                        runEstimated = []
                    }

                    for i in trip.departures.indices {
                        guard let dep = trip.departures[i], i < physicalSeq.count, dep.season.runsIn(month: month, weekday: weekday) else {
                            // skipped stop, out-of-season departure, or out-of-range index: breaks any in-progress run
                            flush()
                            continue
                        }
                        if let lastMinute = runMinutes.last, dep.minutesOfDay < lastMinute {
                            // non-monotonic hop: end the current run, start a fresh one at this stop
                            flush()
                        }
                        runStops.append(physicalSeq[i])
                        runMinutes.append(dep.minutesOfDay)
                        runEstimated.append(estimatedSeq[i])
                    }
                    flush()
                }
            }
        }
        return segments
    }

    /// Ranks by `primaryKey` ascending (earliest arrival in depart-after mode, closeness to the
    /// deadline in arrive-before mode — see `findJourneys`), then fewest transfers, then latest
    /// departure; collapses to the best journey per distinct route/leg-kind pattern, drops any
    /// journey dominated by another (see `strictlyDominates` - this is what filters out "walk to
    /// a nearby stop the direct bus already passes through" alternatives that are no better on
    /// any axis), then returns the top 3 non-dominated patterns.
    private static func rankAndDedupe(_ journeys: [Journey], primaryKey: (Journey) -> Int) -> [Journey] {
        func pattern(_ j: Journey) -> String {
            j.legs.map { leg -> String in
                switch leg {
                case .ride(let r): return "R:\(r.routeId):\(r.variantId)"
                case .walk: return "W"
                }
            }.joined(separator: "|")
        }
        func better(_ a: Journey, _ b: Journey) -> Journey {
            if primaryKey(a) != primaryKey(b) { return primaryKey(a) < primaryKey(b) ? a : b }
            if a.transferCount != b.transferCount { return a.transferCount < b.transferCount ? a : b }
            return a.departureMin >= b.departureMin ? a : b
        }
        var bestPerPattern: [String: Journey] = [:]
        for j in journeys {
            let key = pattern(j)
            bestPerPattern[key] = bestPerPattern[key].map { better($0, j) } ?? j
        }

        let candidates = Array(bestPerPattern.values)
        let nonDominated = candidates.filter { candidate in
            !candidates.contains { other in other != candidate && strictlyDominates(other, candidate, primaryKey: primaryKey) }
        }

        return nonDominated.sorted { a, b in
            if primaryKey(a) != primaryKey(b) { return primaryKey(a) < primaryKey(b) }
            if a.transferCount != b.transferCount { return a.transferCount < b.transferCount }
            return a.departureMin > b.departureMin
        }.prefix(3).map { $0 }
    }

    /// True if `a` is never worse than `b` on `primaryKey`/transfers/departure and strictly
    /// better on at least one - i.e. `b` offers no genuine trade-off and shouldn't be shown
    /// alongside `a`.
    private static func strictlyDominates(_ a: Journey, _ b: Journey, primaryKey: (Journey) -> Int) -> Bool {
        let neverWorse = primaryKey(a) <= primaryKey(b) && a.transferCount <= b.transferCount && a.departureMin >= b.departureMin
        let strictlyBetter = primaryKey(a) < primaryKey(b) || a.transferCount < b.transferCount || a.departureMin > b.departureMin
        return neverWorse && strictlyBetter
    }
}
