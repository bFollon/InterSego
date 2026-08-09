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
}
