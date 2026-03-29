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
/// **"Segovia" cluster (all day types):** The route departs from the Estación de
/// Autobuses and has 3 additional urban stops:
///   Outbound: Estación Bus → Iglesia Santo Tomás → Enfrente Bar Norte → Plaza de Toros
///   Inbound:  Plaza de Toros → Enfrente Bar Norte → Iglesia Santo Tomás → Estación Bus
///
/// **Stop notes:**
/// - C. La Fuencisla: optional stop — only some trips call here (blank = not served)
/// - C. La (inbound): same stop, optional; different subset of trips than outbound
///
/// **Partial trips (Saturday only):**
/// - Outbound 14:30: terminates at Ptas. Segovia (La Granja); does not reach Valsaín
/// - Inbound  14:50: originates at F. Cristal (La Granja); Plaza/Frontón/La Pradera not served
///
/// NOTE: The inbound stop order through La Granja differs slightly from the exact reverse
/// of outbound (F. Cristal appears before Piscinas in inbound vs. after Parque Robledo
/// in outbound). This reflects a one-way routing through the town centre.
///
/// Timetable data hardcoded from official Linecar M8 PDF screenshot (2026-03-25).
class M8Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M8"]),
        mode: .production,
        version: "1.0",
    )

    // MARK: - Directions

    private static let directionOutbound = "Segovia → Valsaín"
    private static let directionInbound = "Valsaín → Segovia"

    // MARK: - Stops

    private enum Stops {
        /// Outbound Segovia cluster (4 sub-stops, +2 min each from anchor)
        static let outEstacionBus = BusStop(
            id: "m8-estacion-bus",
            name: "Estación de Autobuses",
            area: "Segovia capital",
            coordinates: "40.944768, -4.121823",
        )
        static let outIglesiaStoTomas = BusStop(
            id: "m8-iglesia-sto-tomas",
            name: "Iglesia Santo Tomás",
            area: "Segovia capital",
            coordinates: "40.941935, -4.118070",
        )
        static let outFrenteBarNorte = BusStop(
            id: "m8-frente-bar-norte",
            name: "Enfrente Bar Norte",
            area: "Segovia capital",
            coordinates: "40.937206, -4.113999",
        )
        static let outPlazaToros = BusStop(
            id: "m8-plaza-de-toros",
            name: "Plaza de Toros",
            area: "Segovia capital",
            coordinates: "40.942093, -4.107603",
        )

        /// Main route stops (outbound)
        static let carrascalejo = BusStop(
            id: "m8-carrascalejo",
            name: "Carrascalejo",
            area: "La Granja",
            coordinates: "40.922847, -4.078270",
        )
        static let penasDElErizo = BusStop(
            id: "m8-penas-del-erizo",
            name: "Peñas del Erizo",
            area: "La Granja",
            coordinates: "40.914155, -4.066063",
        )
        static let cLaFuencisla = BusStop(
            id: "m8-c-la-fuencisla",
            name: "C. La Fuencisla",
            area: "La Granja",
            coordinates: "40.917151, -4.060857",
        )
        static let parqueRobledo = BusStop(
            id: "m8-parque-robledo",
            name: "Parque Robledo",
            area: "La Granja",
            coordinates: "40.910103, -4.058763",
        )
        static let fabricaCristal = BusStop(
            id: "m8-fabrica-cristal",
            name: "Fábrica Cristal",
            area: "La Granja",
            coordinates: "40.902789, -4.006773",
        )
        static let piscinas = BusStop(
            id: "m8-piscinas",
            name: "Piscinas",
            area: "La Granja",
            coordinates: "40.909126, -4.009200",
        )
        static let ptasSegovia = BusStop(
            id: "m8-ptas-segovia",
            name: "Ptas. Segovia",
            area: "La Granja",
            coordinates: "40.900325, -4.009467",
        )
        static let laPradera = BusStop(
            id: "m8-la-pradera",
            name: "La Pradera",
            area: "Valsaín",
            coordinates: "40.878114, -4.018356",
        )
        static let fronton = BusStop(
            id: "m8-fronton",
            name: "Frontón",
            area: "Valsaín",
            coordinates: "40.875741, -4.021955",
        )
        static let plaza = BusStop(
            id: "m8-plaza",
            name: "Plaza",
            area: "Valsaín",
            coordinates: "40.878374, -4.027028",
        )

        /// Inbound stops (with -in suffix)
        static let plazaIn = BusStop(
            id: "m8-plaza-in",
            name: "Plaza",
            area: "Valsaín",
            coordinates: "40.878374, -4.027028",
        )
        static let frontonIn = BusStop(
            id: "m8-fronton-in",
            name: "Frontón",
            area: "Valsaín",
            coordinates: "40.875741, -4.021955",
        )
        static let laPraderaIn = BusStop(
            id: "m8-la-pradera-in",
            name: "La Pradera",
            area: "Valsaín",
            coordinates: "40.878114, -4.018356",
        )
        static let fabricaCristalIn = BusStop(
            id: "m8-fabrica-cristal-in",
            name: "Fábrica Cristal",
            area: "La Granja",
            coordinates: "40.902789, -4.006773",
        )
        static let piscinasIn = BusStop(
            id: "m8-piscinas-in",
            name: "Piscinas",
            area: "La Granja",
            coordinates: "40.909126, -4.009200",
        )
        static let ptasSegoviaIn = BusStop(
            id: "m8-ptas-segovia-in",
            name: "Ptas. Segovia",
            area: "La Granja",
            coordinates: "40.900325, -4.009467",
        )
        static let parqueRobledoIn = BusStop(
            id: "m8-parque-robledo-in",
            name: "Parque Robledo",
            area: "La Granja",
            coordinates: "40.910103, -4.058763",
        )
        static let cLaFuencislaIn = BusStop(
            id: "m8-c-la-fuencisla-in",
            name: "C. La Fuencisla",
            area: "La Granja",
            coordinates: "40.917151, -4.060857",
        )
        static let penasDElErizoIn = BusStop(
            id: "m8-penas-del-erizo-in",
            name: "Peñas del Erizo",
            area: "La Granja",
            coordinates: "40.914155, -4.066063",
        )
        static let carrascalejoIn = BusStop(
            id: "m8-carrascalejo-in",
            name: "Carrascalejo",
            area: "La Granja",
            coordinates: "40.922847, -4.078270",
        )

        /// Inbound Segovia cluster (4 sub-stops, +2 min each from anchor)
        static let inPlazaToros = BusStop(
            id: "m8-plaza-de-toros-in",
            name: "Plaza de Toros",
            area: "Segovia capital",
            coordinates: "40.942093, -4.107603",
        )
        static let inFrenteBarNorte = BusStop(
            id: "m8-frente-bar-norte-in",
            name: "Enfrente Bar Norte",
            area: "Segovia capital",
            coordinates: "40.937206, -4.113999",
        )
        static let inIglesiaStoTomas = BusStop(
            id: "m8-iglesia-sto-tomas-in",
            name: "Iglesia Santo Tomás",
            area: "Segovia capital",
            coordinates: "40.941935, -4.118070",
        )
        static let inEstacionBus = BusStop(
            id: "m8-estacion-bus-in",
            name: "Estación de Autobuses",
            area: "Segovia capital",
            coordinates: "40.944768, -4.121823",
        )
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
        Stops.plazaIn, Stops.frontonIn, Stops.laPraderaIn,
        Stops.fabricaCristalIn, Stops.piscinasIn, Stops.ptasSegoviaIn,
        Stops.parqueRobledoIn, Stops.cLaFuencislaIn,
        Stops.penasDElErizoIn, Stops.carrascalejoIn,
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
        let outId: String
        switch dayType {
        case .weekday: outId = "weekday-outbound"
        case .saturday: outId = "saturday-outbound"
        case .sunday: outId = "sunday-outbound"
        @unknown default: return []
        }
        let inId = outId.replacingOccurrences(of: "outbound", with: "inbound")
        return [
            RouteVariant(
                id: outId,
                label: Self.directionOutbound,
                stops: Self.m8Outbound,
                direction: Self.directionOutbound,
            ),
            RouteVariant(
                id: inId,
                label: Self.directionInbound,
                stops: Self.m8Inbound,
                direction: Self.directionInbound,
            ),
        ]
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M8") == .orderedSame else { return nil }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        return variants.enumerated().map { index, variant in
            RouteView(
                id: variant.id,
                label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction,
                departureLabel: variant.departureLabel,
                swapAction: SwapAction(targetViewId: variants[1 - index].id),
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
                initialViewId: "weekday-outbound",
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
                label: "Domingos y Festivos",
                views: sundayViews,
                initialViewId: "sunday-outbound",
                timetableDayType: .sunday,
                isActiveToday: isSunday,
            ),
        ]
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint(
            "M8Parser: returning hardcoded timetable (PDF parsing bypassed)",
        )
        return buildStaticTimetables()
    }

    // MARK: - Static Timetable

    //
    // Source: Linecar M8 PDF screenshot, 2026-03-25.
    //
    // Segovia cluster (all day types):
    //   Outbound: Estación Bus (+0) → Iglesia Santo Tomás (+2) → Enfrente Bar Norte (+4) → Plaza de Toros (+6)
    //   Inbound:  Plaza de Toros (+0) → Enfrente Bar Norte (+2) → Iglesia Santo Tomás (+4) → Estación Bus (+6)
    //   Anchor for outbound = PDF "Segovia" departure time.
    //   Anchor for inbound  = PDF "Segovia" arrival time − 6 min.
    //
    // C. La Fuencisla: optional stop — listed only for trips that serve it.
    //
    // Saturday partial trips:
    //   Outbound 14:30: ends at Ptas. Segovia (no La Pradera/Frontón/Plaza).
    //   Inbound  14:50: starts at F. Cristal (no Plaza/Frontón/La Pradera).

    private func buildStaticTimetables() -> [BusTimetable] {

        // ══════════════════════════════════════════════════════════════════════════════
        // WEEKDAY OUTBOUND (19 trips)
        // ══════════════════════════════════════════════════════════════════════════════
        let wkSegoviaOut = DepartureTime.clusterDepartures(
            [
                t(7,30), t(8,15), t(9,0), t(9,45), t(10,30), t(11,15), t(12,0),
                t(12,45), t(13,30), t(14,15), t(15,0), t(15,45), t(16,30),
                t(17,15), t(18,0), t(18,45), t(19,30), t(20,15), t(21,45),
            ],
            stopCount: 4, offsetMinutes: 2,
        )
        let wkOutDeps: [[DepartureTime]] = [
            wkSegoviaOut[0], // OUT_ESTACION_BUS
            wkSegoviaOut[1], // OUT_IGLESIA_STO_TOMAS (+2)
            wkSegoviaOut[2], // OUT_FRENTE_BAR_NORTE (+4)
            wkSegoviaOut[3], // OUT_PLAZA_TOROS (+6)
            // CARRAS_CALEJO
            [t(7,40), t(8,25), t(9,10), t(9,55), t(10,40), t(11,25), t(12,10),
             t(12,55), t(13,40), t(14,25), t(15,10), t(15,55), t(16,40),
             t(17,25), t(18,10), t(18,55), t(19,40), t(20,25), t(21,55)],
            // PENAS_DEL_ERIZO
            [t(7,42), t(8,27), t(9,12), t(9,57), t(10,42), t(11,27), t(12,12),
             t(12,57), t(13,42), t(14,27), t(15,12), t(15,57), t(16,42),
             t(17,27), t(18,12), t(18,57), t(19,42), t(20,27), t(21,57)],
            // C_LA_FUENCISLA — optional stop (8 trips only)
            [t(7,44), t(9,59), t(11,29), t(14,29), t(15,14), t(15,59), t(19,44), t(21,59)],
            // PARQUE_ROBLEDO
            [t(7,46), t(8,31), t(9,16), t(10,1), t(10,46), t(11,31), t(12,16),
             t(13,1), t(13,46), t(14,31), t(15,16), t(16,1), t(16,46),
             t(17,31), t(18,16), t(19,1), t(19,46), t(20,31), t(22,1)],
            // FABRICA_CRISTAL
            [t(7,50), t(8,35), t(9,20), t(10,5), t(10,50), t(11,35), t(12,20),
             t(13,5), t(13,50), t(14,35), t(15,20), t(16,5), t(16,50),
             t(17,35), t(18,20), t(19,5), t(19,50), t(20,35), t(22,5)],
            // PISCINAS
            [t(7,52), t(8,37), t(9,22), t(10,7), t(10,52), t(11,37), t(12,22),
             t(13,7), t(13,52), t(14,37), t(15,22), t(16,7), t(16,52),
             t(17,37), t(18,22), t(19,7), t(19,52), t(20,37), t(22,7)],
            // PTAS_SEGOVIA
            [t(7,55), t(8,40), t(9,25), t(10,10), t(10,55), t(11,40), t(12,25),
             t(13,10), t(13,55), t(14,40), t(15,25), t(16,10), t(16,55),
             t(17,40), t(18,25), t(19,10), t(19,55), t(20,40), t(22,10)],
            // LA_PRADERA
            [t(7,58), t(8,43), t(9,28), t(10,13), t(10,58), t(11,43), t(12,28),
             t(13,13), t(13,58), t(14,43), t(15,28), t(16,13), t(16,58),
             t(17,43), t(18,28), t(19,13), t(19,58), t(20,43), t(22,13)],
            // FRONTON
            [t(8,0), t(8,45), t(9,30), t(10,15), t(11,0), t(11,45), t(12,30),
             t(13,15), t(14,0), t(14,45), t(15,30), t(16,15), t(17,0),
             t(17,45), t(18,30), t(19,15), t(20,0), t(20,45), t(22,15)],
            // PLAZA
            [t(8,5), t(8,50), t(9,35), t(10,20), t(11,5), t(11,50), t(12,35),
             t(13,20), t(14,5), t(14,50), t(15,35), t(16,20), t(17,5),
             t(17,50), t(18,35), t(19,20), t(20,5), t(20,50), t(22,20)],
        ]

        // ══════════════════════════════════════════════════════════════════════════════
        // WEEKDAY INBOUND (19 trips)
        // ══════════════════════════════════════════════════════════════════════════════
        // Inbound cluster anchor = PDF "Segovia" arrival time − 6 min
        let wkSegoviaIn = DepartureTime.clusterDepartures(
            [
                t(7,14), t(7,59), t(8,44), t(9,29), t(10,14), t(10,59), t(11,44),
                t(12,29), t(13,14), t(13,59), t(14,44), t(15,29), t(16,14),
                t(16,59), t(17,44), t(18,29), t(19,14), t(19,59), t(21,29),
            ],
            stopCount: 4, offsetMinutes: 2,
        )
        let wkInDeps: [[DepartureTime]] = [
            // PLAZA_IN
            [t(6,45), t(7,30), t(8,15), t(9,0), t(9,45), t(10,30), t(11,15),
             t(12,0), t(12,45), t(13,30), t(14,15), t(15,0), t(15,45),
             t(16,30), t(17,15), t(18,0), t(18,45), t(19,30), t(21,0)],
            // FRONTON_IN
            [t(6,47), t(7,32), t(8,17), t(9,2), t(9,47), t(10,32), t(11,17),
             t(12,2), t(12,47), t(13,32), t(14,17), t(15,2), t(15,47),
             t(16,32), t(17,17), t(18,2), t(18,47), t(19,32), t(21,2)],
            // LA_PRADERA_IN
            [t(6,50), t(7,35), t(8,20), t(9,5), t(9,50), t(10,35), t(11,20),
             t(12,5), t(12,50), t(13,35), t(14,20), t(15,5), t(15,50),
             t(16,35), t(17,20), t(18,5), t(18,50), t(19,35), t(21,5)],
            // FABRICA_CRISTAL_IN
            [t(6,55), t(7,40), t(8,25), t(9,10), t(9,55), t(10,40), t(11,25),
             t(12,10), t(12,55), t(13,40), t(14,25), t(15,10), t(15,55),
             t(16,40), t(17,25), t(18,10), t(18,55), t(19,40), t(21,10)],
            // PISCINAS_IN
            [t(6,58), t(7,43), t(8,28), t(9,13), t(9,58), t(10,43), t(11,28),
             t(12,13), t(12,58), t(13,43), t(14,28), t(15,13), t(15,58),
             t(16,43), t(17,28), t(18,13), t(18,58), t(19,43), t(21,13)],
            // PTAS_SEGOVIA_IN
            [t(7,0), t(7,45), t(8,30), t(9,15), t(10,0), t(10,45), t(11,30),
             t(12,15), t(13,0), t(13,45), t(14,30), t(15,15), t(16,0),
             t(16,45), t(17,30), t(18,15), t(19,0), t(19,45), t(21,15)],
            // PARQUE_ROBLEDO_IN
            [t(7,4), t(7,49), t(8,34), t(9,19), t(10,4), t(10,49), t(11,34),
             t(12,19), t(13,4), t(13,49), t(14,34), t(15,19), t(16,4),
             t(16,49), t(17,34), t(18,19), t(19,4), t(19,49), t(21,19)],
            // C_LA_FUENCISLA_IN — optional stop (5 trips only)
            [t(7,51), t(13,6), t(15,21), t(19,51), t(21,21)],
            // PENAS_DEL_ERIZO_IN
            [t(7,8), t(7,53), t(8,38), t(9,23), t(10,8), t(10,53), t(11,38),
             t(12,23), t(13,8), t(13,53), t(14,38), t(15,23), t(16,8),
             t(16,53), t(17,38), t(18,23), t(19,8), t(19,53), t(21,23)],
            // CARRAS_CALEJO_IN
            [t(7,10), t(7,55), t(8,40), t(9,25), t(10,10), t(10,55), t(11,40),
             t(12,25), t(13,10), t(13,55), t(14,40), t(15,25), t(16,10),
             t(16,55), t(17,40), t(18,25), t(19,10), t(19,55), t(21,25)],
            // Inbound Segovia cluster (4 stops, +2 min each)
            wkSegoviaIn[0], // IN_PLAZA_TOROS
            wkSegoviaIn[1], // IN_FRENTE_BAR_NORTE (+2)
            wkSegoviaIn[2], // IN_IGLESIA_STO_TOMAS (+4)
            wkSegoviaIn[3], // IN_ESTACION_BUS (+6)
        ]

        // ══════════════════════════════════════════════════════════════════════════════
        // SATURDAY OUTBOUND (11 trips; trip 6 = 14:30 partial, ends at Ptas. Segovia)
        // ══════════════════════════════════════════════════════════════════════════════
        let satSegoviaOut = DepartureTime.clusterDepartures(
            [
                t(7,45), t(8,50), t(10,30), t(11,30), t(13,45),
                t(14,30), t(15,30), t(17,0), t(18,45), t(20,15), t(21,30),
            ],
            stopCount: 4, offsetMinutes: 2,
        )
        let satOutDeps: [[DepartureTime]] = [
            satSegoviaOut[0], // OUT_ESTACION_BUS
            satSegoviaOut[1], // OUT_IGLESIA_STO_TOMAS (+2)
            satSegoviaOut[2], // OUT_FRENTE_BAR_NORTE (+4)
            satSegoviaOut[3], // OUT_PLAZA_TOROS (+6)
            // CARRAS_CALEJO
            [t(7,50), t(9,0), t(10,40), t(11,40), t(13,55),
             t(14,40), t(15,40), t(17,10), t(18,55), t(20,25), t(21,40)],
            // PENAS_DEL_ERIZO
            [t(7,52), t(9,2), t(10,42), t(11,42), t(13,57),
             t(14,42), t(15,42), t(17,12), t(18,57), t(20,27), t(21,42)],
            // C_LA_FUENCISLA — optional stop (6 trips)
            [t(7,54), t(10,44), t(13,59), t(14,44), t(15,44), t(21,44)],
            // PARQUE_ROBLEDO
            [t(7,56), t(9,6), t(10,46), t(11,46), t(14,1),
             t(14,46), t(15,46), t(17,16), t(19,1), t(20,31), t(21,46)],
            // FABRICA_CRISTAL
            [t(8,0), t(9,10), t(10,50), t(11,50), t(14,5),
             t(14,50), t(15,50), t(17,20), t(19,5), t(20,35), t(21,50)],
            // PISCINAS
            [t(8,2), t(9,12), t(10,52), t(11,52), t(14,7),
             t(14,52), t(15,52), t(17,22), t(19,7), t(20,37), t(21,52)],
            // PTAS_SEGOVIA (all 11 trips)
            [t(8,5), t(9,15), t(10,55), t(11,55), t(14,10),
             t(14,55), t(15,55), t(17,25), t(19,10), t(20,40), t(21,55)],
            // LA_PRADERA — 10 trips (trip 6 = 14:30 ends at Ptas. Segovia)
            [t(8,10), t(9,20), t(11,0), t(12,0), t(14,15),
             t(16,0), t(17,30), t(19,15), t(20,45), t(22,0)],
            // FRONTON
            [t(8,13), t(9,23), t(11,3), t(12,3), t(14,18),
             t(16,3), t(17,33), t(19,17), t(20,47), t(22,2)],
            // PLAZA
            [t(8,15), t(9,25), t(11,5), t(12,5), t(14,20),
             t(16,5), t(17,35), t(19,20), t(20,50), t(22,5)],
        ]

        // ══════════════════════════════════════════════════════════════════════════════
        // SATURDAY INBOUND (10 trips; trip 6 = partial, originates at F. Cristal 14:50)
        // ══════════════════════════════════════════════════════════════════════════════
        // Inbound cluster anchor = PDF "Segovia" arrival − 6 min
        let satSegoviaIn = DepartureTime.clusterDepartures(
            [
                t(7,39), t(8,44), t(9,54), t(12,39), t(14,49),
                t(15,9), t(16,44), t(18,14), t(19,49), t(21,19),
            ],
            stopCount: 4, offsetMinutes: 2,
        )
        let satInDeps: [[DepartureTime]] = [
            // PLAZA_IN — 9 trips (partial trip 6 does not start here)
            [t(7,10), t(8,15), t(9,25), t(12,10), t(14,20),
             t(16,15), t(17,45), t(19,20), t(20,50)],
            // FRONTON_IN
            [t(7,12), t(8,17), t(9,27), t(12,12), t(14,22),
             t(16,17), t(17,47), t(19,22), t(20,52)],
            // LA_PRADERA_IN
            [t(7,15), t(8,20), t(9,30), t(12,15), t(14,25),
             t(16,20), t(17,50), t(19,25), t(20,55)],
            // FABRICA_CRISTAL_IN — all 10 trips (partial trip 6 starts here at 14:50)
            [t(7,20), t(8,25), t(9,35), t(12,20), t(14,30),
             t(14,50), t(16,25), t(17,55), t(19,30), t(21,0)],
            // PISCINAS_IN
            [t(7,23), t(8,28), t(9,38), t(12,23), t(14,33),
             t(14,52), t(16,28), t(17,58), t(19,33), t(21,3)],
            // PTAS_SEGOVIA_IN
            [t(7,25), t(8,30), t(9,40), t(12,25), t(14,35),
             t(14,55), t(16,30), t(18,0), t(19,35), t(21,5)],
            // PARQUE_ROBLEDO_IN
            [t(7,29), t(8,34), t(9,44), t(12,29), t(14,39),
             t(14,59), t(16,34), t(18,4), t(19,39), t(21,9)],
            // C_LA_FUENCISLA_IN — optional stop (6 trips)
            [t(12,31), t(14,41), t(15,1), t(18,6), t(19,41), t(21,11)],
            // PENAS_DEL_ERIZO_IN
            [t(7,33), t(8,38), t(9,48), t(12,33), t(14,43),
             t(15,3), t(16,38), t(18,8), t(19,43), t(21,13)],
            // CARRAS_CALEJO_IN
            [t(7,35), t(8,40), t(9,50), t(12,35), t(14,45),
             t(15,5), t(16,40), t(18,10), t(19,45), t(21,15)],
            // Inbound Segovia cluster (4 stops, +2 min each)
            satSegoviaIn[0], // IN_PLAZA_TOROS
            satSegoviaIn[1], // IN_FRENTE_BAR_NORTE (+2)
            satSegoviaIn[2], // IN_IGLESIA_STO_TOMAS (+4)
            satSegoviaIn[3], // IN_ESTACION_BUS (+6)
        ]

        // ══════════════════════════════════════════════════════════════════════════════
        // SUNDAY OUTBOUND (8 trips)
        // ══════════════════════════════════════════════════════════════════════════════
        let sunSegoviaOut = DepartureTime.clusterDepartures(
            [
                t(10,30), t(11,30), t(13,45), t(15,30),
                t(17,0), t(18,45), t(20,15), t(21,30),
            ],
            stopCount: 4, offsetMinutes: 2,
        )
        let sunOutDeps: [[DepartureTime]] = [
            sunSegoviaOut[0], // OUT_ESTACION_BUS
            sunSegoviaOut[1], // OUT_IGLESIA_STO_TOMAS (+2)
            sunSegoviaOut[2], // OUT_FRENTE_BAR_NORTE (+4)
            sunSegoviaOut[3], // OUT_PLAZA_TOROS (+6)
            // CARRAS_CALEJO
            [t(10,40), t(11,40), t(13,55), t(15,40), t(17,10), t(18,55), t(20,25), t(21,40)],
            // PENAS_DEL_ERIZO
            [t(10,42), t(11,42), t(13,57), t(15,42), t(17,12), t(18,57), t(20,27), t(21,42)],
            // C_LA_FUENCISLA — optional stop (3 trips)
            [t(13,59), t(15,44), t(21,44)],
            // PARQUE_ROBLEDO
            [t(10,46), t(11,46), t(14,1), t(15,46), t(17,16), t(19,1), t(20,31), t(21,46)],
            // FABRICA_CRISTAL
            [t(10,50), t(11,50), t(14,5), t(15,50), t(17,20), t(19,5), t(20,35), t(21,50)],
            // PISCINAS
            [t(10,52), t(11,52), t(14,7), t(15,52), t(17,22), t(19,7), t(20,37), t(21,52)],
            // PTAS_SEGOVIA
            [t(10,55), t(11,55), t(14,10), t(15,55), t(17,25), t(19,10), t(20,40), t(21,55)],
            // LA_PRADERA
            [t(11,0), t(11,58), t(14,15), t(16,0), t(17,28), t(19,13), t(20,43), t(22,0)],
            // FRONTON
            [t(11,3), t(12,0), t(14,18), t(16,3), t(17,30), t(19,15), t(20,45), t(22,2)],
            // PLAZA
            [t(11,5), t(12,5), t(14,20), t(16,5), t(17,35), t(19,20), t(20,50), t(22,5)],
        ]

        // ══════════════════════════════════════════════════════════════════════════════
        // SUNDAY INBOUND (6 trips)
        // ══════════════════════════════════════════════════════════════════════════════
        // Inbound cluster anchor = PDF "Segovia" arrival − 6 min
        let sunSegoviaIn = DepartureTime.clusterDepartures(
            [t(12,39), t(14,49), t(16,44), t(18,14), t(19,49), t(21,19)],
            stopCount: 4, offsetMinutes: 2,
        )
        let sunInDeps: [[DepartureTime]] = [
            [t(12,10), t(14,20), t(16,15), t(17,45), t(19,20), t(20,50)], // PLAZA_IN
            [t(12,12), t(14,22), t(16,17), t(17,47), t(19,22), t(20,52)], // FRONTON_IN
            [t(12,15), t(14,25), t(16,20), t(17,50), t(19,25), t(20,55)], // LA_PRADERA_IN
            [t(12,20), t(14,30), t(16,25), t(17,55), t(19,30), t(21,0)],  // FABRICA_CRISTAL_IN
            [t(12,23), t(14,33), t(16,28), t(17,58), t(19,33), t(21,3)],  // PISCINAS_IN
            [t(12,25), t(14,35), t(16,30), t(18,0),  t(19,35), t(21,5)],  // PTAS_SEGOVIA_IN
            [t(12,29), t(14,39), t(16,34), t(18,4),  t(19,39), t(21,9)],  // PARQUE_ROBLEDO_IN
            [t(12,31), t(14,41), t(18,6),  t(19,41), t(21,11)],           // C_LA_FUENCISLA_IN (5 trips)
            [t(12,33), t(14,43), t(16,38), t(18,8),  t(19,43), t(21,13)], // PENAS_DEL_ERIZO_IN
            [t(12,35), t(14,45), t(16,40), t(18,10), t(19,45), t(21,15)], // CARRAS_CALEJO_IN
            sunSegoviaIn[0], // IN_PLAZA_TOROS
            sunSegoviaIn[1], // IN_FRENTE_BAR_NORTE (+2)
            sunSegoviaIn[2], // IN_IGLESIA_STO_TOMAS (+4)
            sunSegoviaIn[3], // IN_ESTACION_BUS (+6)
        ]

        return buildTimetables(stops: Self.m8Outbound, dayType: .weekday, direction: Self.directionOutbound, deps: wkOutDeps)
            + buildTimetables(stops: Self.m8Inbound, dayType: .weekday, direction: Self.directionInbound, deps: wkInDeps)
            + buildTimetables(stops: Self.m8Outbound, dayType: .saturday, direction: Self.directionOutbound, deps: satOutDeps)
            + buildTimetables(stops: Self.m8Inbound, dayType: .saturday, direction: Self.directionInbound, deps: satInDeps)
            + buildTimetables(stops: Self.m8Outbound, dayType: .sunday, direction: Self.directionOutbound, deps: sunOutDeps)
            + buildTimetables(stops: Self.m8Inbound, dayType: .sunday, direction: Self.directionInbound, deps: sunInDeps)
    }

    private func t(_ h: Int, _ m: Int) -> DepartureTime {
        DepartureTime(hour: h, minute: m)
    }

    private func buildTimetables(
        stops: [BusStop],
        dayType: DayType,
        direction: String,
        deps: [[DepartureTime]],
    ) -> [BusTimetable] {
        stops.enumerated().map { i, stop in
            BusTimetable(
                routeId: "M8",
                stopId: stop.id,
                dayType: dayType,
                departures: deps[i],
                direction: direction,
            )
        }
    }
}
