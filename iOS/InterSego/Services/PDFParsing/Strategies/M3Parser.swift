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

/// Parser for M3 route (Segovia – La Granja – Valsain – Parque Nacional de Guadarrama)
///
/// M3 is a linear route operating Saturday only. The bus travels from Segovia to
/// Navacerrada via La Granja, Valsain, and Parque Nacional de Guadarrama, then
/// returns the same way.
///
/// "Segovia" in the PDF is a cluster of 4 sub-stops:
///   Estación de Autobuses → Iglesia Santo Tomás → Frente Bar Norte → Plaza de Toros
/// Times for the cluster are estimated at +2 min per sub-stop from the anchor time.
///
/// No weekday or Sunday service. No seasonal restrictions.
///
/// Timetable data loaded from Timetables/m3.json (migrated from PDF 2026-03-23).
class M3Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M3"]),
        mode: .production,
        version: "1.2",
    )

    // MARK: - Directions

    private static let directionOutbound = "Segovia → Navacerrada"
    private static let directionInbound = "Navacerrada → Segovia"

    // MARK: - Stops

    private enum Stops {
        /// Segovia cluster
        static let estacionBus = BusStopRegistry.estacionAutobuses
        static let iglesiaStTomas = BusStopRegistry.iglesiaStTomas
        static let frenteBarNorte = BusStopRegistry.frenteBarNorte
        static let plazaToros = BusStopRegistry.plazaDeToros

        /// Route stops
        static let urbCarrascalejo = BusStopRegistry.urbCarrascalejo
        static let parqueRobledo = BusStopRegistry.parqueRobledo
        static let laGranja = BusStopRegistry.laGranja
        static let valsain = BusStopRegistry.valsainPradera
        static let bocaDelAsno = BusStopRegistry.bocaDelAsno
        static let puenteMosquitos = BusStopRegistry.puenteMosquitos
        static let navacerrada = BusStopRegistry.navacerrada
    }

    // Outbound: Segovia cluster → ... → Navacerrada
    static let m3Outbound: [BusStop] = [
        Stops.estacionBus, Stops.iglesiaStTomas, Stops.frenteBarNorte,
        Stops.plazaToros,
        Stops.urbCarrascalejo, Stops.parqueRobledo, Stops.laGranja,
        Stops.valsain, Stops.bocaDelAsno, Stops.puenteMosquitos,
        Stops.navacerrada,
    ]

    // Inbound: Navacerrada → ... → Segovia cluster (reversed)
    static let m3Inbound: [BusStop] = [
        Stops.navacerrada, Stops.puenteMosquitos, Stops.bocaDelAsno,
        Stops.valsain, Stops.laGranja, Stops.parqueRobledo,
        Stops.urbCarrascalejo,
        Stops.plazaToros, Stops.frenteBarNorte, Stops.iglesiaStTomas,
        Stops.estacionBus,
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains {
            $0.caseInsensitiveCompare(routeId) == .orderedSame
        }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M3") == .orderedSame else {
            return []
        }
        return [Self.m3Outbound, Self.m3Inbound]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M3") == .orderedSame else {
            return []
        }
        switch dayType {
        case .saturday:
            return [
                RouteVariant(
                    id: "regular",
                    label: Self.directionOutbound,
                    stops: Self.m3Outbound,
                    direction: Self.directionOutbound,
                ),
                RouteVariant(
                    id: "reverse",
                    label: Self.directionInbound,
                    stops: Self.m3Inbound,
                    direction: Self.directionInbound,
                ),
            ]
        default:
            return []
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M3") == .orderedSame else {
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
        guard routeId.caseInsensitiveCompare("M3") == .orderedSame else {
            return []
        }
        guard let saturdayViews = getRouteViews(routeId, dayType: .saturday)
        else { return [] }
        let dow = Calendar.current.component(.weekday, from: today)
        let isSaturday = dow == 7
        return [
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
        DebugConfig.debugPrint("M3Parser: loading timetable from JSON bundle")
        return try TimetableLoader().load("M3")
    }
}
