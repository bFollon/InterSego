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
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.data.RouteViewStop
import com.github.bfollon.intersego.data.SwapAction
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.TimetableLoader
import com.github.bfollon.intersego.services.pdfparsing.CapableParser
import com.github.bfollon.intersego.services.pdfparsing.ParserCapabilities
import com.github.bfollon.intersego.services.pdfparsing.ParserMode
import com.github.bfollon.intersego.services.pdfparsing.RouteStopsProvider

/**
 * Parser for M1 route (Segovia – Garcillán via Polígono, Casino, Valverde, Abades, Martín Miguel)
 *
 * M1 is a circular route with two main directions:
 *   circularA: Segovia → Polígono → Casino → Valverde → Abades → Martín Miguel → Garcillán → Segovia
 *   circularB: Segovia → Polígono → Garcillán → Martín Miguel → Abades → Valverde → Casino → Polígono → Segovia
 *
 * Saturday runs a shorter Segovia ↔ Abades variant (no Polígono, no Garcillán).
 *
 * Timetable data loaded from assets/timetables/m1.json.
 * Annotations encoded in JSON:
 *   ★  = juneToSept (Jun 13–Sep 13 only)
 *   (*) = garcillan-gasolinera alternateId (different pickup location, year-round)
 *   L Y V = monFriOnly
 *   #  = friOnly
 */
class M1Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M1"),
        mode = ParserMode.PRODUCTION,
        version = "2.1"
    )

    companion object {
        private const val DIRECTION_CIRCULAR_A = "Segovia → Garcillán"
        private const val DIRECTION_CIRCULAR_B = "Garcillán → Segovia"
        private const val DIRECTION_SAT_OUTBOUND = "Segovia → Abades"
        private const val DIRECTION_SAT_INBOUND = "Abades → Segovia"

        private object Stops {
            val SEGOVIA       = BusStopRegistry.estacionAutobuses
            val POLIGONO      = BusStopRegistry.poligonoIndM1
            val POLIGONO_2    = BusStopRegistry.poligonoIndM1B
            val CASINO        = BusStopRegistry.casinoUnion
            val VALVERDE      = BusStopRegistry.valverdeMajano
            val ABADES        = BusStopRegistry.abades
            val MARTIN_MIGUEL = BusStopRegistry.martinMiguel
            val GARCILLAN     = BusStopRegistry.garcillan
            val SEGOVIA_RET   = BusStopRegistry.estacionAutobusesCircRet
        }

        val m1CircularAWeekday: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.POLIGONO, Stops.POLIGONO_2,
            Stops.CASINO, Stops.VALVERDE, Stops.ABADES, Stops.MARTIN_MIGUEL,
            Stops.GARCILLAN, Stops.SEGOVIA_RET
        )

        val m1CircularBWeekday: List<BusStop> = listOf(
            Stops.GARCILLAN, Stops.MARTIN_MIGUEL, Stops.ABADES, Stops.VALVERDE,
            Stops.CASINO, Stops.POLIGONO_2, Stops.POLIGONO, Stops.SEGOVIA
        )

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
        return listOf(m1CircularAWeekday, m1CircularBWeekday, m1SaturdayOutbound, m1SaturdayInbound)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M1", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.SATURDAY -> listOf(
                RouteVariant("saturday-outbound", DIRECTION_SAT_OUTBOUND, m1SaturdayOutbound, DIRECTION_SAT_OUTBOUND),
                RouteVariant("saturday-inbound",  DIRECTION_SAT_INBOUND,  m1SaturdayInbound,  DIRECTION_SAT_INBOUND)
            )
            DayType.SUNDAY -> emptyList()
            else -> listOf(
                RouteVariant("circularA", DIRECTION_CIRCULAR_A, m1CircularAWeekday, DIRECTION_CIRCULAR_A),
                RouteVariant("circularB", DIRECTION_CIRCULAR_B, m1CircularBWeekday, DIRECTION_CIRCULAR_B)
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
        val isWeekday  = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val weekdayViews  = getRouteViews(routeId, DayType.WEEKDAY)  ?: return emptyList()
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY) ?: return emptyList()
        return listOf(
            RouteSelectorEntry("entry-lv-a",  "L-V - $DIRECTION_CIRCULAR_A",  weekdayViews,  "circularA",         DayType.WEEKDAY,  isWeekday),
            RouteSelectorEntry("entry-lv-b",  "L-V - $DIRECTION_CIRCULAR_B",  weekdayViews,  "circularB",         DayType.WEEKDAY,  isWeekday),
            RouteSelectorEntry("entry-sab-a", "Sáb - $DIRECTION_SAT_OUTBOUND", saturdayViews, "saturday-outbound", DayType.SATURDAY, isSaturday),
            RouteSelectorEntry("entry-sab-b", "Sáb - $DIRECTION_SAT_INBOUND",  saturdayViews, "saturday-inbound",  DayType.SATURDAY, isSaturday)
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M1Parser: loading timetable from JSON asset")
        return TimetableLoader(context).load("M1")
    }
}
