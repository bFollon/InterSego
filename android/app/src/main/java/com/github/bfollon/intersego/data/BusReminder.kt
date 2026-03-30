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
 * A scheduled reminder for a specific bus departure.
 *
 * One-off reminders ([isDaily] = false) are pruned once [fireDateMillis] is in the past.
 * Daily reminders ([isDaily] = true) persist until manually cancelled and re-schedule every day.
 */
@Serializable
data class BusReminder(
    val id: String,
    val routeId: String,
    val routeNumber: String,
    val stopId: String,
    val stopName: String,
    val direction: String,
    val departureHour: Int,
    val departureMinute: Int,
    /** Lead time snapshot at scheduling time (minutes before departure). */
    val leadMinutes: Int,
    /** Epoch millis of the next (or initial) alarm fire time. */
    val fireDateMillis: Long,
    /** Stable integer used as AlarmManager PendingIntent requestCode (avoids hashCode collisions). */
    val alarmRequestCode: Int,
    /** Non-null when the departure has a seasonal availability restriction. */
    val seasonalNote: String? = null,
    /** True when this reminder fires every day (smart-skips days the bus doesn't run). */
    val isDaily: Boolean = false,
    /** Seasonal availability stored so the receiver can skip non-running days. */
    val seasonalAvailability: SeasonalAvailability? = null,
    /** Day type this departure belongs to (used for smart-skip day-of-week check). */
    val dayType: DayType? = null
) {
    val departureDisplayString: String
        get() = "%02d:%02d".format(departureHour, departureMinute)

    /** Composite key that uniquely identifies a departure slot. */
    val matchKey: String
        get() = matchKey(routeId, stopId, direction, departureHour, departureMinute)

    companion object {
        fun matchKey(routeId: String, stopId: String, direction: String, hour: Int, minute: Int): String =
            "$routeId|$stopId|$direction|$hour:$minute"
    }
}
