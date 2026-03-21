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

package com.github.bfollon.intersego.services.pdfparsing

import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView

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
     * Default implementation creates two variants from getRoutesForId().
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

    /**
     * Get the flat list of selectable route entries.
     *
     * Every parser should return at least one entry representing its operating schedules.
     * The UI hides the route selector when there is only one entry.
     *
     * @param routeId Route ID (e.g., "M6", "M1")
     * @param today Date used to determine which entries are active today
     */
    fun getRouteEntries(routeId: String, today: java.util.Date): List<RouteSelectorEntry> = emptyList()
}
