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

import android.content.Context
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusStopRegistry
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.services.TimetableLoader
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
class M2Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M2"),
        mode = ParserMode.PRODUCTION,
        version = "1.2"
    )

    companion object {
        private const val DIRECTION_CIRCULAR_A = "Segovia → Valseca"
        private const val DIRECTION_CIRCULAR_B = "Valseca → Segovia"

        private object Stops {
            val SEGOVIA = BusStopRegistry.estacionAutobuses
            val CASINO = BusStopRegistry.casinoUnion
            val HONTANARES = BusStopRegistry.hontanares
            val LOS_HUERTOS = BusStopRegistry.losHuertos
            val VALSECA = BusStopRegistry.valseca
            // Return-leg — same canonical stops; direction distinguishes timetable buckets
            val HONTANARES_RETURN = BusStopRegistry.hontanares
            val CASINO_RETURN = BusStopRegistry.casinoUnion
            val SEGOVIA_RETURN = BusStopRegistry.estacionAutobuses
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
        DebugConfig.debugPrint("M2Parser: loading timetable from bundled JSON")
        return TimetableLoader(context).load("M2")
    }
}
