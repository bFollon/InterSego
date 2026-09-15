/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.github.bfollon.intersego.data.BusReminder
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.data.Leg
import com.github.bfollon.intersego.data.SeasonalAvailability
import java.time.LocalDate
import java.util.Calendar

/**
 * Resolves the reminder identity for a planned journey.
 *
 * A journey can start with a walk to a better-connected stop before boarding (see
 * `JourneyPlannerService`), so the reminder fires against the journey's own `departureMin`
 * (when to leave) rather than the first ride's own `depMin` — that keeps the walk time covered.
 * It's still identified against that first ride's route/variant so `ReminderService`'s existing
 * day-type smart-skip logic applies. Seasonal availability isn't preserved through the CSA
 * search, so it's treated as year-round here — acceptable since the journey was already
 * validated against the searched date's day type and season at search time.
 */
object JourneyReminderHelper {
    data class Context(
        val route: BusRoute,
        val stop: BusStop,
        val direction: String,
        val departure: DepartureTime,
        val dayType: DayType,
        val journeyLabel: String,
    )

    /** Null when the journey has no bus ride at all (shouldn't happen in practice) or the first
     * ride's route/origin stop can't be resolved from the caller's lookups. */
    fun build(
        journey: Journey,
        date: LocalDate,
        originName: String,
        destinationName: String,
        routesById: Map<String, BusRoute>,
        stopsById: Map<String, BusStop>,
        routeDataService: RouteDataService,
    ): Context? {
        val firstRide = journey.legs.filterIsInstance<Leg.Ride>().firstOrNull() ?: return null
        val route = routesById[firstRide.routeId] ?: return null
        val stop = stopsById[journey.legs.first().fromStop] ?: return null

        val calendar = Calendar.getInstance().apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
        }
        val dayType = TimetableQueryUtils.primaryDayType(calendar)
        val direction = routeDataService.getRouteViews(firstRide.routeId, dayType)
            .find { it.id == firstRide.variantId }?.direction ?: firstRide.routeId

        val departure = DepartureTime(
            hour = (journey.departureMin / 60) % 24,
            minute = journey.departureMin % 60,
            seasonalAvailability = SeasonalAvailability.YEAR_ROUND,
        )

        return Context(
            route = route,
            stop = stop,
            direction = direction,
            departure = departure,
            dayType = dayType,
            journeyLabel = "$originName → $destinationName",
        )
    }

    fun matchKey(context: Context): String =
        BusReminder.matchKey(context.route.id, context.stop.id, context.direction, context.departure.hour, context.departure.minute)
}
