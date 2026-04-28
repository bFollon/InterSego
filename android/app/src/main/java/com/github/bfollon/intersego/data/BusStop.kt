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
/**
 * Represents a bus stop in the Segovia transportation system
 */
@Serializable
data class BusStop(
    val id: String,
    val name: String,                   // "Plaza Mayor"
    val area: String? = null,           // San Cristóbal de Segovia
    val details: String? = null,        // Junto a C/X
    val coordinates: String,            // "40.948406, -4.116411"
    val routingCoordinates: String? = null, // Optional coordinate used for route planning when the
                                            // bus travels through a different point than the physical
                                            // stop (e.g. stop is just after a turn the bus doesn't make).
                                            // Falls back to [coordinates] when null.
    val routesServed: List<String> = emptyList(),  // List of route IDs that serve this stop
    val stopCode: String? = null,       // Optional official stop code
    val alternates: List<AlternateLocation> = emptyList(), // Sub-locations for specific departures
) {
    fun alternateLocation(id: String): AlternateLocation? = alternates.find { it.id == id }

    val resolvedLatitude: Double?
        get() = coordinates.split(",").getOrNull(0)?.trim()?.toDoubleOrNull()

    val resolvedLongitude: Double?
        get() = coordinates.split(",").getOrNull(1)?.trim()?.toDoubleOrNull()

    val hasCoordinates: Boolean
        get() = resolvedLatitude != null && resolvedLongitude != null

    /** Latitude used for route planning. Falls back to [resolvedLatitude] when no routing override is set. */
    val routingLatitude: Double?
        get() = routingCoordinates?.split(",")?.getOrNull(0)?.trim()?.toDoubleOrNull()
            ?: resolvedLatitude

    /** Longitude used for route planning. Falls back to [resolvedLongitude] when no routing override is set. */
    val routingLongitude: Double?
        get() = routingCoordinates?.split(",")?.getOrNull(1)?.trim()?.toDoubleOrNull()
            ?: resolvedLongitude

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
            coordinates = "40.9487, -4.1171",
            routesServed = listOf("L1", "L2", "L3"),
            stopCode = "001"
        )
    }
}
