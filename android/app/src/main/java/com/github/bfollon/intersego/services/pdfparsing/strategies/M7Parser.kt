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
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.SeasonalAvailability
import com.github.bfollon.intersego.data.RouteSelectorEntry
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
 * Parser for M7 route (Segovia – Tabanera – Palazuelos – Segovia / Torrecaballeros)
 *
 * M7 has three distinct service patterns:
 *
 * **Weekday (Lunes a Viernes):** Simple circular with 4 stops:
 *   Segovia → Tabanera → Palazuelos → Segovia (return)
 *   18 trips/day. The circular returns to Segovia so there's no "reverse" direction.
 *
 * **Saturday (Sábados):** Extended route with 8+ stops:
 *   Outbound: Segovia cluster → Palazuelos → Tabanera → S.Cristóbal → Sonsoto → Trescasas → Cabanillas → Torrecaballeros
 *   Inbound:  Torrecaballeros → Cabanillas → Trescasas → Sonsoto → S.Cristóbal → Tabanera → Palazuelos → Segovia cluster
 *   5 outbound trips (1 partial: 13:30 ends at Trescasas), 4 inbound trips.
 *   "Segovia" is a cluster of urban stops:
 *     Outbound: Estación Bus → Hospital → Andrés Laguna → La Pista → Plaza de Toros
 *     Inbound: Plaza de Toros → La Pista → Andrés Laguna → Jardinillos y Hospital
 *   # = no stop at Chorrillo or Pradillos on Saturdays.
 *
 * **Sunday (Domingos):** Same extended route, fewer trips:
 *   3 outbound, 3 inbound. Some trips are seasonal:
 *     20:30 outbound / 20:55 inbound = SCHOOL_ONLY (periodo lectivo)
 *     19:30 outbound / 19:55 inbound = SUMMER_ONLY (vacaciones escolares estivales, Jul-Aug)
 *   20:30 outbound is partial (ends at Trescasas).
 *   # = no stop at Chorrillo or Pradillos on Sundays.
 *
 * The Valsaín–La Granja feeder service is deferred to a future "M7-AVE" parser.
 *
 * NOTE: Weekday last trip has *21:20 (Segovia) and *21:37 (Tabanera), but
 * Palazuelos shows 21:35 which is BEFORE Tabanera 21:37. The PDF may have
 * a typo or the * trips take a different route. Times included as-is.
 *
 * Timetable data hardcoded from official Linecar M7 PDF screenshot (2026-03-25).
 */
