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
 * Parser for M8 route (Segovia – La Granja – Valsaín – Segovia)
 *
 * Linear route running between Segovia and Valsaín via La Granja de San Ildefonso.
 * Service runs on weekdays, Saturdays, and Sundays/holidays.
 *
 * Timetable data loaded from assets/timetables/m8.json (migrated from PDF 2026-03-25).
 */
class M8Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M8"),
        mode = ParserMode.PRODUCTION,
        version = "1.2"
    )

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Valsaín"
        private const val DIRECTION_INBOUND = "Valsaín → Segovia"

        // ── Stops ─────────────────────────────────────────────────────────────────────

        private object Stops {
            val OUT_ESTACION_BUS = BusStopRegistry.estacionAutobuses
            val OUT_IGLESIA_STO_TOMAS = BusStopRegistry.iglesiaStTomas
            val OUT_FRENTE_BAR_NORTE = BusStopRegistry.frenteBarNorte
            val OUT_PLAZA_TOROS = BusStopRegistry.plazaDeToros
            val CARRASCALEJO = BusStopRegistry.urbCarrascalejo
            val PENAS_DEL_ERIZO = BusStopRegistry.penasDelErizo
            val C_LA_FUENCISLA = BusStopRegistry.cLaFuencisla
            val PARQUE_ROBLEDO = BusStopRegistry.parqueRobledo
            val FABRICA_CRISTAL = BusStopRegistry.fabricaCristal
            val PISCINAS = BusStopRegistry.piscinas
            val PTAS_SEGOVIA = BusStopRegistry.ptasSegovia
            val LA_PRADERA = BusStopRegistry.laPradera
            val FRONTON = BusStopRegistry.fronton
            val PLAZA = BusStopRegistry.plazaValsain
            // Inbound — same canonical stops; direction distinguishes timetable buckets
            val PLAZA_IN = BusStopRegistry.plazaValsain
            val FRONTON_IN = BusStopRegistry.fronton
            val LA_PRADERA_IN = BusStopRegistry.laPradera
            val FABRICA_CRISTAL_IN = BusStopRegistry.fabricaCristal
            val PISCINAS_IN = BusStopRegistry.piscinas
            val PTAS_SEGOVIA_IN = BusStopRegistry.ptasSegovia
            val PARQUE_ROBLEDO_IN = BusStopRegistry.parqueRobledo
            val C_LA_FUENCISLA_IN = BusStopRegistry.cLaFuencisla
            val PENAS_DEL_ERIZO_IN = BusStopRegistry.penasDelErizo
            val CARRASCALEJO_IN = BusStopRegistry.urbCarrascalejo
            val IN_PLAZA_TOROS = BusStopRegistry.plazaDeToros
            val IN_FRENTE_BAR_NORTE = BusStopRegistry.frenteBarNorte
            val IN_IGLESIA_STO_TOMAS = BusStopRegistry.iglesiaStTomas
            val IN_ESTACION_BUS = BusStopRegistry.estacionAutobuses
        }

        // ── Stop lists ────────────────────────────────────────────────────────────────

        val m8Outbound: List<BusStop> = listOf(
            Stops.OUT_ESTACION_BUS, Stops.OUT_IGLESIA_STO_TOMAS,
            Stops.OUT_FRENTE_BAR_NORTE, Stops.OUT_PLAZA_TOROS,
            Stops.CARRASCALEJO, Stops.PENAS_DEL_ERIZO, Stops.C_LA_FUENCISLA,
            Stops.PARQUE_ROBLEDO, Stops.FABRICA_CRISTAL, Stops.PISCINAS,
            Stops.PTAS_SEGOVIA, Stops.LA_PRADERA, Stops.FRONTON, Stops.PLAZA
        )

        // Inbound note: F. Cristal appears before Piscinas/Ptas. Segovia in this direction
        // (bus takes a different one-way path through La Granja town centre)
        val m8Inbound: List<BusStop> = listOf(
            Stops.PLAZA_IN, Stops.FRONTON_IN, Stops.LA_PRADERA_IN,
            Stops.FABRICA_CRISTAL_IN, Stops.PISCINAS_IN, Stops.PTAS_SEGOVIA_IN,
            Stops.PARQUE_ROBLEDO_IN, Stops.C_LA_FUENCISLA_IN,
            Stops.PENAS_DEL_ERIZO_IN, Stops.CARRASCALEJO_IN,
            Stops.IN_PLAZA_TOROS, Stops.IN_FRENTE_BAR_NORTE,
            Stops.IN_IGLESIA_STO_TOMAS, Stops.IN_ESTACION_BUS
        )
    }

    // ── Protocol ──────────────────────────────────────────────────────────────────────

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M8", ignoreCase = true)) return emptyList()
        return listOf(m8Outbound, m8Inbound)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M8", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.WEEKDAY, DayType.SATURDAY, DayType.SUNDAY -> listOf(
                RouteVariant("outbound", DIRECTION_OUTBOUND, m8Outbound, DIRECTION_OUTBOUND),
                RouteVariant("inbound", DIRECTION_INBOUND, m8Inbound, DIRECTION_INBOUND)
            )
            else -> emptyList()
        }
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M8", ignoreCase = true)) return null
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
        if (!routeId.equals("M8", ignoreCase = true)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val isSunday = dow == java.util.Calendar.SUNDAY
        val weekdayViews = getRouteViews(routeId, DayType.WEEKDAY) ?: return emptyList()
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY) ?: return emptyList()
        val sundayViews = getRouteViews(routeId, DayType.SUNDAY) ?: return emptyList()
        return listOf(
            RouteSelectorEntry("entry-lv", "Lunes a Viernes", weekdayViews, "outbound", DayType.WEEKDAY, isWeekday),
            RouteSelectorEntry("entry-sabado", "Sábados", saturdayViews, "outbound", DayType.SATURDAY, isSaturday),
            RouteSelectorEntry("entry-domingo", "Domingos y Festivos", sundayViews, "outbound", DayType.SUNDAY, isSunday)
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M8Parser: loading timetable from JSON asset")
        return TimetableLoader(context).load("M8")
    }

}
