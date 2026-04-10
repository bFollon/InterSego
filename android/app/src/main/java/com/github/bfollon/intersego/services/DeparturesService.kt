/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.intersego.services

import android.content.Context
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.data.BusTimetable
import java.util.Calendar

/**
 * A departure time tagged with the route it belongs to.
 * Used when displaying merged departures from multiple lines.
 */
data class TaggedDeparture(
    val departure: DepartureTime,
    val routeId: String,
    val routeNumber: String
)

/**
 * Loaded data for a single route: route metadata, route views, and timetables.
 */
data class RouteLoadedData(
    val route: BusRoute,
    val views: List<RouteView>,
    val timetables: List<BusTimetable>
)

/**
 * All departures for a given stop, organized by day.
 * todayTaggedDepartures: departures for today, filtered by stop, dayType, and season
 * nextDayTaggedDepartures: map of daysAhead (1-7) to departures for that day
 * routes: all loaded route data
 */
data class DeparturesData(
    val todayTaggedDepartures: List<TaggedDeparture>,
    val nextDayTaggedDepartures: Map<Int, List<TaggedDeparture>>,
    val routes: List<RouteLoadedData>
)

/**
 * Service for loading and organizing departure data for a specific stop.
 * Encapsulates all the logic for fetching timetables, filtering by day type/season,
 * and preparing departures for display.
 *
 * Used by both NextDepartureScreen (for live departures) and DirectionPickerScreen
 * (for listing available directions).
 */
class DeparturesService(private val context: Context) {
    private val pdfService = PDFProcessingService(context)
    private val timetableService = TimetableService(context)

    /**
     * Load all departures for a specific stop.
     *
     * @param stop The bus stop to load departures for
     * @param allRoutes All available routes (used for metadata lookup)
     * @param primaryRouteId Optional route ID to filter to a single route
     * @return DeparturesData containing today's and future departures
     */
    suspend fun loadDepartures(
        stop: BusStop,
        allRoutes: List<BusRoute>,
        primaryRouteId: String? = null
    ): DeparturesData {
        // Determine which routes to load
        val routeIds = if (primaryRouteId != null && primaryRouteId != "none") {
            listOf(primaryRouteId)
        } else {
            pdfService.getRoutesForStop(stop.id).sorted()
        }

        // Load route data for all routes serving this stop
        val loadedRoutes = mutableListOf<RouteLoadedData>()
        val currentDayType = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> DayType.SATURDAY
            Calendar.SUNDAY -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }

        for (routeId in routeIds) {
            val route = allRoutes.find { it.id == routeId } ?: continue
            try {
                val timetables = timetableService.loadTimetables(routeId)
                if (timetables.isEmpty()) continue
                val views = pdfService.getRouteViews(routeId, currentDayType)
                loadedRoutes.add(RouteLoadedData(route, views, timetables))
            } catch (e: Exception) {
                DebugConfig.debugWarn("DeparturesService: failed to load $routeId: ${e.message}")
            }
        }

        // Get today's day types and current day of week
        val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val currentDayTypes = dayTypesForCalendarDay(currentDayOfWeek)

        // Build today's departures for all available directions
        val todayDepartures = buildTodayDepartures(loadedRoutes, stop, currentDayTypes, currentDayOfWeek)

        // Build future departures (up to 7 days ahead) for each direction
        val nextDayDepartures = buildNextDayDepartures(loadedRoutes, stop, currentDayOfWeek)

        return DeparturesData(
            todayTaggedDepartures = todayDepartures,
            nextDayTaggedDepartures = nextDayDepartures,
            routes = loadedRoutes
        )
    }

    /**
     * Build departures for all available directions on today.
     * Returns a flat list organized by direction.
     */
    private fun buildTodayDepartures(
        loadedRoutes: List<RouteLoadedData>,
        stop: BusStop,
        currentDayTypes: Set<DayType>,
        currentDayOfWeek: Int
    ): List<TaggedDeparture> {
        return loadedRoutes.flatMap { routeData ->
            routeData.timetables
                .filter { it.stopId == stop.id && it.dayType in currentDayTypes }
                .flatMap { timetable ->
                    timetable.seasonalDepartures(weekday = currentDayOfWeek)
                        .map { TaggedDeparture(it, routeData.route.id, routeData.route.number) }
                }
        }.sortedBy { it.departure.toMinutesSinceMidnight() }
    }

    /**
     * Build future departures (1-7 days ahead) for each available direction.
     * Returns a map keyed by daysAhead, containing departures for that day.
     */
    private fun buildNextDayDepartures(
        loadedRoutes: List<RouteLoadedData>,
        stop: BusStop,
        currentDayOfWeek: Int
    ): Map<Int, List<TaggedDeparture>> {
        val result = mutableMapOf<Int, List<TaggedDeparture>>()

        for (daysAhead in 1..7) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
            val futureDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val futureDayTypes = dayTypesForCalendarDay(futureDayOfWeek)

            val departures = loadedRoutes.flatMap { routeData ->
                routeData.timetables
                    .filter { it.stopId == stop.id && it.dayType in futureDayTypes }
                    .flatMap { timetable ->
                        timetable.seasonalDepartures(weekday = futureDayOfWeek)
                            .map { TaggedDeparture(it, routeData.route.id, routeData.route.number) }
                    }
            }.sortedBy { it.departure.toMinutesSinceMidnight() }

            if (departures.isNotEmpty()) {
                result[daysAhead] = departures
            }
        }

        return result
    }

    /**
     * Map a Calendar day-of-week to the set of DayType values that might match.
     * M4 uses WEEKEND for Saturday; M6 uses SATURDAY and SUNDAY separately.
     */
    private fun dayTypesForCalendarDay(dayOfWeek: Int): Set<DayType> = when (dayOfWeek) {
        Calendar.SATURDAY -> setOf(DayType.SATURDAY, DayType.WEEKEND)
        Calendar.SUNDAY -> setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
        else -> setOf(DayType.WEEKDAY)
    }
}
