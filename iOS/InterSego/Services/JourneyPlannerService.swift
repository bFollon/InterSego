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
///   - nowMin: minutes-of-day for the current moment, only when the query date is today; used as a floor
///     in arrive-before mode so already-departed buses aren't offered (ignored otherwise, since `departAfterMin`
///     already carries "now" for today via the caller's default)
struct JourneyQuery {
    let origin: String
    let destination: String
    let dayTypes: Set<DayType>
    let month: Int
    let weekday: Int
    let departAfterMin: Int
    var arriveBeforeMin: Int? = nil
    var nowMin: Int? = nil
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

    // Defaults for the `findJourneys` parameters below; user-tunable via TripPlannerPrefs
    // (see JourneySearchCoordinator, the real caller) — kept here as fallbacks for direct/test callers.
    static let defaultMaxWaitMin = 90 // ignore boarding opportunities requiring an unreasonably long wait
    // Neither "transcribed" (official PDF) nor in-app estimated times are a live feed, so both
    // get the same conservative default buffer.
    static let defaultBufferSameStopTranscribed = 15
    static let defaultBufferSameStopEstimated = 15
    static let defaultBufferWalkTranscribed = 15
    static let defaultBufferWalkEstimated = 15

    // Floor buffer used by `findJourneysWithTightTransfers`'s relaxed second pass — 1 minute
    // (not 0) so a "connection" isn't literally simultaneous with the arrival it depends on.
    private static let tightTransferBufferFloor = 1
    static let maxTightTransferResults = 3

    /// One physically contiguous, time-monotonic run of stops within a single trip. See `buildRideSegments`.
    private struct RideSegment {
        let routeId: String
        let variantId: String
        let tripKey: String // unique per (routeId, variantId, dayType, trip index) — identifies "the same vehicle"
        let stops: [String]
        let minutesOfDay: [Int]
        let isEstimated: [Bool]
    }

    /// A finished `Journey` paired with the set of underlying trips (vehicles) it actually rides -
    /// see `rankAndDedupe`'s dedup key, which groups by this rather than by `Journey.legs`' route
    /// IDs so that two journeys boarding the exact same trip(s) but alighting/transferring at a
    /// different shared stop collapse together, while two journeys that simply happen to use the
    /// same *route* at different times of day do not. `hasTightTransfer` is only ever set by
    /// `findJourneysWithTightTransfers`'s tagging pass — see `Reached.hasTightTransfer`.
    private struct Candidate {
        let journey: Journey
        let tripKeys: Set<String>
        var hasTightTransfer: Bool = false
    }

    /// A stop reached during the search, with enough context to compute the next leg's buffer and to reconstruct legs.
    /// `hasTightTransfer` is true once any mid-journey transfer on this path cleared only the relaxed
    /// (`tightTransferBufferFloor`) buffer, not the real configured one — see `ridesFrom`'s `tagBuffer`
    /// params, only ever passed by `findJourneysWithTightTransfers`. Always false for `findJourneys`.
    private struct Reached {
        let stop: String
        let timeMin: Int
        let isEstimatedArrival: Bool
        let legsFromOrigin: [Leg]
        let boardedTripKeys: Set<String> // trips already ridden on this path — a round-1 transfer must use a different one
        var hasTightTransfer: Bool = false
    }

    /// Result of `findJourneysWithTightTransfers`: the normal, always-usable options plus a
    /// smaller set of additional options that only clear a transfer with a below-recommended
    /// margin — surfaced separately so a user who'd otherwise see no results (or fewer than they
    /// might accept) knows they exist, rather than being silently dropped.
    struct JourneySearchResult {
        let journeys: [Journey]
        let tightTransferJourneys: [Journey]
    }

    static func findJourneys(
        routes: [JourneyRouteData],
        transfers: [TransferEdge],
        query: JourneyQuery,
        maxWaitMin: Int = defaultMaxWaitMin,
        bufferSameStopTranscribed: Int = defaultBufferSameStopTranscribed,
        bufferSameStopEstimated: Int = defaultBufferSameStopEstimated,
        bufferWalkTranscribed: Int = defaultBufferWalkTranscribed,
        bufferWalkEstimated: Int = defaultBufferWalkEstimated
    ) -> [Journey] {
        let candidates = searchCandidates(
            routes: routes, transfers: transfers, query: query, maxWaitMin: maxWaitMin,
            bufferSameStopTranscribed: bufferSameStopTranscribed, bufferSameStopEstimated: bufferSameStopEstimated,
            bufferWalkTranscribed: bufferWalkTranscribed, bufferWalkEstimated: bufferWalkEstimated
        )
        return rankFinal(candidates, deadline: query.arriveBeforeMin)
    }

