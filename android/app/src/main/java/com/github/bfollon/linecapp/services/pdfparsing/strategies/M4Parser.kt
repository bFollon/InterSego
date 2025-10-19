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
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.pdfparsing.BusTimetableParser
import com.github.bfollon.linecapp.services.pdfparsing.PDFParsingException
import com.github.bfollon.linecapp.services.pdfparsing.PDFTextDecoder
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
 *
 * TODO: Implement actual parsing logic to extract timetables from PDF text
 */
class M4Parser : BusTimetableParser {

    companion object {
        // Regex pattern to match time format HH:MM or H:MM (e.g., "7:40", "14:30")
        private val TIME_PATTERN = Regex("""\d{1,2}:\d{2}""")

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

        private val stopsMap: Map<String, BusStop> = mapOf(
            "AZOGUEJO" to Stops.AZOGUEJO,
            "DELICIAS" to Stops.DELICIAS,
            "GASOLIN" to Stops.GASOLINERA,
            "PENSION" to Stops.PENSION,
            "POLIGONO" to Stops.POLIGONO,
            "Ctra Valladolid 33" to Stops.CTRA_VALLADOLID_33,
            "LEOPOLDO MORENO" to Stops.LEOPOLDO_MORENO,
            "COLEGIO" to Stops.COLEGIO,
            "PARROQ. SOTILLO" to Stops.PARROQ_SOTILLO,
            "HOTEL AV.SOTILLO" to Stops.HOTEL_AV_SOTILLO,
            "Maspalomas" to Stops.MASPALOMAS,
            "CENTRO BOAL" to Stops.CENTRO_BOAL,
            "PASEO CABANILLAS" to Stops.PASEO_CABANILLAS,
            "RAFAEL DE LAS HERAS" to Stops.RAFAEL_DE_LAS_HERAS,
            "VENTA MAGULLO" to Stops.VENTA_MAGULLO,
        )
    }

    /**
     * Parse M4 timetables from decoded PDF text
     *
     * @param lines The decoded text lines from the PDF (usually just 1 big line!)
     * @param routeId The route ID (M4)
     * @return List of BusTimetable objects (one for weekdays, one for saturdays)
     *
     * The text contains:
     * - Times in format "HH:MM" (e.g., "7:10", "14:30")
     * - Section markers: "LUNES A VIERNES LABORABLES" (weekdays), "SÁBADOS" (saturdays)
     * - Special markers: "*" for July/August only services
     * - Separator text: "JULIO Y AGOSTO" between time groups
     *
     * TODO: Implement your parsing logic here!
     */
    private fun parseM4Timetables(lines: List<String>, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M4Parser: Starting timetable parsing for ${lines.size} lines")

        val timetables = mutableListOf<BusTimetable>()

        // TODO: Your parsing implementation goes here!
        //
        // Suggested approach:
        // 1. Join all lines into one text (or work with lines[0] since it's usually one line)
        // 2. Split by "LUNES A VIERNES LABORABLES" and "SÁBADOS" to get sections
        // 3. Extract all time patterns (HH:MM) using regex
        // 4. Create DepartureTime objects from the extracted times
        // 5. Create BusTimetable objects for each day type
        //
        // Example time extraction regex: """\d{1,2}:\d{2}""".toRegex()
        //
        // Example BusTimetable creation:
        // val weekdayTimetable = BusTimetable(
        //     routeId = routeId,
        //     stopId = "M4_LA_LASTRILLA", // or appropriate stop ID
        //     date = ScheduleDate.today(),
        //     departures = listOf(...), // Your parsed DepartureTime objects
        //     dayType = DayType.WEEKDAY
        // )
        // timetables.add(weekdayTimetable)

        DebugConfig.debugPrint("M4Parser: Parsed ${timetables.size} timetables")
        return timetables
    }

    override fun canParse(routeId: String): Boolean {
        return routeId.equals("M4", ignoreCase = true)
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M4Parser: Starting PDF parsing for $pdfPath")

        val file = File(pdfPath)
        if (!file.exists()) {
            throw PDFParsingException("PDF file not found: $pdfPath")
        }

        try {
            val pdfReader = PdfReader(file)
            val pdfDocument = PdfDocument(pdfReader)

            val timetables = mutableListOf<BusTimetable>()

            // Extract text from all pages
            for (pageNum in 1..pdfDocument.numberOfPages) {
                DebugConfig.debugPrint("M4Parser: Processing page $pageNum")
                val page = pdfDocument.getPage(pageNum)

                // Try standard iText extraction first
                var extractedText = PdfTextExtractor.getTextFromPage(page)

                // Check if text needs decoding (broken PDF encoding)
                if (PDFTextDecoder.needsDecoding(extractedText)) {
                    DebugConfig.debugPrint("M4Parser: Detected broken encoding, using custom decoder...")

                    // Use raw glyph extraction for broken PDFs
                    val strategy = PDFTextDecoder.RawGlyphExtractionStrategy()
                    val processor = PdfCanvasProcessor(strategy)
                    processor.processPageContent(page)
                    val rawText = strategy.resultantText

                    // Decode with +29 character offset (old Linecar PDFs)
                    extractedText = PDFTextDecoder.decodeWithCharacterOffset(rawText, offset = 29)
                    DebugConfig.debugPrint("M4Parser: Successfully decoded broken PDF")
                }

                val lines = extractedText.lines()

                DebugConfig.debugPrint("M4Parser: Page $pageNum has ${lines.size} lines")
                DebugConfig.debugPrint("M4Parser: Total text length: ${extractedText.length} characters")

                DebugConfig.debugPrint("M4Parser: ===== EXTRACTED TEXT =====")
                lines.forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        DebugConfig.debugPrint("  Line $index: $line")
                    }
                }
                DebugConfig.debugPrint("M4Parser: ==========================")

                parseTimeTable(lines)

                // Parse the text and create BusTimetable objects
                val parsedTimetables = parseM4Timetables(lines, routeId)
                timetables.addAll(parsedTimetables)
            }

