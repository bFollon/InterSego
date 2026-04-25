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
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusStopRegistry
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteTab
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
 * Parser for M6 route (Segovia ↔ Torrecaballeros).
 *
 * Route metadata (stop lists, RouteViews, RouteEntries) lives here.
 * Timetable data is loaded from assets/timetables/m6.json via TimetableLoader.
 *
 * Weekday: regular (Azoguejo cluster → Torrecaballeros), extended (→ from Andrés Laguna),
 * and circular (**21:20, Estación Bus loop via Palazuelos/Tabanera).
 * Saturday: circular route to Torrecaballeros (outbound); different urban return leg
 * (Plaza de Toros → La Pista → Andrés Laguna → Jardinillos).
 * Sunday: same rural leg as Saturday; urban return ends at Estación de Autobuses.
 */
class M6Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M6"),
        mode = ParserMode.PRODUCTION,
        version = "1.0"
    )

    data class StopCluster(val stops: List<BusStop>) {
        init { require(stops.isNotEmpty()) }
    }

    enum class ClusterAlignment { FROM_START, FROM_END }

    data class Route(
        val clusters: List<StopCluster>,
        val alignment: ClusterAlignment = ClusterAlignment.FROM_START
    ) {
        val stops: List<BusStop> get() = clusters.flatMap { it.stops }

        fun reversed(): Route = Route(
            clusters = clusters.reversed().map { StopCluster(it.stops.reversed()) },
            alignment = when (alignment) {
                ClusterAlignment.FROM_START -> ClusterAlignment.FROM_END
                ClusterAlignment.FROM_END  -> ClusterAlignment.FROM_START
            }
        )
    }

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Torrecaballeros"
        private const val DIRECTION_INBOUND  = "Torrecaballeros → Segovia"

        private object Stops {
            val AZOGUEJO          = BusStopRegistry.azoguejo
            val DELICIAS          = BusStopRegistry.delicias
            val MONTECORREDORES   = BusStopRegistry.montecorredores
            val SANCRIS           = BusStopRegistry.sanCristobal
            val SANCRIS_IGLESIA   = BusStopRegistry.sanCristobalIglesia
            val SANCRIS_ROTONDA   = BusStopRegistry.sanCristobalRotonda
            val SONSOTO           = BusStopRegistry.sonsoto
            val SONSOTO_2         = BusStopRegistry.sonsoto2
            val TRESCASAS         = BusStopRegistry.trescasas
            val TRESCASAS_2       = BusStopRegistry.trescasas2
            val CABANILLAS        = BusStopRegistry.cabanillas
            val TORRECABALLEROS   = BusStopRegistry.torrecaballeros
            val TORRECABALLEROS_2 = BusStopRegistry.torrecaballeros2
            val TORRECABALLEROS_3 = BusStopRegistry.torrecaballeros3
            val ANDRES_LAGUNA     = BusStopRegistry.andresLaguna
            val LA_PISTA          = BusStopRegistry.laPista
            val HERMANITAS        = BusStopRegistry.hermanitas
            val ESTACION_BUS      = BusStopRegistry.estacionAutobuses
            val PLAZA_TOROS       = BusStopRegistry.plazaDeToros
            val PALAZUELOS        = BusStopRegistry.palazuelos
            val PALAZUELOS_COLEGIO = BusStopRegistry.palazuelosColegio
            val TABANERA          = BusStopRegistry.tabanera
            val TABANERA_2        = BusStopRegistry.tabanera2
            val JARDINILLOS       = BusStopRegistry.jardinillos
        }

        object Routes {
            object Weekday {
                val regular = Route(
                    clusters = listOf(
                        StopCluster(listOf(Stops.AZOGUEJO, Stops.DELICIAS, Stops.MONTECORREDORES)),
                        StopCluster(listOf(Stops.SANCRIS, Stops.SANCRIS_IGLESIA, Stops.SANCRIS_ROTONDA)),
                        StopCluster(listOf(Stops.SONSOTO, Stops.SONSOTO_2)),
                        StopCluster(listOf(Stops.TRESCASAS, Stops.TRESCASAS_2)),
                        StopCluster(listOf(Stops.CABANILLAS)),
                        StopCluster(listOf(Stops.TORRECABALLEROS, Stops.TORRECABALLEROS_2, Stops.TORRECABALLEROS_3))
                    )
                )
                val reversed = regular.reversed()

                val extended = Route(
                    clusters = listOf(
                        StopCluster(listOf(
                            Stops.ANDRES_LAGUNA, Stops.LA_PISTA, Stops.HERMANITAS,
                            Stops.AZOGUEJO, Stops.DELICIAS, Stops.MONTECORREDORES
                        ))
                    ) + regular.clusters.drop(1)
                )
                val extendedReversed = extended.reversed()

                val circular = Route(
                    clusters = listOf(
                        StopCluster(listOf(Stops.ESTACION_BUS, Stops.ANDRES_LAGUNA, Stops.LA_PISTA, Stops.PLAZA_TOROS)),
                        StopCluster(listOf(Stops.PALAZUELOS, Stops.PALAZUELOS_COLEGIO)),
                        StopCluster(listOf(Stops.TABANERA, Stops.TABANERA_2)),
                        StopCluster(listOf(Stops.SANCRIS_IGLESIA, Stops.SANCRIS_ROTONDA)),
                        StopCluster(listOf(Stops.SONSOTO, Stops.SONSOTO_2)),
                        StopCluster(listOf(Stops.TRESCASAS, Stops.TRESCASAS_2)),
                        StopCluster(listOf(Stops.CABANILLAS)),
                        StopCluster(listOf(Stops.TORRECABALLEROS, Stops.TORRECABALLEROS_2, Stops.TORRECABALLEROS_3)),
                        StopCluster(listOf(Stops.DELICIAS, Stops.AZOGUEJO))
                    )
                )
            }

            object Saturday {
                val regular: Route = Weekday.circular.copy(clusters = Weekday.circular.clusters.dropLast(1))

                val reversed: Route = run {
                    val base = regular.reversed()
                    Route(
                        clusters = base.clusters.dropLast(1) + listOf(
                            StopCluster(listOf(Stops.PLAZA_TOROS, Stops.LA_PISTA, Stops.ANDRES_LAGUNA, Stops.JARDINILLOS))
                        ),
                        alignment = base.alignment
                    )
                }
            }

            object Sunday {
                val regular: Route = Weekday.circular.copy(clusters = Weekday.circular.clusters.dropLast(1))
                val reversed: Route = regular.reversed()
            }
        }
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M6Parser: loading timetable from JSON asset")
        return TimetableLoader(context).load("M6")
    }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M6", ignoreCase = true)) return emptyList()
        return getRouteVariants(routeId, DayType.WEEKDAY).map { it.stops }
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M6", ignoreCase = true)) return null

        val extendedOnlyIds = setOf(
            Stops.ANDRES_LAGUNA.id, Stops.LA_PISTA.id, Stops.HERMANITAS.id
        )

        fun extendedStops(route: Route): List<RouteViewStop> = route.stops.map { stop ->
            RouteViewStop(stop, isExtendedOnly = stop.id in extendedOnlyIds)
        }

        fun plainStops(route: Route): List<RouteViewStop> = route.stops.map { RouteViewStop(it) }

        return when (dayType) {
            DayType.WEEKDAY -> {
                val tabs = listOf(
                    RouteTab("Regular", "weekday-unified"),
                    RouteTab("Circular", "weekday-circular")
                )
                listOf(
                    RouteView(
                        id = "weekday-unified",
                        label = "Segovia → Torrecaballeros",
                        stops = extendedStops(Routes.Weekday.extended),
                        direction = DIRECTION_OUTBOUND,
                        departureLabel = null,
                        swapAction = SwapAction("weekday-unified-reversed"),
                        tabs = tabs,
                        extendedSectionLabel = "Ruta extendida"
                    ),
                    RouteView(
                        id = "weekday-unified-reversed",
                        label = "Torrecaballeros → Segovia",
                        stops = extendedStops(Routes.Weekday.extendedReversed),
                        direction = DIRECTION_INBOUND,
                        departureLabel = null,
                        swapAction = SwapAction("weekday-unified"),
                        tabs = tabs,
                        extendedSectionLabel = "Ruta extendida"
                    ),
                    RouteView(
                        id = "weekday-circular",
                        label = "Circular",
                        stops = plainStops(Routes.Weekday.circular),
                        direction = DIRECTION_OUTBOUND,
                        departureLabel = "Circular",
                        tabs = tabs
                    )
                )
            }

            DayType.SATURDAY -> listOf(
                RouteView(
                    id = "saturday-regular",
                    label = "Segovia → Torrecaballeros",
                    stops = plainStops(Routes.Saturday.regular),
                    direction = DIRECTION_OUTBOUND,
                    departureLabel = "Sábado"
                ),
                RouteView(
                    id = "saturday-reversed",
                    label = "Torrecaballeros → Segovia",
                    stops = plainStops(Routes.Saturday.reversed),
                    direction = DIRECTION_INBOUND,
                    departureLabel = "Sábado"
                )
            )

            DayType.SUNDAY -> listOf(
                RouteView(
                    id = "sunday-regular",
                    label = "Segovia → Torrecaballeros",
                    stops = plainStops(Routes.Sunday.regular),
                    direction = DIRECTION_OUTBOUND,
                    departureLabel = "Domingo",
                    swapAction = SwapAction("sunday-reversed")
                ),
                RouteView(
                    id = "sunday-reversed",
                    label = "Torrecaballeros → Segovia",
                    stops = plainStops(Routes.Sunday.reversed),
                    direction = DIRECTION_INBOUND,
                    departureLabel = "Domingo",
                    swapAction = SwapAction("sunday-regular")
                )
            )

            else -> getRouteViews(routeId, DayType.WEEKDAY)
        }
    }

    override fun getRouteEntries(routeId: String, today: java.util.Date): List<RouteSelectorEntry> {
        if (!routeId.equals("M6", ignoreCase = true)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday  = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val isSunday   = dow == java.util.Calendar.SUNDAY
        val weekdayViews  = getRouteViews(routeId, DayType.WEEKDAY)!!
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY)!!
        val sundayViews   = getRouteViews(routeId, DayType.SUNDAY)!!
        return listOf(
            RouteSelectorEntry("entry-lv-regular",   "L-V - Regular",   weekdayViews,                                               "weekday-unified",   DayType.WEEKDAY,  isWeekday),
            RouteSelectorEntry("entry-lv-circular",  "L-V - Circular",  weekdayViews,                                               "weekday-circular",  DayType.WEEKDAY,  isWeekday),
            RouteSelectorEntry("entry-sabado-ida",   "Sáb - Ida",       saturdayViews.filter { it.id == "saturday-regular" },       "saturday-regular",  DayType.SATURDAY, isSaturday),
            RouteSelectorEntry("entry-sabado-vuelta","Sáb - Vuelta",    saturdayViews.filter { it.id == "saturday-reversed" },      "saturday-reversed", DayType.SATURDAY, isSaturday),
            RouteSelectorEntry("entry-domingo",      "Domingo",          sundayViews,                                                "sunday-regular",    DayType.SUNDAY,   isSunday)
        )
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        val views = getRouteViews(routeId, dayType) ?: return emptyList()
        return views.map { view ->
            RouteVariant(
                id = view.id,
                label = view.label,
                stops = view.stops.map { it.stop },
                direction = view.direction,
                departureLabel = view.departureLabel
            )
        }
    }
}
