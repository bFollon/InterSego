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

package com.github.bfollon.intersego.services.pdfparsing.strategies

import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusStopRegistry
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.SeasonalAvailability
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteTab
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.data.RouteViewStop
import com.github.bfollon.intersego.data.SwapAction
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.pdfparsing.CapableParser
import com.github.bfollon.intersego.services.pdfparsing.ParserCapabilities
import com.github.bfollon.intersego.services.pdfparsing.ParserMode
import com.github.bfollon.intersego.services.pdfparsing.RouteStopsProvider

/**
 * Parser for M4 route (La Lastrilla - El Sotillo)
 *
 * M4 is a circular route operating weekdays and Saturdays (July/August only).
 * The bus travels from Azoguejo through La Lastrilla to El Sotillo and back.
 *
 * Service types:
 *   YEAR_ROUND (JULIO Y AGOSTO): buses that run throughout the year, including in summer.
 *     In July/August these are the only buses that run.
 *   SCHOOL_ONLY: buses that run only during the school term (not July/August).
 *
 * Asterisk (*) trips go to El Sotillo first, then continue to La Lastrilla (reverse direction).
 *
 * Footnotes:
 *   * = bus goes to El Sotillo first, then continues to La Lastrilla
 *   Saturday service runs July/August only
 *   No Sunday or public holiday service
 *
 * Timetable data hardcoded from official Linecar M4 PDF (2026-04-15).
 */
