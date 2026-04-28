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
 * A selectable entry in the route picker sheet.
 *
 * Each entry represents one distinct route experience: a specific operating schedule
 * (day type) combined with a starting direction. Entries are shown in a "Rutas" bottom
 * sheet so users can browse routes that may not be running today.
 *
 * @param id Stable unique identifier for this entry.
 * @param label User-visible label (e.g., "L-V - Regular", "Sábado", "L-V - Segovia → Garcillán").
 * @param views All route views belonging to this entry, including swap targets.
 * @param initialViewId The view to display first when this entry is selected.
 * @param timetableDayType DayType used to look up timetables for non-today navigation.
 * @param isActiveToday True when this entry's schedule is in effect today.
 */
data class RouteSelectorEntry(
    val id: String,
    val label: String,
    val views: List<RouteView>,
    val initialViewId: String,
    val timetableDayType: DayType,
    val isActiveToday: Boolean
)
