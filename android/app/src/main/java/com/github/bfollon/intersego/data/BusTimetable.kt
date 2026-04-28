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
