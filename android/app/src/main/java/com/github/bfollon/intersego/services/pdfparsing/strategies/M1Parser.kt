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
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.pdfparsing.CapableParser
import com.github.bfollon.intersego.services.pdfparsing.ParserCapabilities
import com.github.bfollon.intersego.services.pdfparsing.ParserMode
import com.github.bfollon.intersego.services.pdfparsing.PDFParsingException
import com.github.bfollon.intersego.services.pdfparsing.PDFTextDecoder
import com.github.bfollon.intersego.services.pdfparsing.RouteStopsProvider
import com.github.bfollon.intersego.services.pdfparsing.TimetableParserUtils
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import java.io.File
import java.time.LocalTime

/**
 * Parser for M1 route (TODO: add route name once confirmed from PDF)
 *
 * PDF Structure:
 * - TODO: Document structure after analysing the extracted PDF lines
 *
 * Status: DEBUG skeleton — prints extracted lines for analysis, returns no timetables.
 */
class M1Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M1"),
        mode = ParserMode.DEBUG,
        version = "0.1"
    )

    /**
     * Internal state used during parsing.
     * TODO: Expand once PDF structure is understood.
     */
    private data class ParsingState(
        val currentDayType: DayType,
        val regularRouteWeekdayTimetables: List<BusTimetable>,
        val regularRouteWeekendTimetables: List<BusTimetable>,
        val reverseRouteWeekdayTimetables: List<BusTimetable>,
        val reverseRouteWeekendTimetables: List<BusTimetable>
    )

    companion object {
        // TODO: Fill in direction labels once confirmed from PDF
        private const val DIRECTION_REGULAR = "TODO: Regular direction"
        private const val DIRECTION_REVERSE = "TODO: Reverse direction"

        // TODO: Define BusStop instances once stop names and coordinates are gathered
        private object Stops {
            // Example:
            // val STOP_NAME = BusStop(
            //     name = "Stop Name",
            //     area = "Area Name",
            //     coordinates = "40.000000, -4.000000",
            // )
        }

        // TODO: Fill in stop lists once stops are defined above
        val m1RegularRoute: List<BusStop> = emptyList()
        val m1ReverseRoute: List<BusStop> = emptyList()
    }

    override fun canParse(routeId: String): Boolean {
        return capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }
    }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        return if (routeId.equals("M1", ignoreCase = true)) {
            listOf(m1RegularRoute, m1ReverseRoute)
        } else {
            emptyList()
        }
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M1", ignoreCase = true)) return emptyList()
        return listOf(
            RouteVariant(
                id = "regular",
                label = DIRECTION_REGULAR,
                stops = m1RegularRoute,
                direction = DIRECTION_REGULAR
            ),
            RouteVariant(
                id = "reverse",
                label = DIRECTION_REVERSE,
                stops = m1ReverseRoute,
                direction = DIRECTION_REVERSE
            ),
        )
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M1Parser: Starting PDF parsing for $pdfPath")

        val file = File(pdfPath)
        if (!file.exists()) {
            throw PDFParsingException("PDF file not found: $pdfPath")
        }

        val pdfReader = PdfReader(file)
        val pdfDocument = PdfDocument(pdfReader)

        try {
            val allLines = mutableListOf<String>()

            for (pageNum in 1..pdfDocument.numberOfPages) {
                DebugConfig.debugPrint("M1Parser: Processing page $pageNum")
                val page = pdfDocument.getPage(pageNum)

                val extractedText = PDFTextDecoder.extractText(page, tag = "M1Parser")
                val lines = extractedText.lines()

                DebugConfig.debugPrint("M1Parser: Page $pageNum has ${lines.size} lines")

                DebugConfig.debugPrint("M1Parser: ===== EXTRACTED TEXT (page $pageNum) =====")
                lines.forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        DebugConfig.debugPrint("  Line $index: $line")
                    }
                }
                DebugConfig.debugPrint("M1Parser: ==========================================")

                allLines.addAll(lines)
            }

            val timetables = parseTimeTable(allLines)
            DebugConfig.debugPrint("M1Parser: Finished parsing, created ${timetables.size} timetables")
            return timetables

        } catch (e: Exception) {
            DebugConfig.debugError("M1Parser: Error parsing PDF", e)
            throw PDFParsingException("Failed to parse M1 PDF: ${e.message}", e)
        } finally {
            pdfDocument.close()
        }
    }

    private fun createInitialTimetables(stops: List<BusStop>, dayType: DayType, direction: String): List<BusTimetable> {
        return stops.map { stop ->
            BusTimetable(
                routeId = "M1",
                stopId = stop.name,
                dayType = dayType,
                direction = direction,
                departures = emptyList()
            )
        }
    }

    private fun updateTimetables(timetables: List<BusTimetable>, times: List<LocalTime>, seasonal: SeasonalAvailability): List<BusTimetable> {
        return timetables.zip(times).map { (timetable, time) ->
            timetable.copy(
                departures = timetable.departures + DepartureTime(time.hour, time.minute, seasonalAvailability = seasonal)
            )
        }
    }

    private fun parseTimeTable(lines: List<String>): List<BusTimetable> {
        // TODO: Implement parsing logic once PDF structure is understood from the debug output above.
        // Use M4Parser as a reference for a simple line-per-journey approach.
        //
        // Suggested steps:
        // 1. Run the app with DEBUG mode and check logcat for "M1Parser" tag
        // 2. Identify day type headers (LUNES A VIERNES, SÁBADOS, DOMINGOS)
        // 3. Identify time rows and how many times per row (= number of stops)
        // 4. Map the times per row to stop positions
        // 5. Fill in Stops above and m1RegularRoute / m1ReverseRoute
        // 6. Implement the state machine below (modelled on M4Parser)
        // 7. Change mode to ParserMode.PRODUCTION when complete

        if (m1RegularRoute.isEmpty()) {
            DebugConfig.debugPrint("M1Parser: Stops not yet defined — returning empty timetables (DEBUG skeleton)")
            return emptyList()
        }

        val initialState = ParsingState(
            currentDayType = DayType.WEEKDAY,
            regularRouteWeekdayTimetables = createInitialTimetables(m1RegularRoute, DayType.WEEKDAY, DIRECTION_REGULAR),
            regularRouteWeekendTimetables = createInitialTimetables(m1RegularRoute, DayType.WEEKEND, DIRECTION_REGULAR),
            reverseRouteWeekdayTimetables = createInitialTimetables(m1ReverseRoute, DayType.WEEKDAY, DIRECTION_REVERSE),
            reverseRouteWeekendTimetables = createInitialTimetables(m1ReverseRoute, DayType.WEEKEND, DIRECTION_REVERSE)
        )

        val finalState = lines.fold(initialState) { state, line ->
            val newDayType = TimetableParserUtils.detectDayType(line)
            when {
                newDayType != null -> state.copy(currentDayType = newDayType)
                TimetableParserUtils.hasTimes(line) -> {
                    // TODO: handle time rows here
                    state
                }
                else -> state
            }
        }

        return finalState.regularRouteWeekdayTimetables +
                finalState.regularRouteWeekendTimetables +
                finalState.reverseRouteWeekdayTimetables +
                finalState.reverseRouteWeekendTimetables
    }
}
