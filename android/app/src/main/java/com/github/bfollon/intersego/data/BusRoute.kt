/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.data

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
    val sourceURL: String,               // URL to official timetable source (PDF or web page)
    val routeType: RouteType,           // URBAN or INTERURBAN
    val isCircular: Boolean = false,    // True if route forms a loop (no single destination)
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
            sourceURL = "https://example.com/linea-1.pdf",
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
            sourceURL = "https://example.com/linea-40.pdf",
            routeType = RouteType.INTERURBAN,
            color = "#2196F3"
        )
    }
}
