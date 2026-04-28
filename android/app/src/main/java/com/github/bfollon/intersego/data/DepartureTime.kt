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
 * Represents a single bus departure time
 */
@Serializable
data class DepartureTime(
    val hour: Int,                      // 0-23
    val minute: Int,                    // 0-59
    val notes: String? = null,          // Optional notes: "Solo laborables", etc.
    val seasonalAvailability: SeasonalAvailability = SeasonalAvailability.YEAR_ROUND,
    val variantLabel: String? = null,    // Route variant label for display (e.g., "Regular", "Extendido", "Circular")
    val alternateLocationId: String? = null // ID of an AlternateLocation on the stop; null = primary coordinates
) : Comparable<DepartureTime> {

    fun alternateLocationName(stop: com.github.bfollon.intersego.data.BusStop): String? =
        alternateLocationId?.let { stop.alternateLocation(it)?.name }

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
         * Generate departure time lists for each stop in a cluster.
         *
         * Given anchor times (PDF times for the first stop) and a stop count,
         * returns one departure list per cluster stop. Stop at index `i` gets
         * each anchor time plus `i * offsetMinutes`.
         *
         * Example: `clusterDepartures(listOf(t(8,30), t(16,0)), 4, 2)` returns:
         *   [0] = [8:30, 16:00]   (anchor + 0)
         *   [1] = [8:32, 16:02]   (anchor + 2)
         *   [2] = [8:34, 16:04]   (anchor + 4)
         *   [3] = [8:36, 16:06]   (anchor + 6)
         */
        fun clusterDepartures(
            anchorTimes: List<DepartureTime>,
            stopCount: Int,
            offsetMinutes: Int = 2
        ): List<MutableList<DepartureTime>> {
            return (0 until stopCount).map { index ->
                anchorTimes.map { anchor ->
                    val totalMinutes = anchor.hour * 60 + anchor.minute + index * offsetMinutes
                    DepartureTime(
                        hour = (totalMinutes / 60) % 24,
                        minute = totalMinutes % 60,
                        seasonalAvailability = anchor.seasonalAvailability
                    )
                }.toMutableList()
            }
        }

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
