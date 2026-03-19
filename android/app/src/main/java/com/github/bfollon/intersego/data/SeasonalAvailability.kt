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
import java.time.Month

@Serializable
enum class SeasonalAvailability {
    YEAR_ROUND,
    SCHOOL_ONLY,
    /** Runs July–August only (default summer months, used by M4). */
    SUMMER_ONLY,
    /** Runs June–September only (M1 summer service: 13 Jun – 13 Sep, approximated as full months). */
    JUNE_TO_SEPT_ONLY,
    /** Runs on Mondays and Fridays only (L Y V annotation in M1). */
    MON_FRI_ONLY,
    /** Runs on Fridays only (# annotation in M1). */
    FRI_ONLY;

    /**
     * Returns true if this departure runs in the given [month] (1–12) and optional [weekday]
     * (Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, …, Saturday=7).
     *
     * When [weekday] is null, day-of-week restrictions ([MON_FRI_ONLY], [FRI_ONLY]) are treated
     * as unrestricted — useful for callers that only have month context.
     */
    fun runsIn(
        month: Month,
        weekday: Int? = null,
        summerMonths: Set<Month> = DEFAULT_SUMMER_MONTHS
    ): Boolean = when (this) {
        YEAR_ROUND        -> true
        SUMMER_ONLY       -> month in summerMonths
        JUNE_TO_SEPT_ONLY -> month in JUNE_TO_SEPT_MONTHS
        SCHOOL_ONLY       -> month !in summerMonths
        MON_FRI_ONLY      -> weekday == null || weekday == java.util.Calendar.MONDAY || weekday == java.util.Calendar.FRIDAY
        FRI_ONLY          -> weekday == null || weekday == java.util.Calendar.FRIDAY
    }

    companion object {
        val DEFAULT_SUMMER_MONTHS: Set<Month> = setOf(Month.JULY, Month.AUGUST)
        val JUNE_TO_SEPT_MONTHS: Set<Month>   = setOf(Month.JUNE, Month.JULY, Month.AUGUST, Month.SEPTEMBER)
    }
}
