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

package com.github.bfollon.intersego.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/**
 * Represents a bus timetable for a specific route and stop
 */
@Serializable
data class BusTimetable(
    val id: String = UUID.randomUUID().toString(),
    val routeId: String,                // Route this timetable belongs to
    val stopId: String,                 // Stop this timetable is for
    val dayType: DayType,               // WEEKDAY, WEEKEND, or HOLIDAY
    val departures: List<DepartureTime>, // List of departure times
    val direction: String? = null,       // Optional direction: "Ida" or "Vuelta"
) {
    /**
     * Departures filtered for the current season (month) and optional day of week.
     *
     * @param month   the calendar month (defaults to today)
     * @param weekday Calendar.DAY_OF_WEEK value (Sun=1…Sat=7); null disables day-of-week filtering
     */
    fun seasonalDepartures(
        month: java.time.Month = LocalDate.now().month,
        weekday: Int? = null
    ): List<DepartureTime> = departures.filter { it.seasonalAvailability.runsIn(month, weekday) }

    val firstDeparture: DepartureTime?
        get() = seasonalDepartures().minOrNull()

    val lastDeparture: DepartureTime?
        get() = seasonalDepartures().maxOrNull()

    fun getNextDepartures(currentHour: Int, currentMinute: Int, limit: Int = 5): List<DepartureTime> {
        return seasonalDepartures()
            .filter { it.isFuture(currentHour, currentMinute) }
            .sorted()
            .take(limit)
    }

    fun hasRemainingDepartures(currentHour: Int, currentMinute: Int): Boolean {
        return seasonalDepartures().any { it.isFuture(currentHour, currentMinute) }
    }

    /**
     * Get display description for this timetable
     */
    fun getDisplayDescription(): String {
        val dayTypeStr = when (dayType) {
            DayType.WEEKDAY -> "Laborables"
            DayType.WEEKEND -> "Fines de semana"
            DayType.HOLIDAY -> "Festivos"
            DayType.SATURDAY -> "Sábado"
            DayType.SUNDAY -> "Domingo"
        }

        val directionStr = direction?.let { " - $it" } ?: ""

        return "$dayTypeStr$directionStr"
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
