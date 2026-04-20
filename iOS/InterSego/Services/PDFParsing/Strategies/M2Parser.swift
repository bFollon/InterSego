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

/// Parser for M2 route (Segovia – Hontanares – Los Huertos – Valseca)
///
/// M2 is a circular route operating Monday–Friday only. Each service departs Segovia,
/// goes outbound through Casino, Hontanares, and Los Huertos, then backtracks through
/// Hontanares to reach Valseca before returning to Segovia:
///
///   Segovia → Casino → Hontanares → Los Huertos → Hontanares → Valseca → Casino → Segovia
///
/// The timetable PDF shows two half-tables:
///   circularA (left): Segovia → Casino → Hontanares → Los Huertos → Valseca (outbound view)
///   circularB (right): Los Huertos → Hontanares → Valseca → Casino → Segovia (return view)
///
/// Note: The 7:25 inbound service originates from Valseca and follows the direct reverse
/// of the outbound route (Valseca → Los Huertos → Hontanares → Casino → Segovia). In the
/// circularB stop list this means Valseca's departure time (7:25) precedes Los Huertos (7:35),
/// which is chronologically out of stop order for that run. All other circularB services
/// follow the expected stop sequence.
///
/// No weekend service. No seasonal restrictions.
///
/// Timetable data hardcoded from official Linecar M2 PDF screenshot (2026-03-20).
class M2Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M2"]),
        mode: .production,
        version: "1.2",
    )

    // MARK: - Directions

    private static let directionCircularA = "Segovia → Valseca"
    private static let directionCircularB = "Valseca → Segovia"

    // MARK: - Stops

    private enum Stops {
        static let segovia = BusStopRegistry.estacionAutobuses
        static let casino = BusStopRegistry.casinoUnion
        static let hontanares = BusStopRegistry.hontanares
        static let losHuertos = BusStopRegistry.losHuertos
        static let valseca = BusStopRegistry.valseca
        /// Return-leg stops — same canonical stops; direction string distinguishes timetable buckets
        static let hontanaresToReturn = BusStopRegistry.hontanares
        static let casinoReturn = BusStopRegistry.casinoUnion
        static let segoviaReturn = BusStopRegistry.estacionAutobuses
    }

    // circularA: outbound view — Segovia → Casino → Hontanares → Los Huertos → Valseca
    static let m2CircularA: [BusStop] = [
        Stops.segovia, Stops.casino, Stops.hontanares, Stops.losHuertos,
        Stops.valseca,
    ]

    // circularB: return view — Los Huertos → Hontanares → Valseca → Casino → Segovia
    static let m2CircularB: [BusStop] = [
        Stops.losHuertos, Stops.hontanaresToReturn, Stops.valseca,
        Stops.casinoReturn, Stops.segoviaReturn,
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains {
            $0.caseInsensitiveCompare(routeId) == .orderedSame
        }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M2") == .orderedSame else {
            return []
        }
        return [Self.m2CircularA, Self.m2CircularB]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M2") == .orderedSame else {
            return []
        }
        switch dayType {
        case .saturday, .sunday:
            return []
        default:
            return [
                RouteVariant(
                    id: "circularA",
                    label: Self.directionCircularA,
                    stops: Self.m2CircularA,
                    direction: Self.directionCircularA,
                ),
                RouteVariant(
                    id: "circularB",
                    label: Self.directionCircularB,
                    stops: Self.m2CircularB,
                    direction: Self.directionCircularB,
                ),
            ]
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M2") == .orderedSame else {
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
        guard routeId.caseInsensitiveCompare("M2") == .orderedSame else {
            return []
        }
        guard let weekdayViews = getRouteViews(routeId, dayType: .weekday)
        else { return [] }
        let dow = Calendar.current.component(.weekday, from: today)
        let isWeekday = dow != 7 && dow != 1
        return [
            RouteSelectorEntry(
                id: "entry-lv",
                label: "Lunes a Viernes",
                views: weekdayViews,
                initialViewId: "circularA",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
        ]
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M2Parser: loading timetable from bundled JSON")
        return try TimetableLoader().load("M2")
    }
}
