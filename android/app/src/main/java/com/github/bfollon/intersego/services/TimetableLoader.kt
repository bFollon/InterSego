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

package com.github.bfollon.intersego.services

import android.content.Context
import com.github.bfollon.intersego.data.AlternateLocation
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteTab
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.data.RouteViewStop
import com.github.bfollon.intersego.data.SeasonalAvailability
import com.github.bfollon.intersego.data.SwapAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.util.Calendar
import java.util.Date

/**
 * Loads timetable data and route structure from bundled JSON assets.
 *
 * Reads `assets/timetables/{routeId.lowercase()}.json`, interprets the trip-major
 * schema defined in docs/TIMETABLE_JSON_REFACTOR.md.
 *
 * - [load] returns List<BusTimetable>, the same contract as the old buildStaticTimetables().
 * - [loadRoutesForId], [loadRouteVariants], [loadRouteViews], [loadRouteEntries] replace
 *   the route-structure methods previously hardcoded in each parser.
 */
class TimetableLoader(private val context: Context) {

    @Serializable
    private data class TimetableFile(
        val routeId: String,
        val version: String,
        val stops: List<JsonStop>,
        val variants: List<JsonVariant>,
        val routeDisplay: JsonRouteDisplay,
        val timetables: List<JsonTimetableSection>
    )

    @Serializable
    private data class JsonStop(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val alternates: List<JsonAlternate> = emptyList()
    )

    @Serializable
    private data class JsonAlternate(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double
    )

    @Serializable
    private data class JsonVariant(
        val id: String,
        val label: String,
        val stopSequence: List<String>,
        val swapTargetId: String? = null
    )

    @Serializable
    private data class JsonRouteDisplay(
        val type: String,
        val tabsLabel: String? = null,
        val mergedDirectionLabel: String? = null,
        val tabs: List<JsonRouteTab> = emptyList(),
        val entries: List<JsonRouteEntry>
    )

    @Serializable
    private data class JsonRouteTab(
        val label: String,
        val variantId: String
    )

    @Serializable
    private data class JsonRouteEntry(
        val id: String,
        val label: String,
        val variantId: String,
        val dayType: String
    )

    @Serializable
    private data class JsonTimetableSection(
        val variantId: String,
        val dayType: String,
        val trips: List<JsonTrip>
    )

    @Serializable
    private data class JsonTrip(
        val season: String? = null,
        val variantLabel: String? = null,
        val departures: List<JsonElement>
    )

    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - File loading

    private fun loadFile(routeId: String): TimetableFile {
        val text = context.assets
            .open("timetables/${routeId.lowercase()}.json")
            .bufferedReader()
            .readText()
        return json.decodeFromString(text)
    }

    // MARK: - Public API: timetables

    fun load(routeId: String): List<BusTimetable> {
        return buildTimetables(loadFile(routeId))
    }

    // MARK: - Public API: route structure

    /** All stop sequences for this route, one per variant. Used by the closest-stop finder. */
    fun loadRoutesForId(routeId: String): List<List<BusStop>> {
        val file = loadFile(routeId)
        val stopsById = stopsIndex(file)
        return file.variants.map { variant ->
            variant.stopSequence.mapNotNull { stopsById[it] }
        }
    }

