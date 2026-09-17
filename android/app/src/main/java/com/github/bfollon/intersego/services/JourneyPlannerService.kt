/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.data.JourneyRouteData
import com.github.bfollon.intersego.data.Leg
import com.github.bfollon.intersego.data.TransferEdge
import java.time.Month

/**
 * A journey search request. [dayTypes], [month] and [weekday] are resolved by the caller
 * (via [TimetableQueryUtils.dayTypesForDate] and the query date) rather than computed here, so
 * this whole service stays pure Kotlin with no Android/Calendar dependency and is trivially
 * unit-testable on the JVM.
 *
 * Exactly one of two search modes applies: if [arriveBeforeMin] is set, it takes over — the
 * whole day is searched (regardless of [departAfterMin]) for journeys arriving at or before it,
 * ranked by closeness to that deadline first. Otherwise [departAfterMin] applies as before,
 * ranked by earliest arrival first. See "Arrive-before mode" in `docs/JOURNEY_PLANNER.md`.
 *
 * @param origin physicalStopId
 * @param destination physicalStopId
 * @param weekday java.util.Calendar.DAY_OF_WEEK value (Sun=1…Sat=7), for MON_FRI_ONLY/FRI_ONLY seasonal filtering
 * @param departAfterMin minutes-of-day; only journeys departing at or after this are returned (ignored when [arriveBeforeMin] is set)
 * @param arriveBeforeMin minutes-of-day; when set, switches to arrive-before mode (see above)
 * @param nowMin minutes-of-day for the current moment, only when the query date is today; used as a floor
 * in arrive-before mode so already-departed buses aren't offered (ignored otherwise, since [departAfterMin]
 * already carries "now" for today via the caller's default)
 */
data class JourneyQuery(
    val origin: String,
    val destination: String,
    val dayTypes: Set<DayType>,
    val month: Month,
    val weekday: Int,
    val departAfterMin: Int,
    val arriveBeforeMin: Int? = null,
    val nowMin: Int? = null,
)

/**
 * Journey search, per `docs/JOURNEY_PLANNER.md`. Pure logic, no Android dependencies — takes
 * already-loaded route/transfer data and returns a ranked, deduplicated list of journeys.
 *
 * Implements a 2-round bounded Connection Scan (round 0 = direct, round 1 = one transfer),
 * justified by the Epic 0 spike's "never more than 1 transfer" result (see the spec doc).
 */
object JourneyPlannerService {

    // Defaults for the [findJourneys] parameters below; user-tunable via TripPlannerPrefs
    // (see JourneySearchCoordinator, the real caller) — kept here as fallbacks for direct/test callers.
    const val DEFAULT_MAX_WAIT_MIN = 90 // ignore boarding opportunities requiring an unreasonably long wait
    // Neither "transcribed" (official PDF) nor in-app estimated times are a live feed, so both
    // get the same conservative default buffer.
    const val DEFAULT_BUFFER_SAME_STOP_TRANSCRIBED = 15
    const val DEFAULT_BUFFER_SAME_STOP_ESTIMATED = 15
    const val DEFAULT_BUFFER_WALK_TRANSCRIBED = 15
    const val DEFAULT_BUFFER_WALK_ESTIMATED = 15

    // Floor buffer used by [findJourneysWithTightTransfers]'s relaxed second pass — 1 minute
    // (not 0) so a "connection" isn't literally simultaneous with the arrival it depends on.
    private const val TIGHT_TRANSFER_BUFFER_FLOOR = 1
    const val MAX_TIGHT_TRANSFER_RESULTS = 3

    /** One physically contiguous, time-monotonic run of stops within a single trip. See [buildRideSegments]. */
    private data class RideSegment(
        val routeId: String,
        val variantId: String,
        val tripKey: String, // unique per (routeId, variantId, dayType, trip index) — identifies "the same vehicle"
        val stops: List<String>,
        val minutesOfDay: List<Int>,
        val isEstimated: List<Boolean>,
    )

    /** A finished [Journey] paired with the set of underlying trips (vehicles) it actually rides -
     * see [rankAndDedupe]'s dedup key, which groups by this rather than by [Journey.legs]' route
     * IDs so that two journeys boarding the exact same trip(s) but alighting/transferring at a
     * different shared stop collapse together, while two journeys that simply happen to use the
     * same *route* at different times of day do not. [hasTightTransfer] is only ever set by
     * [findJourneysWithTightTransfers]'s tagging pass — see [Reached.hasTightTransfer]. */
    private data class Candidate(val journey: Journey, val tripKeys: Set<String>, val hasTightTransfer: Boolean = false)

