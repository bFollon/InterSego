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
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.pdfparsing.CapableParser
import com.github.bfollon.intersego.services.pdfparsing.ParserCapabilities
import com.github.bfollon.intersego.services.pdfparsing.ParserMode
import com.github.bfollon.intersego.services.pdfparsing.RouteStopsProvider

/**
 * Parser for M1 route (Segovia – Garcillán via Polígono, Casino, Valverde, Abades, Martín Miguel)
 *
 * The M1 PDF uses non-standard font encodings (no ToUnicode tables) and a sparse table layout
 * where most rows have dashes in many columns. Reliable PDF extraction is not feasible, so
 * timetable data is hardcoded from the official Linecar schedule.
 *
 * Source: Linecar M1 PDF, verified from screenshot dated 2026-03-18.
 *
 * Markers:
 *   * = summer only (13 Jun – 13 Sep, SeasonalAvailability.SUMMER_ONLY)
 *   # = Garcillán only on Fridays (treated as YEAR_ROUND for simplicity)
 *   H = highlighted cell in PDF (no semantic meaning, ignored)
 *   L Y V = Lunes y Viernes (Mon & Fri only, treated as YEAR_ROUND for simplicity)
 *
 * No Sunday service. Saturday runs a shorter Segovia ↔ Abades variant (SG-Labajos route).
 *
 * Coordinates are placeholders (0.0, 0.0) pending real GPS data.
 */
