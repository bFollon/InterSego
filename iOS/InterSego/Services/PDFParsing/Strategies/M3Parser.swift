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
/// The PDF shows two tables:
///   outbound: Segovia → Navacerrada (top table)
///   inbound:  Navacerrada → Segovia (bottom table)
///
/// "Segovia" in the PDF is a cluster of 4 sub-stops:
///   Estación de Autobuses → Iglesia Santo Tomás → Frente Bar Norte → Plaza de Toros
/// Times for the cluster are estimated at +2 min per sub-stop from the anchor time.
///
/// No weekday or Sunday service. No seasonal restrictions.
///
/// Timetable data hardcoded from official Linecar M3 PDF screenshot (2026-03-23).
class M3Parser: CapableParser, RouteStopsProvider {

    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M3"]),
        mode: .production,
        version: "1.0"
    )

    // MARK: - Directions

    private static let directionOutbound = "Segovia → Navacerrada"
    private static let directionInbound = "Navacerrada → Segovia"

    // MARK: - Stops

    private enum Stops {
        // Segovia cluster — outbound order
        static let estacionBus     = BusStop(id: "m3-estacion-bus",      name: "Estación de Autobuses",      area: "Segovia",      coordinates: "40.944768, -4.121823")
        static let iglesiaStTomas  = BusStop(id: "m3-iglesia-sto-tomas", name: "Iglesia Santo Tomás",        area: "Segovia",      coordinates: "0.0, 0.0")
        static let frenteBarNorte  = BusStop(id: "m3-frente-bar-norte",  name: "Frente Bar Norte",           area: "Segovia",      coordinates: "0.0, 0.0")
        static let plazaToros      = BusStop(id: "m3-plaza-toros",       name: "Plaza de Toros",             area: "Segovia",      coordinates: "40.942093, -4.107603")

        // Route stops
        static let urbCarrascalejo = BusStop(id: "m3-urb-carrascalejo",  name: "Urb. Carrascalejo",          area: "Carrascalejo", coordinates: "0.0, 0.0")
        static let parqueRobledo   = BusStop(id: "m3-parque-robledo",    name: "Parque Robledo",             area: "Robledo",      coordinates: "0.0, 0.0")
        static let laGranja        = BusStop(id: "m3-la-granja",         name: "La Granja (Pta de Segovia)", area: "La Granja",    coordinates: "40.901389, -4.003333")
        static let valsain         = BusStop(id: "m3-valsain",           name: "Valsain (La Pradera)",       area: "Valsaín",      coordinates: "40.877500, -4.019444")
        static let bocaDelAsno     = BusStop(id: "m3-boca-del-asno",     name: "Boca del Asno",              area: "Valsaín",      coordinates: "0.0, 0.0")
        static let puenteMosquitos = BusStop(id: "m3-puente-mosquitos",  name: "Puente de los Mosquitos",    area: "Navacerrada",  coordinates: "0.0, 0.0")
        static let navacerrada     = BusStop(id: "m3-navacerrada",       name: "Navacerrada",                area: "Navacerrada",  coordinates: "40.780833, -4.008056")

        // Segovia cluster — inbound (return) order: reversed cluster + distinct IDs
        static let plazaTorosIn      = BusStop(id: "m3-plaza-toros-in",        name: "Plaza de Toros",         area: "Segovia",  coordinates: "40.942093, -4.107603")
        static let frenteBarNorteIn  = BusStop(id: "m3-frente-bar-norte-in",   name: "Frente Bar Norte",       area: "Segovia",  coordinates: "0.0, 0.0")
        static let iglesiaStTomasIn  = BusStop(id: "m3-iglesia-sto-tomas-in",  name: "Iglesia Santo Tomás",    area: "Segovia",  coordinates: "0.0, 0.0")
        static let estacionBusIn     = BusStop(id: "m3-estacion-bus-in",       name: "Estación de Autobuses",  area: "Segovia",  coordinates: "40.944768, -4.121823")
    }

    // Outbound: Segovia cluster → ... → Navacerrada
    static let m3Outbound: [BusStop] = [
        Stops.estacionBus, Stops.iglesiaStTomas, Stops.frenteBarNorte, Stops.plazaToros,
        Stops.urbCarrascalejo, Stops.parqueRobledo, Stops.laGranja,
        Stops.valsain, Stops.bocaDelAsno, Stops.puenteMosquitos, Stops.navacerrada
    ]

    // Inbound: Navacerrada → ... → Segovia cluster (reversed)
    static let m3Inbound: [BusStop] = [
        Stops.navacerrada, Stops.puenteMosquitos, Stops.bocaDelAsno,
        Stops.valsain, Stops.laGranja, Stops.parqueRobledo, Stops.urbCarrascalejo,
        Stops.plazaTorosIn, Stops.frenteBarNorteIn, Stops.iglesiaStTomasIn, Stops.estacionBusIn
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains { $0.caseInsensitiveCompare(routeId) == .orderedSame }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M3") == .orderedSame else { return [] }
        return [Self.m3Outbound, Self.m3Inbound]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M3") == .orderedSame else { return [] }
        switch dayType {
        case .saturday:
            return [
                RouteVariant(id: "regular", label: Self.directionOutbound,
                             stops: Self.m3Outbound, direction: Self.directionOutbound),
                RouteVariant(id: "reverse", label: Self.directionInbound,
                             stops: Self.m3Inbound, direction: Self.directionInbound)
            ]
        default:
            return []
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M3") == .orderedSame else { return nil }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        return variants.enumerated().map { index, variant in
            let swapTargetId = variants.count == 2 ? variants[1 - index].id : nil
            return RouteView(
                id: variant.id, label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction, departureLabel: variant.departureLabel,
                swapAction: swapTargetId.map { SwapAction(targetViewId: $0) }
            )
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M3") == .orderedSame else { return [] }
        guard let saturdayViews = getRouteViews(routeId, dayType: .saturday) else { return [] }
        let dow = Calendar.current.component(.weekday, from: today)
        let isSaturday = dow == 7
        return [
            RouteSelectorEntry(id: "entry-sabado", label: "Sábados", views: saturdayViews, initialViewId: "regular", timetableDayType: .saturday, isActiveToday: isSaturday)
        ]
    }

    func parse(pdfPath: String, routeId: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M3Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // MARK: - Static Timetable
    //
    // Source: Linecar M3 PDF screenshot, 2026-03-23.
    // Saturday service only (Servicio de los Sábados). No weekday or Sunday service.
    //
    // "Segovia" in the PDF is a cluster of 4 sub-stops. The PDF gives one time for
    // "Segovia"; sub-stop times are estimated at +2 min per position from the anchor.
    //   Outbound anchor = Estación de Autobuses (first sub-stop)
    //   Inbound anchor  = Plaza de Toros (first sub-stop arriving back)

    private func buildStaticTimetables() -> [BusTimetable] {

        // ── Saturday outbound: Segovia → Navacerrada ─────────────────────────────────────────
        // PDF times:  8:30  8:40  8:46  8:50  8:54  8:59  9:09  9:20
        //            16:00 16:10 16:16 16:20 16:24 16:29 16:39 16:50
        //
        // Segovia cluster (Estación→Iglesia→BarNorte→PlazaToros): 4 stops, +2 min each
        let segoviaOut = DepartureTime.clusterDepartures(
            [t(8,30), t(16,0)], stopCount: 4, offsetMinutes: 2
        )
        let outDeps: [[DepartureTime]] = [
            segoviaOut[0],  // ESTACION_BUS (anchor)
            segoviaOut[1],  // IGLESIA_STO_TOMAS (+2)
            segoviaOut[2],  // FRENTE_BAR_NORTE (+4)
            segoviaOut[3],  // PLAZA_TOROS (+6)
            [t(8,40),  t(16,10)],  // URB_CARRASCALEJO
            [t(8,46),  t(16,16)],  // PARQUE_ROBLEDO
            [t(8,50),  t(16,20)],  // LA_GRANJA
            [t(8,54),  t(16,24)],  // VALSAIN
            [t(8,59),  t(16,29)],  // BOCA_DEL_ASNO
            [t(9,9),   t(16,39)],  // PUENTE_MOSQUITOS
            [t(9,20),  t(16,50)],  // NAVACERRADA
        ]

        // ── Saturday inbound: Navacerrada → Segovia ──────────────────────────────────────────
        // PDF times:  9:30  9:42  9:52  9:56  9:59 10:03 10:09 10:20
        //            17:00 17:12 17:22 17:26 17:29 17:33 17:39 17:50
        //
        // Segovia cluster inbound (PlazaToros→BarNorte→Iglesia→Estación): 4 stops, +2 min each
        // Anchor = PDF "Segovia" arrival minus 6 min (e.g., 10:20 → 10:14)
        let segoviaIn = DepartureTime.clusterDepartures(
            [t(10,14), t(17,44)], stopCount: 4, offsetMinutes: 2
        )
        let inDeps: [[DepartureTime]] = [
            [t(9,30),  t(17,0)],   // NAVACERRADA
            [t(9,42),  t(17,12)],  // PUENTE_MOSQUITOS
            [t(9,52),  t(17,22)],  // BOCA_DEL_ASNO
            [t(9,56),  t(17,26)],  // VALSAIN
            [t(9,59),  t(17,29)],  // LA_GRANJA
            [t(10,3),  t(17,33)],  // PARQUE_ROBLEDO
            [t(10,9),  t(17,39)],  // URB_CARRASCALEJO
            segoviaIn[0],  // PLAZA_TOROS_IN (anchor)
            segoviaIn[1],  // FRENTE_BAR_NORTE_IN (+2)
            segoviaIn[2],  // IGLESIA_STO_TOMAS_IN (+4)
            segoviaIn[3],  // ESTACION_BUS_IN (+6 = PDF time)
        ]

        return buildTimetables(stops: Self.m3Outbound, dayType: .saturday, direction: Self.directionOutbound, deps: outDeps)
             + buildTimetables(stops: Self.m3Inbound, dayType: .saturday, direction: Self.directionInbound, deps: inDeps)
    }

    private func t(_ h: Int, _ m: Int, _ s: SeasonalAvailability = .yearRound) -> DepartureTime {
        DepartureTime(hour: h, minute: m, seasonalAvailability: s)
    }

    private func buildTimetables(
        stops: [BusStop],
        dayType: DayType,
        direction: String,
        deps: [[DepartureTime]]
    ) -> [BusTimetable] {
        stops.enumerated().map { (i, stop) in
            BusTimetable(
                routeId: "M3",
                stopId: stop.id,
                dayType: dayType,
                departures: deps[i],
                direction: direction
            )
        }
    }
}
