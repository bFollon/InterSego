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
 * Parser for M2 route (Segovia – Hontanares – Los Huertos – Valseca)
 *
 * M2 is a circular route operating Monday–Friday only. Each service departs Segovia,
 * goes outbound through Casino, Hontanares, and Los Huertos, then backtracks through
 * Hontanares to reach Valseca before returning to Segovia:
 *
 *   Segovia → Casino → Hontanares → Los Huertos → Hontanares → Valseca → Casino → Segovia
 *
 * The timetable PDF shows two half-tables:
 *   circularA (left): Segovia → Casino → Hontanares → Los Huertos → Valseca (outbound view)
 *   circularB (right): Los Huertos → Hontanares → Valseca → Casino → Segovia (return view)
 *
 * Note: The 7:25 inbound service originates from Valseca and follows the direct reverse
 * of the outbound route (Valseca → Los Huertos → Hontanares → Casino → Segovia). In the
 * circularB stop list this means Valseca's departure time (7:25) precedes Los Huertos (7:35),
 * which is chronologically out of stop order for that run. All other circularB services
 * follow the expected stop sequence.
 *
 * No weekend service. No seasonal restrictions.
 *
 * Timetable data hardcoded from official Linecar M2 PDF screenshot (2026-03-20).
 */
class M2Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M2"),
        mode = ParserMode.PRODUCTION,
        version = "1.0"
    )

    companion object {
        private const val DIRECTION_CIRCULAR_A = "Segovia → Valseca"
        private const val DIRECTION_CIRCULAR_B = "Valseca → Segovia"

        private object Stops {
            // Outbound stops
            val SEGOVIA = BusStop(
                id = "m2-segovia",
                name = "Estación de Autobuses",
                area = "Segovia Capital",
                coordinates = "40.944973, -4.122431"
            )
            val CASINO = BusStop(
                id = "m2-casino",
                name = "Casino",
                area = "Casino de la Unión",
                coordinates = "40.965154, -4.209251"
            )
            val HONTANARES = BusStop(
                id = "m2-hontanares",
                name = "Hontanares de Eresma",
                area = "Hontanares de Eresma",
                coordinates = "40.983628, -4.204160"
            )
            val LOS_HUERTOS = BusStop(
                id = "m2-los-huertos",
                name = "Los Huertos",
                area = "Los Huertos",
                coordinates = "41.009124, -4.219216"
            )
            val VALSECA = BusStop(
                id = "m2-valseca",
                name = "Valseca",
                area = "Valseca",
                coordinates = "40.999306, -4.174266"
            )

            // Return-leg stops — same physical locations, distinct IDs for route positioning
            val HONTANARES_RETURN = BusStop(
                id = "m2-hontanares-return",
                name = "Hontanares de Eresma",
                area = "Hontanares de Eresma",
                coordinates = "40.983628, -4.204160"
            )
            val CASINO_RETURN = BusStop(
                id = "m2-casino-return",
                name = "Casino",
                area = "Casino de la Unión",
                coordinates = "40.965154, -4.209251"
            )
            val SEGOVIA_RETURN = BusStop(
                id = "m2-segovia-return",
                name = "Estación de Autobuses",
                area = "Segovia Capital",
                coordinates = "40.944973, -4.122431"
            )
        }

        // circularA: outbound view — Segovia → Casino → Hontanares → Los Huertos → Valseca
        val m2CircularA: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.CASINO, Stops.HONTANARES, Stops.LOS_HUERTOS, Stops.VALSECA
        )

        // circularB: return view — Los Huertos → Hontanares → Valseca → Casino → Segovia
        // Note: LOS_HUERTOS and VALSECA share stop IDs with circularA (same physical stops).
        val m2CircularB: List<BusStop> = listOf(
            Stops.LOS_HUERTOS,
            Stops.HONTANARES_RETURN,
            Stops.VALSECA,
            Stops.CASINO_RETURN,
            Stops.SEGOVIA_RETURN
        )
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M2", ignoreCase = true)) return emptyList()
        return listOf(m2CircularA, m2CircularB)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M2", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.SATURDAY, DayType.SUNDAY -> emptyList()
            else -> listOf(
                RouteVariant("circularA", DIRECTION_CIRCULAR_A, m2CircularA, DIRECTION_CIRCULAR_A),
                RouteVariant("circularB", DIRECTION_CIRCULAR_B, m2CircularB, DIRECTION_CIRCULAR_B)
            )
        }
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M2", ignoreCase = true)) return null
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
        if (!routeId.equals("M2", ignoreCase = true)) return emptyList()
        val weekdayViews = getRouteViews(routeId, DayType.WEEKDAY) ?: return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        return listOf(
            RouteSelectorEntry(
                "entry-lv",
                "Lunes a Viernes",
                weekdayViews,
                "circularA",
                DayType.WEEKDAY,
                isWeekday
            )
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M2Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // ── Static timetable ─────────────────────────────────────────────────────────────────────
    //
    // Source: Linecar M2 PDF screenshot, 2026-03-20.
    // Weekday service only (Lunes a Viernes). No Saturday or Sunday service.
    //
    // The route is circular: the bus travels outbound from Segovia to Los Huertos, backtracks
    // through Hontanares, continues to Valseca, then returns to Segovia.
    // The left (outbound) and right (inbound) tables in the PDF are complementary halves of
    // the same circular service.

    private fun buildStaticTimetables(): List<BusTimetable> {

        // ── Weekday circularA: Segovia → Casino → Hontanares → Los Huertos → Valseca ──────────
        val circADeps = arrayOf(
            mutableListOf(t(9, 0), t(13, 45), t(16, 15), t(19, 15)), // SEGOVIA
            mutableListOf(t(9, 5), t(13, 50), t(16, 20), t(19, 20)), // CASINO
            mutableListOf(t(9, 10), t(13, 55), t(16, 25), t(19, 25)), // HONTANARES
            mutableListOf(t(9, 15), t(14, 0), t(16, 30), t(19, 30)), // LOS_HUERTOS
            mutableListOf(t(9, 30), t(14, 15), t(16, 40), t(19, 40))  // VALSECA
        )

        // ── Weekday circularB: Los Huertos → Hontanares → Valseca → Casino → Segovia ──────────
        //
        // The 7:25 service originates from Valseca: its stop times are Valseca(7:25),
        // Los Huertos(7:35), Hontanares(7:40), Casino(7:42), Segovia(7:55).
        // In this stop list Valseca appears at position 2 (after Los Huertos), so the 7:25
        // Valseca time is chronologically earlier than Los Huertos 7:35. All other runs
        // (9:15, 14:00, 16:30, 19:30) follow the expected stop order.
        val circBDeps = arrayOf(
            mutableListOf(t(7, 35), t(9, 15), t(14, 0), t(16, 30), t(19, 30)), // LOS_HUERTOS
            mutableListOf(t(7, 40), t(9, 20), t(14, 5), t(16, 35), t(19, 35)), // HONTANARES_RETURN
            mutableListOf(
                t(7, 25),
                t(9, 30),
                t(14, 15),
                t(16, 45),
                t(19, 45)
            ), // VALSECA (7:25 = Valseca-originating run)
            mutableListOf(t(7, 42), t(9, 35), t(14, 20), t(16, 50), t(19, 50)), // CASINO_RETURN
            mutableListOf(t(7, 55), t(9, 50), t(14, 35), t(17, 5), t(20, 5))   // SEGOVIA_RETURN
        )

        return buildTimetables(m2CircularA, DayType.WEEKDAY, DIRECTION_CIRCULAR_A, circADeps) +
                buildTimetables(m2CircularB, DayType.WEEKDAY, DIRECTION_CIRCULAR_B, circBDeps)
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
            routeId = "M2",
            stopId = stop.id,
            dayType = dayType,
            direction = direction,
            departures = deps[i]
        )
    }
}
