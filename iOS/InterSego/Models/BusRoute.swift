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

struct BusRoute: Codable, Identifiable, Hashable {
    let id: String
    let number: String // "1", "2", "40"
    let name: String // "Centro - San Lorenzo"
    let origin: String
    let destination: String
    let pdfURL: String
    let routeType: RouteType
    let isCircular: Bool
    let color: String?
    let active: Bool

    init(id: String = UUID().uuidString, number: String, name: String,
         origin: String, destination: String, pdfURL: String,
         routeType: RouteType, isCircular: Bool = false, color: String? = nil, active: Bool = true)
    {
        self.id = id
        self.number = number
        self.name = name
        self.origin = origin
        self.destination = destination
        self.pdfURL = pdfURL
        self.routeType = routeType
        self.isCircular = isCircular
        self.color = color
        self.active = active
    }

    var displayName: String {
        "Línea \(number): \(name)"
    }

    var shortName: String {
        "L\(number)"
    }
}
