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
 * Request body for POST /boardings.
 */
@Serializable
data class BoardingRequest(
    val stopId: String,
    val routeId: String,
    val direction: String,
    val tripKey: String,
    val boardedAt: String,
    val scheduledDepartureTime: String? = null
) {
    companion object {
        /**
         * Build the synthetic trip key used by both the POST body and GET filtering.
         *
         * Format: "{routeId}|{direction}|{dayType}|{HH:MM}"
         * Example: "M4|Lastrilla → Sotillo|WEEKDAY|07:30"
         *
         * [dayType] must be one of WEEKDAY, SATURDAY, SUNDAY — use [DayType.name].
         */
        fun makeTripKey(
            routeId: String,
            direction: String,
            dayType: DayType,
            departure: DepartureTime
        ): String = "$routeId|$direction|${dayType.name}|${"%02d:%02d".format(departure.hour, departure.minute)}"
    }
}
