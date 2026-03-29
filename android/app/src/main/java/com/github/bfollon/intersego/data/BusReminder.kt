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
 * Reminders are today-only: once [fireDateMillis] is in the past they are pruned.
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
    /** Epoch millis when the notification should fire (departure time − lead minutes). */
    val fireDateMillis: Long,
    /** Stable integer used as AlarmManager PendingIntent requestCode (avoids hashCode collisions). */
    val alarmRequestCode: Int,
    /** Non-null when the departure has a seasonal availability restriction. */
    val seasonalNote: String? = null
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
