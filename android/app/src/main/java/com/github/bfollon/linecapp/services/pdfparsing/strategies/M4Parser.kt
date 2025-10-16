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
import com.github.bfollon.linecapp.data.ScheduleDate
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.pdfparsing.BusTimetableParser
import com.github.bfollon.linecapp.services.pdfparsing.PDFParsingException
import com.github.bfollon.linecapp.services.pdfparsing.PDFTextDecoder
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import java.io.File

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

                // Print first few lines for debugging
                DebugConfig.debugPrint("M4Parser: ===== EXTRACTED TEXT =====")
                lines.take(5).forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        DebugConfig.debugPrint("  Line $index: ${line.take(80)}")
                    }
                }
                DebugConfig.debugPrint("M4Parser: ==========================")

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
}
