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
 */
class RouteDataService(private val context: Context) {

    private val supportedRoutes = listOf("M1", "M2", "M3", "M4", "M5", "M6", "M7", "M7-AVE", "M8")

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
                DebugConfig.debugError("RouteDataService: Failed to index stops for $routeId", e)
            }
        }
        DebugConfig.debugPrint("RouteDataService: Initialized with ${supportedRoutes.size} routes, ${index.size} stops indexed")
        return index
    }

    suspend fun parseTimetables(routeId: String): List<BusTimetable> = withContext(Dispatchers.IO) {
        DebugConfig.debugPrint("RouteDataService: Loading timetables for route $routeId")
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