            pdfDocument.close()

            DebugConfig.debugPrint("M4Parser: Finished parsing, created ${timetables.size} timetables")
            return timetables

        } catch (e: Exception) {
            DebugConfig.debugError("M4Parser: Error parsing PDF", e)
            throw PDFParsingException("Failed to parse M4 PDF: ${e.message}", e)
        }
    }

    fun parseTimeTable(lines: List<String>) {

        val (_, matches) = lines.fold(
            Pair(
                emptyList<LocalTime>(),
                emptyList<Pair<BusStop, LocalTime>>()
            )
        ) { (timesAcc, matchesAcc), line ->
            val (times: List<LocalTime>, matches: List<Pair<BusStop, LocalTime>>) = when {
                hasTimes(line) && isReverseRoute(line) -> {
                    val times = sortTimes(timesAcc + extractTimes(line))

                    when (times.size) {
                        m4ReverseRoute.size -> { // School route with extra church stop
                            Pair(emptyList(), matchesAcc + m4ReverseRoute.zip(times))
                        }

                        m4ReverseRoute.size - 1 -> { // School route
                            Pair(
                                emptyList(), matchesAcc + m4ReverseRoute
                                    .filterIndexed { index, _ -> index != m4ReverseRoute.size - 1 }
                                    .zip(times))
                        }

                        else -> { // Regular route
                            Pair(
                                emptyList(), matchesAcc + m4ReverseRoute
                                    .filterIndexed { index, _ ->
                                        index != m4ReverseRoute.size - 1 &&
                                                index != m4ReverseRoute.indexOf(Stops.PASEO_CABANILLAS)
                                    }.zip(times)
                            )
                        }
                    }
                }

                hasTimes(line) -> {
                    val times = sortTimes(timesAcc + extractTimes(line))

                    when (times.size) {
                        m4RegularRoute.size -> { // School route
                            Pair(emptyList(), matchesAcc + m4RegularRoute.zip(times))
                        }

                        m4RegularRoute.size - 1 -> { // Non-school route
                            Pair(
                                emptyList(),
                                matchesAcc + m4RegularRoute.filter { stop -> stop != Stops.PASEO_CABANILLAS }
                                    .zip(times))
                        }

                        else -> { // Incomplete route. Accumulate for next pass
                            DebugConfig.debugPrint("Incomplete route, accumulating...")
                            Pair(times, matchesAcc)
                        }
                    }
                }

                else -> Pair(timesAcc, matchesAcc)
            }

            Pair(times, matches)
        }

        DebugConfig.debugPrint(
            "Parsed times (${matches.size}"
        )

        matches.map { (stop, time) ->
            Pair(
                stop.name,
                time
            )
        }.forEach { DebugConfig.debugPrint("$it") }
    }

    fun hasTimes(line: String): Boolean = TIME_PATTERN.containsMatchIn(line)

    /**
     * Check if line contains times marked with asterisk (July/August only services)
     * Example: "7:40* 7:43 14:30" -> true (because of "7:40*")
     */
    fun isReverseRoute(line: String): Boolean = TIME_WITH_ASTERISK_PATTERN.containsMatchIn(line)

    /**
     * Extract all times from a line, ignoring everything else
     * Returns a list of LocalTime objects
     * Example: "JULIO Y AGOSTO 7:40* 7:43 14:30" -> [LocalTime(7,40), LocalTime(7,43), LocalTime(14,30)]
     */
    fun extractTimes(line: String): List<LocalTime> {
        val formatter = DateTimeFormatter.ofPattern("H:mm")
        return TIME_PATTERN.findAll(line)
            .map { LocalTime.parse(it.value, formatter) }
            .toList()
    }

    /**
     * Sort a list of LocalTime objects chronologically
     * Example: [LocalTime(14,30), LocalTime(7,40), LocalTime(9,15)] -> [LocalTime(7,40), LocalTime(9,15), LocalTime(14,30)]
     */
    fun sortTimes(times: List<LocalTime>): List<LocalTime> {
        return times.sorted() // LocalTime implements Comparable, so we can just call sorted()
    }
}