    /** A stop reached during the search, with enough context to compute the next leg's buffer and to reconstruct legs.
     * [hasTightTransfer] is true once any mid-journey transfer on this path cleared only the relaxed
     * ([TIGHT_TRANSFER_BUFFER_FLOOR]) buffer, not the real configured one — see [ridesFrom]'s `tagBuffer`
     * params, only ever passed by [findJourneysWithTightTransfers]. Always false for [findJourneys]. */
    private data class Reached(
        val stop: String,
        val timeMin: Int,
        val isEstimatedArrival: Boolean,
        val legsFromOrigin: List<Leg>,
        val boardedTripKeys: Set<String>, // trips already ridden on this path — a round-1 transfer must use a different one
        val hasTightTransfer: Boolean = false,
    )

    /** Result of [findJourneysWithTightTransfers]: the normal, always-usable options plus a
     * smaller set of additional options that only clear a transfer with a below-recommended
     * margin — surfaced separately so a user who'd otherwise see no results (or fewer than they
     * might accept) knows they exist, rather than being silently dropped. */
    data class JourneySearchResult(val journeys: List<Journey>, val tightTransferJourneys: List<Journey>)

    fun findJourneys(
        routes: List<JourneyRouteData>,
        transfers: List<TransferEdge>,
        query: JourneyQuery,
        maxWaitMin: Int = DEFAULT_MAX_WAIT_MIN,
        bufferSameStopTranscribed: Int = DEFAULT_BUFFER_SAME_STOP_TRANSCRIBED,
        bufferSameStopEstimated: Int = DEFAULT_BUFFER_SAME_STOP_ESTIMATED,
        bufferWalkTranscribed: Int = DEFAULT_BUFFER_WALK_TRANSCRIBED,
        bufferWalkEstimated: Int = DEFAULT_BUFFER_WALK_ESTIMATED,
    ): List<Journey> {
        val candidates = searchCandidates(
            routes, transfers, query, maxWaitMin,
            bufferSameStopTranscribed, bufferSameStopEstimated, bufferWalkTranscribed, bufferWalkEstimated,
        )
        return rankFinal(candidates, query.arriveBeforeMin)
    }

    /**
     * As [findJourneys], but also returns a second, capped list of journeys whose only viable
     * mid-journey transfer has a margin strictly between [TIGHT_TRANSFER_BUFFER_FLOOR] and the
     * configured buffer — i.e. journeys the normal search silently excludes. A single search pass
     * gates on the relaxed floor buffer (so it finds a strict superset of the normal results) and
     * tags each candidate with whether it only cleared that floor, not the real configured buffer
     * (see [ridesFrom]'s `tagBuffer` params) — candidates are then partitioned by that tag, rather
     * than diffing two separately-ranked/top-3-capped result lists, which would wrongly surface
     * plain normal-buffer-compliant candidates that simply didn't make the normal top 3. See
     * "Viajes con transbordos ajustados" in docs/JOURNEY_PLANNER.md.
     */
    fun findJourneysWithTightTransfers(
        routes: List<JourneyRouteData>,
        transfers: List<TransferEdge>,
        query: JourneyQuery,
        maxWaitMin: Int = DEFAULT_MAX_WAIT_MIN,
        bufferSameStopTranscribed: Int = DEFAULT_BUFFER_SAME_STOP_TRANSCRIBED,
        bufferSameStopEstimated: Int = DEFAULT_BUFFER_SAME_STOP_ESTIMATED,
        bufferWalkTranscribed: Int = DEFAULT_BUFFER_WALK_TRANSCRIBED,
        bufferWalkEstimated: Int = DEFAULT_BUFFER_WALK_ESTIMATED,
    ): JourneySearchResult {
        val allCandidates = searchCandidates(
            routes, transfers, query, maxWaitMin,
            bufferSameStopTranscribed = TIGHT_TRANSFER_BUFFER_FLOOR,
            bufferSameStopEstimated = TIGHT_TRANSFER_BUFFER_FLOOR,
            bufferWalkTranscribed = TIGHT_TRANSFER_BUFFER_FLOOR,
            bufferWalkEstimated = TIGHT_TRANSFER_BUFFER_FLOOR,
            tagSameStopTranscribed = bufferSameStopTranscribed,
            tagSameStopEstimated = bufferSameStopEstimated,
            tagWalkTranscribed = bufferWalkTranscribed,
            tagWalkEstimated = bufferWalkEstimated,
        )
        val normalCandidates = allCandidates.filter { !it.hasTightTransfer }
        val tightCandidates = allCandidates.filter { it.hasTightTransfer }

        val normalJourneys = rankFinal(normalCandidates, query.arriveBeforeMin)
        val tightJourneys = rankFinal(tightCandidates, query.arriveBeforeMin).take(MAX_TIGHT_TRANSFER_RESULTS)

        return JourneySearchResult(normalJourneys, tightJourneys)
    }