    /// As `findJourneys`, but also returns a second, capped list of journeys whose only viable
    /// mid-journey transfer has a margin strictly between `tightTransferBufferFloor` and the
    /// configured buffer — i.e. journeys the normal search silently excludes. A single search pass
    /// gates on the relaxed floor buffer (so it finds a strict superset of the normal results) and
    /// tags each candidate with whether it only cleared that floor, not the real configured buffer
    /// (see `ridesFrom`'s `tagBuffer` params) — candidates are then partitioned by that tag, rather
    /// than diffing two separately-ranked/top-3-capped result lists, which would wrongly surface
    /// plain normal-buffer-compliant candidates that simply didn't make the normal top 3. See
    /// "Viajes con transbordos ajustados" in docs/JOURNEY_PLANNER.md.
    static func findJourneysWithTightTransfers(
        routes: [JourneyRouteData],
        transfers: [TransferEdge],
        query: JourneyQuery,
        maxWaitMin: Int = defaultMaxWaitMin,
        bufferSameStopTranscribed: Int = defaultBufferSameStopTranscribed,
        bufferSameStopEstimated: Int = defaultBufferSameStopEstimated,
        bufferWalkTranscribed: Int = defaultBufferWalkTranscribed,
        bufferWalkEstimated: Int = defaultBufferWalkEstimated
    ) -> JourneySearchResult {
        let allCandidates = searchCandidates(
            routes: routes, transfers: transfers, query: query, maxWaitMin: maxWaitMin,
            bufferSameStopTranscribed: tightTransferBufferFloor, bufferSameStopEstimated: tightTransferBufferFloor,
            bufferWalkTranscribed: tightTransferBufferFloor, bufferWalkEstimated: tightTransferBufferFloor,
            tagSameStopTranscribed: bufferSameStopTranscribed, tagSameStopEstimated: bufferSameStopEstimated,
            tagWalkTranscribed: bufferWalkTranscribed, tagWalkEstimated: bufferWalkEstimated
        )
        let normalCandidates = allCandidates.filter { !$0.hasTightTransfer }
        let tightCandidates = allCandidates.filter { $0.hasTightTransfer }

        let normalJourneys = rankFinal(normalCandidates, deadline: query.arriveBeforeMin)
        let tightJourneys = Array(rankFinal(tightCandidates, deadline: query.arriveBeforeMin).prefix(maxTightTransferResults))

        return JourneySearchResult(journeys: normalJourneys, tightTransferJourneys: tightJourneys)
    }

