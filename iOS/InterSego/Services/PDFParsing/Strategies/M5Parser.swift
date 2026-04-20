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

/// Parser for M5 route (Azoguejo – Sto. Domingo de Pirón)
///
/// M5 is a linear route operating weekdays and Saturdays. The bus travels from
/// Azoguejo to Santo Domingo de Pirón via Tizneros, Espirdo, La Higuera, Brieva,
/// and Basardilla.
///
/// Partial trips: some weekday services only run Azoguejo–La Higuera (outbound) or
/// start from Brieva/La Higuera (inbound). Stops not served are null in the JSON.
///
/// Departure location footnotes:
///   Weekday outbound + inbound: Azoguejo (primary stop)
///   Saturday outbound + inbound: Estación de Autobuses (alternateId = "estacion-autobuses")
///
/// No Sunday service. No seasonal restrictions.
///
/// Timetable data loaded from Timetables/m5.json.
class M5Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M5"]),
        mode: .production,
        version: "1.4",
    )

    // MARK: - Directions

    private static let directionOutbound = "Azoguejo → Sto. Domingo de Pirón"
    private static let directionInbound = "Sto. Domingo de Pirón → Azoguejo"

    // MARK: - Stops

    // Outbound: Azoguejo → Sto. Domingo de Pirón
    static let m5Outbound: [BusStop] = [
        BusStopRegistry.azoguejo,
        BusStopRegistry.tizneros,
        BusStopRegistry.espirdo,
        BusStopRegistry.laHiguera,
        BusStopRegistry.brieva,
        BusStopRegistry.basardilla,
        BusStopRegistry.stoDomingoPiron,
    ]

    // Inbound: Sto. Domingo de Pirón → Azoguejo
    static let m5Inbound: [BusStop] = [
        BusStopRegistry.stoDomingoPiron,
        BusStopRegistry.basardilla,
        BusStopRegistry.brieva,
        BusStopRegistry.laHiguera,
        BusStopRegistry.espirdo,
        BusStopRegistry.tizneros,
        BusStopRegistry.azoguejo,
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes
            .contains { $0.caseInsensitiveCompare(routeId) == .orderedSame }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M5") == .orderedSame else { return [] }
        return [Self.m5Outbound, Self.m5Inbound]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M5") == .orderedSame else { return [] }
        switch dayType {
        case .weekday, .saturday:
            return [
                RouteVariant(id: "regular", label: Self.directionOutbound,
                             stops: Self.m5Outbound, direction: Self.directionOutbound),
                RouteVariant(id: "reverse", label: Self.directionInbound,
                             stops: Self.m5Inbound, direction: Self.directionInbound),
            ]
        default:
            return []
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M5") == .orderedSame else { return nil }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        return variants.enumerated().map { index, variant in
            let swapTargetId = variants.count == 2 ? variants[1 - index].id : nil
            return RouteView(
                id: variant.id, label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction, departureLabel: variant.departureLabel,
                swapAction: swapTargetId.map { SwapAction(targetViewId: $0) },
            )
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M5") == .orderedSame else { return [] }
        let dow = Calendar.current.component(.weekday, from: today)
        let isWeekday = dow != 1 && dow != 7
        let isSaturday = dow == 7
        guard let weekdayViews = getRouteViews(routeId, dayType: .weekday),
              let saturdayViews = getRouteViews(routeId, dayType: .saturday)
        else { return [] }
        return [
            RouteSelectorEntry(
                id: "entry-lv",
                label: "Lunes a Viernes",
                views: weekdayViews,
                initialViewId: "regular",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-sabado",
                label: "Sábados",
                views: saturdayViews,
                initialViewId: "regular",
                timetableDayType: .saturday,
                isActiveToday: isSaturday,
            ),
        ]
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M5Parser: loading timetable from JSON bundle")
        return try TimetableLoader().load("M5")
    }
}
