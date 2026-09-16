/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.data

/**
 * One segment of a [Journey]: either a bus ride or a walking transfer between two
 * [BusStop.id]-equivalent physical stop identifiers (see `physicalStopId`).
 *
 * See `docs/JOURNEY_PLANNER.md` — this type mirrors iOS's `Leg` enum field-for-field.
 */
sealed class Leg {
    abstract val fromStop: String
    abstract val toStop: String

    /** A bus ride on a single route/variant, covering one connection extracted from the timetable JSON. */
    data class Ride(
        val routeId: String,
        val variantId: String,
        override val fromStop: String,
        override val toStop: String,
        val depMin: Int,
        val arrMin: Int,
        /** True if either endpoint's departure time is cluster-estimated (`timeIsEstimated` in the JSON). */
        val isEstimated: Boolean,
    ) : Leg()

    /** A walk between two distinct physical stops, per `resources/transfers.json`. */
    data class Walk(
        override val fromStop: String,
        override val toStop: String,
        val meters: Int,
        val minutes: Int,
    ) : Leg()
}

/**
 * A complete A-to-B journey: an ordered sequence of [Leg]s.
 *
 * [departureMin]/[arrivalMin]/[transferCount] are stored, not derived from [legs] — a journey
 * may start or end with a [Leg.Walk] (e.g. walking to a better-connected nearby stop before
 * boarding), so "departure" isn't always the first leg's own depMin. The CSA builder computes
 * these directly since it already knows them when it constructs the journey.
 *
 * See `docs/JOURNEY_PLANNER.md` for how journeys are computed (Connection Scan) and ranked.
 */
data class Journey(
    val legs: List<Leg>,
    val departureMin: Int,
    val arrivalMin: Int,
    val transferCount: Int,
) {
    init {
        require(legs.isNotEmpty()) { "Journey must have at least one leg" }
    }

    companion object {
        /**
         * Minimum wait at a transfer point before it's surfaced as an explicit step (see
         * [stepsWithWaits]) rather than left implicit in the leg times either side of it. Single
         * source of truth for this value — mirrored on iOS as `Journey.longWaitThresholdMin`.
         */
        const val LONG_WAIT_THRESHOLD_MIN = 5
    }

    /**
     * The stop a mid-journey transfer boards at, and how many minutes elapse there between
     * arriving (by ride or by walk) and the connecting bus's departure. One entry per [Leg.Ride]
     * that isn't the journey's first leg — i.e. one per transfer, regardless of whether it's a
     * same-stop or walking connection. See [transferMargins].
     */
    data class TransferMargin(val stopId: String, val marginMin: Int)

    /**
     * Computes [TransferMargin]s for every mid-journey transfer. This is the actual elapsed gap
     * for each connection — independent of whichever (possibly user-lowered) buffer the search
     * itself required to accept the journey — so callers can flag a transfer as tight against a
     * fixed safety threshold (`TripPlannerPrefs.recommendedMinBuffer`) regardless of search
     * settings. Mirrored on iOS as `Journey.transferMargins()`.
     */
    fun transferMargins(): List<TransferMargin> {
        val margins = mutableListOf<TransferMargin>()
        var clock = departureMin
        legs.forEachIndexed { index, leg ->
            if (index > 0 && leg is Leg.Ride) {
                margins.add(TransferMargin(stopId = leg.fromStop, marginMin = leg.depMin - clock))
            }
            clock = when (leg) {
                is Leg.Ride -> leg.arrMin
                is Leg.Walk -> clock + leg.minutes
            }
        }
        return margins
    }

    /**
     * [legs] with a [JourneyStep.Wait] step inserted before any Ride that follows more than
     * [LONG_WAIT_THRESHOLD_MIN] minutes after the previous leg ends. Only mid-journey transfers
     * are considered — never before the first leg, since that's simply when you start the
     * journey, not something to "wait out" (see [departureMin]'s doc comment on why it can
     * precede the first leg's own `depMin`).
     */
    fun stepsWithWaits(): List<JourneyStep> {
        val steps = mutableListOf<JourneyStep>()
        var clock = departureMin
        legs.forEachIndexed { index, leg ->
            if (index > 0 && leg is Leg.Ride) {
                val gap = leg.depMin - clock
                if (gap > LONG_WAIT_THRESHOLD_MIN) {
                    steps.add(JourneyStep.Wait(gap))
                }
            }
            steps.add(JourneyStep.LegStep(leg))
            clock = when (leg) {
                is Leg.Ride -> leg.arrMin
                is Leg.Walk -> clock + leg.minutes
            }
        }
        return steps
    }
}

/**
 * One row of a journey's leg-by-leg breakdown: either an actual [Leg], or an explicit wait
 * inserted between two legs by [Journey.stepsWithWaits].
 */
sealed class JourneyStep {
    data class LegStep(val leg: Leg) : JourneyStep()
    data class Wait(val minutes: Int) : JourneyStep()
}
