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
 * Represents a bus timetable for a specific route and stop
 */
@Serializable
data class BusTimetable(
    val id: String = UUID.randomUUID().toString(),
    val routeId: String,                // Route this timetable belongs to
    val stopId: String,                 // Stop this timetable is for
    val date: ScheduleDate? = null,     // Optional specific date (null if applies to all days of type)
    val dayType: DayType,               // WEEKDAY, WEEKEND, or HOLIDAY
    val departures: List<DepartureTime>, // List of departure times
    val direction: String? = null       // Optional direction: "Ida" or "Vuelta"
) {
    /**
     * Number of departures in this timetable
     */
    val departureCount: Int
        get() = departures.size

    /**
     * First departure time
     */
    val firstDeparture: DepartureTime?
        get() = departures.minOrNull()

    /**
     * Last departure time
     */
    val lastDeparture: DepartureTime?
        get() = departures.maxOrNull()

    /**
     * Get next N departures from a given time
     */
    fun getNextDepartures(currentHour: Int, currentMinute: Int, limit: Int = 5): List<DepartureTime> {
        return departures
            .filter { it.isFuture(currentHour, currentMinute) }
            .sorted()
            .take(limit)
    }

    /**
     * Check if there are any departures remaining today
     */
    fun hasRemainingDepartures(currentHour: Int, currentMinute: Int): Boolean {
        return departures.any { it.isFuture(currentHour, currentMinute) }
    }

    /**
     * Get display description for this timetable
     */
    fun getDisplayDescription(): String {
        val dayTypeStr = when (dayType) {
            DayType.WEEKDAY -> "Laborables"
            DayType.WEEKEND -> "Fines de semana"
            DayType.HOLIDAY -> "Festivos"
        }

        val directionStr = direction?.let { " - $it" } ?: ""
        val dateStr = date?.let { " (${it.toDisplayString()})" } ?: ""

        return "$dayTypeStr$directionStr$dateStr"
    }

    companion object {
        /**
         * Example weekday timetable
         */
        val exampleWeekday = BusTimetable(
            id = "tt-1-weekday",
            routeId = "L1",
            stopId = "stop-plaza-mayor",
            dayType = DayType.WEEKDAY,
            departures = listOf(
                DepartureTime(7, 0),
                DepartureTime(7, 30),
                DepartureTime(8, 0),
                DepartureTime(8, 30),
                DepartureTime(9, 0)
            ),
            direction = "Ida"
        )

        /**
         * Example weekend timetable
         */
        val exampleWeekend = BusTimetable(
            id = "tt-1-weekend",
            routeId = "L1",
            stopId = "stop-plaza-mayor",
            dayType = DayType.WEEKEND,
            departures = listOf(
                DepartureTime(8, 0),
                DepartureTime(9, 0),
                DepartureTime(10, 0),
                DepartureTime(11, 0),
                DepartureTime(12, 0)
            ),
            direction = "Ida"
        )
    }
}
