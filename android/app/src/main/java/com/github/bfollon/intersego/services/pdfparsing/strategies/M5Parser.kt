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
 * Parser for M5 route (Segovia – Sto. Domingo de Pirón)
 *
 * M5 is a linear route operating weekdays and Saturdays. The bus travels from Segovia
 * to Santo Domingo de Pirón via Tizneros, Espirdo, La Higuera, Brieva, and Basardilla.
 *
 * Partial trips: some weekday services only run Segovia–La Higuera (outbound) or
 * start from Brieva/La Higuera (inbound). Stops not served are null in the JSON.
 *
 * No Sunday service. No seasonal restrictions.
 *
 * Timetable data loaded from assets/timetables/m5.json (migrated from PDF 2026-03-25).
 */
class M5Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M5"),
        mode = ParserMode.PRODUCTION,
        version = "1.2"
    )

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Sto. Domingo de Pirón"
        private const val DIRECTION_INBOUND = "Sto. Domingo de Pirón → Segovia"

        val m5Outbound: List<BusStop> = listOf(
            BusStopRegistry.estacionAutobuses,
            BusStopRegistry.tizneros,
            BusStopRegistry.espirdo,
            BusStopRegistry.laHiguera,
            BusStopRegistry.brieva,
            BusStopRegistry.basardilla,
            BusStopRegistry.stoDomingoPiron
        )

        val m5Inbound: List<BusStop> = listOf(
            BusStopRegistry.stoDomingoPiron,
            BusStopRegistry.basardilla,
            BusStopRegistry.brieva,
            BusStopRegistry.laHiguera,
            BusStopRegistry.espirdo,
            BusStopRegistry.tizneros,
            BusStopRegistry.estacionAutobuses
        )
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M5", ignoreCase = true)) return emptyList()
        return listOf(m5Outbound, m5Inbound)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M5", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.WEEKDAY, DayType.SATURDAY -> listOf(
                RouteVariant("regular", DIRECTION_OUTBOUND, m5Outbound, DIRECTION_OUTBOUND),
                RouteVariant("reverse", DIRECTION_INBOUND, m5Inbound, DIRECTION_INBOUND)
            )
            else -> emptyList()
        }
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M5", ignoreCase = true)) return null
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
        if (!routeId.equals("M5", ignoreCase = true)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val weekdayViews = getRouteViews(routeId, DayType.WEEKDAY) ?: return emptyList()
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY) ?: return emptyList()
        return listOf(
            RouteSelectorEntry(
                "entry-lv",
                "Lunes a Viernes",
                weekdayViews,
                "regular",
                DayType.WEEKDAY,
                isWeekday
            ),
            RouteSelectorEntry(
                "entry-sabado",
                "Sábados",
                saturdayViews,
                "regular",
                DayType.SATURDAY,
                isSaturday
            )
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M5Parser: loading timetable from JSON asset")
        return TimetableLoader(context).load("M5")
    }
}