    private static func searchCandidates(
        routes: [JourneyRouteData],
        transfers: [TransferEdge],
        query: JourneyQuery,
        maxWaitMin: Int,
        bufferSameStopTranscribed: Int,
        bufferSameStopEstimated: Int,
        bufferWalkTranscribed: Int,
        bufferWalkEstimated: Int,
        // When non-nil (only passed by findJourneysWithTightTransfers), the *gate* buffers above
        // are expected to be the relaxed floor, and a candidate is tagged hasTightTransfer=true if
        // its transfer margin falls short of these (the real configured buffers) instead — see
        // ridesFrom. findJourneys never passes these, so its candidates are never tagged.
        tagSameStopTranscribed: Int? = nil,
        tagSameStopEstimated: Int? = nil,
        tagWalkTranscribed: Int? = nil,
        tagWalkEstimated: Int? = nil
    ) -> [Candidate] {
        let segments = buildRideSegments(routes: routes, dayTypes: query.dayTypes, month: query.month, weekday: query.weekday)
        let walkNeighbors = buildWalkIndex(transfers)

        // Arrive-before mode searches the whole day (any bus, however early, is a candidate) and
        // ranks by closeness to the deadline afterwards - departAfterMin doesn't apply. It's still
        // floored at "now" (query.nowMin) when the query date is today, so already-departed buses
        // aren't offered as boarding candidates.
        let searchStartMin = query.arriveBeforeMin != nil ? (query.nowMin ?? 0) : query.departAfterMin

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

        var journeys: [Candidate] = []
        var round0RideReach: [Reached] = []

        for start in round0Starts {
            for reached in ridesFrom(start, segments: segments, buffer: 0, requireDifferentTripThan: nil, bufferWalkTranscribed: bufferWalkTranscribed, bufferWalkEstimated: bufferWalkEstimated, maxWaitMin: maxWaitMin) {
                round0RideReach.append(reached)
                if reached.stop == query.destination {
                    journeys.append(Candidate(journey: toJourney(reached), tripKeys: reached.boardedTripKeys))
                } else {
                    for edge in walkNeighbors[reached.stop] ?? [] {
                        let other = edge.from == reached.stop ? edge.to : edge.from
                        if other == query.destination {
                            let walkLeg = Leg.Walk(fromStop: reached.stop, toStop: other, meters: edge.meters, minutes: edge.walkMinutes)
                            let extended = Reached(stop: other, timeMin: reached.timeMin + edge.walkMinutes, isEstimatedArrival: reached.isEstimatedArrival, legsFromOrigin: reached.legsFromOrigin + [.walk(walkLeg)], boardedTripKeys: reached.boardedTripKeys)
                            journeys.append(Candidate(journey: toJourney(extended), tripKeys: extended.boardedTripKeys))
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
            let tagBuffer = start.isEstimatedArrival ? tagSameStopEstimated : tagSameStopTranscribed
            for reached in ridesFrom(
                start, segments: segments, buffer: buffer, requireDifferentTripThan: start.boardedTripKeys,
                bufferWalkTranscribed: bufferWalkTranscribed, bufferWalkEstimated: bufferWalkEstimated, maxWaitMin: maxWaitMin,
                tagBuffer: tagBuffer, tagBufferWalkTranscribed: tagWalkTranscribed, tagBufferWalkEstimated: tagWalkEstimated
            ) {
                if reached.stop == query.destination {
                    journeys.append(Candidate(journey: toJourney(reached), tripKeys: reached.boardedTripKeys, hasTightTransfer: reached.hasTightTransfer))
                } else {
                    for edge in walkNeighbors[reached.stop] ?? [] {
                        let other = edge.from == reached.stop ? edge.to : edge.from
                        if other == query.destination {
                            let walkLeg = Leg.Walk(fromStop: reached.stop, toStop: other, meters: edge.meters, minutes: edge.walkMinutes)
                            let extended = Reached(stop: other, timeMin: reached.timeMin + edge.walkMinutes, isEstimatedArrival: reached.isEstimatedArrival, legsFromOrigin: reached.legsFromOrigin + [.walk(walkLeg)], boardedTripKeys: reached.boardedTripKeys, hasTightTransfer: reached.hasTightTransfer)
                            journeys.append(Candidate(journey: toJourney(extended), tripKeys: extended.boardedTripKeys, hasTightTransfer: extended.hasTightTransfer))
                        }
                    }
                }
            }
        }

        return journeys
    }

    /// Final ranking/dedup/deadline-filter step shared by `findJourneys` and
    /// `findJourneysWithTightTransfers` — see the inline comment below for why arrive-before mode
    /// ranks by latest-departure-first rather than closeness to the deadline.
    private static func rankFinal(_ candidates: [Candidate], deadline: Int?) -> [Journey] {
        if let deadline {
            // Latest departure first (least total time spent travelling/waiting), then fewest
            // transfers, then earliest arrival - NOT closeness to the deadline. Closeness alone
            // rewards riding further than necessary on the very trip that already reaches the
            // destination and walking back, since "closer to the deadline" looks better even
            // though it's strictly worse (later arrival, extra walking, same everything else).
            // Latest-departure-first sidesteps that: such a detour never departs later than just
            // getting off at the right stop, so it can only win ties by *also* arriving earlier -
            // which is never true of a walk-back detour. Least total walk time is the final
            // tie-break for the case that leaves untouched: overshooting on a bus past the real
            // transfer point, then walking back to board that *same trip* earlier upstream - which
            // ties exactly on arrival/transfers/departure (same trip, same everything) and would
            // otherwise slip through as a second, needlessly effortful "option." See
            // docs/JOURNEY_PLANNER.md.
            return rankAndDedupe(candidates.filter { $0.journey.arrivalMin <= deadline }) { [-$0.departureMin, $0.transferCount, $0.arrivalMin, totalWalkMinutes($0)] }
        } else {
            return rankAndDedupe(candidates) { [$0.arrivalMin, $0.transferCount, -$0.departureMin, totalWalkMinutes($0)] }
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
    ///
    /// `tagBuffer`/`tagBufferWalkTranscribed`/`tagBufferWalkEstimated`, when non-nil (only passed
    /// by `findJourneysWithTightTransfers`), are the *real* configured buffers — selected via the
    /// exact same same-stop/walk/estimated branching as `buffer` itself — used only to mark the
    /// resulting `Reached.hasTightTransfer` when the actual margin clears `buffer` (the relaxed
    /// floor gating the search) but not this stricter one. They never affect which connections are
    /// found, only how they're tagged.
    private static func ridesFrom(
        _ start: Reached,
        segments: [RideSegment],
        buffer: Int,
        requireDifferentTripThan: Set<String>?,
        bufferWalkTranscribed: Int,
        bufferWalkEstimated: Int,
        maxWaitMin: Int,
        tagBuffer: Int? = nil,
        tagBufferWalkTranscribed: Int? = nil,
        tagBufferWalkEstimated: Int? = nil
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
                var isTightThisHop = false
                if requireDifferentTripThan != nil {
                    let effectiveTagBuffer: Int? = cameFromWalk
                        ? ((start.isEstimatedArrival || segment.isEstimated[i]) ? tagBufferWalkEstimated : tagBufferWalkTranscribed)
                        : tagBuffer
                    if let effectiveTagBuffer, segment.minutesOfDay[i] < start.timeMin + effectiveTagBuffer {
                        isTightThisHop = true
                    }
                }
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
                        boardedTripKeys: start.boardedTripKeys.union([segment.tripKey]),
                        hasTightTransfer: start.hasTightTransfer || isTightThisHop
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

    private static func totalWalkMinutes(_ journey: Journey) -> Int {
        journey.legs.reduce(0) { total, leg in
            if case .walk(let w) = leg { return total + w.minutes }
            return total
        }
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

    /// Extracts, per trip, the time-monotonic runs of populated departures — see "Connection
    /// extraction" and "Non-monotonic departures" in `docs/JOURNEY_PLANNER.md`. A `null` entry
    /// (a stop this trip doesn't pick up at, e.g. a school-only stop outside term) does not end
    /// the run: the trip is still physically moving, so the ride bridges straight from the last
    /// populated stop to the next one. Only a non-monotonic hop (13 cluster-noise cases + 1
    /// genuine mid-route-origin case in the current data) ends a run and starts a fresh one — a
    /// rider is conservatively assumed unable to ride straight through that kind of break.
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
                            // skipped stop, out-of-season departure, or out-of-range index: this
                            // trip doesn't stop here, but keeps moving — bridge over it rather
                            // than ending the run (see docstring above)
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

    /// Ranks by `rankKey` lexicographically — each element ascending, i.e. lower is always
    /// better (callers negate fields where "higher is better," like departure time, before
    /// passing them in) — see `findJourneys` for what the two modes' keys are and why they
    /// differ.
    ///
    /// Dominance filtering runs first, over *every* candidate (see `strictlyDominates` - this is
    /// what filters out "walk to a nearby stop the direct bus already passes through"
    /// alternatives that are no better on any axis). Sorting and taking the top 3 then enforces at
    /// most one journey per underlying trip-combination (`Candidate.tripKeys`) — e.g. boarding the
    /// same 10:00 bus and getting off at whichever of several shared stops connects to the same
    /// onward bus is one option, not three — while still letting genuinely different departures
    /// that happen to use the same *route* each earn their own slot. See docs/JOURNEY_PLANNER.md.
    private static func rankAndDedupe(_ candidates: [Candidate], rankKey: (Journey) -> [Int]) -> [Journey] {
        let nonDominated = candidates.enumerated().filter { index, candidate in
            !candidates.enumerated().contains { otherIndex, other in
                otherIndex != index && strictlyDominates(other, candidate, rankKey: rankKey)
            }
        }.map { $0.element }

        let sorted = nonDominated.sorted { a, b in
            let ka = rankKey(a.journey)
            let kb = rankKey(b.journey)
            for i in ka.indices where ka[i] != kb[i] {
                return ka[i] < kb[i]
            }
            return false
        }

        var result: [Journey] = []
        var usedTripKeys: Set<Set<String>> = []
        for candidate in sorted {
            if result.count >= 3 { break }
            if usedTripKeys.insert(candidate.tripKeys).inserted {
                result.append(candidate.journey)
            }
        }
        return result
    }

    /// True if every element of `rankKey(a.journey)` is <= the corresponding element of
    /// `rankKey(b.journey)`, and strictly less on at least one - i.e. `b` offers no genuine
    /// trade-off and shouldn't be shown alongside `a`.
    private static func strictlyDominates(_ a: Candidate, _ b: Candidate, rankKey: (Journey) -> [Int]) -> Bool {
        let ka = rankKey(a.journey)
        let kb = rankKey(b.journey)
        let neverWorse = ka.indices.allSatisfy { ka[$0] <= kb[$0] }
        let strictlyBetter = ka.indices.contains { ka[$0] < kb[$0] }
        return neverWorse && strictlyBetter
    }
}
