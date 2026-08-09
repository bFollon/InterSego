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

/**
 * Builds the deduplicated (by physicalStopId) directory of every stop across every route, for
 * the journey planner's origin/destination picker. A stop that carries alias ids (e.g.
 * `estacion-autobuses-circ-ret`) appears once, under its canonical physicalStopId, with the
 * union of routes serving any of its aliases.
 */
class StopDirectoryService(private val context: Context) {

    data class Entry(
        val physicalStopId: String,
        val stop: BusStop,
        val routeIds: List<String>,
    )

    fun buildDirectory(routeIds: List<String>): List<Entry> {
        val loader = TimetableLoader(context)
        // physicalStopId -> (canonical BusStop, route ids serving it)
        val canonicalStops = LinkedHashMap<String, BusStop>()
        val routesByPhysicalId = LinkedHashMap<String, MutableSet<String>>()

        for (routeId in routeIds) {
            val stopsById = runCatching { loader.loadBusStopsById(routeId) }.getOrNull() ?: continue
            val physicalIds = runCatching { loader.loadPhysicalStopIds(routeId) }.getOrNull() ?: continue

            for ((rawId, stop) in stopsById) {
                val physicalId = physicalIds[rawId] ?: rawId
                routesByPhysicalId.getOrPut(physicalId) { mutableSetOf() }.add(routeId)
                // Prefer the entry whose own id *is* the physicalStopId (the canonical, non-alias one).
                val existing = canonicalStops[physicalId]
                if (existing == null || rawId == physicalId) {
                    canonicalStops[physicalId] = stop
                }
            }
        }

        return canonicalStops.map { (physicalId, stop) ->
            Entry(physicalId, stop, routesByPhysicalId[physicalId].orEmpty().sorted())
        }.sortedBy { it.stop.name }
    }
}
