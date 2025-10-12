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
 * Represents a bus route in the Segovia transportation system
 */
@Serializable
data class BusRoute(
    val id: String = UUID.randomUUID().toString(),
    val number: String,                 // "1", "2", "40"
    val name: String,                   // "Centro - San Lorenzo"
    val origin: String,                 // Starting point
    val destination: String,            // End point
    val pdfURL: String,                 // URL to timetable PDF
    val routeType: RouteType,           // URBAN or INTERURBAN
    val color: String? = null,          // Optional route color for UI (hex code)
    val active: Boolean = true          // Whether route is currently in service
) {
    /**
     * Display name for the route
     */
    val displayName: String
        get() = "Línea $number: $name"

    /**
     * Short display name
     */
    val shortName: String
        get() = "L$number"

    companion object {
        /**
         * Example urban route
         */
        val exampleUrban = BusRoute(
            id = "L1",
            number = "1",
            name = "Centro - Pío XII",
            origin = "Centro",
            destination = "Pío XII",
            pdfURL = "https://example.com/linea-1.pdf",
            routeType = RouteType.URBAN,
            color = "#FF5722"
        )

        /**
         * Example interurban route
         */
        val exampleInterurban = BusRoute(
            id = "L40",
            number = "40",
            name = "Segovia - La Granja",
            origin = "Segovia",
            destination = "La Granja de San Ildefonso",
            pdfURL = "https://example.com/linea-40.pdf",
            routeType = RouteType.INTERURBAN,
            color = "#2196F3"
        )
    }
}
