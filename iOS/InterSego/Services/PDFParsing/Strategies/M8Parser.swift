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

/// Parser for M8 route (Segovia – La Granja – Valsaín – Segovia)
///
/// Linear route running between Segovia and Valsaín via La Granja de San Ildefonso.
/// Service runs on weekdays, Saturdays, and Sundays/holidays.
///
/// Timetable data loaded from Timetables/m8.json (migrated from PDF 2026-03-25).
class M8Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M8"]),
        mode: .production,
        version: "1.2",
    )

    // MARK: - Directions

    private static let directionOutbound = "Segovia → Valsaín"
    private static let directionInbound = "Valsaín → Segovia"

    // MARK: - Stops

    private enum Stops {
        /// Segovia cluster (shared outbound/inbound)
        static let outEstacionBus = BusStopRegistry.estacionAutobuses
        static let outIglesiaStoTomas = BusStopRegistry.iglesiaStTomas
        static let outFrenteBarNorte = BusStopRegistry.frenteBarNorte
        static let outPlazaToros = BusStopRegistry.plazaDeToros

        /// Main route stops
        static let carrascalejo = BusStopRegistry.urbCarrascalejo
        static let penasDElErizo = BusStopRegistry.penasDelErizo
        static let cLaFuencisla = BusStopRegistry.cLaFuencisla
        static let parqueRobledo = BusStopRegistry.parqueRobledo
        static let fabricaCristal = BusStopRegistry.fabricaCristal
        static let piscinas = BusStopRegistry.piscinas
        static let ptasSegovia = BusStopRegistry.ptasSegovia
        static let laPradera = BusStopRegistry.laPradera
        static let fronton = BusStopRegistry.fronton
        static let plaza = BusStopRegistry.plazaValsain

        // Inbound aliases (same canonical stops)
        static let inPlazaToros = BusStopRegistry.plazaDeToros
        static let inFrenteBarNorte = BusStopRegistry.frenteBarNorte
        static let inIglesiaStoTomas = BusStopRegistry.iglesiaStTomas
        static let inEstacionBus = BusStopRegistry.estacionAutobuses
    }

    // MARK: - Stop Lists

    /// Outbound: Segovia cluster → La Granja → Valsaín
    static let m8Outbound: [BusStop] = [
        Stops.outEstacionBus, Stops.outIglesiaStoTomas,
        Stops.outFrenteBarNorte, Stops.outPlazaToros,
        Stops.carrascalejo, Stops.penasDElErizo, Stops.cLaFuencisla,
        Stops.parqueRobledo, Stops.fabricaCristal, Stops.piscinas,
        Stops.ptasSegovia, Stops.laPradera, Stops.fronton, Stops.plaza,
    ]

    /// Inbound: Valsaín → La Granja → Segovia cluster
    /// Note: F. Cristal appears before Piscinas in inbound (one-way routing through La Granja)
    static let m8Inbound: [BusStop] = [
        Stops.plaza, Stops.fronton, Stops.laPradera,
        Stops.fabricaCristal, Stops.piscinas, Stops.ptasSegovia,
        Stops.parqueRobledo, Stops.cLaFuencisla,
        Stops.penasDElErizo, Stops.carrascalejo,
        Stops.inPlazaToros, Stops.inFrenteBarNorte,
        Stops.inIglesiaStoTomas, Stops.inEstacionBus,
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains {
            $0.caseInsensitiveCompare(routeId) == .orderedSame
        }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M8") == .orderedSame else { return [] }
        return [Self.m8Outbound, Self.m8Inbound]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M8") == .orderedSame else { return [] }
        switch dayType {
        case .weekday, .saturday, .sunday:
            return [
                RouteVariant(
                    id: "outbound",
                    label: Self.directionOutbound,
                    stops: Self.m8Outbound,
                    direction: Self.directionOutbound,
                ),
                RouteVariant(
                    id: "inbound",
                    label: Self.directionInbound,
                    stops: Self.m8Inbound,
                    direction: Self.directionInbound,
                ),
            ]
        @unknown default:
            return []
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M8") == .orderedSame else { return nil }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        return variants.enumerated().map { index, variant in
            let swapTargetId = variants.count == 2 ? variants[1 - index].id : nil
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
        guard routeId.caseInsensitiveCompare("M8") == .orderedSame else { return [] }
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
                initialViewId: "outbound",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-sabado",
                label: "Sábados",
                views: saturdayViews,
                initialViewId: "outbound",
                timetableDayType: .saturday,
                isActiveToday: isSaturday,
            ),
            RouteSelectorEntry(
                id: "entry-domingo",
                label: "Domingos y Festivos",
                views: sundayViews,
                initialViewId: "outbound",
                timetableDayType: .sunday,
                isActiveToday: isSunday,
            ),
        ]
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M8Parser: loading timetable from JSON bundle")
        return try TimetableLoader().load("M8")
    }
}
