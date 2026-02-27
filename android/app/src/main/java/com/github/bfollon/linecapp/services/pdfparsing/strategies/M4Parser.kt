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

package com.github.bfollon.linecapp.services.pdfparsing.strategies

import com.github.bfollon.linecapp.data.BusTimetable
import com.github.bfollon.linecapp.data.BusStop
import com.github.bfollon.linecapp.data.DayType
import com.github.bfollon.linecapp.data.DepartureTime
import com.github.bfollon.linecapp.data.RouteVariant
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.pdfparsing.BusTimetableParser
import com.github.bfollon.linecapp.services.pdfparsing.CapableParser
import com.github.bfollon.linecapp.services.pdfparsing.ParserCapabilities
import com.github.bfollon.linecapp.services.pdfparsing.ParserMode
import com.github.bfollon.linecapp.services.pdfparsing.PDFParsingException
import com.github.bfollon.linecapp.services.pdfparsing.PDFTextDecoder
import com.github.bfollon.linecapp.services.pdfparsing.RouteStopsProvider
import com.github.bfollon.linecapp.services.pdfparsing.TimetableParserUtils
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import java.io.File
import java.time.LocalTime

/**
 * Parser for M4 route (La Lastrilla - El Sotillo)
 *
 * PDF Structure:
 * - Two sections: "LUNES A VIERNES LABORABLES" (Weekdays) and "SÁBADOS" (Saturdays)
 * - Table format with stops as columns and departure times as rows
 * - Times formatted as HH:MM (e.g., "7:10", "14:46")
 * - Some rows marked with * for July/August only
 *
 * NOTE: Linecar has updated their PDFs:
 * - Old PDFs (2024/07): Broken font encoding (needs +29 character offset decoding)
 * - New PDFs (2025/10): Standard encoding (works with iText out of the box)
 */
