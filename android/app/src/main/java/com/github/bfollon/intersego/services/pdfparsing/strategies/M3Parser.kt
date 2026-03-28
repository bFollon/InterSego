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
 * Parser for M3 route (Segovia – La Granja – Valsain – Parque Nacional de Guadarrama)
 *
 * M3 is a linear route operating Saturday only. The bus travels from Segovia to
 * Navacerrada via La Granja, Valsain, and Parque Nacional de Guadarrama, then
 * returns the same way.
 *
 * The PDF shows two tables:
 *   outbound: Segovia → Navacerrada (top table)
 *   inbound:  Navacerrada → Segovia (bottom table)
 *
 * "Segovia" in the PDF is a cluster of 4 sub-stops:
 *   Estación de Autobuses → Iglesia Santo Tomás → Frente Bar Norte → Plaza de Toros
 * Times for the cluster are estimated at +2 min per sub-stop from the anchor time.
 *
 * No weekday or Sunday service. No seasonal restrictions.
 *
 * Timetable data hardcoded from official Linecar M3 PDF screenshot (2026-03-23).
 */
class M3Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M3"),
        mode = ParserMode.PRODUCTION,
        version = "1.0"
    )

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Navacerrada"
        private const val DIRECTION_INBOUND = "Navacerrada → Segovia"

        private object Stops {
            // Segovia cluster — outbound order
            val ESTACION_BUS = BusStop(
                id = "m3-estacion-bus",
                name = "Estación de Autobuses",
                area = "Segovia capital",
                coordinates = "40.944768, -4.121823"
            )
            val IGLESIA_STO_TOMAS = BusStop(
                id = "m3-iglesia-sto-tomas",
                name = "Iglesia Santo Tomás",
                area = "Segovia capital",
                coordinates = "40.941935, -4.118070"
            )
            val FRENTE_BAR_NORTE = BusStop(
                id = "m3-frente-bar-norte",
                name = "Frente Bar Norte",
                area = "Segovia capital",
                coordinates = "40.937206, -4.113999"
            )
            val PLAZA_TOROS = BusStop(
                id = "m3-plaza-toros",
                name = "Plaza de Toros",
                area = "Segovia capital",
                coordinates = "40.942093, -4.107603"
            )

            // Route stops
            val URB_CARRASCALEJO = BusStop(
                id = "m3-urb-carrascalejo",
                name = "Urb. Carrascalejo",
                area = "Carrascalejo",
                coordinates = "40.922847, -4.078270"
            )
            val PARQUE_ROBLEDO = BusStop(
                id = "m3-parque-robledo",
                name = "Parque Robledo",
                area = "Robledo",
                coordinates = "40.910111, -4.058750"
            )
            val LA_GRANJA = BusStop(
                id = "m3-la-granja",
                name = "La Granja (Pta de Segovia)",
                area = "La Granja",
                coordinates = "40.900286, -4.009502"
            )
            val VALSAIN = BusStop(
                id = "m3-valsain",
                name = "Valsain (La Pradera)",
                area = "Valsaín",
                coordinates = "40.878114, -4.018356"
            )
            val BOCA_DEL_ASNO = BusStop(
                id = "m3-boca-del-asno",
                name = "Boca del Asno",
                area = "Valsaín",
                coordinates = "40.844128, -4.025668"
            )
            val PUENTE_MOSQUITOS = BusStop(
                id = "m3-puente-mosquitos",
                name = "Puente de los Mosquitos",
                area = "Navacerrada",
                coordinates = "40.823325, -4.017340"
            )
            val NAVACERRADA = BusStop(
                id = "m3-navacerrada",
                name = "Navacerrada",
                area = "Navacerrada",
                coordinates = "40.788863, -4.003666"
            )

            // Segovia cluster — inbound (return) order: reversed cluster + distinct IDs
            val PLAZA_TOROS_IN = BusStop(
                id = "m3-plaza-toros-in",
                name = "Plaza de Toros",
                area = "Segovia capital",
                coordinates = "40.942093, -4.107603"
            )
            val FRENTE_BAR_NORTE_IN = BusStop(
                id = "m3-frente-bar-norte-in",
                name = "Frente Bar Norte",
                area = "Segovia capital",
                coordinates = "40.937206, -4.113999"
            )
            val IGLESIA_STO_TOMAS_IN = BusStop(
                id = "m3-iglesia-sto-tomas-in",
                name = "Iglesia Santo Tomás",
                area = "Segovia capital",
                coordinates = "40.941935, -4.118070"
            )
            val ESTACION_BUS_IN = BusStop(
                id = "m3-estacion-bus-in",
                name = "Estación de Autobuses",
                area = "Segovia capital",
                coordinates = "40.944768, -4.121823"
            )
        }

        // Outbound: Segovia cluster → ... → Navacerrada
        val m3Outbound: List<BusStop> = listOf(
            Stops.ESTACION_BUS, Stops.IGLESIA_STO_TOMAS, Stops.FRENTE_BAR_NORTE, Stops.PLAZA_TOROS,
            Stops.URB_CARRASCALEJO, Stops.PARQUE_ROBLEDO, Stops.LA_GRANJA,
            Stops.VALSAIN, Stops.BOCA_DEL_ASNO, Stops.PUENTE_MOSQUITOS, Stops.NAVACERRADA
        )

        // Inbound: Navacerrada → ... → Segovia cluster (reversed)
        val m3Inbound: List<BusStop> = listOf(
            Stops.NAVACERRADA,
            Stops.PUENTE_MOSQUITOS,
            Stops.BOCA_DEL_ASNO,
            Stops.VALSAIN,
            Stops.LA_GRANJA,
            Stops.PARQUE_ROBLEDO,
            Stops.URB_CARRASCALEJO,
            Stops.PLAZA_TOROS_IN,
            Stops.FRENTE_BAR_NORTE_IN,
            Stops.IGLESIA_STO_TOMAS_IN,
            Stops.ESTACION_BUS_IN
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
        DebugConfig.debugPrint("M3Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // ── Static timetable ─────────────────────────────────────────────────────────────────────
    //
    // Source: Linecar M3 PDF screenshot, 2026-03-23.
    // Saturday service only (Servicio de los Sábados). No weekday or Sunday service.
    //
    // "Segovia" in the PDF is a cluster of 4 sub-stops. The PDF gives one time for
    // "Segovia"; sub-stop times are estimated at +2 min per position from the anchor.
    //   Outbound anchor = Estación de Autobuses (first sub-stop)
    //   Inbound anchor  = Plaza de Toros (first sub-stop arriving back)

    private fun buildStaticTimetables(): List<BusTimetable> {

        // ── Saturday outbound: Segovia → Navacerrada ─────────────────────────────────────────
        // PDF times:  8:30  8:40  8:46  8:50  8:54  8:59  9:09  9:20
        //            16:00 16:10 16:16 16:20 16:24 16:29 16:39 16:50
        //
        // Segovia cluster (Estación→Iglesia→BarNorte→PlazaToros): 4 stops, +2 min each
        val segoviaOut = DepartureTime.clusterDepartures(
            mutableListOf(t(8, 30), t(16, 0)), stopCount = 4, offsetMinutes = 2
        )
        val outDeps = arrayOf(
            segoviaOut[0],  // ESTACION_BUS (anchor)
            segoviaOut[1],  // IGLESIA_STO_TOMAS (+2)
            segoviaOut[2],  // FRENTE_BAR_NORTE (+4)
            segoviaOut[3],  // PLAZA_TOROS (+6)
            mutableListOf(t(8, 40), t(16, 10)),  // URB_CARRASCALEJO
            mutableListOf(t(8, 46), t(16, 16)),  // PARQUE_ROBLEDO
            mutableListOf(t(8, 50), t(16, 20)),  // LA_GRANJA
            mutableListOf(t(8, 54), t(16, 24)),  // VALSAIN
            mutableListOf(t(8, 59), t(16, 29)),  // BOCA_DEL_ASNO
            mutableListOf(t(9, 9), t(16, 39)),  // PUENTE_MOSQUITOS
            mutableListOf(t(9, 20), t(16, 50)),  // NAVACERRADA
        )

        // ── Saturday inbound: Navacerrada → Segovia ──────────────────────────────────────────
        // PDF times:  9:30  9:42  9:52  9:56  9:59 10:03 10:09 10:20
        //            17:00 17:12 17:22 17:26 17:29 17:33 17:39 17:50
        //
        // Segovia cluster inbound (PlazaToros→BarNorte→Iglesia→Estación): 4 stops, +2 min each
        // Anchor = PDF "Segovia" arrival minus 6 min (e.g., 10:20 → 10:14)
        val segoviaIn = DepartureTime.clusterDepartures(
            mutableListOf(t(10, 14), t(17, 44)), stopCount = 4, offsetMinutes = 2
        )
        val inDeps = arrayOf(
            mutableListOf(t(9, 30), t(17, 0)),   // NAVACERRADA
            mutableListOf(t(9, 42), t(17, 12)),  // PUENTE_MOSQUITOS
            mutableListOf(t(9, 52), t(17, 22)),  // BOCA_DEL_ASNO
            mutableListOf(t(9, 56), t(17, 26)),  // VALSAIN
            mutableListOf(t(9, 59), t(17, 29)),  // LA_GRANJA
            mutableListOf(t(10, 3), t(17, 33)),  // PARQUE_ROBLEDO
            mutableListOf(t(10, 9), t(17, 39)),  // URB_CARRASCALEJO
            segoviaIn[0],  // PLAZA_TOROS_IN (anchor)
            segoviaIn[1],  // FRENTE_BAR_NORTE_IN (+2)
            segoviaIn[2],  // IGLESIA_STO_TOMAS_IN (+4)
            segoviaIn[3],  // ESTACION_BUS_IN (+6 = PDF time)
        )

        return buildTimetables(m3Outbound, DayType.SATURDAY, DIRECTION_OUTBOUND, outDeps) +
                buildTimetables(m3Inbound, DayType.SATURDAY, DIRECTION_INBOUND, inDeps)
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
            routeId = "M3",
            stopId = stop.id,
            dayType = dayType,
            direction = direction,
            departures = deps[i]
        )
    }
}
