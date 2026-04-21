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

/// Parser for M1 route (Segovia – Garcillán via Polígono, Casino, Valverde, Abades, Martín Miguel)
///
/// M1 is a circular route with two main directions:
///   Direction A (circularA): Segovia → Polígono → Polígono2 → Casino → Valverde → Abades
///                             → Martín Miguel → Garcillán → Segovia (full outbound, direct return)
///   Direction B (circularB): Segovia → Polígono → Polígono2 → Garcillán → Martín Miguel
///                             → Abades → Valverde → Casino → Polígono2 → Polígono → Segovia
///                             (direct outbound, return via all villages)
///
/// Saturday runs a shorter Segovia ↔ Abades variant (no Polígono, no Garcillán).
/// No Sunday service.
///
/// Timetable data loaded from Timetables/m1.json.
/// Annotations encoded in JSON:
///   ★  = juneToSept (Jun 13–Sep 13 only)
///   (*) = garcillan-gasolinera alternateId (different pickup location, year-round)
///   L Y V = monFriOnly
///   #  = friOnly
class M1Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M1"]),
        mode: .production,
        version: "2.1",
    )

    // MARK: - Directions

    private static let directionCircularA = "Segovia → Garcillán"
    private static let directionCircularB = "Garcillán → Segovia"
    private static let directionSatOutbound = "Segovia → Abades"
    private static let directionSatInbound = "Abades → Segovia"

    // MARK: - Stops

    private enum Stops {
        static let segovia = BusStopRegistry.estacionAutobuses
        static let poligono = BusStopRegistry.poligonoIndM1
        static let poligono2 = BusStopRegistry.poligonoIndM1B
        static let casino = BusStopRegistry.casinoUnion
        static let valverde = BusStopRegistry.valverdeMajano
        static let abades = BusStopRegistry.abades
        static let martinMiguel = BusStopRegistry.martinMiguel
        static let garcillan = BusStopRegistry.garcillan
        static let segoviaReturn = BusStopRegistry.estacionAutobusesCircRet
    }

    /// Direction A: Segovia → (all villages) → Garcillán → Segovia (return)
    static let m1CircularAWeekday: [BusStop] = [
        Stops.segovia, Stops.poligono, Stops.poligono2,
        Stops.casino, Stops.valverde, Stops.abades, Stops.martinMiguel,
        Stops.garcillan, Stops.segoviaReturn,
    ]

    /// Direction B: Garcillán → (all villages) → Segovia
    static let m1CircularBWeekday: [BusStop] = [
        Stops.garcillan, Stops.martinMiguel, Stops.abades, Stops.valverde,
        Stops.casino, Stops.poligono2, Stops.poligono, Stops.segovia,
    ]

    // Saturday: shorter Segovia ↔ Abades route
    static let m1SaturdayOutbound: [BusStop] = [
        Stops.segovia, Stops.casino, Stops.valverde, Stops.abades,
    ]
    static let m1SaturdayInbound: [BusStop] = [
        Stops.abades, Stops.valverde, Stops.segovia,
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains {
            $0.caseInsensitiveCompare(routeId) == .orderedSame
        }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else {
            return []
        }
        return [Self.m1CircularAWeekday, Self.m1CircularBWeekday, Self.m1SaturdayOutbound, Self.m1SaturdayInbound]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else {
            return []
        }
        switch dayType {
        case .saturday:
            return [
                RouteVariant(
                    id: "saturday-outbound",
                    label: Self.directionSatOutbound,
                    stops: Self.m1SaturdayOutbound,
                    direction: Self.directionSatOutbound,
                ),
                RouteVariant(
                    id: "saturday-inbound",
                    label: Self.directionSatInbound,
                    stops: Self.m1SaturdayInbound,
                    direction: Self.directionSatInbound,
                ),
            ]
        case .sunday:
            return []
        default:
            return [
                RouteVariant(
                    id: "circularA",
                    label: Self.directionCircularA,
                    stops: Self.m1CircularAWeekday,
                    direction: Self.directionCircularA,
                ),
                RouteVariant(
                    id: "circularB",
                    label: Self.directionCircularB,
                    stops: Self.m1CircularBWeekday,
                    direction: Self.directionCircularB,
                ),
            ]
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else {
            return nil
        }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        return variants.enumerated().map { index, variant in
            let swapTargetId =
                variants.count == 2 ? variants[1 - index].id : nil
            return RouteView(
                id: variant.id,
                label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction,
                departureLabel: variant.departureLabel,
                swapAction: swapTargetId.map { SwapAction(targetViewId: $0) },
            )
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else {
            return []
        }
        let dow = Calendar.current.component(.weekday, from: today) // 1=Sun, 7=Sat
        let isWeekday = dow != 7 && dow != 1
        let isSaturday = dow == 7
        guard let weekdayViews = getRouteViews(routeId, dayType: .weekday),
              let saturdayViews = getRouteViews(routeId, dayType: .saturday)
        else { return [] }
        return [
            RouteSelectorEntry(
                id: "entry-lv-a",
                label: "L-V - \(Self.directionCircularA)",
                views: weekdayViews,
                initialViewId: "circularA",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-lv-b",
                label: "L-V - \(Self.directionCircularB)",
                views: weekdayViews,
                initialViewId: "circularB",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-sab-a",
                label: "Sáb - \(Self.directionSatOutbound)",
                views: saturdayViews,
                initialViewId: "saturday-outbound",
                timetableDayType: .saturday,
                isActiveToday: isSaturday,
            ),
            RouteSelectorEntry(
                id: "entry-sab-b",
                label: "Sáb - \(Self.directionSatInbound)",
                views: saturdayViews,
                initialViewId: "saturday-inbound",
                timetableDayType: .saturday,
                isActiveToday: isSaturday,
            ),
        ]
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M1Parser: loading timetable from JSON bundle")
        return try TimetableLoader().load("M1")
    }
}
