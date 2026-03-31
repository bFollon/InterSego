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
/// **Weekday:** Simple circular with 4 stops:
///   Segovia → Tabanera → Palazuelos → Segovia (return). 18 trips/day.
///
/// **Saturday:** Extended route with 8+ stops:
///   Outbound: Segovia cluster → Palazuelos → Tabanera → S.Cristóbal → Sonsoto → Trescasas → Cabanillas → Torrecaballeros
///   Inbound:  Reverse of above with different Segovia cluster
///
/// **Sunday:** Same extended route, fewer trips, some seasonal (school-term / summer).
///
/// The Valsaín–La Granja feeder service is deferred to a future "M7-AVE" parser.
///
/// Timetable data hardcoded from official Linecar M7 PDF screenshot (2026-03-25).
class M7Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M7"]),
        mode: .production,
        version: "1.5",
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
        /// Palazuelos cluster (weekday only — school stop likely skipped on weekends)
        static let palazuelosWk = BusStopRegistry.palazuelos
        static let palazuelosColegioWk = BusStopRegistry.palazuelosColegio
        static let segoviaWkRet = BusStopRegistry.estacionAutobuses

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

    /// Weekday circular
    /// Weekday circular: Segovia → Tabanera → Palazuelos (cluster) → Segovia
    static let m7WeekdayCircular: [BusStop] = [
        Stops.segoviaWk, Stops.tabaneraWk, Stops.tabaneraWk2, Stops.palazuelosWk,
        Stops.palazuelosColegioWk, Stops.segoviaWkRet,
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
        DebugConfig.debugPrint(
            "M7Parser: returning hardcoded timetable (PDF parsing bypassed)",
        )
        return buildStaticTimetables()
    }

    // MARK: - Static Timetable

    //
    // Source: Linecar M7 PDF screenshot, 2026-03-25.
    // Three service patterns: weekday circular, Saturday extended, Sunday extended.
    //
    // Segovia cluster (Saturday/Sunday only):
    //   Outbound: Estación Bus (+0) → Hospital (+2) → Andrés Laguna (+4) → La Pista (+6) → Plaza de Toros (+8)
    //   Inbound:  Plaza de Toros (+0) → La Pista (+2) → Andrés Laguna (+4) → Jardinillos y Hospital (+6)
    //
    // NOTE: Weekday last trip has *21:20/*21:37 with Palazuelos at 21:35 (before Tabanera 21:37).
    //       This appears to be a PDF anomaly. Times included as-is.

    private func buildStaticTimetables() -> [BusTimetable] {
        let sch = SeasonalAvailability.schoolOnly
        let sum = SeasonalAvailability.summerOnly

        // ══════════════════════════════════════════════════════════════════════════════════════
        // WEEKDAY CIRCULAR: Segovia → Tabanera → Palazuelos → Segovia (return)
        // ══════════════════════════════════════════════════════════════════════════════════════
        let wkDeps: [[DepartureTime]] =
            [
                // SEGOVIA_WK (17 departures — trip 1 has no Segovia departure)
                [
                    t(7, 40), t(8, 25), t(9, 10), t(9, 55), t(10, 40),
                    t(11, 25), t(12, 10),
                    t(13, 0), t(13, 40), t(14, 15), t(15, 15), t(15, 55),
                    t(16, 40),
                    t(17, 25), t(18, 30), t(20, 10), t(21, 20),
                ],
                // TABANERA_WK (18 departures)
                [
                    t(7, 15), t(8, 0), t(8, 45), t(9, 30), t(10, 15), t(11, 0),
                    t(11, 45), t(12, 30),
                    t(13, 20), t(14, 0), t(14, 35), t(15, 35), t(16, 15),
                    t(17, 0),
                    t(17, 45), t(18, 50), t(20, 30), t(21, 37),
                ],
                // TABANERA_WK_2 (+1 min from anchor)
                [
                    t(7, 16), t(8, 1), t(8, 46), t(9, 31), t(10, 16), t(11, 1),
                    t(11, 46), t(12, 31),
                    t(13, 21), t(14, 1), t(14, 36), t(15, 36), t(16, 16),
                    t(17, 1),
                    t(17, 46), t(18, 51), t(20, 31), t(21, 38),
                ],
                // PALAZUELOS cluster (2 stops, +2 min each)
            ]
            + DepartureTime.clusterDepartures(
                [
                    t(7, 20), t(8, 5), t(8, 50), t(9, 35), t(10, 20), t(11, 5),
                    t(11, 50), t(12, 35),
                    t(13, 25), t(14, 5), t(14, 40), t(15, 40), t(16, 20),
                    t(17, 5),
                    t(17, 50), t(18, 55), t(20, 35), t(21, 35),
                ],
                stopCount: 2,
                offsetMinutes: 2,
            ) + [
                // SEGOVIA_WK_RET (18 departures)
                [
                    t(7, 30), t(8, 15), t(9, 0), t(9, 45), t(10, 30), t(11, 15),
                    t(12, 0), t(12, 45),
                    t(13, 35), t(14, 15), t(14, 50), t(15, 50), t(16, 30),
                    t(17, 15),
                    t(18, 0), t(19, 5), t(20, 45), t(22, 5),
                ],
            ]

        // ══════════════════════════════════════════════════════════════════════════════════════
        // SATURDAY OUTBOUND
        // ══════════════════════════════════════════════════════════════════════════════════════
        // Segovia outbound cluster (5 stops, +2 min each)
        let satSegoviaOut = DepartureTime.clusterDepartures(
            [t(9, 20), t(13, 30), t(15, 15), t(19, 30), t(22, 30)],
            stopCount: 5,
            offsetMinutes: 2,
        )
        let satSanCrisOut = DepartureTime.clusterDepartures(
            [t(9, 40), t(13, 49), t(15, 35), t(19, 50), t(22, 50)],
            stopCount: 3,
            offsetMinutes: 1,
        )
        let satSonsotoOut = DepartureTime.clusterDepartures(
            [t(9, 43), t(13, 51), t(15, 38), t(19, 53), t(22, 53)],
            stopCount: 2,
            offsetMinutes: 1,
        )
        let satTrescasasOut = DepartureTime.clusterDepartures(
            [t(9, 45), t(13, 53), t(15, 41), t(19, 56), t(22, 56)],
            stopCount: 2,
            offsetMinutes: 1,
        )
        let satTorrecabOut = DepartureTime.clusterDepartures(
            [t(9, 50), t(15, 45), t(20, 0), t(23, 0)],
            stopCount: 3,
            offsetMinutes: 1,
        )
        let satOutDeps: [[DepartureTime]] = [
            satSegoviaOut[0], // OUT_ESTACION_BUS (anchor)
            satSegoviaOut[1], // OUT_HOSPITAL (+2)
            satSegoviaOut[2], // OUT_ANDRES_LAGUNA (+4)
            satSegoviaOut[3], // OUT_LA_PISTA (+6)
            satSegoviaOut[4], // OUT_PLAZA_TOROS (+8)
            [t(9, 35), t(13, 45), t(15, 30), t(19, 45), t(22, 45)], // PALAZUELOS
            [t(9, 37), t(13, 47), t(15, 32), t(19, 47), t(22, 47)], // TABANERA
            [t(9, 38), t(13, 48), t(15, 33), t(19, 48), t(22, 48)], // TABANERA_2 (+1)
            satSanCrisOut[0], // S_CRISTOBAL (anchor)
            satSanCrisOut[1], // IGLESIA (+1)
            satSanCrisOut[2], // ROTONDA (+2)
            satSonsotoOut[0], // SONSOTO / Potro (anchor)
            satSonsotoOut[1], // SONSOTO2 (+1)
            satTrescasasOut[0], // TRESCASAS / Plaza de la constitución (anchor)
            satTrescasasOut[1], // TRESCASAS2 (+1)
            [t(9, 47), t(15, 43), t(19, 58), t(22, 58)], // CABANILLAS (no trip 2)
            satTorrecabOut[0], // TORRECAB (anchor)
            satTorrecabOut[1], // TORRECAB2 (+1)
            satTorrecabOut[2], // TORRECAB3 (+2)
        ]

        // ══════════════════════════════════════════════════════════════════════════════════════
        // SATURDAY INBOUND
        // ══════════════════════════════════════════════════════════════════════════════════════
        let satTorrecabIn = DepartureTime.clusterDepartures(
            [t(10, 8), t(15, 58), t(18, 28), t(22, 58)], // PDF torrecab − 2
            stopCount: 3,
            offsetMinutes: 1,
        )
        let satTrescasasIn = DepartureTime.clusterDepartures(
            [t(10, 15), t(16, 4), t(18, 34), t(23, 4)], // PDF trescasas − 1
            stopCount: 2,
            offsetMinutes: 1,
        )
        let satSonsotoIn = DepartureTime.clusterDepartures(
            [t(10, 18), t(16, 9), t(18, 39), t(23, 9)], // PDF sonsoto − 1
            stopCount: 2,
            offsetMinutes: 1,
        )
        let satSanCrisIn = DepartureTime.clusterDepartures(
            [t(10, 22), t(16, 13), t(18, 43), t(23, 13)],
            stopCount: 3,
            offsetMinutes: 1,
        )
        let satInDeps: [[DepartureTime]] = [
            satTorrecabIn[0], // TORRECAB3 (anchor = PDF torrecab − 2)
            satTorrecabIn[1], // TORRECAB2 (+1)
            satTorrecabIn[2], // TORRECAB (+2 = original PDF time)
            [t(10, 13), t(16, 2), t(18, 32), t(23, 2)], // CABANILLAS
            satTrescasasIn[0], // TRESCASAS2 (anchor = PDF trescasas − 1)
            satTrescasasIn[1], // TRESCASAS (+1 = original PDF time)
            satSonsotoIn[0], // SONSOTO2 (anchor = PDF sonsoto − 1)
            satSonsotoIn[1], // SONSOTO (+1 = original PDF time)
            satSanCrisIn[0], // ROTONDA (anchor)
            satSanCrisIn[1], // IGLESIA (+1)
            satSanCrisIn[2], // S_CRISTOBAL (+2)
            [t(10, 24), t(16, 14), t(18, 44), t(23, 14)], // TABANERA_2 (inbound: −1 min before anchor)
            [t(10, 25), t(16, 15), t(18, 45), t(23, 15)], // TABANERA (PDF time)
            [t(10, 28), t(16, 18), t(18, 48), t(23, 18)], // PALAZUELOS
            // Segovia inbound cluster (4 stops, +2 min each; anchor = PDF "Segovia" arrival − 6 min)
        ] + DepartureTime.clusterDepartures(
            [t(10, 39), t(16, 24), t(18, 54), t(23, 24)],
            stopCount: 4,
            offsetMinutes: 2,
        )

        // ══════════════════════════════════════════════════════════════════════════════════════
        // SUNDAY OUTBOUND
        // ══════════════════════════════════════════════════════════════════════════════════════
        // Segovia outbound cluster (5 stops, +2 min each; seasonal availability preserved)
        let sunSegoviaOut = DepartureTime.clusterDepartures(
            [t(11, 45), t(20, 30, sch), t(19, 30, sum), t(21, 45)],
            stopCount: 5,
            offsetMinutes: 2,
        )
        let sunSanCrisOut = DepartureTime.clusterDepartures(
            [t(12, 6), t(20, 50, sch), t(19, 50, sum), t(22, 5)],
            stopCount: 3,
            offsetMinutes: 1,
        )
        let sunSonsotoOut = DepartureTime.clusterDepartures(
            [t(12, 9), t(20, 53, sch), t(19, 53, sum), t(22, 8)],
            stopCount: 2,
            offsetMinutes: 1,
        )
        let sunTrescasasOut = DepartureTime.clusterDepartures(
            [t(12, 10), t(20, 55, sch), t(19, 55, sum), t(22, 10)],
            stopCount: 2,
            offsetMinutes: 1,
        )
        let sunTorrecabOut = DepartureTime.clusterDepartures(
            [t(12, 15), t(22, 15)],
            stopCount: 3,
            offsetMinutes: 1,
        )
        let sunOutDeps: [[DepartureTime]] = [
            sunSegoviaOut[0], // OUT_ESTACION_BUS (anchor)
            sunSegoviaOut[1], // OUT_HOSPITAL (+2)
            sunSegoviaOut[2], // OUT_ANDRES_LAGUNA (+4)
            sunSegoviaOut[3], // OUT_LA_PISTA (+6)
            sunSegoviaOut[4], // OUT_PLAZA_TOROS (+8)
            [t(12, 0), t(20, 45, sch), t(19, 45, sum), t(22, 0)], // PALAZUELOS
            [t(12, 3), t(20, 47, sch), t(19, 47, sum), t(22, 2)], // TABANERA
            [t(12, 4), t(20, 48, sch), t(19, 48, sum), t(22, 3)], // TABANERA_2 (+1)
            sunSanCrisOut[0], // S_CRISTOBAL (anchor)
            sunSanCrisOut[1], // IGLESIA (+1)
            sunSanCrisOut[2], // ROTONDA (+2)
            sunSonsotoOut[0], // SONSOTO / Potro (anchor)
            sunSonsotoOut[1], // SONSOTO2 (+1)
            sunTrescasasOut[0], // TRESCASAS / Plaza de la constitución (anchor)
            sunTrescasasOut[1], // TRESCASAS2 (+1)
            [t(12, 13), t(22, 12)], // CABANILLAS
            sunTorrecabOut[0], // TORRECAB (anchor)
            sunTorrecabOut[1], // TORRECAB2 (+1)
            sunTorrecabOut[2], // TORRECAB3 (+2)
        ]

        // ══════════════════════════════════════════════════════════════════════════════════════
        // SUNDAY INBOUND
        // ══════════════════════════════════════════════════════════════════════════════════════
        let sunTorrecabIn = DepartureTime.clusterDepartures(
            [t(12, 13), t(16, 28)], // PDF torrecab − 2
            stopCount: 3,
            offsetMinutes: 1,
        )
        let sunTrescasasIn = DepartureTime.clusterDepartures(
            [t(12, 19), t(16, 34)], // PDF trescasas − 1
            stopCount: 2,
            offsetMinutes: 1,
        )
        let sunSonsotoIn = DepartureTime.clusterDepartures(
            [t(12, 22), t(16, 37), t(20, 54, sch), t(19, 54, sum)], // PDF sonsoto − 1
            stopCount: 2,
            offsetMinutes: 1,
        )
        let sunSanCrisIn = DepartureTime.clusterDepartures(
            [t(12, 25), t(16, 40), t(21, 0, sch), t(20, 0, sum)],
            stopCount: 3,
            offsetMinutes: 1,
        )
        let sunInDeps: [[DepartureTime]] = [
            sunTorrecabIn[0], // TORRECAB3 (anchor = PDF torrecab − 2)
            sunTorrecabIn[1], // TORRECAB2 (+1)
            sunTorrecabIn[2], // TORRECAB (+2 = original PDF time)
            [t(12, 17), t(16, 32)], // CABANILLAS
            sunTrescasasIn[0], // TRESCASAS2 (anchor = PDF trescasas − 1)
            sunTrescasasIn[1], // TRESCASAS (+1 = original PDF time)
            sunSonsotoIn[0], // SONSOTO2 (anchor = PDF sonsoto − 1)
            sunSonsotoIn[1], // SONSOTO (+1 = original PDF time)
            sunSanCrisIn[0], // ROTONDA (anchor)
            sunSanCrisIn[1], // IGLESIA (+1)
            sunSanCrisIn[2], // S_CRISTOBAL (+2)
            [t(12, 27), t(16, 42), t(21, 4, sch), t(20, 4, sum)], // TABANERA_2 (inbound: −1 min before anchor)
            [t(12, 28), t(16, 43), t(21, 5, sch), t(20, 5, sum)], // TABANERA (PDF time)
            [t(12, 33), t(16, 45), t(21, 8, sch), t(20, 8, sum)], // PALAZUELOS
            // Segovia inbound cluster (4 stops, +2 min each; anchor = PDF "Segovia" arrival − 6 min)
        ] + DepartureTime.clusterDepartures(
            [t(12, 39), t(16, 54), t(21, 24, sch), t(20, 24, sum)],
            stopCount: 4,
            offsetMinutes: 2,
        )

        return buildTimetables(
            stops: Self.m7WeekdayCircular,
            dayType: .weekday,
            direction: Self.directionWeekdayCircular,
            deps: wkDeps,
        )
            + buildTimetables(
                stops: Self.m7ExtOutbound,
                dayType: .saturday,
                direction: Self.directionSatSunOutbound,
                deps: satOutDeps,
            )
            + buildTimetables(
                stops: Self.m7ExtInbound,
                dayType: .saturday,
                direction: Self.directionSatSunInbound,
                deps: satInDeps,
            )
            + buildTimetables(
                stops: Self.m7ExtOutbound,
                dayType: .sunday,
                direction: Self.directionSatSunOutbound,
                deps: sunOutDeps,
            )
            + buildTimetables(
                stops: Self.m7ExtInbound,
                dayType: .sunday,
                direction: Self.directionSatSunInbound,
                deps: sunInDeps,
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
                routeId: "M7",
                stopId: stop.id,
                dayType: dayType,
                departures: deps[i],
                direction: direction,
            )
        }
    }
}
