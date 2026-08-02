/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.content.Context
import android.location.Location
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import java.util.Calendar

/**
 * Finds the bus stop closest to the user's location across all supported routes.
 *
 * When multiple stops are within 100 m of the minimum distance, tie-breaks by
 * picking the route with the soonest next departure.
 */
class ClosestStopFinderService(private val context: Context) {

    data class Result(
        val stopId: String
    )

    sealed class ClosestStopError : Exception() {
        object NoStopsFound : ClosestStopError() {
            override val message: String = "No se encontraron paradas disponibles en este momento."
        }
    }

    /**
     * @param userLocation The user's current location.
     * @param routes The full list of known routes (used to resolve route metadata by ID).
     */
    suspend fun findClosest(userLocation: Location, routes: List<BusRoute>): Result {
        val pdfService = RouteDataService(context)
        val supportedRouteIds = pdfService.getSupportedRoutes()
        val dayType = getCurrentDayType()

        val candidates = mutableListOf<StopCandidate>()
        // Global dedup: each physical stop is a candidate at most once across all routes
        val seenStopIds = mutableSetOf<String>()

        for (routeId in supportedRouteIds) {
            val route = routes.find { it.id == routeId } ?: continue
            val views = pdfService.getRouteViews(routeId, dayType)
            if (views.isEmpty()) continue

            for (view in views) {
                for (viewStop in view.stops) {
                    val stop = viewStop.stop
                    if (!seenStopIds.add(stop.id)) continue
                    if (!stop.hasCoordinates) continue

                    val stopLocation = Location("").apply {
                        latitude = stop.resolvedLatitude!!
                        longitude = stop.resolvedLongitude!!
                    }
                    val distance = userLocation.distanceTo(stopLocation)
                    candidates.add(StopCandidate(route, stop, view.id, distance))
                }
            }
        }

        if (candidates.isEmpty()) throw ClosestStopError.NoStopsFound

        candidates.sortBy { it.distance }
        DebugConfig.debugPrint("ClosestStopFinderService: Closest stop is ${candidates[0].stop.name} at ${candidates[0].distance.toInt()} m")

        // Tie-break: if multiple stops are within 100 m of the minimum, pick by soonest departure
        val minDist = candidates[0].distance
        val tied = candidates.filter { it.distance <= minDist + 100f }

        val winner = if (tied.size == 1) {
            tied[0]
        } else {
            DebugConfig.debugPrint("ClosestStopFinderService: ${tied.size} stops within tie-break range, resolving by soonest departure")
            pickBySoonestDeparture(tied, dayType)
        }

        DebugConfig.debugPrint("ClosestStopFinderService: Winner → ${winner.stop.name} (${winner.stop.id})")
        return Result(winner.stop.id)
    }

    private suspend fun pickBySoonestDeparture(
        candidates: List<StopCandidate>,
        dayType: DayType
    ): StopCandidate {
        val timetableService = TimetableService(context)
        val now = java.time.LocalTime.now()
        val currentTotalMinutes = now.hour * 60 + now.minute

        // Match against all DayType values that apply today, so routes that store
        // Saturday/Sunday under WEEKEND (e.g. M4) are not silently skipped.
        val matchDayTypes: Set<DayType> = when (dayType) {
            DayType.SATURDAY -> setOf(DayType.SATURDAY, DayType.WEEKEND)
            DayType.SUNDAY   -> setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
            else             -> setOf(DayType.WEEKDAY)
        }

        var best = candidates[0]
        var bestMinutesUntil = Int.MAX_VALUE

        for (candidate in candidates) {
            val timetables = timetableService.loadTimetables(candidate.route.id)
            val stopTimetables = timetables.filter {
                it.stopId == candidate.stop.id && it.dayType in matchDayTypes
            }
            for (timetable in stopTimetables) {
                val next = timetable.departures
                    .filter { dep -> dep.hour * 60 + dep.minute > currentTotalMinutes }
                    .minByOrNull { dep -> dep.hour * 60 + dep.minute }
                    ?: continue
                val until = next.hour * 60 + next.minute - currentTotalMinutes
                if (until < bestMinutesUntil) {
                    bestMinutesUntil = until
                    best = candidate
                }
            }
        }

        return best
    }

    private fun getCurrentDayType(): DayType = TimetableQueryUtils.primaryDayType()

    private data class StopCandidate(
        val route: BusRoute,
        val stop: BusStop,
        val viewId: String,
        val distance: Float
    )
}
