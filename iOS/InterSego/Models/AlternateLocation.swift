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

/// A named sub-location of a `BusStop` where specific trips depart or arrive
/// instead of the stop's primary coordinates.
struct AlternateLocation: Codable, Hashable {
    let id: String
    let name: String
    let coordinates: String // "lat, lon"

    var resolvedLatitude: Double? {
        let parts = coordinates.split(separator: ",")
        guard parts.count >= 2 else { return nil }
        return Double(parts[0].trimmingCharacters(in: .whitespaces))
    }

    var resolvedLongitude: Double? {
        let parts = coordinates.split(separator: ",")
        guard parts.count >= 2 else { return nil }
        return Double(parts[1].trimmingCharacters(in: .whitespaces))
    }
}
