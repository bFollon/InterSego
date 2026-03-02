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

struct BusStop: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let area: String?
    let details: String?
    let coordinates: String    // "40.948406, -4.116411"
    let routesServed: [String]
    let stopCode: String?

    init(id: String = UUID().uuidString, name: String, area: String? = nil,
         details: String? = nil, coordinates: String,
         routesServed: [String] = [], stopCode: String? = nil) {
        self.id = id
        self.name = name
        self.area = area
        self.details = details
        self.coordinates = coordinates
        self.routesServed = routesServed
        self.stopCode = stopCode
    }

    private var parsedCoordinates: (lat: Double, lon: Double)? {
        let parts = coordinates.split(separator: ",")
        guard parts.count >= 2,
              let lat = Double(parts[0].trimmingCharacters(in: .whitespaces)),
              let lon = Double(parts[1].trimmingCharacters(in: .whitespaces))
        else { return nil }
        return (lat, lon)
    }

    var resolvedLatitude: Double? { parsedCoordinates?.lat }
    var resolvedLongitude: Double? { parsedCoordinates?.lon }

    var hasCoordinates: Bool { parsedCoordinates != nil }

    var displayName: String {
        if let stopCode {
            return "\(name) (\(stopCode))"
        }
        return name
    }
}