    private fun searchCandidates(
        routes: List<JourneyRouteData>,
        transfers: List<TransferEdge>,
        query: JourneyQuery,
        maxWaitMin: Int,
        bufferSameStopTranscribed: Int,
        bufferSameStopEstimated: Int,
        bufferWalkTranscribed: Int,
        bufferWalkEstimated: Int,
        // When non-null (only passed by findJourneysWithTightTransfers), the *gate* buffers above
        // are expected to be the relaxed floor, and a candidate is tagged hasTightTransfer=true if
        // its transfer margin falls short of these (the real configured buffers) instead — see
        // ridesFrom. findJourneys never passes these, so its candidates are never tagged.
        tagSameStopTranscribed: Int? = null,
        tagSameStopEstimated: Int? = null,
        tagWalkTranscribed: Int? = null,
        tagWalkEstimated: Int? = null,
    ): List<Candidate> {
        val segments = buildRideSegments(routes, query.dayTypes, query.month, query.weekday)
        val walkNeighbors = buildWalkIndex(transfers)

        // Arrive-before mode searches the whole day (any bus, however early, is a candidate) and
        // ranks by closeness to the deadline afterwards - departAfterMin doesn't apply. It's still
        // floored at "now" (query.nowMin) when the query date is today, so already-departed buses
        // aren't offered as boarding candidates.
        val searchStartMin = if (query.arriveBeforeMin != null) (query.nowMin ?: 0) else query.departAfterMin

        // Round 0: origin (+ its immediate walk neighbors) as boarding points, no buffer (nothing to transfer from yet).
        val origin = Reached(query.origin, searchStartMin, isEstimatedArrival = false, legsFromOrigin = emptyList(), boardedTripKeys = emptySet())
        val round0Starts = listOf(origin) + walkNeighbors[query.origin].orEmpty().map { edge ->
            val other = if (edge.from == query.origin) edge.to else edge.from
            Reached(
                stop = other,
                timeMin = searchStartMin + edge.walkMinutes,
                isEstimatedArrival = false,
                legsFromOrigin = listOf(Leg.Walk(query.origin, other, edge.meters, edge.walkMinutes)),
                boardedTripKeys = emptySet(),
            )
        }

        val journeys = mutableListOf<Candidate>()
        val round0RideReach = mutableListOf<Reached>()

        for (start in round0Starts) {
            for (reached in ridesFrom(start, segments, buffer = 0, requireDifferentTripThan = null, bufferWalkTranscribed, bufferWalkEstimated, maxWaitMin)) {
                round0RideReach += reached
                if (reached.stop == query.destination) {
                    journeys += Candidate(toJourney(reached), reached.boardedTripKeys, reached.hasTightTransfer)
                } else {
                    walkNeighbors[reached.stop]?.forEach { edge ->
                        val other = if (edge.from == reached.stop) edge.to else edge.from
                        if (other == query.destination) {
                            val walkLeg = Leg.Walk(reached.stop, other, edge.meters, edge.walkMinutes)
                            val arrived = reached.copy(stop = other, timeMin = reached.timeMin + edge.walkMinutes, legsFromOrigin = reached.legsFromOrigin + walkLeg)
                            journeys += Candidate(toJourney(arrived), arrived.boardedTripKeys, arrived.hasTightTransfer)
                        }
                    }
                }
            }
        }

        // Round 1: transfer at every round-0 ride-reachable stop, or a walk from it, onto a *different* trip.
        // The walk itself adds no buffer to the arrival time here — the transfer buffer is
        // applied at the *next boarding* check inside ridesFrom (see its `cameFromWalk` branch).
        val round1StartsRaw = round0RideReach + round0RideReach.flatMap { reached ->
            walkNeighbors[reached.stop].orEmpty().map { edge ->
                val other = if (edge.from == reached.stop) edge.to else edge.from
                val walkLeg = Leg.Walk(reached.stop, other, edge.meters, edge.walkMinutes)
                reached.copy(
                    stop = other,
                    timeMin = reached.timeMin + edge.walkMinutes,
                    legsFromOrigin = reached.legsFromOrigin + walkLeg,
                )
            }
        }
        // In arrive-before mode, a stop already reached past the deadline can only produce
        // further journeys that are also past it (time only moves forward) - prune it here.
        val round1Starts = query.arriveBeforeMin?.let { deadline -> round1StartsRaw.filter { it.timeMin <= deadline } } ?: round1StartsRaw

        for (start in round1Starts) {
            val buffer = if (start.isEstimatedArrival) bufferSameStopEstimated else bufferSameStopTranscribed
            val tagBuffer = if (start.isEstimatedArrival) tagSameStopEstimated else tagSameStopTranscribed
            // Note: the *walk* buffer (vs. same-stop buffer) is selected per-segment inside ridesFrom
            // when the previous leg was a Walk — see there.
            for (reached in ridesFrom(
                start, segments, buffer = buffer, requireDifferentTripThan = start.boardedTripKeys,
                bufferWalkTranscribed, bufferWalkEstimated, maxWaitMin,
                tagBuffer, tagWalkTranscribed, tagWalkEstimated,
            )) {
                if (reached.stop == query.destination) {
                    journeys += Candidate(toJourney(reached), reached.boardedTripKeys, reached.hasTightTransfer)
                } else {
                    walkNeighbors[reached.stop]?.forEach { edge ->
                        val other = if (edge.from == reached.stop) edge.to else edge.from
                        if (other == query.destination) {
                            val walkLeg = Leg.Walk(reached.stop, other, edge.meters, edge.walkMinutes)
                            val arrived = reached.copy(stop = other, timeMin = reached.timeMin + edge.walkMinutes, legsFromOrigin = reached.legsFromOrigin + walkLeg)
                            journeys += Candidate(toJourney(arrived), arrived.boardedTripKeys, arrived.hasTightTransfer)
                        }
                    }
                }
            }
        }

        return journeys
    }