class M7Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M7"),
        mode = ParserMode.PRODUCTION,
        version = "1.2"
    )

    companion object {
        private const val DIRECTION_WEEKDAY_CIRCULAR = "Circular"
        private const val DIRECTION_SAT_SUN_OUTBOUND = "Segovia → Torrecaballeros"
        private const val DIRECTION_SAT_SUN_INBOUND = "Torrecaballeros → Segovia"

        // ── Stops ───────────────────────────────────────────────────────────────────────────────

        private object Stops {
            // Weekday circular stops
            val SEGOVIA_WK = BusStop(
                id = "m7-segovia",
                name = "Estación de Autobuses",
                area = "Segovia Capital",
                coordinates = "40.944768, -4.121823"
            )
            val TABANERA_WK = BusStop(
                id = "m7-tabanera",
                name = "Tabanera",
                area = "Tabanera",
                coordinates = "40.934336, -4.067014"
            )

            // Palazuelos cluster (weekday only — school stop likely skipped on weekends)
            val PALAZUELOS_WK = BusStop(
                id = "m7-palazuelos",
                name = "Palazuelos",
                area = "Palazuelos",
                coordinates = "40.931068, -4.064340"
            )
            val PALAZUELOS_COLEGIO_WK = BusStop(
                id = "m7-palazuelos-colegio",
                name = "Colegio",
                area = "Palazuelos",
                coordinates = "40.933921, -4.063495"
            )
            val SEGOVIA_WK_RET = BusStop(
                id = "m7-segovia-ret",
                name = "Estación de Autobuses",
                area = "Segovia Capital",
                coordinates = "40.944768, -4.121823"
            )

            // Saturday/Sunday outbound Segovia cluster (5 sub-stops, +2 min each from anchor)
            // Recorrido urbano: Estación Bus → Hospital → Andrés Laguna → La Pista → Plaza de Toros
            val OUT_ESTACION_BUS = BusStop(
                id = "m7-estacion-bus",
                name = "Estación de Autobuses",
                area = "Segovia Capital",
                coordinates = "40.944768, -4.121823"
            )
            val OUT_HOSPITAL = BusStop(
                id = "m7-hospital",
                name = "Hospital",
                area = "Segovia Capital",
                coordinates = "0.0, 0.0"
            )
            val OUT_ANDRES_LAGUNA = BusStop(
                id = "m7-andres-laguna",
                name = "Andrés Laguna",
                area = "Segovia Capital",
                coordinates = "40.939106, -4.115582"
            )
            val OUT_LA_PISTA = BusStop(
                id = "m7-la-pista",
                name = "La Pista",
                area = "Segovia Capital",
                coordinates = "40.937354, -4.111411"
            )
            val OUT_PLAZA_TOROS = BusStop(
                id = "m7-plaza-toros",
                name = "Plaza de Toros",
                area = "Segovia capital",
                coordinates = "40.942093, -4.107603"
            )

            // Extended route stops (shared by Saturday and Sunday)
            val PALAZUELOS = BusStop(
                id = "m7-palazuelos-ext",
                name = "Palazuelos",
                area = "Palazuelos",
                coordinates = "40.931068, -4.064340"
            )
            val TABANERA = BusStop(
                id = "m7-tabanera-ext",
                name = "Tabanera",
                area = "Tabanera",
                coordinates = "40.934336, -4.067014"
            )
            val S_CRISTOBAL = BusStop(
                id = "m7-s-cristobal",
                name = "S. Cristóbal",
                area = "San Cristóbal de Segovia",
                coordinates = "0.0, 0.0"
            )
            val SONSOTO = BusStop(
                id = "m7-sonsoto",
                name = "Sonsoto",
                area = "Sonsoto",
                coordinates = "0.0, 0.0"
            )
            val TRESCASAS = BusStop(
                id = "m7-trescasas",
                name = "Trescasas",
                area = "Trescasas",
                coordinates = "0.0, 0.0"
            )
            val CABANILLAS = BusStop(
                id = "m7-cabanillas",
                name = "Cabanillas",
                area = "Cabanillas",
                coordinates = "0.0, 0.0"
            )
            val TORRECAB = BusStop(
                id = "m7-torrecaballeros",
                name = "Torrecaballeros",
                area = "Torrecaballeros",
                coordinates = "0.0, 0.0"
            )

            // Saturday/Sunday inbound Segovia cluster (4 sub-stops, +2 min each from anchor)
            // Recorrido urbano: Plaza de Toros → La Pista → Andrés Laguna → Jardinillos y Hospital
            val IN_PLAZA_TOROS = BusStop(
                id = "m7-plaza-toros-in",
                name = "Plaza de Toros",
                area = "Segovia capital",
                coordinates = "40.942093, -4.107603"
            )
            val IN_LA_PISTA = BusStop(
                id = "m7-la-pista-in",
                name = "La Pista",
                area = "Segovia capital",
                coordinates = "40.937354, -4.111411"
            )
            val IN_ANDRES_LAGUNA = BusStop(
                id = "m7-andres-laguna-in",
                name = "Andrés Laguna",
                area = "Segovia capital",
                coordinates = "40.939106, -4.115582"
            )
            val IN_JARDINILLOS = BusStop(
                id = "m7-jardinillos",
                name = "Jardinillos y Hospital",
                area = "Segovia capital",
                coordinates = "40.944361, -4.120831"
            )
        }

        // ── Stop lists ──────────────────────────────────────────────────────────────────────────

        // Weekday circular: Segovia → Tabanera → Palazuelos (cluster) → Segovia
        val m7WeekdayCircular: List<BusStop> = listOf(
            Stops.SEGOVIA_WK,
            Stops.TABANERA_WK,
            Stops.PALAZUELOS_WK,
            Stops.PALAZUELOS_COLEGIO_WK,
            Stops.SEGOVIA_WK_RET
        )

        // Saturday/Sunday outbound: Segovia cluster → ... → Torrecaballeros
        val m7ExtOutbound: List<BusStop> = listOf(
            Stops.OUT_ESTACION_BUS, Stops.OUT_HOSPITAL, Stops.OUT_ANDRES_LAGUNA,
            Stops.OUT_LA_PISTA, Stops.OUT_PLAZA_TOROS,
            Stops.PALAZUELOS, Stops.TABANERA, Stops.S_CRISTOBAL,
            Stops.SONSOTO, Stops.TRESCASAS, Stops.CABANILLAS, Stops.TORRECAB
        )

        // Saturday/Sunday inbound: Torrecaballeros → ... → Segovia cluster
        val m7ExtInbound: List<BusStop> = listOf(
            Stops.TORRECAB, Stops.CABANILLAS, Stops.TRESCASAS,
            Stops.SONSOTO, Stops.S_CRISTOBAL, Stops.TABANERA, Stops.PALAZUELOS,
            Stops.IN_PLAZA_TOROS, Stops.IN_LA_PISTA, Stops.IN_ANDRES_LAGUNA, Stops.IN_JARDINILLOS
        )
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M7", ignoreCase = true)) return emptyList()
        return listOf(m7WeekdayCircular, m7ExtOutbound, m7ExtInbound)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M7", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.WEEKDAY -> listOf(
                RouteVariant(
                    "weekday-circular",
                    DIRECTION_WEEKDAY_CIRCULAR,
                    m7WeekdayCircular,
                    DIRECTION_WEEKDAY_CIRCULAR
                )
            )

            DayType.SATURDAY -> listOf(
                RouteVariant(
                    "saturday-outbound",
                    DIRECTION_SAT_SUN_OUTBOUND,
                    m7ExtOutbound,
                    DIRECTION_SAT_SUN_OUTBOUND
                ),
                RouteVariant(
                    "saturday-inbound",
                    DIRECTION_SAT_SUN_INBOUND,
                    m7ExtInbound,
                    DIRECTION_SAT_SUN_INBOUND
                )
            )

            DayType.SUNDAY -> listOf(
                RouteVariant(
                    "sunday-outbound",
                    DIRECTION_SAT_SUN_OUTBOUND,
                    m7ExtOutbound,
                    DIRECTION_SAT_SUN_OUTBOUND
                ),
                RouteVariant(
                    "sunday-inbound",
                    DIRECTION_SAT_SUN_INBOUND,
                    m7ExtInbound,
                    DIRECTION_SAT_SUN_INBOUND
                )
            )

            else -> emptyList()
        }
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M7", ignoreCase = true)) return null
        val variants = getRouteVariants(routeId, dayType)
        if (variants.isEmpty()) return null
        return variants.mapIndexed { index, variant ->
            val swapTargetId = if (variants.size == 2) variants[1 - index].id else null
            RouteView(
                id = variant.id, label = variant.label,
                stops = variant.stops.map { RouteViewStop(it) },
                direction = variant.direction, departureLabel = variant.departureLabel,
                swapAction = swapTargetId?.let { SwapAction(it) }
            )
        }
    }

    override fun getRouteEntries(routeId: String, today: java.util.Date): List<RouteSelectorEntry> {
        if (!routeId.equals("M7", ignoreCase = true)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val isSunday = dow == java.util.Calendar.SUNDAY
        val weekdayViews = getRouteViews(routeId, DayType.WEEKDAY) ?: return emptyList()
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY) ?: return emptyList()
        val sundayViews = getRouteViews(routeId, DayType.SUNDAY) ?: return emptyList()
        return listOf(
            RouteSelectorEntry(
                "entry-lv",
                "Lunes a Viernes",
                weekdayViews,
                "weekday-circular",
                DayType.WEEKDAY,
                isWeekday
            ),
            RouteSelectorEntry(
                "entry-sabado",
                "Sábados",
                saturdayViews,
                "saturday-outbound",
                DayType.SATURDAY,
                isSaturday
            ),
            RouteSelectorEntry(
                "entry-domingo",
                "Domingos",
                sundayViews,
                "sunday-outbound",
                DayType.SUNDAY,
                isSunday
            )
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M7Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // ── Static timetable ─────────────────────────────────────────────────────────────────────
    //
    // Source: Linecar M7 PDF screenshot, 2026-03-25.
    // Three service patterns: weekday circular, Saturday extended, Sunday extended.
    //
    // Segovia cluster (Saturday/Sunday only):
    //   Outbound: Estación Bus (+0) → Hospital (+2) → Andrés Laguna (+4) → La Pista (+6) → Plaza de Toros (+8)
    //   Inbound:  Plaza de Toros (+0) → La Pista (+2) → Andrés Laguna (+4) → Jardinillos y Hospital (+6)
    //   Cluster offsets estimated at +2 min per sub-stop from the PDF anchor time.
    //
    // NOTE: Weekday last trip has *21:20/*21:37 with Palazuelos at 21:35 (before Tabanera 21:37).
    //       This appears to be a PDF anomaly. Times included as-is.

    private fun buildStaticTimetables(): List<BusTimetable> {
        val sch = SeasonalAvailability.SCHOOL_ONLY
        val sum = SeasonalAvailability.SUMMER_ONLY

        // ══════════════════════════════════════════════════════════════════════════════════════
        // WEEKDAY CIRCULAR: Segovia → Tabanera → Palazuelos → Segovia (return)
        // ══════════════════════════════════════════════════════════════════════════════════════
        // 18 trips. First trip starts from Tabanera (no Segovia departure).
        val wkDeps = arrayOf(
            // SEGOVIA_WK (17 departures — trip 1 has no Segovia departure)
            mutableListOf(
                t(7, 40), t(8, 25), t(9, 10), t(9, 55), t(10, 40), t(11, 25), t(12, 10),
                t(13, 0), t(13, 40), t(14, 15), t(15, 15), t(15, 55), t(16, 40),
                t(17, 25), t(18, 30), t(20, 10), t(21, 20)
            ),
            // TABANERA_WK (18 departures)
            mutableListOf(
                t(7, 15), t(8, 0), t(8, 45), t(9, 30), t(10, 15), t(11, 0), t(11, 45), t(12, 30),
                t(13, 20), t(14, 0), t(14, 35), t(15, 35), t(16, 15), t(17, 0),
                t(17, 45), t(18, 50), t(20, 30), t(21, 37)
            ),
            // PALAZUELOS cluster (2 stops, +2 min each)
            *DepartureTime.clusterDepartures(
                mutableListOf(
                    t(7, 20),
                    t(8, 5),
                    t(8, 50),
                    t(9, 35),
                    t(10, 20),
                    t(11, 5),
                    t(11, 50),
                    t(12, 35),
                    t(13, 25),
                    t(14, 5),
                    t(14, 40),
                    t(15, 40),
                    t(16, 20),
                    t(17, 5),
                    t(17, 50),
                    t(18, 55),
                    t(20, 35),
                    t(21, 35)
                ),
                stopCount = 2, offsetMinutes = 2
            ).toTypedArray(),
            // SEGOVIA_WK_RET (18 departures — all trips return to Segovia)
            mutableListOf(
                t(7, 30), t(8, 15), t(9, 0), t(9, 45), t(10, 30), t(11, 15), t(12, 0), t(12, 45),
                t(13, 35), t(14, 15), t(14, 50), t(15, 50), t(16, 30), t(17, 15),
                t(18, 0), t(19, 5), t(20, 45), t(22, 5)
            )
        )

        // ══════════════════════════════════════════════════════════════════════════════════════
        // SATURDAY OUTBOUND: Segovia cluster → ... → Torrecaballeros
        // ══════════════════════════════════════════════════════════════════════════════════════
        // 5 trips. Trip 2 (13:30) is partial — ends at Trescasas.
        // Segovia cluster: anchor = PDF "Segovia" time; sub-stops at +2/+4/+6/+8 min.
        // Segovia outbound cluster (5 stops, +2 min each)
        val satSegoviaOut = DepartureTime.clusterDepartures(
            mutableListOf(t(9, 20), t(13, 30), t(15, 15), t(19, 30), t(22, 30)),
            stopCount = 5, offsetMinutes = 2
        )
        val satOutDeps = arrayOf(
            satSegoviaOut[0],  // OUT_ESTACION_BUS (anchor)
            satSegoviaOut[1],  // OUT_HOSPITAL (+2)
            satSegoviaOut[2],  // OUT_ANDRES_LAGUNA (+4)
            satSegoviaOut[3],  // OUT_LA_PISTA (+6)
            satSegoviaOut[4],  // OUT_PLAZA_TOROS (+8)
            // PALAZUELOS — PDF times
            mutableListOf(t(9, 35), t(13, 45), t(15, 30), t(19, 45), t(22, 45)),
            // TABANERA
            mutableListOf(t(9, 37), t(13, 47), t(15, 32), t(19, 47), t(22, 47)),
            // S_CRISTOBAL
            mutableListOf(t(9, 40), t(13, 49), t(15, 35), t(19, 50), t(22, 50)),
            // SONSOTO
            mutableListOf(t(9, 43), t(13, 51), t(15, 38), t(19, 53), t(22, 53)),
            // TRESCASAS — trip 2 ends here
            mutableListOf(t(9, 45), t(13, 53), t(15, 41), t(19, 56), t(22, 56)),
            // CABANILLAS (4 trips — trip 2 is dash)
            mutableListOf(t(9, 47), t(15, 43), t(19, 58), t(22, 58)),
            // TORRECAB (4 trips — trip 2 is dash)
            mutableListOf(t(9, 50), t(15, 45), t(20, 0), t(23, 0))
        )

        // ══════════════════════════════════════════════════════════════════════════════════════
        // SATURDAY INBOUND: Torrecaballeros → ... → Segovia cluster
        // ══════════════════════════════════════════════════════════════════════════════════════
        // 4 full trips.
        // Segovia cluster inbound: anchor = Palazuelos PDF time; Plaza de Toros = Palazuelos + gap;
        // then +2/+4/+6 min for remaining sub-stops.
        // PDF gives Palazuelos and Segovia times; the gap from Palazuelos to Segovia (final) is
        // used to distribute across 4 cluster stops.
        val satInDeps = arrayOf(
            // TORRECAB
            mutableListOf(t(10, 10), t(16, 0), t(18, 30), t(23, 0)),
            // CABANILLAS
            mutableListOf(t(10, 13), t(16, 2), t(18, 32), t(23, 2)),
            // TRESCASAS
            mutableListOf(t(10, 16), t(16, 5), t(18, 35), t(23, 5)),
            // SONSOTO
            mutableListOf(t(10, 19), t(16, 10), t(18, 40), t(23, 10)),
            // S_CRISTOBAL
            mutableListOf(t(10, 22), t(16, 13), t(18, 43), t(23, 13)),
            // TABANERA
            mutableListOf(t(10, 25), t(16, 15), t(18, 45), t(23, 15)),
            // PALAZUELOS
            mutableListOf(t(10, 28), t(16, 18), t(18, 48), t(23, 18)),
            // Segovia inbound cluster (4 stops, +2 min each; anchor = PDF "Segovia" arrival − 6 min)
            *DepartureTime.clusterDepartures(
                mutableListOf(t(10, 39), t(16, 24), t(18, 54), t(23, 24)),
                stopCount = 4, offsetMinutes = 2
            ).toTypedArray()
        )

        // ══════════════════════════════════════════════════════════════════════════════════════
        // SUNDAY OUTBOUND: Segovia cluster → ... → Torrecaballeros
        // ══════════════════════════════════════════════════════════════════════════════════════
        // 3 year-round trips + 1 school-only (20:30) + 1 summer-only (19:30) at same stops.
        // Trip 2 (20:30 school / 19:30 summer) is partial — ends at Trescasas.
        // Segovia outbound cluster (5 stops, +2 min each; seasonal availability preserved)
        val sunSegoviaOut = DepartureTime.clusterDepartures(
            mutableListOf(t(11, 45), t(20, 30, sch), t(19, 30, sum), t(21, 45)),
            stopCount = 5, offsetMinutes = 2
        )
        val sunOutDeps = arrayOf(
            sunSegoviaOut[0],  // OUT_ESTACION_BUS (anchor)
            sunSegoviaOut[1],  // OUT_HOSPITAL (+2)
            sunSegoviaOut[2],  // OUT_ANDRES_LAGUNA (+4)
            sunSegoviaOut[3],  // OUT_LA_PISTA (+6)
            sunSegoviaOut[4],  // OUT_PLAZA_TOROS (+8)
            // PALAZUELOS
            mutableListOf(t(12, 0), t(20, 45, sch), t(19, 45, sum), t(22, 0)),
            // TABANERA
            mutableListOf(t(12, 3), t(20, 47, sch), t(19, 47, sum), t(22, 2)),
            // S_CRISTOBAL
            mutableListOf(t(12, 6), t(20, 50, sch), t(19, 50, sum), t(22, 5)),
            // SONSOTO
            mutableListOf(t(12, 9), t(20, 53, sch), t(19, 53, sum), t(22, 8)),
            // TRESCASAS — school/summer trips end here (partial)
            mutableListOf(t(12, 10), t(20, 55, sch), t(19, 55, sum), t(22, 10)),
            // CABANILLAS (2 trips — school/summer trips are dash)
            mutableListOf(t(12, 13), t(22, 12)),
            // TORRECAB (2 trips — school/summer trips are dash)
            mutableListOf(t(12, 15), t(22, 15))
        )

        // ══════════════════════════════════════════════════════════════════════════════════════
        // SUNDAY INBOUND: Torrecaballeros → ... → Segovia cluster
        // ══════════════════════════════════════════════════════════════════════════════════════
        // 2 full year-round trips + 1 school-only partial (starts from Sonsoto) +
        // 1 summer-only partial (starts from Sonsoto).
        val sunInDeps = arrayOf(
            // TORRECAB (2 trips — school/summer trips are dash)
            mutableListOf(t(12, 15), t(16, 30)),
            // CABANILLAS (2 trips)
            mutableListOf(t(12, 17), t(16, 32)),
            // TRESCASAS (2 trips)
            mutableListOf(t(12, 20), t(16, 35)),
            // SONSOTO (4 trips — school/summer partials start here)
            mutableListOf(t(12, 23), t(16, 38), t(20, 55, sch), t(19, 55, sum)),
            // S_CRISTOBAL
            mutableListOf(t(12, 25), t(16, 40), t(21, 0, sch), t(20, 0, sum)),
            // TABANERA
            mutableListOf(t(12, 28), t(16, 43), t(21, 5, sch), t(20, 5, sum)),
            // PALAZUELOS
            mutableListOf(t(12, 33), t(16, 45), t(21, 8, sch), t(20, 8, sum)),
            // Segovia inbound cluster (4 stops, +2 min each; anchor = PDF "Segovia" arrival − 6 min)
            *DepartureTime.clusterDepartures(
                mutableListOf(t(12, 39), t(16, 54), t(21, 24, sch), t(20, 24, sum)),
                stopCount = 4, offsetMinutes = 2
            ).toTypedArray()
        )

        return buildTimetables(
            m7WeekdayCircular,
            DayType.WEEKDAY,
            DIRECTION_WEEKDAY_CIRCULAR,
            wkDeps
        ) +
                buildTimetables(
                    m7ExtOutbound,
                    DayType.SATURDAY,
                    DIRECTION_SAT_SUN_OUTBOUND,
                    satOutDeps
                ) +
                buildTimetables(
                    m7ExtInbound,
                    DayType.SATURDAY,
                    DIRECTION_SAT_SUN_INBOUND,
                    satInDeps
                ) +
                buildTimetables(
                    m7ExtOutbound,
                    DayType.SUNDAY,
                    DIRECTION_SAT_SUN_OUTBOUND,
                    sunOutDeps
                ) +
                buildTimetables(m7ExtInbound, DayType.SUNDAY, DIRECTION_SAT_SUN_INBOUND, sunInDeps)
    }

    private fun t(h: Int, m: Int, s: SeasonalAvailability = SeasonalAvailability.YEAR_ROUND) =
        DepartureTime(h, m, seasonalAvailability = s)

    private fun buildTimetables(
        stops: List<BusStop>,
        dayType: DayType,
        direction: String,
        deps: Array<MutableList<DepartureTime>>
    ): List<BusTimetable> = stops.mapIndexed { i, stop ->
        BusTimetable(
            routeId = "M7",
            stopId = stop.id,
            dayType = dayType,
            direction = direction,
            departures = deps[i]
        )
    }
}
