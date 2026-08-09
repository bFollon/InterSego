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
 * @param origin physicalStopId
 * @param destination physicalStopId
 * @param weekday java.util.Calendar.DAY_OF_WEEK value (Sun=1…Sat=7), for MON_FRI_ONLY/FRI_ONLY seasonal filtering
 * @param departAfterMin minutes-of-day; only journeys departing at or after this are returned
 */
data class JourneyQuery(
    val origin: String,
    val destination: String,
    val dayTypes: Set<DayType>,
    val month: Month,
    val weekday: Int,
    val departAfterMin: Int,
)

/**
 * Journey search, per `docs/JOURNEY_PLANNER.md`. Pure logic, no Android dependencies — takes
 * already-loaded route/transfer data and returns a ranked, deduplicated list of journeys.
 *
 * Implements a 2-round bounded Connection Scan (round 0 = direct, round 1 = one transfer),
 * justified by the Epic 0 spike's "never more than 1 transfer" result (see the spec doc).
 */
object JourneyPlannerService {

    private const val MAX_WAIT_MIN = 90 // ignore boarding opportunities requiring an unreasonably long wait
    private const val BUFFER_SAME_STOP_TRANSCRIBED = 3
    private const val BUFFER_SAME_STOP_ESTIMATED = 8
    private const val BUFFER_WALK_TRANSCRIBED = 3
    private const val BUFFER_WALK_ESTIMATED = 8

    /** One physically contiguous, time-monotonic run of stops within a single trip. See [buildRideSegments]. */
    private data class RideSegment(
        val routeId: String,
        val variantId: String,
        val tripKey: String, // unique per (routeId, variantId, dayType, trip index) — identifies "the same vehicle"
        val stops: List<String>,
        val minutesOfDay: List<Int>,
        val isEstimated: List<Boolean>,
    )

    /** A stop reached during the search, with enough context to compute the next leg's buffer and to reconstruct legs. */
    private data class Reached(
        val stop: String,
        val timeMin: Int,
        val isEstimatedArrival: Boolean,
        val legsFromOrigin: List<Leg>,
        val boardedTripKeys: Set<String>, // trips already ridden on this path — a round-1 transfer must use a different one
    )

