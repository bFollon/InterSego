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

/**
 * Represents a route variant for display in the UI.
 *
 * Each variant has its own stop list and direction, allowing routes with
 * multiple variants (like M6) to be presented individually with correct
 * stop sequences.
 *
 * @param id Unique identifier for this variant
 * @param label Display label shown in the dropdown (e.g., "Segovia → Torrecaballeros (Extendido)")
 * @param stops Ordered list of stops for this variant
 * @param direction Direction string matching BusTimetable.direction for timetable filtering
 * @param departureLabel Label stamped on DepartureTime.variantLabel for this variant's departures.
 *   Used to filter which badges are shown (badges only appear for departures from other variants).
 */
data class RouteVariant(
    val id: String,
    val label: String,
    val stops: List<BusStop>,
    val direction: String,
    val departureLabel: String? = null
)
