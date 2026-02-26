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

package com.github.bfollon.linecapp.services.pdfparsing

import com.github.bfollon.linecapp.data.BusStop
import com.github.bfollon.linecapp.data.DayType
import com.github.bfollon.linecapp.data.RouteVariant
import com.github.bfollon.linecapp.data.RouteView
import com.github.bfollon.linecapp.data.RouteViewStop

/**
 * Interface for parsers that provide route and stop definitions
 *
 * Enables UI components to query parsers for route structures
 * without hardcoding stop lists in multiple places.
 */
interface RouteStopsProvider {
    /**
     * Get all route variations for a given route ID (legacy, day-type-agnostic)
     *
     * Most routes have two variations: regular and reverse direction.
     * Returns empty list if route ID is not supported by this provider.
     *
     * @param routeId Route ID (e.g., "M4", "M6")
     * @return List of route variations, each variation is a list of BusStop
     */
    fun getRoutesForId(routeId: String): List<List<BusStop>>

    /**
     * Get named route variants for a given route ID and day type.
     *
     * Each variant includes a display label, stop list, and direction string.
     * This allows the UI to present a route selector dropdown with correct
     * stop sequences per variant.
     *
     * Default implementation creates two variants from getRoutesForId().
     *
     * @param routeId Route ID (e.g., "M4", "M6")
     * @param dayType Day type to filter variants for
     * @return List of RouteVariant for display
     */
    fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        val routes = getRoutesForId(routeId)
        return listOfNotNull(
            routes.getOrNull(0)?.let { RouteVariant("regular", "Regular", it, "Regular") },
            routes.getOrNull(1)?.let { RouteVariant("reverse", "Reverse", it, "Reverse") }
        )
    }

    /**
     * Get rich route views for display, with tabs, swap actions, and extended stop markers.
     *
     * Returns null by default, meaning the screen should fall back to converting
     * getRouteVariants() into plain RouteView objects.
     */
    fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? = null
}
