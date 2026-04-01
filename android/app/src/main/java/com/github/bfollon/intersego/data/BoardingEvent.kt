/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follón
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
