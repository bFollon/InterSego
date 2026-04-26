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
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.TimetableLoader
import com.github.bfollon.intersego.services.pdfparsing.CapableParser
import com.github.bfollon.intersego.services.pdfparsing.ParserCapabilities
import com.github.bfollon.intersego.services.pdfparsing.ParserMode
import com.github.bfollon.intersego.services.pdfparsing.RouteStopsProvider

class M4Parser(private val context: Context) : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M4"),
        mode = ParserMode.PRODUCTION,
        version = "3.4"
    )

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> =
        runCatching { TimetableLoader(context).loadRoutesForId(routeId) }.getOrDefault(emptyList())

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> =
        runCatching { TimetableLoader(context).loadRouteVariants(routeId, dayType) }.getOrDefault(emptyList())

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? =
        runCatching { TimetableLoader(context).loadRouteViews(routeId, dayType) }.getOrElse { null }

    override fun getRouteEntries(routeId: String, today: java.util.Date): List<RouteSelectorEntry> =
        runCatching { TimetableLoader(context).loadRouteEntries(routeId, today) }.getOrDefault(emptyList())

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M4Parser: loading timetable from JSON asset")
        return TimetableLoader(context).load("M4")
    }
}
