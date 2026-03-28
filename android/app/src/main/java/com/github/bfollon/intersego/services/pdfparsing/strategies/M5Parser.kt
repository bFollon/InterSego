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
 * Parser for M5 route (Segovia – Sto. Domingo de Pirón)
 *
 * M5 is a linear route operating weekdays and Saturdays. The bus travels from Segovia
 * to Santo Domingo de Pirón via Tizneros, Espirdo, La Higuera, Brieva, and Basardilla.
 *
 * The PDF shows four tables:
 *   Weekday outbound:  Segovia → Sto. Domingo de Pirón (3 trips, 2 partial)
 *   Weekday inbound:   Sto. Domingo de Pirón → Segovia (3 trips, 2 partial)
 *   Saturday outbound:  Segovia → Sto. Domingo de Pirón (1 trip)
 *   Saturday inbound:   Sto. Domingo de Pirón → Segovia (1 trip)
 *
 * Partial trips: some weekday services only run Segovia–La Higuera (outbound) or
 * start from Brieva/La Higuera (inbound). Stops not served simply have fewer departures.
 *
 * Footnotes:
 *   * = departure from Via Roma (weekday outbound Segovia)
 *   *** = departure from Estación de Autobuses (Saturday outbound Segovia)
 *
 * No Sunday service. No seasonal restrictions.
 *
 * Timetable data hardcoded from official Linecar M5 PDF screenshot (2026-03-25).
 */