    fun findJourneys(
        routes: List<JourneyRouteData>,
        transfers: List<TransferEdge>,
        query: JourneyQuery,
    ): List<Journey> {
        val segments = buildRideSegments(routes, query.dayTypes, query.month, query.weekday)
        val walkNeighbors = buildWalkIndex(transfers)

        // Round 0: origin (+ its immediate walk neighbors) as boarding points, no buffer (nothing to transfer from yet).
        val origin = Reached(query.origin, query.departAfterMin, isEstimatedArrival = false, legsFromOrigin = emptyList(), boardedTripKeys = emptySet())
        val round0Starts = listOf(origin) + walkNeighbors[query.origin].orEmpty().map { edge ->
            val other = if (edge.from == query.origin) edge.to else edge.from
            Reached(
                stop = other,
                timeMin = query.departAfterMin + edge.walkMinutes,
                isEstimatedArrival = false,
                legsFromOrigin = listOf(Leg.Walk(query.origin, other, edge.meters, edge.walkMinutes)),
                boardedTripKeys = emptySet(),
            )
        }

        val journeys = mutableListOf<Journey>()
        val round0RideReach = mutableListOf<Reached>()

        for (start in round0Starts) {
            for (reached in ridesFrom(start, segments, buffer = 0, requireDifferentTripThan = null)) {
                round0RideReach += reached
                if (reached.stop == query.destination) {
                    journeys += toJourney(reached)
                } else {
                    walkNeighbors[reached.stop]?.forEach { edge ->
                        val other = if (edge.from == reached.stop) edge.to else edge.from
                        if (other == query.destination) {
                            val walkLeg = Leg.Walk(reached.stop, other, edge.meters, edge.walkMinutes)
                            journeys += toJourney(reached.copy(stop = other, timeMin = reached.timeMin + edge.walkMinutes, legsFromOrigin = reached.legsFromOrigin + walkLeg))
                        }
                    }
                }
            }
        }

        // Round 1: transfer at every round-0 ride-reachable stop, or a walk from it, onto a *different* trip.
        // The walk itself adds no buffer to the arrival time here — the transfer buffer is
        // applied at the *next boarding* check inside ridesFrom (see its `cameFromWalk` branch).
        val round1Starts = round0RideReach + round0RideReach.flatMap { reached ->
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

        for (start in round1Starts) {
            val buffer = if (start.isEstimatedArrival) BUFFER_SAME_STOP_ESTIMATED else BUFFER_SAME_STOP_TRANSCRIBED
            // Note: the *walk* buffer (vs. same-stop buffer) is selected per-segment inside ridesFrom
            // when the previous leg was a Walk — see there.
            for (reached in ridesFrom(start, segments, buffer = buffer, requireDifferentTripThan = start.boardedTripKeys)) {
                if (reached.stop == query.destination) {
                    journeys += toJourney(reached)
                } else {
                    walkNeighbors[reached.stop]?.forEach { edge ->
                        val other = if (edge.from == reached.stop) edge.to else edge.from
                        if (other == query.destination) {
                            val walkLeg = Leg.Walk(reached.stop, other, edge.meters, edge.walkMinutes)
                            journeys += toJourney(reached.copy(stop = other, timeMin = reached.timeMin + edge.walkMinutes, legsFromOrigin = reached.legsFromOrigin + walkLeg))
                        }
                    }
                }
            }
        }

        return rankAndDedupe(journeys)
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
     */
    private fun ridesFrom(
        start: Reached,
        segments: List<RideSegment>,
        buffer: Int,
        requireDifferentTripThan: Set<String>?,
    ): List<Reached> {
        val cameFromWalk = start.legsFromOrigin.lastOrNull() is Leg.Walk
        val results = mutableListOf<Reached>()
        for (segment in segments) {
            if (requireDifferentTripThan != null && segment.tripKey in requireDifferentTripThan) continue
            for (i in segment.stops.indices) {
                if (segment.stops[i] != start.stop) continue
                val effectiveBuffer = when {
                    requireDifferentTripThan == null -> 0 // round 0 boarding from origin/its walk neighbor: no prior ride to buffer against
                    cameFromWalk -> if (start.isEstimatedArrival || segment.isEstimated[i]) BUFFER_WALK_ESTIMATED else BUFFER_WALK_TRANSCRIBED
                    else -> buffer
                }
                if (segment.minutesOfDay[i] < start.timeMin + effectiveBuffer) continue
                // MAX_WAIT_MIN caps genuine mid-journey transfer waits, not the gap between the
                // query's departAfterMin and the first bus of the day — a query for "after 00:00"
                // legitimately waiting until the morning's first departure is not a "long transfer".
                if (requireDifferentTripThan != null && segment.minutesOfDay[i] - start.timeMin > MAX_WAIT_MIN) continue
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
     * Extracts, per trip, the maximal contiguous (no gaps) runs of adjacent, time-monotonic
     * populated departures — see "Connection extraction" and "Non-monotonic departures" in
     * `docs/JOURNEY_PLANNER.md`. A trip with a non-monotonic hop in the middle (13 cluster-noise
     * cases + 1 genuine mid-route-origin case in the current data) simply produces two shorter
     * segments instead of one continuous one; a rider is conservatively assumed unable to ride
     * straight through the break.
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
                            // skipped stop, out-of-season departure, or out-of-range index: breaks any in-progress run
                            flush()
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

    /** Ranks by earliest arrival, then fewest transfers, then latest departure; collapses to the
     * best journey per distinct route/leg-kind pattern, then returns the top 3 patterns. */
    private fun rankAndDedupe(journeys: List<Journey>): List<Journey> {
        fun pattern(j: Journey) = j.legs.joinToString("|") { leg ->
            when (leg) {
                is Leg.Ride -> "R:${leg.routeId}:${leg.variantId}"
                is Leg.Walk -> "W"
            }
        }
        fun betterOf(a: Journey, b: Journey): Journey {
            if (a.arrivalMin != b.arrivalMin) return if (a.arrivalMin < b.arrivalMin) a else b
            if (a.transferCount != b.transferCount) return if (a.transferCount < b.transferCount) a else b
            return if (a.departureMin >= b.departureMin) a else b
        }
        val bestPerPattern = journeys.groupBy(::pattern).values.map { it.reduce(::betterOf) }
        return bestPerPattern.sortedWith(
            compareBy<Journey> { it.arrivalMin }
                .thenBy { it.transferCount }
                .thenByDescending { it.departureMin }
        ).take(3)
    }
}