class M1Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M1"),
        mode = ParserMode.DEBUG,
        version = "1.4"
    )

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Garcillán"
        private const val DIRECTION_INBOUND  = "Garcillán → Segovia"

        private object Stops {
            val SEGOVIA       = BusStop(name = "Segovia",              coordinates = "40.944973, -4.122431")
            val POLIGONO      = BusStop(name = "Polígono Industrial",  coordinates = "40.957976, -4.198156")
            val POLIGONO_2      = BusStop(name = "Polígono Industrial 2",  coordinates = "40.957554, -4.206457")

            val CASINO        = BusStop(name = "Casino",               coordinates = "40.965154, -4.209251")
            val VALVERDE      = BusStop(name = "Valverde de Majano",   coordinates = "40.956274, -4.235343")
            val ABADES        = BusStop(name = "Abades",               coordinates = "40.915804, -4.267038")
            val MARTIN_MIGUEL = BusStop(name = "Martín Miguel",        coordinates = "40.951889, -4.268660")
            val GARCILLAN     = BusStop(name = "Garcillán",            coordinates = "40.976809, -4.264724")
        }

        // Weekday outbound: 8 named stops (PDF has 8 columns; index 7 = return terminal, skipped)
        // Polígono is a two-stop cluster: POLIGONO → POLIGONO_2 on the way out.
        val m1WeekdayOutbound: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.POLIGONO, Stops.POLIGONO_2, Stops.CASINO, Stops.VALVERDE,
            Stops.ABADES, Stops.MARTIN_MIGUEL, Stops.GARCILLAN
        )

        val m1WeekdayInbound: List<BusStop> = listOf(
            Stops.GARCILLAN, Stops.MARTIN_MIGUEL, Stops.ABADES, Stops.VALVERDE,
            Stops.CASINO, Stops.POLIGONO_2, Stops.POLIGONO, Stops.SEGOVIA
        )

        // Saturday runs a shorter variant: Segovia ↔ Abades only (SG-Labajos route)
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
        return listOf(m1WeekdayOutbound, m1WeekdayInbound)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M1", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.SATURDAY -> listOf(
                RouteVariant("outbound", DIRECTION_OUTBOUND, m1SaturdayOutbound, DIRECTION_OUTBOUND),
                RouteVariant("inbound",  DIRECTION_INBOUND,  m1SaturdayInbound,  DIRECTION_INBOUND)
            )
            DayType.SUNDAY -> emptyList()
            else -> listOf(
                RouteVariant("outbound", DIRECTION_OUTBOUND, m1WeekdayOutbound, DIRECTION_OUTBOUND),
                RouteVariant("inbound",  DIRECTION_INBOUND,  m1WeekdayInbound,  DIRECTION_INBOUND)
            )
        }
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M1Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // ── Static timetable (weekday outbound, 11 rows × 8 stops) ───────────────────────────────
    //
    // Rows match the PDF table top-to-bottom. Dashes in the PDF = stop omitted from that array.
    // Index 7 in the PDF outbound table (return-to-Segovia terminal) is not stored.
    // Polígono is a two-stop cluster; both stops share the same scheduled times.

    private fun buildStaticTimetables(): List<BusTimetable> {
        val yr = SeasonalAvailability.YEAR_ROUND
        val su = SeasonalAvailability.SUMMER_ONLY

        // ── Weekday outbound ─────────────────────────────────────────────────────────────────
        val wkOut = arrayOf(
            // Segovia (11 departures)
            mutableListOf(t(6,40), t(7,25), t(8,25), t(10,0), t(12,0), t(13,0), t(14,40), t(15,15), t(18,0), t(19,30), t(20,50)),
            // Polígono IND. stop 1 (9 departures — rows 6 and 10 are dashes)
            mutableListOf(t(6,50), t(7,40), t(8,35), t(10,10), t(12,5), t(14,45), t(15,20), t(18,5), t(20,55)),
            // Polígono IND. stop 2 — cluster, +2 min estimate from stop 1
            mutableListOf(t(6,52), t(7,42), t(8,37), t(10,12), t(12,7), t(14,47), t(15,22), t(18,7), t(20,57)),
            // Casino (6 departures — rows 1-3 and 8 are dashes; rows 4,5,7,9,11 are summer-only)
            mutableListOf(t(10,15,su), t(12,10,su), t(13,7), t(14,50,su), t(18,10,su), t(21,0,su)),
            // Valverde (8 departures — rows 1,2,4 are dashes)
            mutableListOf(t(8,40), t(12,15), t(13,10), t(14,55), t(15,25), t(18,15), t(19,40), t(21,5)),
            // Abades (8 departures — rows 1,2,4 are dashes)
            mutableListOf(t(8,45), t(12,20), t(13,15), t(15,0), t(15,30), t(18,20), t(19,45), t(21,10)),
            // Martín Miguel (3 departures — only rows 5,8,11)
            mutableListOf(t(12,25), t(15,35), t(21,15)),
            // Garcillán (4 departures — only rows 5,8,10,11; row 10 is #=Fridays-only, kept as yearRound)
            mutableListOf(t(12,30), t(15,40), t(19,50), t(21,20))
        )

        // ── Weekday inbound ──────────────────────────────────────────────────────────────────
        val wkIn = arrayOf(
            // Garcillán (7 departures)
            mutableListOf(t(6,55), t(8,40,su), t(10,40,su), t(12,30), t(15,40), t(16,25), t(21,20)),
            // Martín Miguel (5 departures — rows 2,3,5,7,9-11 are dashes)
            mutableListOf(t(7,0), t(9,40), t(12,25), t(15,35), t(21,15)),
            // Abades (9 departures)
            mutableListOf(t(7,5), t(7,40), t(10,45), t(12,20), t(15,0), t(15,30), t(16,0), t(18,20), t(21,10)),
            // Valverde (10 departures)
            mutableListOf(t(7,10), t(7,50), t(9,40), t(10,50), t(12,15), t(15,5), t(15,25), t(16,5), t(18,25), t(21,5)),
            // Casino (3 departures — only rows 6,9,12; rows 6,12 are summer-only)
            mutableListOf(t(12,10,su), t(16,10), t(21,0,su)),
            // Polígono IND. stop 2 — cluster, PDF anchor time (stop 1 is +2 min)
            mutableListOf(t(7,15), t(10,55), t(12,5), t(15,10), t(15,20), t(18,5), t(20,55)),
            // Polígono IND. stop 1 — +2 min from stop 2
            mutableListOf(t(7,17), t(10,57), t(12,7), t(15,12), t(15,22), t(18,7), t(20,57)),
            // Segovia (12 departures)
            mutableListOf(t(7,25), t(8,0), t(8,55), t(10,0), t(11,0), t(12,45), t(15,15), t(16,0), t(16,20), t(16,50), t(18,35), t(21,35))
        )

        // ── Saturday outbound: Segovia → Abades ─────────────────────────────────────────────
        val satOut = arrayOf(
            mutableListOf(t(13,30)), // Segovia
            mutableListOf(t(13,40)), // Casino
            mutableListOf(t(13,45)), // Valverde
            mutableListOf(t(13,50))  // Abades
        )

        // ── Saturday inbound: Abades → Segovia ──────────────────────────────────────────────
        val satIn = arrayOf(
            mutableListOf(t(10,45)), // Abades
            mutableListOf(t(10,50)), // Valverde
            mutableListOf(t(11,0))   // Segovia
        )

        return buildTimetables(m1WeekdayOutbound,  DayType.WEEKDAY,   DIRECTION_OUTBOUND, wkOut)  +
               buildTimetables(m1WeekdayInbound,   DayType.WEEKDAY,   DIRECTION_INBOUND,  wkIn)   +
               buildTimetables(m1SaturdayOutbound, DayType.SATURDAY,  DIRECTION_OUTBOUND, satOut) +
               buildTimetables(m1SaturdayInbound,  DayType.SATURDAY,  DIRECTION_INBOUND,  satIn)
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
            routeId    = "M1",
            stopId     = stop.name,
            dayType    = dayType,
            direction  = direction,
            departures = deps[i]
        )
    }
}
