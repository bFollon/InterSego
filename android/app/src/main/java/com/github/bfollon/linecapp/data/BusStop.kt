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

package com.github.bfollon.linecapp.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a bus stop in the Segovia transportation system
 */
@Serializable
data class BusStop(
    val id: String = UUID.randomUUID().toString(),
    val name: String,                   // "Plaza Mayor"
    val area: String? = null,                  // San Cristóbal de Segovia
    val details: String? = null,                  // Junto a C/X
    val address: String,                // Full address
    val latitude: Double? = null,       // GPS latitude (nullable until geocoded)
    val longitude: Double? = null,      // GPS longitude (nullable until geocoded)
    val routesServed: List<String> = emptyList(),  // List of route IDs that serve this stop
    val stopCode: String? = null,        // Optional official stop code
    val coordinates: String? = null,
    val isApproximate: Boolean = false
) {
    /**
     * Check if stop has valid coordinates
     */
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null

    /**
     * Display name with stop code if available
     */
    val displayName: String
        get() = if (stopCode != null) {
            "$name ($stopCode)"
        } else {
            name
        }

    /**
     * Number of routes serving this stop
     */
    val routeCount: Int
        get() = routesServed.size

    companion object {
        /**
         * Example bus stop
         */
        val example = BusStop(
            id = "stop-plaza-mayor",
            name = "Plaza Mayor",
            address = "Plaza Mayor, Segovia",
            latitude = 40.9487,
            longitude = -4.1171,
            routesServed = listOf("L1", "L2", "L3"),
            stopCode = "001"
        )
    }
}
