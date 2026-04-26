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

class M2Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M2"]),
        mode: .production,
        version: "1.2",
    )

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains { $0.caseInsensitiveCompare(routeId) == .orderedSame }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        (try? TimetableLoader().loadRoutesForId(routeId)) ?? []
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        (try? TimetableLoader().loadRouteVariants(routeId, dayType: dayType)) ?? []
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        try? TimetableLoader().loadRouteViews(routeId, dayType: dayType)
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        (try? TimetableLoader().loadRouteEntries(routeId, today: today)) ?? []
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M2Parser: loading timetable from bundled JSON")
        return try TimetableLoader().load("M2")
    }
}