    /** Final ranking/dedup/deadline-filter step shared by [findJourneys] and
     * [findJourneysWithTightTransfers] — see the inline comment below for why arrive-before mode
     * ranks by latest-departure-first rather than closeness to the deadline. */
    private fun rankFinal(candidates: List<Candidate>, deadline: Int?): List<Journey> {
        return if (deadline != null) {
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
            rankAndDedupe(candidates.filter { it.journey.arrivalMin <= deadline }) { listOf(-it.departureMin, it.transferCount, it.arrivalMin, it.totalWalkMinutes()) }
        } else {
            rankAndDedupe(candidates) { listOf(it.arrivalMin, it.transferCount, -it.departureMin, it.totalWalkMinutes()) }
        }
    }

    /**
     * From [start] (already at [Reached.stop] at [Reached.timeMin]), board every eligible trip
     * segment and ride it forward, yielding one [Reached] per stop reachable this way (multiple
     * boarding trips considered, not just the earliest). [buffer] is the minimum same-stop
     * transfer buffer already in minutes (0 for round 0 / boarding from origin); when the
     * previous leg was a [Leg.Walk], the walk-specific buffer is used instead (computed inline).
     * [requireDifferentTripThan], when non-null, excludes segments whose tripKey was already
     * ridden earlier on this path — this is what makes a round-1 transfer an actual transfer
     * rather than a same-trip continuation, and (as a side effect) is exactly what prevents a
     * circular route from "teleporting": every ride is a single trip's own ordered stop
     * sequence, walked strictly forward from a real boarding index, never re-entered out of order.
     *
     * [tagBuffer]/[tagBufferWalkTranscribed]/[tagBufferWalkEstimated], when non-null (only passed
     * by [findJourneysWithTightTransfers]), are the *real* configured buffers — selected via the
     * exact same same-stop/walk/estimated branching as [buffer] itself — used only to mark the
     * resulting [Reached.hasTightTransfer] when the actual margin clears [buffer] (the relaxed
     * floor gating the search) but not this stricter one. They never affect which connections are
     * found, only how they're tagged.
     */
    private fun ridesFrom(
        start: Reached,
        segments: List<RideSegment>,
        buffer: Int,
        requireDifferentTripThan: Set<String>?,
        bufferWalkTranscribed: Int,
        bufferWalkEstimated: Int,
        maxWaitMin: Int,
        tagBuffer: Int? = null,
        tagBufferWalkTranscribed: Int? = null,
        tagBufferWalkEstimated: Int? = null,
    ): List<Reached> {
        val cameFromWalk = start.legsFromOrigin.lastOrNull() is Leg.Walk
        val results = mutableListOf<Reached>()
        for (segment in segments) {
            if (requireDifferentTripThan != null && segment.tripKey in requireDifferentTripThan) continue
            for (i in segment.stops.indices) {
                if (segment.stops[i] != start.stop) continue
                val effectiveBuffer = when {
                    requireDifferentTripThan == null -> 0 // round 0 boarding from origin/its walk neighbor: no prior ride to buffer against
                    cameFromWalk -> if (start.isEstimatedArrival || segment.isEstimated[i]) bufferWalkEstimated else bufferWalkTranscribed
                    else -> buffer
                }
                if (segment.minutesOfDay[i] < start.timeMin + effectiveBuffer) continue
                // maxWaitMin caps genuine mid-journey transfer waits, not the gap between the
                // query's departAfterMin and the first bus of the day — a query for "after 00:00"
                // legitimately waiting until the morning's first departure is not a "long transfer".
                if (requireDifferentTripThan != null && segment.minutesOfDay[i] - start.timeMin > maxWaitMin) continue
                val isTightThisHop = requireDifferentTripThan != null && run {
                    val effectiveTagBuffer = if (cameFromWalk) {
                        if (start.isEstimatedArrival || segment.isEstimated[i]) tagBufferWalkEstimated else tagBufferWalkTranscribed
                    } else {
                        tagBuffer
                    }
                    effectiveTagBuffer != null && segment.minutesOfDay[i] < start.timeMin + effectiveTagBuffer
                }
                for (j in i + 1 until segment.stops.size) {
                    val leg = Leg.Ride(
                        routeId = segment.routeId,
                        variantId = segment.variantId,
                        fromStop = segment.stops[i],
                        toStop = segment.stops[j],
                        depMin = segment.minutesOfDay[i],
                        arrMin = segment.minutesOfDay[j],
                        isEstimated = segment.isEstimated[i] || segment.isEstimated[j],
                    )
                    results += Reached(
                        stop = segment.stops[j],
                        timeMin = segment.minutesOfDay[j],
                        isEstimatedArrival = segment.isEstimated[j],
                        legsFromOrigin = start.legsFromOrigin + leg,
                        boardedTripKeys = start.boardedTripKeys + segment.tripKey,
                        hasTightTransfer = start.hasTightTransfer || isTightThisHop,
                    )
                }
            }
        }
        return results
    }

