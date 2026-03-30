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

import java.util.Calendar
import kotlinx.serialization.Serializable

/**
 * Type of day for timetable schedules
 */
@Serializable
enum class DayType {
    /**
     * Monday through Friday (laborables)
     */
    WEEKDAY,

    SATURDAY,
    SUNDAY,

    /**
     * Saturday and Sunday (fines de semana)
     */
    WEEKEND,

    /**
     * Public holidays (festivos)
     */
    HOLIDAY
}

/**
 * Returns true if this day type applies on the given [Calendar.DAY_OF_WEEK] value.
 * HOLIDAY is treated as Sunday for scheduling purposes.
 */
fun DayType.matchesCalendarDay(dayOfWeek: Int): Boolean = when (this) {
    DayType.WEEKDAY -> dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
    DayType.SATURDAY -> dayOfWeek == Calendar.SATURDAY
    DayType.SUNDAY -> dayOfWeek == Calendar.SUNDAY
    DayType.WEEKEND -> dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    DayType.HOLIDAY -> dayOfWeek == Calendar.SUNDAY
}