class M4Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M4"),
        mode = ParserMode.PRODUCTION,
        version = "1.0"
    )

    /**
     * Internal state used during parsing to track day type and accumulate timetables
     */
    private data class ParsingState(
        val currentDayType: DayType,
        val incompleteJourney: List<LocalTime>,
        val isSummerSection: Boolean,
        val regularRouteWeekdayTimetables: List<BusTimetable>,
        val regularRouteWeekendTimetables: List<BusTimetable>,
        val reverseRouteWeekdayTimetables: List<BusTimetable>,
        val reverseRouteWeekendTimetables: List<BusTimetable>
    )

    companion object {
        private const val DIRECTION_REGULAR = "Lastrilla → Sotillo"
        private const val DIRECTION_REVERSE = "Sotillo → Lastrilla"

        // Pattern to match time with asterisk (e.g., "7:40*", "14:30 *")
        private val TIME_WITH_ASTERISK_PATTERN = Regex("""\d{1,2}:\d{2}\s*\*""")

        private object Stops {
            val AZOGUEJO = BusStop(
                name = "Azoguejo",
                address = "Pl. Artillería, 40001 Segovia"
            )
            val DELICIAS = BusStop(
                name = "Delicias",
                address = "Via roma 48, 40003 Segovia"
            )
            val GASOLINERA = BusStop(
                name = "Gasolinera",
                address = "Cam. Viejo, 28, 40196 La Lastrilla, Segovia"
            )
            val PENSION = BusStop(
                name = "Pensión",
                address = "C. Cerro de la Fuente, 50, 40196 La Lastrilla, Segovia"
            )
            val POLIGONO = BusStop(
                name = "Polígono",
                address = "Cam. Valseca, 25-5, 40196 La Lastrilla, Segovia"
            )
            val CTRA_VALLADOLID_33 = BusStop(
                name = "Carretera de Valladolid",
                address = "Ctra. de Valladolid, 33, 40196 La Lastrilla, Segovia"
            )
            val LEOPOLDO_MORENO = BusStop(
                name = "Leopoldo Moreno",
                address = "Ctra. de Valladolid, 21, 40196 La Lastrilla, Segovia"
            )
            val COLEGIO = BusStop(
                name = "Colegio",
                address = "Cam. San Cristóbal, 1, 40196 La Lastrilla, Segovia"
            )
            val PARROQ_SOTILLO = BusStop(
                name = "Parroquia el Sotillo",
                address = "Av. el Sotillo, 25, 40196 La Lastrilla, Segovia"
            )
            val HOTEL_AV_SOTILLO = BusStop(
                name = "Hotel Avenida del Sotillo",
                address = "Av. el Sotillo, 1, 40196 La Lastrilla, Segovia"
            )
            val MASPALOMAS = BusStop(
                name = "Calle Maspalomas",
                address = "C. Maspalomas, 21, 40196 La Lastrilla, Segovia"
            )
            val CENTRO_BOAL = BusStop(
                name = "Centro Cultural Julio Boal",
                address = "Cam. Torrecaballeros, 46, 40196 La Lastrilla, Segovia"
            )
            val PASEO_CABANILLAS = BusStop(
                name = "Colegio Madres Concepcionistas",
                address = "P.º Cabanillas, 40196 La Lastrilla, Segovia"
            )
            val RAFAEL_DE_LAS_HERAS = BusStop(
                name = "Rafael de las Heras",
                address = "C. Rafael de las Heras, 11, 40196 La Lastrilla, Segovia"
            )
            val VENTA_MAGULLO = BusStop(
                name = "Venta Magullo",
                address = "C. Rafael de las Heras, 1, 40196 La Lastrilla, Segovia"
            )
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
            // El Sotillo
            Stops.HOTEL_AV_SOTILLO,
            Stops.MASPALOMAS,
            Stops.CENTRO_BOAL,
            Stops.PASEO_CABANILLAS,
            Stops.PARROQ_SOTILLO,
            Stops.RAFAEL_DE_LAS_HERAS,
            Stops.VENTA_MAGULLO,

            Stops.AZOGUEJO
        )

        val m4ReverseRoute = listOf(
            Stops.AZOGUEJO,
            Stops.DELICIAS,
            // El Sotillo
            Stops.HOTEL_AV_SOTILLO,
            Stops.MASPALOMAS,
            Stops.CENTRO_BOAL,
            Stops.PASEO_CABANILLAS,
            Stops.PARROQ_SOTILLO,
            Stops.RAFAEL_DE_LAS_HERAS,
            Stops.VENTA_MAGULLO,
            // La Lastrilla
            Stops.GASOLINERA,
            Stops.PENSION,
            Stops.POLIGONO,
            Stops.CTRA_VALLADOLID_33,
            Stops.LEOPOLDO_MORENO,
            Stops.COLEGIO,
            Stops.PARROQ_SOTILLO,

            Stops.AZOGUEJO
        )

    }

    override fun canParse(routeId: String): Boolean {
        return capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }
    }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        return if (routeId.equals("M4", ignoreCase = true)) {
            listOf(m4RegularRoute, m4ReverseRoute)
        } else {
            emptyList()
        }
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M4", ignoreCase = true)) return emptyList()
        return listOf(
            RouteVariant(
                id = "regular",
                label = DIRECTION_REGULAR,
                stops = m4RegularRoute,
                direction = DIRECTION_REGULAR
            ),
            RouteVariant(
                id = "reverse",
                label = DIRECTION_REVERSE,
                stops = m4ReverseRoute,
                direction = DIRECTION_REVERSE
            ),
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M4Parser: Starting PDF parsing for $pdfPath")

        val file = File(pdfPath)
        if (!file.exists()) {
            throw PDFParsingException("PDF file not found: $pdfPath")
        }

        val pdfReader = PdfReader(file)
        val pdfDocument = PdfDocument(pdfReader)

        try {
            val timetables = mutableListOf<BusTimetable>()

            for (pageNum in 1..pdfDocument.numberOfPages) {
                DebugConfig.debugPrint("M4Parser: Processing page $pageNum")
                val page = pdfDocument.getPage(pageNum)

                val extractedText = PDFTextDecoder.extractText(page, tag = "M4Parser")
                val lines = extractedText.lines()

                DebugConfig.debugPrint("M4Parser: Page $pageNum has ${lines.size} lines")

                DebugConfig.debugPrint("M4Parser: ===== EXTRACTED TEXT =====")
                lines.forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        DebugConfig.debugPrint("  Line $index: $line")
                    }
                }
                DebugConfig.debugPrint("M4Parser: ==========================")

                timetables.addAll(parseTimeTable(lines))
            }

            DebugConfig.debugPrint("M4Parser: Finished parsing, created ${timetables.size} timetables")
            return timetables

        } catch (e: Exception) {
            DebugConfig.debugError("M4Parser: Error parsing PDF", e)
            throw PDFParsingException("Failed to parse M4 PDF: ${e.message}", e)
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Detect day type from section header lines.
     * Maps SATURDAY/SUNDAY → WEEKEND since M4 only distinguishes weekday vs weekend.
     */
    private fun detectDayType(line: String): DayType? =
        TimetableParserUtils.detectDayType(line)?.let { dayType ->
            when (dayType) {
                DayType.SATURDAY, DayType.SUNDAY -> DayType.WEEKEND
                else -> dayType
            }
        }

    /**
     * Create initial empty timetables for a route
     */
    private fun createInitialTimetables(stops: List<BusStop>, dayType: DayType, direction: String): List<BusTimetable> {
        return stops.map { stop ->
            BusTimetable(
                routeId = "M4",
                stopId = stop.name,
                dayType = dayType,
                direction = direction,
                departures = emptyList()
            )
        }
    }

    /**
     * Update timetables with new departure times
     * Zips times with timetables and adds each time to the corresponding timetable
     */
    private fun updateTimetables(timetables: List<BusTimetable>, times: List<LocalTime>, runsInSummer: Boolean): List<BusTimetable> {
        return timetables.zip(times).map { (timetable, time) ->
            timetable.copy(
                departures = timetable.departures + DepartureTime(time.hour, time.minute, runsInSummer = runsInSummer)
            )
        }
    }

    private fun parseTimeTable(lines: List<String>): List<BusTimetable> {
        // Create initial empty timetables for all routes and day types
        val initialState = ParsingState(
            currentDayType = DayType.WEEKDAY,
            incompleteJourney = emptyList(),
            isSummerSection = false,
            regularRouteWeekdayTimetables = createInitialTimetables(m4RegularRoute, DayType.WEEKDAY, DIRECTION_REGULAR),
            regularRouteWeekendTimetables = createInitialTimetables(m4RegularRoute, DayType.WEEKEND, DIRECTION_REGULAR),
            reverseRouteWeekdayTimetables = createInitialTimetables(m4ReverseRoute, DayType.WEEKDAY, DIRECTION_REVERSE),
            reverseRouteWeekendTimetables = createInitialTimetables(m4ReverseRoute, DayType.WEEKEND, DIRECTION_REVERSE)
        )

        // Fold through lines, building timetables on-the-fly
        val finalState = lines.fold(initialState) { state, line ->
            // Check if line contains a day type marker
            val newDayType = detectDayType(line)

            // Check if line marks a summer-only section
            val isSummerMarker = line.contains("JULIO Y AGOSTO", ignoreCase = true)

            // Process lines with times
            when {
                newDayType != null -> {
                    state.copy(currentDayType = newDayType)
                }

                isSummerMarker -> {
                    // Mark that the next journey is a summer (year-round) journey
                    state.copy(isSummerSection = true)
                }

                TimetableParserUtils.hasTimes(line) && hasAsteriskTimes(line) -> {
                    val times = TimetableParserUtils.sortTimes(state.incompleteJourney + TimetableParserUtils.extractTimes(line))

                    // Select the correct timetables list based on current day type
                    val currentTimetables = if (state.currentDayType == DayType.WEEKDAY) {
                        state.reverseRouteWeekdayTimetables
                    } else {
                        state.reverseRouteWeekendTimetables
                    }

                    when (times.size) {
                        m4ReverseRoute.size -> { // Full route
                            val updatedTimetables = updateTimetables(currentTimetables, times, state.isSummerSection)
                            if (state.currentDayType == DayType.WEEKDAY) {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, reverseRouteWeekdayTimetables = updatedTimetables)
                            } else {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, reverseRouteWeekendTimetables = updatedTimetables)
                            }
                        }

                        m4ReverseRoute.size - 1 -> { // Route without last stop
                            val filteredTimetables = currentTimetables.filterIndexed { index, _ ->
                                index != m4ReverseRoute.size - 1
                            }
                            val updatedTimetables = updateTimetables(filteredTimetables, times, state.isSummerSection)
                            // Merge back into full list
                            val mergedTimetables = currentTimetables.mapIndexed { index, tt ->
                                if (index == m4ReverseRoute.size - 1) tt else updatedTimetables[if (index < m4ReverseRoute.size - 1) index else index - 1]
                            }
                            if (state.currentDayType == DayType.WEEKDAY) {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, reverseRouteWeekdayTimetables = mergedTimetables)
                            } else {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, reverseRouteWeekendTimetables = mergedTimetables)
                            }
                        }

                        else -> { // Route without last stop and school stop
                            val filteredTimetables = currentTimetables.filterIndexed { index, _ ->
                                index != m4ReverseRoute.size - 1 &&
                                        index != m4ReverseRoute.indexOf(Stops.PASEO_CABANILLAS)
                            }
                            val updatedTimetables = updateTimetables(filteredTimetables, times, state.isSummerSection)
                            // Merge back into full list
                            val schoolIndex = m4ReverseRoute.indexOf(Stops.PASEO_CABANILLAS)
                            val mergedTimetables = currentTimetables.mapIndexed { index, tt ->
                                when {
                                    index == m4ReverseRoute.size - 1 || index == schoolIndex -> tt
                                    index < schoolIndex -> updatedTimetables[index]
                                    index < m4ReverseRoute.size - 1 -> updatedTimetables[index - 1]
                                    else -> updatedTimetables[index - 2]
                                }
                            }
                            if (state.currentDayType == DayType.WEEKDAY) {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, reverseRouteWeekdayTimetables = mergedTimetables)
                            } else {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, reverseRouteWeekendTimetables = mergedTimetables)
                            }
                        }
                    }
                }

                TimetableParserUtils.hasTimes(line) -> {
                    val times = TimetableParserUtils.sortTimes(state.incompleteJourney + TimetableParserUtils.extractTimes(line))

                    // Select the correct timetables list based on current day type
                    val currentTimetables = if (state.currentDayType == DayType.WEEKDAY) {
                        state.regularRouteWeekdayTimetables
                    } else {
                        state.regularRouteWeekendTimetables
                    }

                    when (times.size) {
                        m4RegularRoute.size -> { // Full route
                            val updatedTimetables = updateTimetables(currentTimetables, times, state.isSummerSection)
                            if (state.currentDayType == DayType.WEEKDAY) {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, regularRouteWeekdayTimetables = updatedTimetables)
                            } else {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, regularRouteWeekendTimetables = updatedTimetables)
                            }
                        }

                        m4RegularRoute.size - 1 -> { // Route without school stop
                            val filteredTimetables = currentTimetables.filter { tt ->
                                tt.stopId != Stops.PASEO_CABANILLAS.name
                            }
                            val updatedTimetables = updateTimetables(filteredTimetables, times, state.isSummerSection)
                            // Merge back into full list
                            val schoolIndex = m4RegularRoute.indexOf(Stops.PASEO_CABANILLAS)
                            val mergedTimetables = currentTimetables.mapIndexed { index, tt ->
                                if (index == schoolIndex) {
                                    tt
                                } else if (index < schoolIndex) {
                                    updatedTimetables[index]
                                } else {
                                    updatedTimetables[index - 1]
                                }
                            }
                            if (state.currentDayType == DayType.WEEKDAY) {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, regularRouteWeekdayTimetables = mergedTimetables)
                            } else {
                                state.copy(incompleteJourney = emptyList(), isSummerSection = false, regularRouteWeekendTimetables = mergedTimetables)
                            }
                        }

                        else -> { // Incomplete route. Accumulate for next pass
                            DebugConfig.debugPrint("Incomplete route, accumulating...")
                            state.copy(incompleteJourney = times)
                        }
                    }
                }

                else -> state // No changes, keep current state
            }
        }

        // Flatten all 4 timetable lists into a single list
        val allTimetables = finalState.regularRouteWeekdayTimetables +
                finalState.regularRouteWeekendTimetables +
                finalState.reverseRouteWeekdayTimetables +
                finalState.reverseRouteWeekendTimetables

        // Sort departures within each timetable
        val sortedTimetables = allTimetables.map { timetable ->
            timetable.copy(
                departures = timetable.departures.sortedBy { it.hour * 60 + it.minute }
            )
        }

        DebugConfig.debugPrint("Created ${sortedTimetables.size} timetables")

        // Show first 5 and some with reverse direction
        sortedTimetables.take(5).forEach { DebugConfig.debugPrint("$it") }
        sortedTimetables.filter { it.direction == DIRECTION_REVERSE }.take(5).forEach {
            DebugConfig.debugPrint("$it")
        }

        return sortedTimetables
    }

    /**
     * Check if line contains times marked with asterisk (indicates reverse route journeys).
     * Example: "7:40* 7:43 14:30" -> true (because of "7:40*")
     */
    private fun hasAsteriskTimes(line: String): Boolean = TIME_WITH_ASTERISK_PATTERN.containsMatchIn(line)
}


