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
            val text = context.assets
                .open("route_polylines/$routeId-$viewId.json")
                .bufferedReader().readText()
            val file = json.decodeFromString<PolylineFile>(text)
            file.coordinates.mapNotNull { pair ->
                if (pair.size >= 2) GeoPoint(pair[0], pair[1]) else null
            }
        } catch (_: Exception) { emptyList() }
    }
}