    /** Named variants for a given day type. Returns empty when the route has no service that day. */
    fun loadRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        val file = loadFile(routeId)
        val stopsById = stopsIndex(file)
        val relevant = relevantVariantIds(dayType, file)
        return file.variants
            .filter { it.id in relevant }
            .map { variant ->
                val stops = variant.stopSequence.mapNotNull { stopsById[it] }
                RouteVariant(id = variant.id, label = variant.label, stops = stops, direction = variant.label)
            }
    }

    /** RouteViews for a given day type, or null when the route has no service that day. */
    fun loadRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        val file = loadFile(routeId)
        val stopsById = stopsIndex(file)
        val views = buildRouteViews(dayType, file, stopsById)
        return views.ifEmpty { null }
    }

    /** RouteSelectorEntries built from routeDisplay.entries. */
    fun loadRouteEntries(routeId: String, today: Date): List<RouteSelectorEntry> {
        val file = loadFile(routeId)
        val stopsById = stopsIndex(file)
        return file.routeDisplay.entries.map { entry ->
            val dayType = parseDayType(entry.dayType)
            val views = buildRouteViews(dayType, file, stopsById)
            RouteSelectorEntry(
                id = entry.id,
                label = entry.label,
                views = views,
                initialViewId = entry.variantId,
                timetableDayType = dayType,
                isActiveToday = isActiveToday(entry.dayType, today)
            )
        }
    }

    /**
     * Stop map keyed by stop ID — exposed for parsers that still build their own views
     * (e.g. M6 with tabs+swap hybrid display).
     */
    fun loadBusStopsById(routeId: String): Map<String, BusStop> = stopsIndex(loadFile(routeId))

    // MARK: - Route structure helpers

    private fun stopsIndex(file: TimetableFile): Map<String, BusStop> =
        file.stops.associate { it.id to buildBusStop(it) }

    private fun buildBusStop(jsonStop: JsonStop): BusStop = BusStop(
        id = jsonStop.id,
        name = jsonStop.name,
        coordinates = "${jsonStop.lat}, ${jsonStop.lon}",
        alternates = jsonStop.alternates.map {
            AlternateLocation(id = it.id, name = it.name, coordinates = "${it.lat}, ${it.lon}")
        }
    )

    /**
     * Returns the set of variant IDs relevant for a given day type, including swap targets
     * so that both directions are available when needed.
     */
    private fun relevantVariantIds(dayType: DayType, file: TimetableFile): Set<String> {
        val dayStr = dayTypeString(dayType)
        val fromEntries = file.routeDisplay.entries
            .filter { it.dayType == dayStr }
            .map { it.variantId }
        if (fromEntries.isEmpty()) return emptySet()

        val ids = fromEntries.toMutableSet()
        val variantsById = file.variants.associateBy { it.id }
        for (variantId in fromEntries) {
            variantsById[variantId]?.swapTargetId?.let { ids.add(it) }
        }
        return ids
    }

    private fun buildRouteViews(
        dayType: DayType,
        file: TimetableFile,
        stopsById: Map<String, BusStop>
    ): List<RouteView> {
        val relevant = relevantVariantIds(dayType, file)
        if (relevant.isEmpty()) return emptyList()

        val isTabsType = file.routeDisplay.type == "tabs"
        val routeTabs = file.routeDisplay.tabs
            .takeIf { it.isNotEmpty() }
            ?.map { RouteTab(label = it.label, viewId = it.variantId) }

        return file.variants
            .filter { it.id in relevant }
            .map { variant ->
                val stops = variant.stopSequence.mapNotNull { stopsById[it] }.map { RouteViewStop(stop = it) }
                val swapAction = if (isTabsType) null else variant.swapTargetId?.let { SwapAction(targetViewId = it) }
                RouteView(
                    id = variant.id,
                    label = variant.label,
                    stops = stops,
                    direction = variant.label,
                    swapAction = swapAction,
                    tabs = routeTabs,
                    tabsLabel = file.routeDisplay.tabsLabel,
                    mergedDirectionLabel = if (isTabsType) file.routeDisplay.mergedDirectionLabel else null
                )
            }
    }

    private fun dayTypeString(dayType: DayType): String = when (dayType) {
        DayType.WEEKDAY  -> "weekday"
        DayType.SATURDAY -> "saturday"
        DayType.SUNDAY   -> "sunday"
    }

    private fun isActiveToday(dayTypeStr: String, today: Date): Boolean {
        val dow = Calendar.getInstance().apply { time = today }.get(Calendar.DAY_OF_WEEK)
        return when (dayTypeStr) {
            "weekday"  -> dow != Calendar.SUNDAY && dow != Calendar.SATURDAY
            "saturday" -> dow == Calendar.SATURDAY
            "sunday"   -> dow == Calendar.SUNDAY
            else       -> false
        }
    }

    // MARK: - Timetable builder (unchanged)

    private fun buildTimetables(file: TimetableFile): List<BusTimetable> {
        val variantsById = file.variants.associateBy { it.id }
        val stopsById = file.stops.associateBy { it.id }
        val result = mutableListOf<BusTimetable>()

        for (section in file.timetables) {
            val variant = variantsById[section.variantId] ?: continue
            val dayType = parseDayType(section.dayType)
            val stopSequence = variant.stopSequence
            val depsByStop = Array(stopSequence.size) { mutableListOf<DepartureTime>() }

            for (trip in section.trips) {
                val tripSeason = parseSeason(trip.season)
                trip.departures.forEachIndexed { i, element ->
                    if (i >= stopSequence.size) return@forEachIndexed
                    val parentStop = stopsById[stopSequence[i]]
                    parseDeparture(element, tripSeason, trip.variantLabel, parentStop)?.let { depsByStop[i].add(it) }
                }
            }

            stopSequence.forEachIndexed { i, stopId ->
                result.add(
                    BusTimetable(
                        routeId = file.routeId,
                        stopId = stopId,
                        dayType = dayType,
                        direction = variant.label,
                        departures = depsByStop[i]
                    )
                )
            }
        }

        return result
    }

    private fun parseDayType(value: String): DayType = when (value) {
        "weekday"  -> DayType.WEEKDAY
        "saturday" -> DayType.SATURDAY
        "sunday"   -> DayType.SUNDAY
        else       -> DayType.WEEKDAY
    }

    private fun parseSeason(value: String?): SeasonalAvailability = when (value) {
        null, "yearRound"  -> SeasonalAvailability.YEAR_ROUND
        "schoolOnly"       -> SeasonalAvailability.SCHOOL_ONLY
        "summerOnly"       -> SeasonalAvailability.SUMMER_ONLY
        "juneToSept"       -> SeasonalAvailability.JUNE_TO_SEPT_ONLY
        "monFriOnly"       -> SeasonalAvailability.MON_FRI_ONLY
        "friOnly"          -> SeasonalAvailability.FRI_ONLY
        else               -> SeasonalAvailability.YEAR_ROUND
    }

    private fun parseDeparture(
        element: JsonElement,
        tripSeason: SeasonalAvailability,
        tripVariantLabel: String?,
        parentStop: JsonStop?
    ): DepartureTime? {
        if (element is JsonNull) return null
        if (element is JsonPrimitive) {
            val hhmm = element.int
            return DepartureTime(hhmm / 100, hhmm % 100, seasonalAvailability = tripSeason, variantLabel = tripVariantLabel)
        }
        if (element is JsonObject) {
            val hhmm = element["hhmm"]!!.jsonPrimitive.int
            val season = element["season"]?.jsonPrimitive?.content
                ?.let { parseSeason(it) } ?: tripSeason
            val variantLabel = element["variantLabel"]?.jsonPrimitive?.content ?: tripVariantLabel
            val alternateId = element["alternateId"]?.jsonPrimitive?.content
                ?.takeIf { id -> parentStop?.alternates?.any { it.id == id } == true }
            return DepartureTime(
                hhmm / 100, hhmm % 100,
                seasonalAvailability = season,
                variantLabel = variantLabel,
                alternateLocationId = alternateId
            )
        }
        return null
    }
}
