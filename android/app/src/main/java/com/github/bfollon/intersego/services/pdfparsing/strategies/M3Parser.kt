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
 * Parser for M3 route (Segovia – La Granja – Valsain – Parque Nacional de Guadarrama)
 *
 * M3 is a linear route operating Saturday only. The bus travels from Segovia to
 * Navacerrada via La Granja, Valsain, and Parque Nacional de Guadarrama, then
 * returns the same way.
 *
 * "Segovia" in the PDF is a cluster of 4 sub-stops:
 *   Estación de Autobuses → Iglesia Santo Tomás → Frente Bar Norte → Plaza de Toros
 * Times for the cluster are estimated at +2 min per sub-stop from the anchor time.
 *
 * No weekday or Sunday service. No seasonal restrictions.
 *
 * Timetable data loaded from assets/timetables/m3.json (migrated from PDF 2026-03-23).
 */
class M3Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M3"),
        mode = ParserMode.PRODUCTION,
        version = "1.2"
    )

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Navacerrada"
        private const val DIRECTION_INBOUND = "Navacerrada → Segovia"

        private object Stops {
            val ESTACION_BUS = BusStopRegistry.estacionAutobuses
            val IGLESIA_STO_TOMAS = BusStopRegistry.iglesiaStTomas
            val FRENTE_BAR_NORTE = BusStopRegistry.frenteBarNorte
            val PLAZA_TOROS = BusStopRegistry.plazaDeToros
            val URB_CARRASCALEJO = BusStopRegistry.urbCarrascalejo
            val PARQUE_ROBLEDO = BusStopRegistry.parqueRobledo
            val LA_GRANJA = BusStopRegistry.laGranja
            val VALSAIN = BusStopRegistry.valsainPradera
            val BOCA_DEL_ASNO = BusStopRegistry.bocaDelAsno
            val PUENTE_MOSQUITOS = BusStopRegistry.puenteMosquitos
            val NAVACERRADA = BusStopRegistry.navacerrada
        }

        val m3Outbound: List<BusStop> = listOf(
            Stops.ESTACION_BUS, Stops.IGLESIA_STO_TOMAS, Stops.FRENTE_BAR_NORTE, Stops.PLAZA_TOROS,
            Stops.URB_CARRASCALEJO, Stops.PARQUE_ROBLEDO, Stops.LA_GRANJA,
            Stops.VALSAIN, Stops.BOCA_DEL_ASNO, Stops.PUENTE_MOSQUITOS, Stops.NAVACERRADA
        )

        val m3Inbound: List<BusStop> = listOf(
            Stops.NAVACERRADA,
            Stops.PUENTE_MOSQUITOS,
            Stops.BOCA_DEL_ASNO,
            Stops.VALSAIN,
            Stops.LA_GRANJA,
            Stops.PARQUE_ROBLEDO,
            Stops.URB_CARRASCALEJO,
            Stops.PLAZA_TOROS,
            Stops.FRENTE_BAR_NORTE,
            Stops.IGLESIA_STO_TOMAS,
            Stops.ESTACION_BUS
        )
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M3", ignoreCase = true)) return emptyList()
        return listOf(m3Outbound, m3Inbound)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M3", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.SATURDAY -> listOf(
                RouteVariant("regular", DIRECTION_OUTBOUND, m3Outbound, DIRECTION_OUTBOUND),
                RouteVariant("reverse", DIRECTION_INBOUND, m3Inbound, DIRECTION_INBOUND)
            )
            else -> emptyList()
        }
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M3", ignoreCase = true)) return null
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
        if (!routeId.equals("M3", ignoreCase = true)) return emptyList()
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY) ?: return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isSaturday = dow == java.util.Calendar.SATURDAY
        return listOf(
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
        DebugConfig.debugPrint("M3Parser: loading timetable from JSON asset")
        return TimetableLoader(context).load("M3")
    }
}
