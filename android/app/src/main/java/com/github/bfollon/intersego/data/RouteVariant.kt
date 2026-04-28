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
