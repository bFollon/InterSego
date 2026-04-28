/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
    val tabsLabel: String? = null,
    val extendedSectionLabel: String? = null,
    val mergedDirectionLabel: String? = null
)
