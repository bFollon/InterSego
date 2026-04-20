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
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.SeasonalAvailability
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loads timetable data from bundled JSON assets and produces [BusTimetable] objects.
 *
 * Reads `assets/timetables/{routeId.lowercase()}.json`, interprets the trip-major
 * schema defined in docs/TIMETABLE_JSON_REFACTOR.md, and returns the same
 * List<BusTimetable> contract previously fulfilled by each parser's
 * buildStaticTimetables().
 */
class TimetableLoader(private val context: Context) {

    @Serializable
    private data class TimetableFile(
        val routeId: String,
        val version: String,
        val stops: List<JsonStop>,
        val variants: List<JsonVariant>,
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
    private data class JsonTimetableSection(
        val variantId: String,
        val dayType: String,
        val trips: List<JsonTrip>
    )

    @Serializable
    private data class JsonTrip(
        val season: String? = null,
        val departures: List<JsonElement>
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(routeId: String): List<BusTimetable> {
        val text = context.assets
            .open("timetables/${routeId.lowercase()}.json")
            .bufferedReader()
            .readText()
        val file = json.decodeFromString<TimetableFile>(text)
        return buildTimetables(file)
    }

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
                    parseDeparture(element, tripSeason, parentStop)?.let { depsByStop[i].add(it) }
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
        parentStop: JsonStop?
    ): DepartureTime? {
        if (element is JsonNull) return null
        if (element is JsonPrimitive) {
            val hhmm = element.int
            return DepartureTime(hhmm / 100, hhmm % 100, seasonalAvailability = tripSeason)
        }
        if (element is JsonObject) {
            val hhmm = element["hhmm"]!!.jsonPrimitive.int
            val season = element["season"]?.jsonPrimitive?.content
                ?.let { parseSeason(it) } ?: tripSeason
            val variantLabel = element["variantLabel"]?.jsonPrimitive?.content
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
