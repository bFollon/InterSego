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

/// Parser for M4 route (La Lastrilla - El Sotillo)
///
/// M4 is a circular route operating weekdays and Saturdays (July/August only).
/// The bus travels from Azoguejo through La Lastrilla to El Sotillo and back.
///
/// Service types:
///   YEAR_ROUND (JULIO Y AGOSTO): buses that run throughout the year, including in summer.
///     In July/August these are the only buses that run.
///   SCHOOL_ONLY: buses that run only during the school term (not July/August).
///
/// Asterisk (*) trips go to El Sotillo first, then continue to La Lastrilla (reverse direction).
///
/// Footnotes:
///   * = bus goes to El Sotillo first, then continues to La Lastrilla
///   Saturday service runs July/August only
///   No Sunday or public holiday service
///
/// Timetable data hardcoded from official Linecar M4 PDF (2026-04-15).
class M4Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M4"]),
        mode: .production,
        version: "2.3",
    )

    // MARK: - Directions

    private static let directionRegular = "Lastrilla → Sotillo"
    private static let directionReverse = "Sotillo → Lastrilla"

    // MARK: - Stops

    private enum Stops {
        static let azoguejo = BusStopRegistry.azoguejo
        static let delicias = BusStopRegistry.delicias
        static let gasolinera = BusStopRegistry.gasolineraLastrilla
        static let pension = BusStopRegistry.pension
        static let poligono = BusStopRegistry.poligonoLastrilla
        static let ctraValladolid33 = BusStopRegistry.ctraValladolid
        static let leopoldoMoreno = BusStopRegistry.leopoldoMoreno
        static let colegio = BusStopRegistry.colegioLastrilla
        static let parroqSotillo = BusStopRegistry.parroquiaSotillo
        static let hotelAvSotillo = BusStopRegistry.hotelAvSotillo
        static let maspalomas = BusStopRegistry.maspalomas
        static let centroBoal = BusStopRegistry.centroBoal
        static let paseoCabanillas = BusStopRegistry.paseoCabanillasSotillo
        static let rafaelDeLasHeras = BusStopRegistry.rafaelLasHeras
        static let ventaMagullo = BusStopRegistry.ventaMagullo
    }

    static let m4RegularRoute: [BusStop] = [
        Stops.azoguejo,
        Stops.delicias,
        Stops.gasolinera,
        Stops.pension,
        Stops.poligono,
        Stops.ctraValladolid33,
        Stops.leopoldoMoreno,
        Stops.colegio,
        // El Sotillo
        Stops.hotelAvSotillo,
        Stops.maspalomas,
        Stops.centroBoal,
        Stops.paseoCabanillas,
        Stops.parroqSotillo,
        Stops.rafaelDeLasHeras,
        Stops.ventaMagullo,

        Stops.azoguejo,
    ]

    static let m4ReverseRoute: [BusStop] = [
        Stops.azoguejo,
        Stops.delicias,
        // El Sotillo
        Stops.hotelAvSotillo,
        Stops.maspalomas,
        Stops.centroBoal,
        Stops.paseoCabanillas,
        Stops.parroqSotillo,
        Stops.rafaelDeLasHeras,
        Stops.ventaMagullo,
        // La Lastrilla
        Stops.gasolinera,
        Stops.pension,
        Stops.poligono,
        Stops.ctraValladolid33,
        Stops.leopoldoMoreno,
        Stops.colegio,
        Stops.parroqSotillo,

        Stops.azoguejo,
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains {
            $0.caseInsensitiveCompare(routeId) == .orderedSame
        }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else {
            return []
        }
        // Drop last stop (Azoguejo arrival) — it's a terminus, not a departure stop
        return [
            Array(Self.m4RegularRoute),
            Array(Self.m4ReverseRoute),
        ]
    }

    func getRouteVariants(_ routeId: String, dayType _: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else {
            return []
        }
        return [
            RouteVariant(
                id: "regular",
                label: Self.directionRegular,
                stops: Array(Self.m4RegularRoute),
                direction: Self.directionRegular,
            ),
            RouteVariant(
                id: "reverse",
                label: Self.directionReverse,
                stops: Array(Self.m4ReverseRoute),
                direction: Self.directionReverse,
            ),
        ]
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else {
            return nil
        }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        let tabs = [
            RouteTab(label: "La Lastrilla", viewId: "regular"),
            RouteTab(label: "El Sotillo", viewId: "reverse"),
        ]
        return variants.map { variant in
            RouteView(
                id: variant.id,
                label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction,
                departureLabel: variant.departureLabel,
                swapAction: nil,
                tabs: tabs,
                tabsLabel: "Pasa primero por",
                mergedDirectionLabel: "La Lastrilla · El Sotillo",
            )
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else {
            return []
        }
        let dow = Calendar.current.component(.weekday, from: today)
        let isWeekday = dow != 1 && dow != 7 // not Sunday (1) and not Saturday (7)
        let isSaturday = dow == 7
        guard let weekdayViews = getRouteViews(routeId, dayType: .weekday),
              let saturdayViews = getRouteViews(routeId, dayType: .saturday)
        else { return [] }
        return [
            RouteSelectorEntry(
                id: "entry-lv-lastrilla",
                label: "L-V La Lastrilla primero",
                views: weekdayViews,
                initialViewId: "regular",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-lv-sotillo",
                label: "L-V El Sotillo primero",
                views: weekdayViews,
                initialViewId: "reverse",
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
        DebugConfig.debugPrint(
            "M4Parser: returning hardcoded timetable (PDF parsing bypassed)",
        )
        return buildStaticTimetables()
    }

    // MARK: - Static Timetable

    //
    // Source: Linecar M4 PDF, 2026-04-15.
    // Weekday + Saturday (Jul/Aug only) service. No Sunday service.
    //
    // Seasonal annotations:
    //   yr = .yearRound  (JULIO Y AGOSTO rows: run all year; in summer these are the only buses)
    //   sc = .schoolOnly (non-summer rows: run only during the school term)
    //
    // Regular direction (Lastrilla → Sotillo) — 15 stops:
    //   [0]=AZOGUEJO  [1]=DELICIAS  [2]=GASOLINERA  [3]=PENSION  [4]=POLIGONO
    //   [5]=CTRA      [6]=LEOPOLDO  [7]=COLEGIO     [8]=HOTEL    [9]=MASPALOMAS
    //   [10]=CENTRO   [11]=PASEO    [12]=PARROQ      [13]=RAFAEL  [14]=VENTA
    //
    // Reverse direction (Sotillo → Lastrilla) — 16 stops:
    //   [0]=AZOGUEJO  [1]=DELICIAS  [2]=HOTEL   [3]=MASPALOMAS  [4]=CENTRO
    //   [5]=PASEO     [6]=PARROQ1   [7]=RAFAEL  [8]=VENTA       [9]=GASOLINERA
    //   [10]=PENSION  [11]=POLIGONO [12]=CTRA   [13]=LEOPOLDO   [14]=COLEGIO  [15]=PARROQ2
    //
    // Times encoded as HHMM integers (e.g. 740 = 07:40, 1523 = 15:23).
    // -1 means the bus does not serve that stop on this trip (skipped in the add helpers below).
    // PASEO [11] in regular / [5] in reverse = Colegio Madres Concepcionistas (SCHOOL_ONLY stop).

    private func buildStaticTimetables() -> [BusTimetable] {
        let yr = SeasonalAvailability.yearRound
        let sc = SeasonalAvailability.schoolOnly

        var wkRegDeps: [[DepartureTime]] = Array(repeating: [], count: 15)
        var wkRevDeps: [[DepartureTime]] = Array(repeating: [], count: 16)
        var satRegDeps: [[DepartureTime]] = Array(repeating: [], count: 15)

        func add(to deps: inout [[DepartureTime]], season: SeasonalAvailability, times: [Int]) {
            for (i, hhmm) in times.enumerated() {
                if hhmm >= 0 {
                    deps[i].append(t(hhmm / 100, hhmm % 100, season))
                }
            }
        }

        // ── Weekday Regular: Lastrilla → Sotillo ─────────────────────────────────────────
        // -1 at index 11 (PASEO): SCHOOL_ONLY stop, not served by YEAR_ROUND trips.
        let wkRegTrips: [(SeasonalAvailability, [Int])] = [
            (yr, [  700, 703, 706, 708, 710, 711, 712, 713,  -1, 714, 716, 718,  -1, 720, 721, 723, 728 ]),
            (yr, [  910, 913, 916, 918, 920, 921, 922, 923,  -1, 924, 926, 928,  -1, 930, 931, 933, 938 ]),
            (yr, [  940, 943, 946, 948, 950, 951, 952, 953,  -1, 954, 956, 958,  -1,1000,1001,1003,1008 ]),
            (yr, [ 1010,1013,1016,1018,1020,1021,1022,1023,  -1,1024,1026,1028,  -1,1030,1031,1033,1038 ]),
            (yr, [ 1140,1143,1146,1148,1150,1151,1152,1153,  -1,1154,1156,1158,  -1,1200,1201,1203,1208 ]),
            (yr, [ 1210,1213,1216,1218,1220,1221,1222,1223,  -1,1224,1226,1228,  -1,1230,1231,1233,1238 ]),
            (yr, [ 1240,1243,1246,1248,1250,1251,1252,1253,  -1,1254,1256,1258,  -1,1300,1301,1303,1308 ]),
            (yr, [ 1310,1313,1316,1318,1320,1321,1322,1323,  -1,1324,1326,1328,  -1,1330,1331,1333,1338 ]),
            (sc, [ 1400,1403,1406,1408,1410,1411,1412,1413,  -1,1414,1416,1418,1420,1421,1422,1423,1428 ]),
            (yr, [ 1520,1523,1526,1528,1530,1531,1532,1533,  -1,1534,1536,1538,  -1,1540,1541,1543,1548 ]),
            (yr, [ 1620,1623,1626,1628,1630,1631,1632,1633,  -1,1634,1636,1638,  -1,1640,1641,1643,1648 ]),
            (yr, [ 1700,1703,1706,1708,1710,1711,1712,1713,  -1,1714,1716,1718,  -1,1720,1721,1723,1728 ]),
            (yr, [ 1810,1813,1816,1818,1820,1821,1822,1823,  -1,1824,1826,1828,  -1,1830,1831,1833,1838 ]),
            (yr, [ 1910,1913,1916,1918,1920,1921,1922,1923,  -1,1924,1926,1928,  -1,1930,1931,1933,1938 ]),
            (yr, [ 1940,1943,1946,1948,1950,1951,1952,1953,  -1,1954,1956,1958,  -1,2000,2001,2003,2008 ]),
            (yr, [ 2010,2013,2016,2018,2020,2021,2022,2023,  -1,2024,2026,2028,  -1,2030,2031,2033,2038 ]),
            (yr, [ 2040,2043,2046,2048,2050,2051,2052,2053,  -1,2054,2056,2058,  -1,2100,2101,2103,2108 ]),
            (yr, [ 2110,2113,2116,2118,2120,2121,2122,2123,  -1,2124,2126,2128,  -1,2130,2131,2133,2148 ]),
        ]
        for (season, times) in wkRegTrips {
            add(to: &wkRegDeps, season: season, times: times)
        }

        // ── Weekday Reverse: Sotillo → Lastrilla (*) ──────────────────────────────────────
        // Asterisk buses go to El Sotillo first, then continue to La Lastrilla.
        // Times are in route stop order (sorted chronologically per trip).
        // 7:40* and 8:20* serve all 16 stops including PARROQ2 (stop 15).
        // tR() stamps variantLabel="Sotillo" so departure rows show the "Sotillo" badge.
        func addReverse(to deps: inout [[DepartureTime]], season: SeasonalAvailability, times: [Int]) {
            for (i, hhmm) in times.enumerated() {
                if hhmm >= 0 {
                    deps[i].append(tR(hhmm / 100, hhmm % 100, season))
                }
            }
        }
        let wkRevFull: [(SeasonalAvailability, [Int])] = [
            (sc, [ 740, 743, 746, 747, 748, 750, 751, 752, 753, 756, 758, 800, 801, 803, 804, 806, 811 ]),
            (sc, [ 820, 823, 826, 827, 828, 830, 831, 832, 833, 837, 839, 841, 842, 843, 844, 846, 852 ]),
        ]
        for (season, times) in wkRevFull {
            addReverse(to: &wkRevDeps, season: season, times: times)
        }
        // 14:40* and 21:40* do NOT serve PARROQ2 (stop 15) — only stops 0–14.
        // -1 at index 5 (PASEO): 21:40* is YEAR_ROUND; school stop not served.
        let wkRevPartial: [(SeasonalAvailability, [Int])] = [
            (sc, [ 1440,1443,1446,1447,1448,1450,1451,1452,1453,1456,1458,1500,1501,1503,1504,  -1,1508 ]),
            (yr, [ 2140,2143,2146,2147,2148,  -1,2150,2151,2152,2156,2158,2200,2201,2203,2204,  -1,2208 ]),
        ]
        for (season, times) in wkRevPartial {
            addReverse(to: &wkRevDeps, season: season, times: times)
            // stop 15 (PARROQ2) receives no departure for these trips
        }

        // ── Saturday Regular: Lastrilla → Sotillo ─────────────────────────────────────────
        // All Saturday trips are JULIO Y AGOSTO (YEAR_ROUND). No reverse service on Saturdays.
        // -1 at index 11 (PASEO): school stop, not served on Saturdays (Jul/Aug service only).
        let satRegTrips: [(SeasonalAvailability, [Int])] = [
            (yr, [ 1030,1033,1036,1038,1040,1041,1042,1043,  -1,1044,1046,1048,  -1,1050,1051,1053,1100 ]),
            (yr, [ 1400,1403,1406,1408,1410,1411,1412,1413,  -1,1414,1416,1418,  -1,1420,1421,1423,1428 ]),
            (yr, [ 1710,1713,1716,1718,1720,1721,1722,1723,  -1,1724,1726,1728,  -1,1730,1731,1733,1738 ]),
            (yr, [ 2040,2043,2046,2048,2050,2051,2052,2053,  -1,2054,2056,2058,  -1,2100,2101,2103,2108 ]),
        ]
        for (season, times) in satRegTrips {
            add(to: &satRegDeps, season: season, times: times)
        }

        return buildTimetables(
            stops: Array(Self.m4RegularRoute),
            dayType: .weekday,
            direction: Self.directionRegular,
            deps: wkRegDeps,
        )
            + buildTimetables(
                stops: Array(Self.m4ReverseRoute),
                dayType: .weekday,
                direction: Self.directionReverse,
                deps: wkRevDeps,
            )
            + buildTimetables(
                stops: Array(Self.m4RegularRoute),
                dayType: .saturday,
                direction: Self.directionRegular,
                deps: satRegDeps,
            )
    }

    private func t(_ h: Int, _ m: Int, _ s: SeasonalAvailability = .yearRound) -> DepartureTime {
        DepartureTime(hour: h, minute: m, seasonalAvailability: s)
    }

    /// Reverse-direction departure: carries "Sotillo" label so the UI can badge it.
    private func tR(_ h: Int, _ m: Int, _ s: SeasonalAvailability = .yearRound) -> DepartureTime {
        DepartureTime(hour: h, minute: m, seasonalAvailability: s, variantLabel: "Sotillo")
    }

    private func buildTimetables(
        stops: [BusStop],
        dayType: DayType,
        direction: String,
        deps: [[DepartureTime]],
    ) -> [BusTimetable] {
        stops.enumerated().map { i, stop in
            BusTimetable(
                routeId: "M4",
                stopId: stop.id,
                dayType: dayType,
                departures: deps[i],
                direction: direction,
            )
        }
    }
}
