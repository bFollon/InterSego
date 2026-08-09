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
 * Pure (no Android dependency) trip-major view of one route's timetable JSON, as needed for
 * connection extraction. See `docs/JOURNEY_PLANNER.md`. Distinct from [BusTimetable], which is
 * the stop-major pivot used for display.
 */
data class JourneyStop(
    val id: String,
    val physicalStopId: String,
    val isEstimated: Boolean,
)

data class JourneyVariant(
    val id: String,
    val stopSequence: List<String>,
)

/** One populated departure: a resolved minute-of-day plus its effective season. */
data class JourneyDeparture(
    val minutesOfDay: Int,
    val season: SeasonalAvailability,
)

/** One trip's departures, index-aligned with its variant's [JourneyVariant.stopSequence]. `null` = stop skipped. */
data class JourneyTrip(
    val departures: List<JourneyDeparture?>,
)

data class JourneyTimetableSection(
    val variantId: String,
    val dayType: DayType,
    val trips: List<JourneyTrip>,
)

data class JourneyRouteData(
    val routeId: String,
    val stops: List<JourneyStop>,
    val variants: List<JourneyVariant>,
    val timetables: List<JourneyTimetableSection>,
)

/** A walking edge between two distinct physical stops, from `resources/transfers.json`. Bidirectional. */
data class TransferEdge(
    val from: String,
    val to: String,
    val meters: Int,
    val walkMinutes: Int,
)