class M5Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M5"),
        mode = ParserMode.PRODUCTION,
        version = "1.0"
    )

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Sto. Domingo de Pirón"
        private const val DIRECTION_INBOUND = "Sto. Domingo de Pirón → Segovia"

        private object Stops {
            val SEGOVIA = BusStop(
                id = "m5-segovia",
                name = "Segovia",
                area = "Segovia capital",
                coordinates = "40.944768, -4.121823"
            )
            val TIZNEROS = BusStop(
                id = "m5-tizneros",
                name = "Tizneros",
                area = "Tizneros",
                coordinates = "40.991521, -4.055240"
            )
            val ESPIRDO = BusStop(
                id = "m5-espirdo",
                name = "Espirdo",
                area = "Espirdo",
                coordinates = "40.996957, -4.073623"
            )
            val LA_HIGUERA = BusStop(
                id = "m5-la-higuera",
                name = "La Higuera",
                area = "La Higuera",
                coordinates = "41.016117, -4.080770"
            )
            val BRIEVA = BusStop(
                id = "m5-brieva",
                name = "Brieva",
                area = "Brieva",
                coordinates = "41.035677, -4.052387"
            )
            val BASARDILLA = BusStop(
                id = "m5-basardilla",
                name = "Basardilla",
                area = "Basardilla",
                coordinates = "41.027220, -4.025058"
            )
            val STO_DOMINGO_PIRON = BusStop(
                id = "m5-sto-domingo-piron",
                name = "Sto. Domingo de Pirón",
                area = "Sto. Domingo de Pirón",
                coordinates = "41.041438, -3.989562"
            )

            // Inbound copies with -in suffix
            val SEGOVIA_IN = BusStop(
                id = "m5-segovia-in",
                name = "Segovia",
                area = "Segovia capital",
                coordinates = "40.944768, -4.121823"
            )
            val TIZNEROS_IN = BusStop(
                id = "m5-tizneros-in",
                name = "Tizneros",
                area = "Tizneros",
                coordinates = "40.991521, -4.055240"
            )
            val ESPIRDO_IN = BusStop(
                id = "m5-espirdo-in",
                name = "Espirdo",
                area = "Espirdo",
                coordinates = "40.996957, -4.073623"
            )
            val LA_HIGUERA_IN = BusStop(
                id = "m5-la-higuera-in",
                name = "La Higuera",
                area = "La Higuera",
                coordinates = "41.016117, -4.080770"
            )
            val BRIEVA_IN = BusStop(
                id = "m5-brieva-in",
                name = "Brieva",
                area = "Brieva",
                coordinates = "41.035677, -4.052387"
            )
            val BASARDILLA_IN = BusStop(
                id = "m5-basardilla-in",
                name = "Basardilla",
                area = "Basardilla",
                coordinates = "41.027220, -4.025058"
            )
            val STO_DOMINGO_PIRON_IN = BusStop(
                id = "m5-sto-domingo-piron-in",
                name = "Sto. Domingo de Pirón",
                area = "Sto. Domingo de Pirón",
                coordinates = "41.041438, -3.989562"
            )
        }

        // Outbound: Segovia → Sto. Domingo de Pirón
        val m5Outbound: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.TIZNEROS, Stops.ESPIRDO, Stops.LA_HIGUERA,
            Stops.BRIEVA, Stops.BASARDILLA, Stops.STO_DOMINGO_PIRON
        )

        // Inbound: Sto. Domingo de Pirón → Segovia
        val m5Inbound: List<BusStop> = listOf(
            Stops.STO_DOMINGO_PIRON_IN, Stops.BASARDILLA_IN, Stops.BRIEVA_IN,
            Stops.LA_HIGUERA_IN, Stops.ESPIRDO_IN, Stops.TIZNEROS_IN, Stops.SEGOVIA_IN
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
        DebugConfig.debugPrint("M5Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // ── Static timetable ─────────────────────────────────────────────────────────────────────
    //
    // Source: Linecar M5 PDF screenshot, 2026-03-25.
    // Weekday + Saturday service. No Sunday service. No seasonal restrictions.
    //
    // Partial trips:
    //   Outbound: 15:15 and 20:30 only go to La Higuera (Brieva/Basardilla/StoDomingo = dash)
    //   Inbound:  07:25 starts from Brieva (StoDomingo/Basardilla = dash)
    //             15:45 starts from La Higuera (StoDomingo/Basardilla/Brieva = dash)
    //
    // Footnotes:
    //   * weekday outbound Segovia = departure from Via Roma
    //   *** Saturday outbound Segovia = departure from Estación de Autobuses

    private fun buildStaticTimetables(): List<BusTimetable> {

        // ── Weekday outbound: Segovia → Sto. Domingo de Pirón ──────────────────────────────────
        // PDF row 1: 13:45  14:00  14:10  14:15  14:25  14:30  14:35  (full trip)
        // PDF row 2: 15:15  15:30  15:40  15:45   —      —      —    (partial: ends La Higuera)
        // PDF row 3: 20:30  20:45  20:55  21:00   —      —      —    (partial: ends La Higuera)
        val wkOutDeps = arrayOf(
            mutableListOf(t(13, 45), t(15, 15), t(20, 30)),  // SEGOVIA
            mutableListOf(t(14, 0), t(15, 30), t(20, 45)),  // TIZNEROS
            mutableListOf(t(14, 10), t(15, 40), t(20, 55)),  // ESPIRDO
            mutableListOf(t(14, 15), t(15, 45), t(21, 0)),   // LA_HIGUERA
            mutableListOf(t(14, 25)),                       // BRIEVA (row 1 only)
            mutableListOf(t(14, 30)),                       // BASARDILLA (row 1 only)
            mutableListOf(t(14, 35))                        // STO_DOMINGO_PIRON (row 1 only)
        )

        // ── Weekday inbound: Sto. Domingo de Pirón → Segovia ──────────────────────────────────
        // PDF row 1:  —      —     07:25  07:30  07:35  07:40  07:50  (partial: starts Brieva)
        // PDF row 2: 10:00  10:05  10:10  10:20  10:25  10:30  10:40  (full trip)
        // PDF row 3:  —      —      —     15:45  15:50  15:55  16:05  (partial: starts La Higuera)
        val wkInDeps = arrayOf(
            mutableListOf(t(10, 0)),                         // STO_DOMINGO_PIRON_IN (row 2 only)
            mutableListOf(t(10, 5)),                         // BASARDILLA_IN (row 2 only)
            mutableListOf(t(7, 25), t(10, 10)),              // BRIEVA_IN (rows 1,2)
            mutableListOf(t(7, 30), t(10, 20), t(15, 45)),   // LA_HIGUERA_IN
            mutableListOf(t(7, 35), t(10, 25), t(15, 50)),   // ESPIRDO_IN
            mutableListOf(t(7, 40), t(10, 30), t(15, 55)),   // TIZNEROS_IN
            mutableListOf(t(7, 50), t(10, 40), t(16, 5))     // SEGOVIA_IN
        )

        // ── Saturday outbound: Segovia → Sto. Domingo de Pirón ─────────────────────────────────
        // PDF row 1: 12:45  13:00  13:05  13:10  13:20  13:25  13:30  (full trip)
        val satOutDeps = arrayOf(
            mutableListOf(t(12, 45)),  // SEGOVIA
            mutableListOf(t(13, 0)),   // TIZNEROS
            mutableListOf(t(13, 5)),   // ESPIRDO
            mutableListOf(t(13, 10)),  // LA_HIGUERA
            mutableListOf(t(13, 20)),  // BRIEVA
            mutableListOf(t(13, 25)),  // BASARDILLA
            mutableListOf(t(13, 30))   // STO_DOMINGO_PIRON
        )

        // ── Saturday inbound: Sto. Domingo de Pirón → Segovia ──────────────────────────────────
        // PDF row 1: 10:00  10:05  10:10  10:20  10:25  10:30  10:40  (full trip)
        val satInDeps = arrayOf(
            mutableListOf(t(10, 0)),   // STO_DOMINGO_PIRON_IN
            mutableListOf(t(10, 5)),   // BASARDILLA_IN
            mutableListOf(t(10, 10)),  // BRIEVA_IN
            mutableListOf(t(10, 20)),  // LA_HIGUERA_IN
            mutableListOf(t(10, 25)),  // ESPIRDO_IN
            mutableListOf(t(10, 30)),  // TIZNEROS_IN
            mutableListOf(t(10, 40))   // SEGOVIA_IN
        )

        return buildTimetables(m5Outbound, DayType.WEEKDAY, DIRECTION_OUTBOUND, wkOutDeps) +
                buildTimetables(m5Inbound, DayType.WEEKDAY, DIRECTION_INBOUND, wkInDeps) +
                buildTimetables(m5Outbound, DayType.SATURDAY, DIRECTION_OUTBOUND, satOutDeps) +
                buildTimetables(m5Inbound, DayType.SATURDAY, DIRECTION_INBOUND, satInDeps)
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
            routeId = "M5",
            stopId = stop.id,
            dayType = dayType,
            direction = direction,
            departures = deps[i]
        )
    }
}
