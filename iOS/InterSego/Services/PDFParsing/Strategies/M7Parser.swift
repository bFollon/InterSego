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

/// Parser for M7 route (Segovia – Tabanera – Palazuelos – Segovia / Torrecaballeros)
///
/// M7 has three distinct service patterns:
///
/// **Weekday:** Circular Segovia → Tabanera → Palazuelos → Segovia; 18 trips.
///   First trip originates from Tabanera (no Segovia departure).
///
/// **Saturday/Sunday (extended route):** Segovia cluster → Palazuelos → Tabanera → San Cristóbal
///   → Sonsoto → Trescasas → Cabanillas → Torrecaballeros (and reverse).
///   Saturday: 5 outbound (13:30 partial to Trescasas), 4 inbound.
///   Sunday: 4 outbound (20:30 schoolOnly / 19:30 summerOnly partial to Trescasas), 4 inbound
///   (20:54 schoolOnly / 19:54 summerOnly start from Sonsoto).
///
/// Timetable data loaded from Timetables/m7.json (migrated from PDF 2026-03-25).
class M7Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M7"]),
        mode: .production,
        version: "1.6",
    )

    // MARK: - Directions

    private static let directionWeekdayCircular = "Circular"
    private static let directionSatSunOutbound = "Segovia → Torrecaballeros"
    private static let directionSatSunInbound = "Torrecaballeros → Segovia"

    // MARK: - Stops

    private enum Stops {
        /// Weekday circular stops
        static let segoviaWk = BusStopRegistry.estacionAutobuses
        static let tabaneraWk = BusStopRegistry.tabanera
        static let tabaneraWk2 = BusStopRegistry.tabanera2
        static let palazuelosWk = BusStopRegistry.palazuelos
        static let palazuelosColegioWk = BusStopRegistry.palazuelosColegio
        static let segoviaWkRet = BusStopRegistry.estacionAutobusesCircRet

        /// Saturday/Sunday outbound Segovia cluster (5 sub-stops)
        static let outEstacionBus = BusStopRegistry.estacionAutobuses
        static let outHospital = BusStopRegistry.hospitalSegovia
        static let outAndresLaguna = BusStopRegistry.andresLaguna
        static let outLaPista = BusStopRegistry.laPista
        static let outPlazaToros = BusStopRegistry.plazaDeToros

        /// Extended route stops
        static let palazuelos = BusStopRegistry.palazuelos
        static let tabanera = BusStopRegistry.tabanera
        static let tabanera2 = BusStopRegistry.tabanera2
        static let sCristobal = BusStopRegistry.sanCristobal
        static let sCristobalIglesia = BusStopRegistry.sanCristobalIglesia
        static let sCristobalRotonda = BusStopRegistry.sanCristobalRotonda
        static let sonsoto = BusStopRegistry.sonsoto
        static let sonsoto2 = BusStopRegistry.sonsoto2
        static let trescasas = BusStopRegistry.trescasas
        static let trescasas2 = BusStopRegistry.trescasas2
        static let cabanillas = BusStopRegistry.cabanillas
        static let torrecab = BusStopRegistry.torrecaballeros
        static let torrecab2 = BusStopRegistry.torrecaballeros2
        static let torrecab3 = BusStopRegistry.torrecaballeros3

        /// Saturday/Sunday inbound Segovia cluster (4 sub-stops)
        static let inPlazaToros = BusStopRegistry.plazaDeToros
        static let inLaPista = BusStopRegistry.laPista
        static let inAndresLaguna = BusStopRegistry.andresLaguna
        static let inJardinillos = BusStopRegistry.jardinillos
    }

    // Weekday circular: Segovia → Tabanera → Palazuelos (cluster) → Segovia
    static let m7WeekdayCircular: [BusStop] = [
        Stops.segoviaWk, Stops.tabaneraWk, Stops.tabaneraWk2,
        Stops.palazuelosWk, Stops.palazuelosColegioWk,
        Stops.segoviaWkRet,
    ]

    /// Saturday/Sunday outbound
    static let m7ExtOutbound: [BusStop] = [
        Stops.outEstacionBus, Stops.outHospital, Stops.outAndresLaguna,
        Stops.outLaPista, Stops.outPlazaToros,
        Stops.palazuelos, Stops.tabanera, Stops.tabanera2,
        Stops.sCristobal, Stops.sCristobalIglesia, Stops.sCristobalRotonda,
        Stops.sonsoto, Stops.sonsoto2,
        Stops.trescasas, Stops.trescasas2,
        Stops.cabanillas,
        Stops.torrecab, Stops.torrecab2, Stops.torrecab3,
    ]

    /// Saturday/Sunday inbound
    static let m7ExtInbound: [BusStop] = [
        Stops.torrecab3, Stops.torrecab2, Stops.torrecab,
        Stops.cabanillas,
        Stops.trescasas2, Stops.trescasas,
        Stops.sonsoto2, Stops.sonsoto,
        Stops.sCristobalRotonda, Stops.sCristobalIglesia, Stops.sCristobal,
        Stops.tabanera2, Stops.tabanera, Stops.palazuelos,
        Stops.inPlazaToros, Stops.inLaPista, Stops.inAndresLaguna,
        Stops.inJardinillos,
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains {
            $0.caseInsensitiveCompare(routeId) == .orderedSame
        }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M7") == .orderedSame else {
            return []
        }
        return [Self.m7WeekdayCircular, Self.m7ExtOutbound, Self.m7ExtInbound]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M7") == .orderedSame else {
            return []
        }
        switch dayType {
        case .weekday:
            return [
                RouteVariant(
                    id: "weekday-circular",
                    label: Self.directionWeekdayCircular,
                    stops: Self.m7WeekdayCircular,
                    direction: Self.directionWeekdayCircular,
                ),
            ]
        case .saturday:
            return [
                RouteVariant(
                    id: "saturday-outbound",
                    label: Self.directionSatSunOutbound,
                    stops: Self.m7ExtOutbound,
                    direction: Self.directionSatSunOutbound,
                ),
                RouteVariant(
                    id: "saturday-inbound",
                    label: Self.directionSatSunInbound,
                    stops: Self.m7ExtInbound,
                    direction: Self.directionSatSunInbound,
                ),
            ]
        case .sunday:
            return [
                RouteVariant(
                    id: "sunday-outbound",
                    label: Self.directionSatSunOutbound,
                    stops: Self.m7ExtOutbound,
                    direction: Self.directionSatSunOutbound,
                ),
                RouteVariant(
                    id: "sunday-inbound",
                    label: Self.directionSatSunInbound,
                    stops: Self.m7ExtInbound,
                    direction: Self.directionSatSunInbound,
                ),
            ]
        @unknown default:
            return []
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M7") == .orderedSame else {
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
        guard routeId.caseInsensitiveCompare("M7") == .orderedSame else {
            return []
        }
        let dow = Calendar.current.component(.weekday, from: today)
        let isWeekday = dow != 1 && dow != 7
        let isSaturday = dow == 7
        let isSunday = dow == 1
        guard let weekdayViews = getRouteViews(routeId, dayType: .weekday),
              let saturdayViews = getRouteViews(routeId, dayType: .saturday),
              let sundayViews = getRouteViews(routeId, dayType: .sunday)
        else { return [] }
        return [
            RouteSelectorEntry(
                id: "entry-lv",
                label: "Lunes a Viernes",
                views: weekdayViews,
                initialViewId: "weekday-circular",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-sabado",
                label: "Sábados",
                views: saturdayViews,
                initialViewId: "saturday-outbound",
                timetableDayType: .saturday,
                isActiveToday: isSaturday,
            ),
            RouteSelectorEntry(
                id: "entry-domingo",
                label: "Domingos",
                views: sundayViews,
                initialViewId: "sunday-outbound",
                timetableDayType: .sunday,
                isActiveToday: isSunday,
            ),
        ]
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M7Parser: loading timetable from JSON bundle")
        return try TimetableLoader().load("M7")
    }
}
