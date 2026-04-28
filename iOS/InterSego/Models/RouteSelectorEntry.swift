/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// A selectable entry in the route picker sheet.
///
/// Each entry represents one distinct route experience: a specific operating schedule
/// (day type) combined with a starting direction. Entries are shown in a "Rutas" sheet
/// so users can browse routes that may not be running today.
struct RouteSelectorEntry {
    /// Stable unique identifier for this entry.
    let id: String
    /// User-visible label (e.g., "L-V - Regular", "Sábado", "L-V - Segovia → Garcillán").
    let label: String
    /// All route views belonging to this entry, including swap targets.
    let views: [RouteView]
    /// The view to display first when this entry is selected.
    let initialViewId: String
    /// DayType used to look up timetables for non-today navigation.
    let timetableDayType: DayType
    /// True when this entry's schedule is in effect today.
    let isActiveToday: Bool
}
