/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.content.Context
import com.github.bfollon.intersego.data.AlternateLocation
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.JourneyDeparture
import com.github.bfollon.intersego.data.JourneyRouteData
import com.github.bfollon.intersego.data.JourneyStop
import com.github.bfollon.intersego.data.JourneyTimetableSection
import com.github.bfollon.intersego.data.JourneyTrip
import com.github.bfollon.intersego.data.JourneyVariant
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteTab
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.data.RouteType
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
import java.io.File
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
        val route: JsonRoute,
        val stops: List<JsonStop>,
        val variants: List<JsonVariant>,
        val routeDisplay: JsonRouteDisplay,
        val timetables: List<JsonTimetableSection>
    )

    @Serializable
    private data class JsonRoute(
        val number: String,
        val name: String,
        val origin: String,
        val destination: String,
        val routeType: String,
        val isCircular: Boolean,
        val displayOrder: Int,
        val pdfUrl: String = ""
    )

    @Serializable
    private data class JsonStop(
        val id: String,
        val name: String,
        val area: String? = null,
        val lat: Double,
        val lon: Double,
        val alternates: List<JsonAlternate> = emptyList(),
        val physicalStopId: String? = null,
        val timeIsEstimated: Boolean = false
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
        val direction: String? = null,
        val stopSequence: List<String>,
        val swapTargetId: String? = null,
        val departureLabel: String? = null,
        val extendedSectionLabel: String? = null,
        val extendedStopIds: List<String> = emptyList()
    )

    @Serializable
    private data class JsonRouteDisplay(
        val type: String,
        val tabsLabel: String? = null,
        val mergedDirectionLabel: String? = null,
        val tabs: List<JsonRouteTab> = emptyList(),
        val tabGroups: List<JsonTabGroup> = emptyList(),
        val entries: List<JsonRouteEntry>
    )

    @Serializable
    private data class JsonRouteTab(
        val label: String,
        val variantId: String
    )

    @Serializable
    private data class JsonTabGroup(
        val tabs: List<JsonRouteTab>,
        val appliesTo: List<String>
    )

    @Serializable
    private data class JsonRouteEntry(
        val id: String,
        val label: String,
        val variantId: String,
        val dayType: String,
        val viewIds: List<String>? = null
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
        val cacheFile = TimetableCacheService.cacheFile(context, routeId)
        val text = if (cacheFile.exists()) {
            cacheFile.readText()
        } else {
            context.assets
                .open("timetables/${routeId.lowercase()}.json")
                .bufferedReader()
                .readText()
        }
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
                RouteVariant(id = variant.id, label = variant.label, stops = stops, direction = variant.direction ?: variant.label, departureLabel = variant.departureLabel)
            }
    }

    /** RouteViews for a given day type, or null when the route has no service that day. */
    fun loadRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        val file = loadFile(routeId)
        val stopsById = stopsIndex(file)
        val views = buildRouteViews(relevantVariantIds(dayType, file), file, stopsById)
        return views.ifEmpty { null }
    }

    /** RouteSelectorEntries built from routeDisplay.entries. */
    fun loadRouteEntries(routeId: String, today: Date): List<RouteSelectorEntry> {
        val file = loadFile(routeId)
        val stopsById = stopsIndex(file)
        return file.routeDisplay.entries.map { entry ->
            val dayType = parseDayType(entry.dayType)
            val variantIds = entry.viewIds?.toSet() ?: relevantVariantIds(dayType, file)
            val views = buildRouteViews(variantIds, file, stopsById)
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

    fun getVersion(routeId: String): String = loadFile(routeId).version

    /**
     * Pure (no Android dependency) trip-major view of this route, for the journey planner's
     * connection extraction. See `docs/JOURNEY_PLANNER.md` and [JourneyRouteData].
     */
    fun loadJourneyRouteData(routeId: String): JourneyRouteData {
        val file = loadFile(routeId)
        val stops = file.stops.map {
            JourneyStop(id = it.id, physicalStopId = it.physicalStopId ?: it.id, isEstimated = it.timeIsEstimated)
        }
        val variants = file.variants.map { JourneyVariant(id = it.id, stopSequence = it.stopSequence) }
        val timetables = file.timetables.map { section ->
            val tripSize = section.trips.maxOfOrNull { it.departures.size } ?: 0
            JourneyTimetableSection(
                variantId = section.variantId,
                dayType = parseDayType(section.dayType),
                trips = section.trips.map { trip ->
                    val tripSeason = parseSeason(trip.season)
                    JourneyTrip(
                        departures = (0 until tripSize).map { i ->
                            trip.departures.getOrNull(i)?.let { parseJourneyDeparture(it, tripSeason) }
                        }
                    )
                }
            )
        }
        return JourneyRouteData(routeId = routeId, stops = stops, variants = variants, timetables = timetables)
    }

    private fun parseJourneyDeparture(element: JsonElement, tripSeason: SeasonalAvailability): JourneyDeparture? {
        if (element is JsonNull) return null
        if (element is JsonPrimitive) {
            val hhmm = element.int
            return JourneyDeparture((hhmm / 100) * 60 + (hhmm % 100), tripSeason)
        }
        if (element is JsonObject) {
            val hhmm = element["hhmm"]!!.jsonPrimitive.int
            val season = element["season"]?.jsonPrimitive?.content?.let { parseSeason(it) } ?: tripSeason
            return JourneyDeparture((hhmm / 100) * 60 + (hhmm % 100), season)
        }
        return null
    }

    fun loadBusStopsById(routeId: String): Map<String, BusStop> = stopsIndex(loadFile(routeId))

    fun loadRoute(routeId: String): BusRoute {
        val r = loadFile(routeId).route
        val type = if (r.routeType == "INTERURBAN") RouteType.INTERURBAN else RouteType.URBAN
        return BusRoute(
            id = routeId.uppercase(),
            number = r.number,
            name = r.name,
            origin = r.origin,
            destination = r.destination,
            pdfURL = r.pdfUrl,
            routeType = type,
            isCircular = r.isCircular
        )
    }

    /**
     * Scans all timetable assets and the disk cache, and returns routes sorted by
     * displayOrder. Routes that exist only on disk (discovered via the server's
     * route manifest, see [TimetableCacheService]) are included alongside bundled ones.
     */
    fun loadAllRoutes(): List<BusRoute> {
        val bundleIds = (context.assets.list("timetables") ?: emptyArray())
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }
        val diskIds = (File(context.filesDir, "timetables").listFiles { f -> f.extension == "json" } ?: emptyArray())
            .map { it.nameWithoutExtension }
        return (bundleIds + diskIds).distinct()
            .mapNotNull { id ->
                val routeId = id.uppercase()
                runCatching { loadFile(routeId) }.getOrNull()?.let { file ->
                    val r = file.route
                    val type = if (r.routeType == "INTERURBAN") RouteType.INTERURBAN else RouteType.URBAN
                    Pair(
                        BusRoute(
                            id = routeId,
                            number = r.number,
                            name = r.name,
                            origin = r.origin,
                            destination = r.destination,
                            pdfURL = r.pdfUrl,
                            routeType = type,
                            isCircular = r.isCircular
                        ),
                        r.displayOrder
                    )
                }
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    // MARK: - Route structure helpers

    private fun stopsIndex(file: TimetableFile): Map<String, BusStop> =
        file.stops.associate { it.id to buildBusStop(it) }

    private fun buildBusStop(jsonStop: JsonStop): BusStop = BusStop(
        id = jsonStop.id,
        name = jsonStop.name,
        area = jsonStop.area,
        coordinates = "${jsonStop.lat}, ${jsonStop.lon}",
        alternates = jsonStop.alternates.map {
            AlternateLocation(id = it.id, name = it.name, coordinates = "${it.lat}, ${it.lon}")
        }
    )

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
        variantIds: Set<String>,
        file: TimetableFile,
        stopsById: Map<String, BusStop>
    ): List<RouteView> {
        if (variantIds.isEmpty()) return emptyList()

        val isTabsType = file.routeDisplay.type == "tabs"
        val globalTabs = file.routeDisplay.tabs
            .takeIf { it.isNotEmpty() }
            ?.map { RouteTab(label = it.label, viewId = it.variantId) }

        return file.variants
            .filter { it.id in variantIds }
            .map { variant ->
                val extendedIds = variant.extendedStopIds.toSet()
                val stops = variant.stopSequence.mapNotNull { stopsById[it] }.map { stop ->
                    RouteViewStop(stop = stop, isExtendedOnly = stop.id in extendedIds)
                }
                val swapAction = if (isTabsType) null else variant.swapTargetId?.let { SwapAction(targetViewId = it) }
                val variantTabs = file.routeDisplay.tabGroups
                    .find { variant.id in it.appliesTo }
                    ?.tabs?.map { RouteTab(label = it.label, viewId = it.variantId) }
                    ?: if (isTabsType) globalTabs else null
                RouteView(
                    id = variant.id,
                    label = variant.label,
                    stops = stops,
                    direction = variant.direction ?: variant.label,
                    departureLabel = variant.departureLabel,
                    swapAction = swapAction,
                    tabs = variantTabs,
                    tabsLabel = file.routeDisplay.tabsLabel,
                    mergedDirectionLabel = if (isTabsType) file.routeDisplay.mergedDirectionLabel else null,
                    extendedSectionLabel = variant.extendedSectionLabel
                )
            }
    }

    private fun dayTypeString(dayType: DayType): String = when (dayType) {
        DayType.WEEKDAY  -> "weekday"
        DayType.SATURDAY -> "saturday"
        DayType.SUNDAY   -> "sunday"
        DayType.WEEKEND  -> "saturday"
        DayType.HOLIDAY  -> "sunday"
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
                        direction = variant.direction ?: variant.label,
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
