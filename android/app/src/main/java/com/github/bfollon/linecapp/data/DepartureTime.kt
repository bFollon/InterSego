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

/**
 * Represents a single bus departure time
 */
@Serializable
data class DepartureTime(
    val hour: Int,                      // 0-23
    val minute: Int,                    // 0-59
    val notes: String? = null,          // Optional notes: "Solo laborables", etc.
    val runsInSummer: Boolean = true,   // true = runs year-round (including July/August), false = only runs Sept-June
    val variantLabel: String? = null     // Route variant label for display (e.g., "Regular", "Extendido", "Circular")
) : Comparable<DepartureTime> {

    init {
        require(hour in 0..23) { "Hour must be between 0 and 23, got $hour" }
        require(minute in 0..59) { "Minute must be between 0 and 59, got $minute" }
    }

    /**
     * Convert to display string in HH:MM format
     */
    fun toDisplayString(): String = "%02d:%02d".format(hour, minute)

    /**
     * Convert to minutes since midnight for easy comparison
     */
    fun toMinutesSinceMidnight(): Int = hour * 60 + minute

    /**
     * Check if this departure is in the past relative to current time
     */
    fun isPast(currentHour: Int, currentMinute: Int): Boolean {
        val currentMinutes = currentHour * 60 + currentMinute
        return toMinutesSinceMidnight() < currentMinutes
    }

    /**
     * Check if this departure is in the future relative to current time
     */
    fun isFuture(currentHour: Int, currentMinute: Int): Boolean {
        return !isPast(currentHour, currentMinute)
    }

    /**
     * Calculate minutes until this departure from current time
     * Returns negative if departure is in the past
     */
    fun minutesUntil(currentHour: Int, currentMinute: Int): Int {
        val currentMinutes = currentHour * 60 + currentMinute
        return toMinutesSinceMidnight() - currentMinutes
    }

    /**
     * Compare departure times for sorting
     */
    override fun compareTo(other: DepartureTime): Int {
        return toMinutesSinceMidnight().compareTo(other.toMinutesSinceMidnight())
    }

    companion object {
        /**
         * Parse departure time from HH:MM string
         */
        fun fromString(timeString: String): DepartureTime? {
            val parts = timeString.split(":")
            if (parts.size != 2) return null

            val hour = parts[0].trim().toIntOrNull() ?: return null
            val minute = parts[1].trim().toIntOrNull() ?: return null

            return try {
                DepartureTime(hour, minute)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /**
         * Example departure times
         */
        val example1 = DepartureTime(8, 30)
        val example2 = DepartureTime(9, 15, "Solo laborables")
        val example3 = DepartureTime(14, 45)
    }
}
