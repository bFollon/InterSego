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