    private fun toJourney(reached: Reached): Journey {
        val legs = reached.legsFromOrigin
        val firstRide = legs.filterIsInstance<Leg.Ride>().first()
        val departureMin = when (val first = legs.first()) {
            is Leg.Ride -> first.depMin
            is Leg.Walk -> firstRide.depMin - walkMinutesBefore(legs, firstRide)
        }
        val transferCount = legs.count { it is Leg.Ride } - 1
        return Journey(legs = legs, departureMin = departureMin, arrivalMin = reached.timeMin, transferCount = transferCount)
    }

    private fun Journey.totalWalkMinutes(): Int = legs.filterIsInstance<Leg.Walk>().sumOf { it.minutes }

    private fun walkMinutesBefore(legs: List<Leg>, firstRide: Leg.Ride): Int {
        var total = 0
        for (leg in legs) {
            if (leg === firstRide) break
            if (leg is Leg.Walk) total += leg.minutes
        }
        return total
    }

    private fun buildWalkIndex(transfers: List<TransferEdge>): Map<String, List<TransferEdge>> {
        val index = HashMap<String, MutableList<TransferEdge>>()
        for (edge in transfers) {
            index.getOrPut(edge.from) { mutableListOf() }.add(edge)
            index.getOrPut(edge.to) { mutableListOf() }.add(edge)
        }
        return index
    }

