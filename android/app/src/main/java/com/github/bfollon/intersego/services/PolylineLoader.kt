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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.osmdroid.util.GeoPoint

object PolylineLoader {
    @Serializable
    private data class PolylineFile(
        val version: String,
        val coordinates: List<List<Double>>
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context, routeId: String, viewId: String): List<GeoPoint> {
        return try {
            val id = "$routeId-$viewId"
            val cacheFile = PolylineCacheService.cacheFile(context, id)
            val text = if (cacheFile.exists()) {
                cacheFile.readText()
            } else {
                context.assets
                    .open("route_polylines/$id.json")
                    .bufferedReader().readText()
            }
            val file = json.decodeFromString<PolylineFile>(text)
            file.coordinates.mapNotNull { pair ->
                if (pair.size >= 2) GeoPoint(pair[0], pair[1]) else null
            }
        } catch (_: Exception) { emptyList() }
    }
}
