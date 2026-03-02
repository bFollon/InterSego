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
    SUMMER_ONLY;

    fun runsIn(month: Month, summerMonths: Set<Month> = DEFAULT_SUMMER_MONTHS): Boolean = when (this) {
        YEAR_ROUND -> true
        SUMMER_ONLY -> month in summerMonths
        SCHOOL_ONLY -> month !in summerMonths
    };

    companion object {
        val DEFAULT_SUMMER_MONTHS: Set<Month> = setOf(Month.JULY, Month.AUGUST)
    }
}
