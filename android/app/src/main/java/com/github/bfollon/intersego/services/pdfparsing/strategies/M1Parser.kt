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
 * Parser for M1 route (Segovia – Garcillán via Polígono, Casino, Valverde, Abades, Martín Miguel)
 *
 * M1 is a circular route with two main directions:
 *   Direction A (circularA): Segovia → Polígono → Polígono2 → Casino → Valverde → Abades
 *                             → Martín Miguel → Garcillán → Segovia (full outbound, direct return)
 *   Direction B (circularB): Segovia → Polígono → Polígono2 → Garcillán → Martín Miguel
 *                             → Abades → Valverde → Casino → Polígono2 → Polígono → Segovia
 *                             (direct outbound, return via all villages)
 *
 * The M1 PDF uses non-standard font encodings. Timetable data is hardcoded from the
 * official Linecar schedule (screenshot dated 2026-03-18).
 *
 * Annotations:
 *   ★  = Jun 13–Sep 13 only (JUNE_TO_SEPT_ONLY)
 *   (*) = different pickup location (gasolinera), NOT summer-restricted (YEAR_ROUND)
 *   L Y V = Lunes y Viernes only (MON_FRI_ONLY)
 *   #  = Fridays only (FRI_ONLY)
 *
 * No Sunday service. Saturday runs a shorter Segovia ↔ Abades variant.
 */
