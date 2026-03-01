/*
 * LineCapp - Bus timetable app for Segovia, Spain
 * Copyright (C) 2024 Bruno Follon
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

package com.github.bfollon.linecapp.services

import com.github.bfollon.linecapp.data.BusStop
import kotlin.math.*

/**
 * Service for generating static map URLs and rendering data from bus stop coordinates.
 *
 * Uses OpenStreetMap (OSM) tile system to display bus stop locations as static images
 * with marker overlays.
 *
 * Usage:
 * ```kotlin
 * val mapData = StaticMapService.getStaticMapData(busStop)
 * if (mapData != null) {
 *     // Display tile with AsyncImage and overlay marker at calculated position
 * }
 * ```
 */
object StaticMapService {

    private const val TAG = "StaticMapService"

    /**
     * Default map dimensions for the static image
     */
    private const val DEFAULT_WIDTH = 600
    private const val DEFAULT_HEIGHT = 300

    /**
     * Default zoom level (higher = more zoomed in)
     * OSM zoom levels: 0 (world) to 19 (building level)
     * Zoom 18 shows ~150m radius with ~0.6m per pixel (street-level detail)
     */
    private const val DEFAULT_ZOOM = 18

    /**
     * Data class containing all information needed to render a static map with marker.
     *
     * @param tileUrl URL to the center OSM tile image (256x256 PNG)
     * @param markerX X pixel position of marker within the center tile (0-255)
     * @param markerY Y pixel position of marker within the center tile (0-255)
     * @param tileX X coordinate of the center tile in the OSM tile grid
     * @param tileY Y coordinate of the center tile in the OSM tile grid
     * @param zoom OSM zoom level used
     */
    data class StaticMapData(
        val tileUrl: String,
        val markerX: Float,
        val markerY: Float,
        val tileX: Int,
        val tileY: Int,
        val zoom: Int
    )

    /**
     * Converts latitude/longitude coordinates to OSM tile coordinates at a given zoom level.
     *
     * Uses Web Mercator projection (EPSG:3857) which OSM tiles are based on.
     *
     * @param lat Latitude in degrees (-85.0511 to 85.0511)
     * @param lon Longitude in degrees (-180 to 180)
     * @param zoom OSM zoom level (0-19)
     * @return Pair of (xtile, ytile) tile coordinates
     */
    fun getTileCoordinates(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom // 2^zoom (bit shift for efficiency)

        val xtile = ((lon + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val ytile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()

        return Pair(xtile, ytile)
    }

    /**
     * Calculates the pixel position of a marker within a 256x256 OSM tile.
     *
     * OSM tiles are always 256x256 pixels. This function determines where within
     * that tile the given lat/lon coordinates fall.
     *
     * @param lat Latitude in degrees
     * @param lon Longitude in degrees
     * @param zoom OSM zoom level
     * @param tileX X coordinate of the tile containing this point
     * @param tileY Y coordinate of the tile containing this point
     * @return Pair of (xPixel, yPixel) position within the tile (0.0-256.0)
     */
    fun getMarkerPixelPosition(
        lat: Double,
        lon: Double,
        zoom: Int,
        tileX: Int,
        tileY: Int
    ): Pair<Float, Float> {
        val n = 1 shl zoom // 2^zoom

        // Convert lat/lon to continuous tile-space coordinates (fractional tiles)
        val xtile = (lon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val ytile = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n

        // Calculate pixel position within this specific tile (0-256 range)
        val xPixel = ((xtile - tileX) * 256.0).toFloat()
        val yPixel = ((ytile - tileY) * 256.0).toFloat()

        return Pair(xPixel, yPixel)
    }

    /**
     * Returns all data needed to render a static map with a marker for a bus stop.
     *
     * This is the main function to use from UI code. It combines tile URL generation
     * and marker position calculation into a single call.
     *
     * @param busStop The bus stop to display
     * @param zoom OSM zoom level (default: 18 for street-level detail)
     * @return StaticMapData with tile URL and marker position, or null if no coordinates
     */
    fun getStaticMapData(busStop: BusStop, zoom: Int = DEFAULT_ZOOM): StaticMapData? {
        if (!busStop.hasCoordinates) {
            DebugConfig.debugWarn("$TAG: Cannot generate map data - no coordinates for stop ${busStop.name}")
            return null
        }

        val lat = busStop.latitude!!
        val lon = busStop.longitude!!

        // Get tile coordinates
        val (tileX, tileY) = getTileCoordinates(lat, lon, zoom)

        // Get marker pixel position within that tile
        val (markerX, markerY) = getMarkerPixelPosition(lat, lon, zoom, tileX, tileY)

        // Generate tile URL
        val tileUrl = "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png"

        DebugConfig.debugPrint("$TAG: Generated map data for ${busStop.name}: tile=($tileX,$tileY), marker=($markerX,$markerY)")

        return StaticMapData(tileUrl, markerX, markerY, tileX, tileY, zoom)
    }

    /**
     * Generate a static map image URL using a tile-based approach.
     *
     * This creates a direct link to an OSM tile server image centered on the coordinates.
     * Note: This doesn't show a marker - use getStaticMapData() for marker support.
     *
     * @param busStop The bus stop to generate a map for
     * @param zoom OSM zoom level 0-19 (default: 18)
     * @return URL string for the static tile image, or null if coordinates unavailable
     */
    fun getTileImageUrl(
        busStop: BusStop,
        zoom: Int = DEFAULT_ZOOM
    ): String? {
        if (!busStop.hasCoordinates) {
            return null
        }

        val (xtile, ytile) = getTileCoordinates(busStop.latitude!!, busStop.longitude!!, zoom)

        // Using OpenStreetMap tile server
        val url = "https://tile.openstreetmap.org/$zoom/$xtile/$ytile.png"

        DebugConfig.debugPrint("$TAG: Generated tile URL for ${busStop.name}: $url")

        return url
    }

    /**
     * Checks if the given bus stop has valid coordinates for map display.
     *
     * @param busStop The bus stop to check
     * @return true if the bus stop has both latitude and longitude, false otherwise
     */
    fun canShowMap(busStop: BusStop): Boolean {
        return busStop.hasCoordinates
    }
}
