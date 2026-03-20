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
