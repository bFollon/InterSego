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
        version: "1.0",
    )

    // MARK: - Directions

    private static let directionCircularA = "Segovia → Valseca"
    private static let directionCircularB = "Valseca → Segovia"

    // MARK: - Stops

    private enum Stops {
        /// Outbound stops
        static let segovia = BusStop(
            id: "m2-segovia",
            name: "Estación de Autobuses",
            area: "Segovia Capital",
            coordinates: "40.944973, -4.122431",
        )
        static let casino = BusStop(
            id: "m2-casino",
            name: "Casino",
            area: "Casino de la Unión",
            coordinates: "40.965154, -4.209251",
        )
        static let hontanares = BusStop(
            id: "m2-hontanares",
            name: "Hontanares de Eresma",
            area: "Hontanares de Eresma",
            coordinates: "40.983628, -4.204160",
        )
        static let losHuertos = BusStop(
            id: "m2-los-huertos",
            name: "Los Huertos",
            area: "Los Huertos",
            coordinates: "41.009124, -4.219216",
        )
        static let valseca = BusStop(
            id: "m2-valseca",
            name: "Valseca",
            area: "Valseca",
            coordinates: "40.999306, -4.174266",
        )
        /// Return-leg stops — same physical locations, distinct IDs for route positioning
        static let hontanaresToReturn = BusStop(
            id: "m2-hontanares-return",
            name: "Hontanares de Eresma",
            area: "Hontanares de Eresma",
            coordinates: "40.983628, -4.204160",
        )
        static let casinoReturn = BusStop(
            id: "m2-casino-return",
            name: "Casino",
            area: "Casino de la Unión",
            coordinates: "40.965154, -4.209251",
        )
        static let segoviaReturn = BusStop(
            id: "m2-segovia-return",
            name: "Estación de Autobuses",
            area: "Segovia Capital",
            coordinates: "40.944973, -4.122431",
        )
    }

    // circularA: outbound view — Segovia → Casino → Hontanares → Los Huertos → Valseca
    static let m2CircularA: [BusStop] = [
        Stops.segovia, Stops.casino, Stops.hontanares, Stops.losHuertos,
        Stops.valseca,
    ]

    // circularB: return view — Los Huertos → Hontanares → Valseca → Casino → Segovia
    // Note: losHuertos and valseca share stop IDs with circularA (same physical stops).
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
        DebugConfig.debugPrint(
            "M2Parser: returning hardcoded timetable (PDF parsing bypassed)",
        )
        return buildStaticTimetables()
    }

    // MARK: - Static Timetable

    //
    // Source: Linecar M2 PDF screenshot, 2026-03-20.
    // Weekday service only (Lunes a Viernes). No Saturday or Sunday service.
    //
    // The route is circular: the bus travels outbound from Segovia to Los Huertos, backtracks
    // through Hontanares, continues to Valseca, then returns to Segovia.
    // The left (outbound) and right (inbound) tables in the PDF are complementary halves of
    // the same circular service.

    private func buildStaticTimetables() -> [BusTimetable] {
        // ── Weekday circularA: Segovia → Casino → Hontanares → Los Huertos → Valseca ──────────
        let circADeps: [[DepartureTime]] = [
            [t(9, 0), t(13, 45), t(16, 15), t(19, 15)], // SEGOVIA
            [t(9, 5), t(13, 50), t(16, 20), t(19, 20)], // CASINO
            [t(9, 10), t(13, 55), t(16, 25), t(19, 25)], // HONTANARES
            [t(9, 15), t(14, 0), t(16, 30), t(19, 30)], // LOS_HUERTOS
            [t(9, 30), t(14, 15), t(16, 40), t(19, 40)], // VALSECA
        ]

        // ── Weekday circularB: Los Huertos → Hontanares → Valseca → Casino → Segovia ──────────
        //
        // The 7:25 service originates from Valseca: its stop times are Valseca(7:25),
        // Los Huertos(7:35), Hontanares(7:40), Casino(7:42), Segovia(7:55).
        // In this stop list Valseca appears at position 2 (after Los Huertos), so the 7:25
        // Valseca time is chronologically earlier than Los Huertos 7:35. All other runs
        // (9:15, 14:00, 16:30, 19:30) follow the expected stop order.
        let circBDeps: [[DepartureTime]] = [
            [t(7, 35), t(9, 15), t(14, 0), t(16, 30), t(19, 30)], // LOS_HUERTOS
            [t(7, 40), t(9, 20), t(14, 5), t(16, 35), t(19, 35)], // HONTANARES_RETURN
            [t(7, 25), t(9, 30), t(14, 15), t(16, 45), t(19, 45)], // VALSECA (7:25 = Valseca-originating run)
            [t(7, 42), t(9, 35), t(14, 20), t(16, 50), t(19, 50)], // CASINO_RETURN
            [t(7, 55), t(9, 50), t(14, 35), t(17, 5), t(20, 5)], // SEGOVIA_RETURN
        ]

        return buildTimetables(
            stops: Self.m2CircularA,
            dayType: .weekday,
            direction: Self.directionCircularA,
            deps: circADeps,
        )
            + buildTimetables(
                stops: Self.m2CircularB,
                dayType: .weekday,
                direction: Self.directionCircularB,
                deps: circBDeps,
            )
    }

    private func t(_ h: Int, _ m: Int, _ s: SeasonalAvailability = .yearRound)
        -> DepartureTime
    {
        DepartureTime(hour: h, minute: m, seasonalAvailability: s)
    }

    private func buildTimetables(
        stops: [BusStop],
        dayType: DayType,
        direction: String,
        deps: [[DepartureTime]],
    ) -> [BusTimetable] {
        stops.enumerated().map { i, stop in
            BusTimetable(
                routeId: "M2",
                stopId: stop.id,
                dayType: dayType,
                departures: deps[i],
                direction: direction,
            )
        }
    }
}
