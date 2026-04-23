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
import com.github.bfollon.intersego.data.RouteTab
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.data.RouteViewStop
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.TimetableLoader
import com.github.bfollon.intersego.services.pdfparsing.CapableParser
import com.github.bfollon.intersego.services.pdfparsing.ParserCapabilities
import com.github.bfollon.intersego.services.pdfparsing.ParserMode
import com.github.bfollon.intersego.services.pdfparsing.RouteStopsProvider

/**
 * Parser for M4 route (La Lastrilla - El Sotillo)
 *
 * M4 is a circular route operating weekdays and Saturdays (July/August only).
 * The bus travels from Azoguejo through La Lastrilla to El Sotillo and back.
 *
 * Timetable data loaded from assets/timetables/m4.json (migrated from PDF 2026-04-15).
 */
class M4Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M4"),
        mode = ParserMode.PRODUCTION,
        version = "3.4"
    )

    companion object {
        private const val DIRECTION_REGULAR = "Lastrilla → Sotillo"
        private const val DIRECTION_REVERSE = "Sotillo → Lastrilla"

        private object Stops {
            val AZOGUEJO = BusStopRegistry.azoguejo
            val AZOGUEJO_END_STOP = BusStopRegistry.azoguejoEndStop
            val DELICIAS = BusStopRegistry.delicias
            val GASOLINERA = BusStopRegistry.gasolineraLastrilla
            val PENSION = BusStopRegistry.pension
            val POLIGONO = BusStopRegistry.poligonoLastrilla
            val CTRA_VALLADOLID_33 = BusStopRegistry.ctraValladolid
            val LEOPOLDO_MORENO = BusStopRegistry.leopoldoMoreno
            val COLEGIO = BusStopRegistry.colegioLastrilla
            val PARROQ_SOTILLO = BusStopRegistry.parroquiaSotillo
            val HOTEL_AV_SOTILLO = BusStopRegistry.hotelAvSotillo
            val MASPALOMAS = BusStopRegistry.maspalomas
            val CENTRO_BOAL = BusStopRegistry.centroBoal
            val PASEO_CABANILLAS = BusStopRegistry.paseoCabanillasSotillo
            val RAFAEL_DE_LAS_HERAS = BusStopRegistry.rafaelLasHeras
            val VENTA_MAGULLO = BusStopRegistry.ventaMagullo
        }

        val m4RegularRoute = listOf(
            Stops.AZOGUEJO,
            Stops.DELICIAS,
            Stops.GASOLINERA,
            Stops.PENSION,
            Stops.POLIGONO,
            Stops.CTRA_VALLADOLID_33,
            Stops.LEOPOLDO_MORENO,
            Stops.COLEGIO,
            Stops.HOTEL_AV_SOTILLO,
            Stops.MASPALOMAS,
            Stops.CENTRO_BOAL,
            Stops.PASEO_CABANILLAS,
            Stops.PARROQ_SOTILLO,
            Stops.RAFAEL_DE_LAS_HERAS,
            Stops.VENTA_MAGULLO,
            Stops.AZOGUEJO_END_STOP
        )

        val m4ReverseRoute = listOf(
            Stops.AZOGUEJO,
            Stops.DELICIAS,
            Stops.HOTEL_AV_SOTILLO,
            Stops.MASPALOMAS,
            Stops.CENTRO_BOAL,
            Stops.PASEO_CABANILLAS,
            Stops.PARROQ_SOTILLO,
            Stops.RAFAEL_DE_LAS_HERAS,
            Stops.VENTA_MAGULLO,
            Stops.GASOLINERA,
            Stops.PENSION,
            Stops.POLIGONO,
            Stops.CTRA_VALLADOLID_33,
            Stops.LEOPOLDO_MORENO,
            Stops.COLEGIO,
            Stops.PARROQ_SOTILLO,
            Stops.AZOGUEJO_END_STOP
        )
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M4", ignoreCase = true)) return emptyList()
        return listOf(m4RegularRoute, m4ReverseRoute)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M4", ignoreCase = true)) return emptyList()
        return listOf(
            RouteVariant("regular", DIRECTION_REGULAR, m4RegularRoute, DIRECTION_REGULAR),
            RouteVariant("reverse", DIRECTION_REVERSE, m4ReverseRoute, DIRECTION_REVERSE)
        )
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M4", ignoreCase = true)) return null
        val variants = getRouteVariants(routeId, dayType)
        if (variants.isEmpty()) return null
        val tabs = listOf(
            RouteTab("La Lastrilla", "regular"),
            RouteTab("El Sotillo", "reverse")
        )
        return variants.map { variant ->
            RouteView(
                id = variant.id, label = variant.label,
                stops = variant.stops.map { RouteViewStop(it) },
                direction = variant.direction, departureLabel = variant.departureLabel,
                swapAction = null,
                tabs = tabs,
                tabsLabel = "Pasa primero por",
                mergedDirectionLabel = "La Lastrilla · El Sotillo"
            )
        }
    }

    override fun getRouteEntries(routeId: String, today: java.util.Date): List<RouteSelectorEntry> {
        if (!routeId.equals("M4", ignoreCase = true)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val weekdayViews = getRouteViews(routeId, DayType.WEEKDAY) ?: return emptyList()
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY) ?: return emptyList()
        return listOf(
            RouteSelectorEntry("entry-lv-lastrilla", "L-V La Lastrilla primero", weekdayViews, "regular", DayType.WEEKDAY, isWeekday),
            RouteSelectorEntry("entry-lv-sotillo", "L-V El Sotillo primero", weekdayViews, "reverse", DayType.WEEKDAY, isWeekday),
            RouteSelectorEntry("entry-sabado", "Sábados", saturdayViews, "regular", DayType.SATURDAY, isSaturday)
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M4Parser: loading timetable from JSON asset")
        return TimetableLoader(context).load("M4")
    }
}
