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

struct PDFParsingError: Error, LocalizedError {
    let message: String
    let underlying: Error?

    init(_ message: String, cause: Error? = nil) {
        self.message = message
        self.underlying = cause
    }

    var errorDescription: String? { message }
}

enum ParserMode {
    case debug
    case production
}

struct ParserCapabilities {
    let supportedRoutes: Set<String>
    let mode: ParserMode
    let version: String

    init(supportedRoutes: Set<String>, mode: ParserMode, version: String = "1.0") {
        self.supportedRoutes = supportedRoutes
        self.mode = mode
        self.version = version
    }
}

protocol BusTimetableParser {
    func parse(pdfPath: String, routeId: String) throws -> [BusTimetable]
    func canParse(routeId: String) -> Bool
}

protocol CapableParser: BusTimetableParser {
    var capabilities: ParserCapabilities { get }
}

protocol RouteStopsProvider {
    func getRoutesForId(_ routeId: String) -> [[BusStop]]
    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant]
    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]?
}

extension RouteStopsProvider {
    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        let routes = getRoutesForId(routeId)
        var variants: [RouteVariant] = []
        if let first = routes.first {
            variants.append(RouteVariant(id: "regular", label: "Regular", stops: first, direction: "Regular"))
        }
        if routes.count > 1 {
            variants.append(RouteVariant(id: "reverse", label: "Reverse", stops: routes[1], direction: "Reverse"))
        }
        return variants
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? { nil }
}