class M1Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M1"),
        mode = ParserMode.PRODUCTION,
        version = "1.8"
    )

    companion object {
        // Direction A: full outbound via villages + direct return to Segovia
        private const val DIRECTION_CIRCULAR_A = "Segovia → Garcillán"

        // Direction B: direct outbound to Garcillán + return via all villages
        private const val DIRECTION_CIRCULAR_B = "Garcillán → Segovia"

        // Saturday linear directions (SG-Labajos variant — not circular)
        private const val DIRECTION_SAT_OUTBOUND = "Segovia → Abades"
        private const val DIRECTION_SAT_INBOUND = "Abades → Segovia"

        private object Stops {
            val SEGOVIA = BusStopRegistry.estacionAutobuses
            val POLIGONO = BusStopRegistry.poligonoIndM1
            val POLIGONO_2 = BusStopRegistry.poligonoIndM1B
            val CASINO = BusStopRegistry.casinoUnion
            val VALVERDE = BusStopRegistry.valverdeMajano
            val ABADES = BusStopRegistry.abades
            val MARTIN_MIGUEL = BusStopRegistry.martinMiguel
            val GARCILLAN = BusStopRegistry.garcillan
            // Circular return — same canonical stops; direction distinguishes timetable buckets
            val SEGOVIA_RETURN = BusStopRegistry.estacionAutobuses
            val POLIGONO_B_IN = BusStopRegistry.poligonoIndM1
            val POLIGONO_2_B_IN = BusStopRegistry.poligonoIndM1B
        }

        // Direction A: Segovia → (all villages) → Garcillán → Segovia (return)
        val m1CircularAWeekday: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.POLIGONO, Stops.POLIGONO_2,
            Stops.CASINO, Stops.VALVERDE, Stops.ABADES, Stops.MARTIN_MIGUEL,
            Stops.GARCILLAN, Stops.SEGOVIA_RETURN
        )

        // Direction B: Segovia → Polígono → Polígono2 → Garcillán → (all villages) → Segovia (return)
        val m1CircularBWeekday: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.POLIGONO, Stops.POLIGONO_2,
            Stops.GARCILLAN, Stops.MARTIN_MIGUEL, Stops.ABADES, Stops.VALVERDE,
            Stops.CASINO, Stops.POLIGONO_2_B_IN, Stops.POLIGONO_B_IN, Stops.SEGOVIA_RETURN
        )

        // Saturday: shorter Segovia ↔ Abades route (SG-Labajos variant)
        val m1SaturdayOutbound: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.CASINO, Stops.VALVERDE, Stops.ABADES
        )
        val m1SaturdayInbound: List<BusStop> = listOf(
            Stops.ABADES, Stops.VALVERDE, Stops.SEGOVIA
        )
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M1", ignoreCase = true)) return emptyList()
        return listOf(m1CircularAWeekday, m1CircularBWeekday)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M1", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.SATURDAY -> listOf(
                RouteVariant(
                    "saturday-outbound",
                    DIRECTION_SAT_OUTBOUND,
                    m1SaturdayOutbound,
                    DIRECTION_SAT_OUTBOUND
                ),
                RouteVariant(
                    "saturday-inbound",
                    DIRECTION_SAT_INBOUND,
                    m1SaturdayInbound,
                    DIRECTION_SAT_INBOUND
                )
            )

            DayType.SUNDAY -> emptyList()
            else -> listOf(
                RouteVariant(
                    "circularA",
                    DIRECTION_CIRCULAR_A,
                    m1CircularAWeekday,
                    DIRECTION_CIRCULAR_A
                ),
                RouteVariant(
                    "circularB",
                    DIRECTION_CIRCULAR_B,
                    m1CircularBWeekday,
                    DIRECTION_CIRCULAR_B
                )
            )
        }
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M1", ignoreCase = true)) return null
        val variants = getRouteVariants(routeId, dayType)
        if (variants.isEmpty()) return null
        return variants.mapIndexed { index, variant ->
            val swapTargetId = if (variants.size == 2) variants[1 - index].id else null
            RouteView(
                id = variant.id,
                label = variant.label,
                stops = variant.stops.map { RouteViewStop(it) },
                direction = variant.direction,
                departureLabel = variant.departureLabel,
                swapAction = swapTargetId?.let { SwapAction(it) }
            )
        }
    }

    override fun getRouteEntries(routeId: String, today: java.util.Date): List<RouteSelectorEntry> {
        if (!routeId.equals("M1", ignoreCase = true)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val weekdayViews = getRouteViews(routeId, DayType.WEEKDAY) ?: return emptyList()
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY) ?: return emptyList()
        return listOf(
            RouteSelectorEntry(
                "entry-lv-a",
                "L-V - $DIRECTION_CIRCULAR_A",
                weekdayViews,
                "circularA",
                DayType.WEEKDAY,
                isWeekday
            ),
            RouteSelectorEntry(
                "entry-lv-b",
                "L-V - $DIRECTION_CIRCULAR_B",
                weekdayViews,
                "circularB",
                DayType.WEEKDAY,
                isWeekday
            ),
            RouteSelectorEntry(
                "entry-sab-a",
                "Sáb - $DIRECTION_SAT_OUTBOUND",
                saturdayViews,
                "saturday-outbound",
                DayType.SATURDAY,
                isSaturday
            ),
            RouteSelectorEntry(
                "entry-sab-b",
                "Sáb - $DIRECTION_SAT_INBOUND",
                saturdayViews,
                "saturday-inbound",
                DayType.SATURDAY,
                isSaturday
            )
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M1Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // ── Static timetable ─────────────────────────────────────────────────────────────────────
    //
    // Source: Linecar M1 PDF screenshot, 2026-03-18.
    // Dashes in the PDF = stop omitted from that row's departure list.
    // Polígono is a two-stop cluster; times are estimated +2 min between stops.

    private fun buildStaticTimetables(): List<BusTimetable> {
        val yr = SeasonalAvailability.YEAR_ROUND
        val jun = SeasonalAvailability.JUNE_TO_SEPT_ONLY
        val fri = SeasonalAvailability.FRI_ONLY
        val lyv = SeasonalAvailability.MON_FRI_ONLY

        // ── Weekday Direction A (circularA): full outbound via all villages + direct return ──────
        //
        // Buses that go Segovia → Casino → Valverde → Abades → Martín Miguel → Garcillán
        // then return directly to Segovia (rows 5, 8, 11 reach Garcillán and return).
        // Note: 15:15 bus (row 8) skips Casino due to a PDF dash.
        val circADeps = arrayOf(
            // SEGOVIA (11 departures)
            mutableListOf(
                t(6, 40),
                t(7, 25),
                t(8, 25),
                t(10, 0),
                t(12, 0),
                t(13, 0),
                t(14, 40),
                t(15, 15),
                t(18, 0),
                t(19, 30),
                t(20, 50)
            ),
            // POLIGONO cluster (2 stops, +2 min each)
            *DepartureTime.clusterDepartures(
                mutableListOf(
                    t(6, 50),
                    t(7, 40),
                    t(8, 35),
                    t(10, 10),
                    t(12, 5),
                    t(14, 45),
                    t(15, 20),
                    t(18, 5),
                    t(20, 55)
                ),
                stopCount = 2, offsetMinutes = 2
            ).toTypedArray(),
            // CASINO (6 — rows 1-3 and 8=15:15 are dashes; ★ = Jun–Sep only)
            mutableListOf(
                t(10, 15, jun),
                t(12, 10, jun),
                t(13, 7),
                t(14, 50, jun),
                t(18, 10, jun),
                t(21, 0, jun)
            ),
            // VALVERDE (8 — rows 1,2,4 dashes)
            mutableListOf(
                t(8, 40),
                t(12, 15),
                t(13, 10),
                t(14, 55),
                t(15, 25),
                t(18, 15),
                t(19, 40),
                t(21, 5)
            ),
            // ABADES (8 — rows 1,2,4 dashes)
            mutableListOf(
                t(8, 45),
                t(12, 20),
                t(13, 15),
                t(15, 0),
                t(15, 30),
                t(18, 20),
                t(19, 45),
                t(21, 10)
            ),
            // MARTIN_MIGUEL (3 — rows 5, 8, 11 only)
            mutableListOf(t(12, 25), t(15, 35), t(21, 15)),
            // GARCILLAN (4 — rows 5, 8, 10, 11; row 10 = # Fridays only)
            mutableListOf(t(12, 30), t(15, 40), t(19, 50, fri), t(21, 20)),
            // SEGOVIA_RETURN — direct return from Garcillán (rows 5, 8, 11)
            mutableListOf(t(12, 45), t(16, 0), t(21, 35))
        )

        // ── Weekday Direction B (circularB): direct outbound to Garcillán + return via villages ──
        //
        // Outbound leg: Segovia → Polígono → Polígono2 → Garcillán (only 6:40 bus documented).
        // Return leg: Garcillán → Martín Miguel → Abades → Valverde → Casino → Polígono2 → Polígono → Segovia.
        // (*) at Garcillán 8:40 and 10:40 = different pickup location (gasolinera), NOT summer-only.
        // Backward pass times from Direction A outbound buses have been removed.
        val circBDeps = arrayOf(
            // SEGOVIA outbound — only the 6:40 direct bus is documented in the PDF
            mutableListOf(t(6, 40)),
            // POLIGONO first pass
            mutableListOf(t(6, 50)),
            // POLIGONO_2 first pass
            mutableListOf(t(6, 52)),
            // GARCILLAN turning point; 8:40 and 10:40 are (*) = gasolinera pickup = year-round
            mutableListOf(t(6, 55), t(8, 40, yr), t(10, 40, yr), t(16, 25)),
            // MARTIN_MIGUEL return leg (9:40 = L Y V Mondays & Fridays only)
            mutableListOf(t(7, 0), t(9, 40, lyv)),
            // ABADES return leg
            mutableListOf(t(7, 5), t(7, 40), t(10, 45), t(16, 0), t(18, 20)),
            // VALVERDE return leg (9:40 = L Y V Mondays & Fridays only)
            mutableListOf(
                t(7, 10),
                t(7, 50),
                t(9, 40, lyv),
                t(10, 50),
                t(15, 5),
                t(16, 5),
                t(18, 25)
            ),
            // CASINO return leg
            mutableListOf(t(16, 10)),
            // POLIGONO_2_B_IN return leg
            mutableListOf(t(7, 15), t(10, 55), t(15, 10)),
            // POLIGONO_B_IN return leg
            mutableListOf(t(7, 17), t(10, 57), t(15, 12)),
            // SEGOVIA_RETURN arrival
            mutableListOf(
                t(7, 25),
                t(8, 0),
                t(8, 55),
                t(10, 0),
                t(11, 0),
                t(15, 15),
                t(16, 20),
                t(16, 50),
                t(18, 35)
            )
        )

        // ── Saturday Direction A: Segovia → Casino → Valverde → Abades ─────────────────────────
        val satADeps = arrayOf(
            mutableListOf(t(13, 30)), // SEGOVIA
            mutableListOf(t(13, 40)), // CASINO
            mutableListOf(t(13, 45)), // VALVERDE
            mutableListOf(t(13, 50))  // ABADES
        )

        // ── Saturday Direction B: Abades → Valverde → Segovia ────────────────────────────────
        val satBDeps = arrayOf(
            mutableListOf(t(10, 45)), // ABADES
            mutableListOf(t(10, 50)), // VALVERDE
            mutableListOf(t(11, 0))   // SEGOVIA
        )

        return buildTimetables(
            m1CircularAWeekday,
            DayType.WEEKDAY,
            DIRECTION_CIRCULAR_A,
            circADeps
        ) +
                buildTimetables(
                    m1CircularBWeekday,
                    DayType.WEEKDAY,
                    DIRECTION_CIRCULAR_B,
                    circBDeps
                ) +
                buildTimetables(
                    m1SaturdayOutbound,
                    DayType.SATURDAY,
                    DIRECTION_SAT_OUTBOUND,
                    satADeps
                ) +
                buildTimetables(m1SaturdayInbound, DayType.SATURDAY, DIRECTION_SAT_INBOUND, satBDeps)
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
            routeId = "M1",
            stopId = stop.id,
            dayType = dayType,
            direction = direction,
            departures = deps[i]
        )
    }
}
