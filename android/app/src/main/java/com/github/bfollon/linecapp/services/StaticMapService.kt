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

/**
 * Service for generating static map URLs from bus stop coordinates.
 *
 * Uses OpenStreetMap (OSM) static map tiles to display bus stop locations.
 * The generated URLs point to embeddable OSM maps with markers.
 *
 * Usage:
 * ```kotlin
 * val mapUrl = StaticMapService.getMapImageUrl(busStop)
 * if (mapUrl != null) {
 *     // Display map using AsyncImage
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
     * 16-17 is good for showing a bus stop in neighborhood context
     */
    private const val DEFAULT_ZOOM = 17

    /**
     * Generates an OpenStreetMap embed URL for the given bus stop.
     *
     * Returns an embeddable OpenStreetMap URL that should be displayed in a WebView.
     * The URL includes a marker at the bus stop location.
     *
     * @param busStop The bus stop to generate a map for
     * @param width Map image width in pixels (default: 600)
     * @param height Map image height in pixels (default: 300)
     * @param zoom OSM zoom level 0-19 (default: 17)
     * @return URL string for the embedded map, or null if coordinates unavailable
     */
    fun getMapEmbedUrl(
        busStop: BusStop,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        zoom: Int = DEFAULT_ZOOM
    ): String? {
        if (!busStop.hasCoordinates) {
            DebugConfig.debugWarn("$TAG: Cannot generate map URL - no coordinates for stop ${busStop.name}")
            return null
        }

        val lat = busStop.latitude!!
        val lon = busStop.longitude!!

        // Using OSM embed URL with bbox and marker
        // Calculate bounding box around the point
        val degreesPerPixel = 360.0 / (256 * Math.pow(2.0, zoom.toDouble()))
        val latOffset = (height / 2) * degreesPerPixel
        val lonOffset = (width / 2) * degreesPerPixel

        val bbox = "${lon - lonOffset},${lat - latOffset},${lon + lonOffset},${lat + latOffset}"

        val url = "https://www.openstreetmap.org/export/embed.html?bbox=$bbox&layer=mapnik&marker=$lat,$lon"

        DebugConfig.debugPrint("$TAG: Generated embed URL for ${busStop.name}: $url")

        return url
    }

    /**
     * Generates an HTML iframe snippet for embedding the map.
     * This can be loaded directly in a WebView.
     *
     * @param busStop The bus stop to generate a map for
     * @param width Map width in pixels (default: 600)
     * @param height Map height in pixels (default: 300)
     * @param zoom OSM zoom level 0-19 (default: 17)
     * @return HTML string to load in WebView, or null if coordinates unavailable
     */
    fun getMapEmbedHtml(
        busStop: BusStop,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        zoom: Int = DEFAULT_ZOOM
    ): String? {
        val embedUrl = getMapEmbedUrl(busStop, width, height, zoom) ?: return null

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 0; }
                    iframe { border: 0; width: 100%; height: 100%; }
                </style>
            </head>
            <body>
                <iframe src="$embedUrl"></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Alternative: Generate a static map image URL using a tile-based approach.
     *
     * This creates a direct link to an OSM tile server image centered on the coordinates.
     * Note: This doesn't show a marker, but is simpler and doesn't require embedding.
     *
     * @param busStop The bus stop to generate a map for
     * @param zoom OSM zoom level 0-19 (default: 17)
     * @return URL string for the static tile image, or null if coordinates unavailable
     */
    fun getTileImageUrl(
        busStop: BusStop,
        zoom: Int = DEFAULT_ZOOM
    ): String? {
        if (!busStop.hasCoordinates) {
            return null
        }

        val lat = busStop.latitude!!
        val lon = busStop.longitude!!

        // Convert lat/lon to tile coordinates
        val xtile = ((lon + 180) / 360 * (1 shl zoom)).toInt()
        val ytile = ((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 shl zoom)).toInt()

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