class M4Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M4"),
        mode = ParserMode.PRODUCTION,
        version = "2.2"
    )

    companion object {
        private const val DIRECTION_REGULAR = "Lastrilla → Sotillo"
        private const val DIRECTION_REVERSE = "Sotillo → Lastrilla"

        private object Stops {
            val AZOGUEJO = BusStopRegistry.azoguejo
            val DELICIAS = BusStopRegistry.delicias
            val GASOLINERA = BusStopRegistry.gasolineraLastrilla
            val PENSION = BusStopRegistry.pension
            val POLIGONO = BusStopRegistry.poligonoLastrilla
            val CTRA_VALLADOLID_33 = BusStopRegistry.ctraValladolid
            val LEOPOLDO_MORENO = BusStopRegistry.leopoldoMoreno
            val COLEGIO = BusStopRegistry.colegioLastrilla
            val PARROQ_SOTILLO = BusStopRegistry.parroquiaSotillo
            val HOTEL_AV_SOTILLO = BusStopRegistry.hotelAvSotillo
            val MASPALOMAS = BusStopRegistry.maspalomas
            val CENTRO_BOAL = BusStopRegistry.centroBoal
            val PASEO_CABANILLAS = BusStopRegistry.paseoCabanillasSotillo
            val RAFAEL_DE_LAS_HERAS = BusStopRegistry.rafaelLasHeras
            val VENTA_MAGULLO = BusStopRegistry.ventaMagullo
        }

        val m4RegularRoute = listOf(
            Stops.AZOGUEJO,
            Stops.DELICIAS,
            Stops.GASOLINERA,
            Stops.PENSION,
            Stops.POLIGONO,
            Stops.CTRA_VALLADOLID_33,
            Stops.LEOPOLDO_MORENO,
            Stops.COLEGIO,
            // El Sotillo
            Stops.HOTEL_AV_SOTILLO,
            Stops.MASPALOMAS,
            Stops.CENTRO_BOAL,
            Stops.PASEO_CABANILLAS,
            Stops.PARROQ_SOTILLO,
            Stops.RAFAEL_DE_LAS_HERAS,
            Stops.VENTA_MAGULLO,

            Stops.AZOGUEJO
        )

        val m4ReverseRoute = listOf(
            Stops.AZOGUEJO,
            Stops.DELICIAS,
            // El Sotillo
            Stops.HOTEL_AV_SOTILLO,
            Stops.MASPALOMAS,
            Stops.CENTRO_BOAL,
            Stops.PASEO_CABANILLAS,
            Stops.PARROQ_SOTILLO,
            Stops.RAFAEL_DE_LAS_HERAS,
            Stops.VENTA_MAGULLO,
            // La Lastrilla
            Stops.GASOLINERA,
            Stops.PENSION,
            Stops.POLIGONO,
            Stops.CTRA_VALLADOLID_33,
            Stops.LEOPOLDO_MORENO,
            Stops.COLEGIO,
            Stops.PARROQ_SOTILLO,

            Stops.AZOGUEJO
        )
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M4", ignoreCase = true)) return emptyList()
        // Drop last stop (Azoguejo arrival) — it's a terminus, not a departure stop
        return listOf(m4RegularRoute.dropLast(1), m4ReverseRoute.dropLast(1))
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M4", ignoreCase = true)) return emptyList()
        return listOf(
            RouteVariant("regular", DIRECTION_REGULAR, m4RegularRoute.dropLast(1), DIRECTION_REGULAR),
            RouteVariant("reverse", DIRECTION_REVERSE, m4ReverseRoute.dropLast(1), DIRECTION_REVERSE)
        )
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M4", ignoreCase = true)) return null
        val variants = getRouteVariants(routeId, dayType)
        if (variants.isEmpty()) return null
        val tabs = listOf(
            RouteTab("La Lastrilla", "regular"),
            RouteTab("El Sotillo", "reverse")
        )
        return variants.map { variant ->
            RouteView(
                id = variant.id, label = variant.label,
                stops = variant.stops.map { RouteViewStop(it) },
                direction = variant.direction, departureLabel = variant.departureLabel,
                swapAction = null,
                tabs = tabs,
                tabsLabel = "Pasa primero por",
                mergedDirectionLabel = "La Lastrilla · El Sotillo"
            )
        }
    }

    override fun getRouteEntries(routeId: String, today: java.util.Date): List<RouteSelectorEntry> {
        if (!routeId.equals("M4", ignoreCase = true)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val weekdayViews = getRouteViews(routeId, DayType.WEEKDAY) ?: return emptyList()
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY) ?: return emptyList()
        return listOf(
            RouteSelectorEntry("entry-lv-lastrilla", "L-V La Lastrilla primero", weekdayViews, "regular", DayType.WEEKDAY, isWeekday),
            RouteSelectorEntry("entry-lv-sotillo", "L-V El Sotillo primero", weekdayViews, "reverse", DayType.WEEKDAY, isWeekday),
            RouteSelectorEntry("entry-sabado", "Sábados", saturdayViews, "regular", DayType.SATURDAY, isSaturday)
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M4Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // ── Static timetable ──────────────────────────────────────────────────────────────────
    //
    // Source: Linecar M4 PDF, 2026-04-15.
    // Weekday + Saturday (Jul/Aug only) service. No Sunday service.
    //
    // Seasonal annotations:
    //   yr = YEAR_ROUND  (JULIO Y AGOSTO rows: run all year; in summer these are the only buses)
    //   sc = SCHOOL_ONLY (non-summer rows: run only during the school term)
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

    private fun buildStaticTimetables(): List<BusTimetable> {
        val yr = SeasonalAvailability.YEAR_ROUND
        val sc = SeasonalAvailability.SCHOOL_ONLY

        val wkRegDeps = Array(15) { mutableListOf<DepartureTime>() }
        val wkRevDeps = Array(16) { mutableListOf<DepartureTime>() }
        val satRegDeps = Array(15) { mutableListOf<DepartureTime>() }

        // ── Weekday Regular: Lastrilla → Sotillo ─────────────────────────────────────────
        listOf(
            yr to intArrayOf( 700, 703, 706, 708, 710, 711, 712, 713, 714, 716, 718, 720, 720, 721, 728),
            yr to intArrayOf( 910, 913, 916, 918, 920, 921, 922, 923, 924, 926, 928, 930, 931, 933, 938),
            yr to intArrayOf( 940, 943, 946, 948, 950, 951, 952, 953, 954, 956, 958,1000,1001,1003,1008),
            yr to intArrayOf(1010,1013,1016,1018,1020,1021,1022,1023,1024,1026,1028,1030,1031,1033,1038),
            yr to intArrayOf(1140,1143,1146,1148,1150,1151,1152,1153,1154,1156,1158,1200,1201,1203,1208),
            yr to intArrayOf(1210,1213,1216,1218,1220,1221,1222,1223,1224,1226,1228,1230,1231,1233,1238),
            yr to intArrayOf(1240,1243,1246,1248,1250,1251,1252,1253,1254,1256,1258,1300,1301,1303,1308),
            yr to intArrayOf(1310,1313,1316,1318,1320,1321,1322,1323,1324,1326,1328,1330,1331,1333,1338),
            sc to intArrayOf(1400,1403,1406,1408,1410,1411,1412,1413,1414,1416,1418,1420,1421,1422,1423),
            yr to intArrayOf(1520,1523,1526,1528,1530,1531,1532,1533,1534,1536,1538,1540,1541,1543,1548),
            yr to intArrayOf(1620,1623,1626,1628,1630,1631,1632,1633,1634,1636,1638,1640,1641,1643,1648),
            yr to intArrayOf(1700,1703,1706,1708,1710,1711,1712,1713,1714,1716,1718,1720,1721,1723,1728),
            yr to intArrayOf(1810,1813,1816,1818,1820,1821,1822,1823,1824,1826,1828,1830,1831,1833,1838),
            yr to intArrayOf(1910,1913,1916,1918,1920,1921,1922,1923,1924,1926,1928,1930,1931,1933,1938),
            yr to intArrayOf(1940,1943,1946,1948,1950,1951,1952,1953,1954,1956,1958,2000,2001,2003,2008),
            yr to intArrayOf(2010,2013,2016,2018,2020,2021,2022,2023,2024,2026,2028,2030,2031,2033,2038),
            yr to intArrayOf(2040,2043,2046,2048,2050,2051,2052,2053,2054,2056,2058,2100,2101,2103,2108),
            yr to intArrayOf(2110,2113,2116,2118,2120,2121,2122,2123,2124,2126,2128,2130,2131,2133,2148),
        ).forEach { (season, times) ->
            times.forEachIndexed { i, hhmm -> wkRegDeps[i].add(t(hhmm / 100, hhmm % 100, season)) }
        }

        // ── Weekday Reverse: Sotillo → Lastrilla (*) ──────────────────────────────────────
        // Asterisk buses go to El Sotillo first, then continue to La Lastrilla.
        // Times are in route stop order (sorted chronologically per trip).
        // 7:40* and 8:20* serve all 16 stops including PARROQ2 (stop 15).
        // tR() stamps variantLabel="Sotillo" so departure rows show the "Sotillo" badge.
        listOf(
            sc to intArrayOf( 740, 743, 746, 747, 748, 750, 751, 752, 753, 756, 758, 800, 801, 803, 804, 806),
            sc to intArrayOf( 820, 823, 826, 827, 828, 830, 831, 832, 833, 837, 839, 841, 842, 843, 844, 846),
        ).forEach { (season, times) ->
            times.forEachIndexed { i, hhmm -> wkRevDeps[i].add(tR(hhmm / 100, hhmm % 100, season)) }
        }
        // 14:40* and 21:40* do NOT serve PARROQ2 (stop 15) — only stops 0–14.
        listOf(
            sc to intArrayOf(1440,1443,1446,1447,1448,1450,1451,1452,1453,1456,1458,1500,1501,1503,1504),
            yr to intArrayOf(2140,2143,2146,2147,2148,2150,2151,2152,2208,2156,2158,2200,2201,2203,2204),
        ).forEach { (season, times) ->
            times.forEachIndexed { i, hhmm -> wkRevDeps[i].add(tR(hhmm / 100, hhmm % 100, season)) }
            // stop 15 (PARROQ2) receives no departure for these trips
        }

        // ── Saturday Regular: Lastrilla → Sotillo ─────────────────────────────────────────
        // All Saturday trips are JULIO Y AGOSTO (YEAR_ROUND). No reverse service on Saturdays.
        listOf(
            yr to intArrayOf(1030,1033,1036,1038,1040,1041,1042,1043,1044,1046,1048,1050,1051,1053,1100),
            yr to intArrayOf(1400,1403,1406,1408,1410,1411,1412,1413,1414,1416,1418,1420,1421,1423,1428),
            yr to intArrayOf(1710,1713,1716,1718,1720,1721,1722,1723,1724,1726,1728,1730,1731,1733,1738),
            yr to intArrayOf(2040,2043,2046,2048,2050,2051,2052,2053,2054,2056,2058,2100,2101,2103,2108),
        ).forEach { (season, times) ->
            times.forEachIndexed { i, hhmm -> satRegDeps[i].add(t(hhmm / 100, hhmm % 100, season)) }
        }

        return buildTimetables(m4RegularRoute.dropLast(1), DayType.WEEKDAY, DIRECTION_REGULAR, wkRegDeps) +
                buildTimetables(m4ReverseRoute.dropLast(1), DayType.WEEKDAY, DIRECTION_REVERSE, wkRevDeps) +
                buildTimetables(m4RegularRoute.dropLast(1), DayType.SATURDAY, DIRECTION_REGULAR, satRegDeps)
    }

    private fun t(h: Int, m: Int, s: SeasonalAvailability = SeasonalAvailability.YEAR_ROUND) =
        DepartureTime(h, m, seasonalAvailability = s)

    /** Reverse-direction departure: carries "Sotillo" label so the UI can badge it. */
    private fun tR(h: Int, m: Int, s: SeasonalAvailability = SeasonalAvailability.YEAR_ROUND) =
        DepartureTime(h, m, seasonalAvailability = s, variantLabel = "Sotillo")

    private fun buildTimetables(
        stops: List<BusStop>,
        dayType: DayType,
        direction: String,
        deps: Array<MutableList<DepartureTime>>
    ): List<BusTimetable> = stops.mapIndexed { i, stop ->
        BusTimetable(
            routeId = "M4",
            stopId = stop.id,
            dayType = dayType,
            direction = direction,
            departures = deps[i]
        )
    }
}
