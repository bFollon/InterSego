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

/**
 * A stop in a route view with display metadata.
 */
data class RouteViewStop(
    val stop: BusStop,
    val isExtendedOnly: Boolean = false
)

/**
 * Describes the swap (direction flip) action for a route view.
 */
data class SwapAction(
    val targetViewId: String
)

/**
 * A tab/chip entry for the route type selector.
 */
data class RouteTab(
    val label: String,
    val viewId: String
)

/**
 * A self-describing view of a route for RouteStopsScreen.
 *
 * Contains all information the screen needs to render the route display,
 * including stop list, direction swap, tab selector, and extended section markers.
 * The screen acts as a dumb renderer with no route-specific logic.
 */
data class RouteView(
    val id: String,
    val label: String,
    val stops: List<RouteViewStop>,
    val direction: String,
    val departureLabel: String? = null,
    val swapAction: SwapAction? = null,
    val tabs: List<RouteTab>? = null,
    val extendedSectionLabel: String? = null
)
