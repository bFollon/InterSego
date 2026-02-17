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

/**
 * Interface for parsers that provide route and stop definitions
 *
 * Enables UI components to query parsers for route structures
 * without hardcoding stop lists in multiple places.
 *
 * Example:
 * ```
 * class M4Parser : RouteStopsProvider {
 *     override fun getRoutesForId(routeId: String): List<List<BusStop>> {
 *         return when (routeId) {
 *             "M4" -> listOf(m4RegularRoute, m4ReverseRoute)
 *             else -> emptyList()
 *         }
 *     }
 * }
 * ```
 */
interface RouteStopsProvider {
    /**
     * Get all route variations for a given route ID
     *
     * Most routes have two variations: regular and reverse direction.
     * Returns empty list if route ID is not supported by this provider.
     *
     * @param routeId Route ID (e.g., "M4", "M6")
     * @return List of route variations, each variation is a list of BusStop
     */
    fun getRoutesForId(routeId: String): List<List<BusStop>>
}
