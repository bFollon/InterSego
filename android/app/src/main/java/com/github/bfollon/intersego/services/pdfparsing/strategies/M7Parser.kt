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
 * Parser for M7 route (Segovia – Tabanera – Palazuelos – Segovia / Torrecaballeros)
 *
 * M7 has three distinct service patterns:
 *
 * **Weekday (Lunes a Viernes):** Circular Segovia → Tabanera → Palazuelos → Segovia; 18 trips.
 *   First trip originates from Tabanera (no Segovia departure).
 *
 * **Saturday/Sunday (extended route):** Segovia cluster → Palazuelos → Tabanera → San Cristóbal
 *   → Sonsoto → Trescasas → Cabanillas → Torrecaballeros (and reverse).
 *   Saturday: 5 outbound (13:30 partial to Trescasas), 4 inbound.
 *   Sunday: 4 outbound (20:30 schoolOnly / 19:30 summerOnly partial to Trescasas), 4 inbound
 *   (20:54 schoolOnly / 19:54 summerOnly start from Sonsoto).
 *
 * Timetable data loaded from assets/timetables/m7.json (migrated from PDF 2026-03-25).
 */
class M7Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M7"),
        mode = ParserMode.PRODUCTION,
        version = "1.7"
    )

    companion object {
        private const val DIRECTION_WEEKDAY_CIRCULAR = "Circular"
        private const val DIRECTION_SAT_SUN_OUTBOUND = "Segovia → Torrecaballeros"
        private const val DIRECTION_SAT_SUN_INBOUND = "Torrecaballeros → Segovia"

        // ── Stops ───────────────────────────────────────────────────────────────────────────────

        private object Stops {
            // Weekday circular stops
            val SEGOVIA_WK = BusStopRegistry.estacionAutobuses
            val TABANERA_WK = BusStopRegistry.tabanera
            val TABANERA_WK_2 = BusStopRegistry.tabanera2
            val PALAZUELOS_WK = BusStopRegistry.palazuelos
            val PALAZUELOS_COLEGIO_WK = BusStopRegistry.palazuelosColegio
            val SEGOVIA_WK_RET = BusStopRegistry.estacionAutobusesCircRet

            // Saturday/Sunday outbound Segovia cluster
            val OUT_ESTACION_BUS = BusStopRegistry.estacionAutobuses
            val OUT_HOSPITAL = BusStopRegistry.hospitalSegovia
            val OUT_ANDRES_LAGUNA = BusStopRegistry.andresLaguna
            val OUT_LA_PISTA = BusStopRegistry.laPista
            val OUT_PLAZA_TOROS = BusStopRegistry.plazaDeToros

            // Extended route stops (shared by Saturday and Sunday)
            val PALAZUELOS = BusStopRegistry.palazuelos
            val TABANERA = BusStopRegistry.tabanera
            val TABANERA_2 = BusStopRegistry.tabanera2
            val S_CRISTOBAL = BusStopRegistry.sanCristobal
            val S_CRISTOBAL_IGLESIA = BusStopRegistry.sanCristobalIglesia
            val S_CRISTOBAL_ROTONDA = BusStopRegistry.sanCristobalRotonda
            val SONSOTO = BusStopRegistry.sonsoto
            val SONSOTO_2 = BusStopRegistry.sonsoto2
            val TRESCASAS = BusStopRegistry.trescasas
            val TRESCASAS_2 = BusStopRegistry.trescasas2
            val CABANILLAS = BusStopRegistry.cabanillas
            val TORRECAB = BusStopRegistry.torrecaballeros
            val TORRECAB_2 = BusStopRegistry.torrecaballeros2
            val TORRECAB_3 = BusStopRegistry.torrecaballeros3

            // Saturday/Sunday inbound Segovia cluster
            val IN_PLAZA_TOROS = BusStopRegistry.plazaDeToros
            val IN_LA_PISTA = BusStopRegistry.laPista
            val IN_ANDRES_LAGUNA = BusStopRegistry.andresLaguna
            val IN_JARDINILLOS = BusStopRegistry.jardinillos
        }

        // ── Stop lists ──────────────────────────────────────────────────────────────────────────

        // Weekday circular: Segovia → Tabanera (cluster) → Palazuelos (cluster) → Segovia
        val m7WeekdayCircular: List<BusStop> = listOf(
            Stops.SEGOVIA_WK,
            Stops.TABANERA_WK,
            Stops.TABANERA_WK_2,
            Stops.PALAZUELOS_WK,
            Stops.PALAZUELOS_COLEGIO_WK,
            Stops.SEGOVIA_WK_RET
        )

        // Saturday/Sunday outbound: Segovia cluster → ... → Torrecaballeros
        val m7ExtOutbound: List<BusStop> = listOf(
            Stops.OUT_ESTACION_BUS, Stops.OUT_HOSPITAL, Stops.OUT_ANDRES_LAGUNA,
            Stops.OUT_LA_PISTA, Stops.OUT_PLAZA_TOROS,
            Stops.PALAZUELOS, Stops.TABANERA, Stops.TABANERA_2,
            Stops.S_CRISTOBAL, Stops.S_CRISTOBAL_IGLESIA, Stops.S_CRISTOBAL_ROTONDA,
            Stops.SONSOTO, Stops.SONSOTO_2,
            Stops.TRESCASAS, Stops.TRESCASAS_2,
            Stops.CABANILLAS,
            Stops.TORRECAB, Stops.TORRECAB_2, Stops.TORRECAB_3
        )

        // Saturday/Sunday inbound: Torrecaballeros → ... → Segovia cluster
        val m7ExtInbound: List<BusStop> = listOf(
            Stops.TORRECAB_3, Stops.TORRECAB_2, Stops.TORRECAB,
            Stops.CABANILLAS,
            Stops.TRESCASAS_2, Stops.TRESCASAS,
            Stops.SONSOTO_2, Stops.SONSOTO,
            Stops.S_CRISTOBAL_ROTONDA, Stops.S_CRISTOBAL_IGLESIA, Stops.S_CRISTOBAL,
            Stops.TABANERA_2, Stops.TABANERA, Stops.PALAZUELOS,
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
        DebugConfig.debugPrint("M7Parser: loading timetable from JSON asset")
        return TimetableLoader(context).load("M7")
    }

}
