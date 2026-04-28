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
 * A boarding event returned by GET /boardings.
 *
 * The server stores these as-is; all trip matching and ETA computation
 * happen on the client using local timetable data.
 */
@Serializable
data class BoardingEvent(
    val id: String,
    val stopId: String,
    val routeId: String,
    val direction: String,
    /** Synthetic trip key: "{routeId}|{direction}|{dayType}|{HH:MM}" */
    val tripKey: String,
    /** ISO 8601 UTC timestamp of when the user boarded. */
    val boardedAt: String,
    /**
     * ISO 8601 UTC timestamp of the scheduled departure at the boarding stop.
     * Null if the submitting client did not include it.
     */
    val scheduledDepartureTime: String? = null,
    val expiresAt: String
)