    /**
     * Extracts, per trip, the time-monotonic runs of populated departures — see "Connection
     * extraction" and "Non-monotonic departures" in `docs/JOURNEY_PLANNER.md`. A `null` entry
     * (a stop this trip doesn't pick up at, e.g. a school-only stop outside term) does not end
     * the run: the trip is still physically moving, so the ride bridges straight from the last
     * populated stop to the next one. Only a non-monotonic hop (13 cluster-noise cases + 1
     * genuine mid-route-origin case in the current data) ends a run and starts a fresh one — a
     * rider is conservatively assumed unable to ride straight through that kind of break.
     */
    private fun buildRideSegments(
        routes: List<JourneyRouteData>,
        dayTypes: Set<DayType>,
        month: Month,
        weekday: Int,
    ): List<RideSegment> {
        val segments = mutableListOf<RideSegment>()
        for (route in routes) {
            val stopsById = route.stops.associateBy { it.id }
            val variantsById = route.variants.associateBy { it.id }
            for (section in route.timetables) {
                if (section.dayType !in dayTypes) continue
                val variant = variantsById[section.variantId] ?: continue
                val physicalSeq = variant.stopSequence.map { stopsById[it]?.physicalStopId ?: it }
                val estimatedSeq = variant.stopSequence.map { stopsById[it]?.isEstimated ?: false }

                section.trips.forEachIndexed { tripIndex, trip ->
                    val tripKey = "${route.routeId}:${section.variantId}:${section.dayType}:$tripIndex"
                    var runStops = mutableListOf<String>()
                    var runMinutes = mutableListOf<Int>()
                    var runEstimated = mutableListOf<Boolean>()

                    fun flush() {
                        if (runStops.size >= 2) {
                            segments += RideSegment(route.routeId, section.variantId, tripKey, runStops, runMinutes, runEstimated)
                        }
                        runStops = mutableListOf()
                        runMinutes = mutableListOf()
                        runEstimated = mutableListOf()
                    }

                    for (i in trip.departures.indices) {
                        val dep = trip.departures[i]
                        if (dep == null || !dep.season.runsIn(month, weekday) || i >= physicalSeq.size) {
                            // skipped stop, out-of-season departure, or out-of-range index: this
                            // trip doesn't stop here, but keeps moving — bridge over it rather
                            // than ending the run (see docstring above)
                            continue
                        }
                        val lastMinute = runMinutes.lastOrNull()
                        if (lastMinute != null && dep.minutesOfDay < lastMinute) {
                            // non-monotonic hop: end the current run, start a fresh one at this stop
                            flush()
                        }
                        runStops.add(physicalSeq[i])
                        runMinutes.add(dep.minutesOfDay)
                        runEstimated.add(estimatedSeq[i])
                    }
                    flush()
                }
            }
        }
        return segments
    }

    /** Ranks by [rankKey] lexicographically — each element ascending, i.e. lower is always
     * better (callers negate fields where "higher is better," like departure time, before
     * passing them in) — see [findJourneys] for what the two modes' keys are and why they
     * differ.
     *
     * Dominance filtering runs first, over *every* candidate (see [strictlyDominates] — this is
     * what filters out "walk to a nearby stop the direct bus already passes through"
     * alternatives that are no better on any axis). Sorting and taking the top 3 then enforces at
     * most one journey per underlying trip-combination ([Candidate.tripKeys]) — e.g. boarding the
     * same 10:00 bus and getting off at whichever of several shared stops connects to the same
     * onward bus is one option, not three — while still letting genuinely different departures
     * that happen to use the same *route* each earn their own slot. See docs/JOURNEY_PLANNER.md. */
    private fun rankAndDedupe(candidates: List<Candidate>, rankKey: (Journey) -> List<Int>): List<Journey> {
        fun compare(a: Candidate, b: Candidate): Int {
            val ka = rankKey(a.journey)
            val kb = rankKey(b.journey)
            return ka.indices.firstNotNullOfOrNull { i -> ka[i].compareTo(kb[i]).takeIf { it != 0 } } ?: 0
        }

        val nonDominated = candidates.filterNot { candidate ->
            candidates.any { other -> other !== candidate && strictlyDominates(other, candidate, rankKey) }
        }

        val sorted = nonDominated.sortedWith(::compare)
        val result = mutableListOf<Journey>()
        val usedTripKeys = mutableSetOf<Set<String>>()
        for (candidate in sorted) {
            if (result.size >= 3) break
            if (usedTripKeys.add(candidate.tripKeys)) {
                result += candidate.journey
            }
        }
        return result
    }

    /** True if every element of [rankKey](a.journey) is <= the corresponding element of
     * [rankKey](b.journey), and strictly less on at least one — i.e. [b] offers no genuine
     * trade-off and shouldn't be shown alongside [a]. */
    private fun strictlyDominates(a: Candidate, b: Candidate, rankKey: (Journey) -> List<Int>): Boolean {
        val ka = rankKey(a.journey)
        val kb = rankKey(b.journey)
        val neverWorse = ka.indices.all { ka[it] <= kb[it] }
        val strictlyBetter = ka.indices.any { ka[it] < kb[it] }
        return neverWorse && strictlyBetter
    }
}
