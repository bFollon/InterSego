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
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinator service for timetable and route metadata queries.
 *
 * All data is sourced from bundled JSON assets via [TimetableLoader].
 * The Strategy Pattern (PDF parsing) was removed when all routes migrated to JSON.
 */
class PDFProcessingService(private val context: Context) {

    private val supportedRoutes = listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8")

    private val stopToRoutes: Map<String, List<String>> by lazy { buildStopToRoutes() }

    private fun buildStopToRoutes(): Map<String, List<String>> {
        val index = mutableMapOf<String, MutableList<String>>()
        val loader = TimetableLoader(context)
        supportedRoutes.forEach { routeId ->
            try {
                loader.loadRoutesForId(routeId).flatten().forEach { stop ->
                    index.getOrPut(stop.id) { mutableListOf() }.let {
                        if (!it.contains(routeId)) it.add(routeId)
                    }
                }
            } catch (e: Exception) {
                DebugConfig.debugError("PDFProcessingService: Failed to index stops for $routeId", e)
            }
        }
        DebugConfig.debugPrint("PDFProcessingService: Initialized with ${supportedRoutes.size} routes, ${index.size} stops indexed")
        return index
    }

    suspend fun parseTimetables(routeId: String): List<BusTimetable> = withContext(Dispatchers.IO) {
        DebugConfig.debugPrint("PDFProcessingService: Loading timetables for route $routeId")
        TimetableLoader(context).load(routeId)
    }

    fun hasParserFor(routeId: String): Boolean = routeId in supportedRoutes

    fun getSupportedRoutes(): List<String> = supportedRoutes

    fun getRoutesForStop(stopId: String): List<String> = stopToRoutes[stopId]?.sorted() ?: emptyList()

    fun getParserVersion(routeId: String): String? = try {
        TimetableLoader(context).getVersion(routeId)
    } catch (e: Exception) {
        null
    }

    fun isDebugParser(routeId: String): Boolean = false

    fun getRoutesForNavigation(routeId: String): List<List<BusStop>> =
        try { TimetableLoader(context).loadRoutesForId(routeId) } catch (e: Exception) { emptyList() }

    fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> =
        try { TimetableLoader(context).loadRouteVariants(routeId, dayType) } catch (e: Exception) { emptyList() }

    fun getRouteEntries(routeId: String, today: java.util.Date = java.util.Date()): List<RouteSelectorEntry> =
        try { TimetableLoader(context).loadRouteEntries(routeId, today) } catch (e: Exception) { emptyList() }

    fun getRouteViews(routeId: String, dayType: DayType): List<RouteView> =
        try { TimetableLoader(context).loadRouteViews(routeId, dayType) ?: emptyList() } catch (e: Exception) { emptyList() }
}
