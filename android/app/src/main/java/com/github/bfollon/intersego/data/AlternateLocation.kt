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
 * A named sub-location of a [BusStop] where specific trips depart or arrive
 * instead of the stop's primary coordinates.
 */
@Serializable
data class AlternateLocation(
    val id: String,
    val name: String,
    val coordinates: String   // "lat, lon"
) {
    val resolvedLatitude: Double?
        get() = coordinates.split(",").getOrNull(0)?.trim()?.toDoubleOrNull()

    val resolvedLongitude: Double?
        get() = coordinates.split(",").getOrNull(1)?.trim()?.toDoubleOrNull()
}
