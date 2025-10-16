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
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.canvas.parser.listener.ITextExtractionStrategy
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.io.font.PdfEncodings
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
 * Font Encoding Issue:
 * - The PDF uses ASCII control codes instead of normal text
 * - Characters are mapped: DC4(0x14)='1', NAK(0x15)='2', SYN(0x16)='3', etc.
 * - This decoder function maps the control codes back to readable text
 *
 * TODO: Implement actual parsing logic to extract timetables from PDF text
 */
class M4Parser : BusTimetableParser {

    /**
     * Custom extraction strategy that captures raw glyph codes from PDF
     * instead of letting iText convert them to Unicode (which fails for this broken PDF)
     */
    private class RawGlyphExtractionStrategy : ITextExtractionStrategy {
        private val result = StringBuilder()

        override fun getResultantText(): String = result.toString()

        override fun eventOccurred(data: IEventData, type: EventType) {
            if (type == EventType.RENDER_TEXT) {
                val renderInfo = data as TextRenderInfo

                // Get the raw PDF string bytes
                try {
                    val pdfString = renderInfo.pdfString

                    // Extract raw bytes from the PDF string
                    if (pdfString != null) {
                        // Get the text value (which contains the raw CID codes as character codes)
                        val text = pdfString.value

                        // Just append the text as-is - the character codes ARE the CID codes
                        result.append(text)
                    }
                } catch (e: Exception) {
                    DebugConfig.debugWarn("Failed to extract raw glyphs: ${e.message}")
                }
            }
        }

        override fun getSupportedEvents(): Set<EventType> {
            return setOf(EventType.RENDER_TEXT)
        }
    }

    /**
     * Decode text from the broken PDF font encoding
     * The PDF uses a character offset of +29 for all text
     *
     * Based on analysis:
     * - 0x00 bytes are separator characters (skip them)
     * - All other characters need +29 added to get the correct character
     * - Examples:
     *   - 'U' (0x55) + 29 = 'r' (0x72)
     *   - '3' (0x33) + 29 = 'P' (0x50)
     *   - 0x14 (DC4) + 29 = '7' (0x31 + 6 = 0x37)... wait that's wrong
     * - Actually it's: character_code + 29 = actual_character
     *   - So 0x14 + 29 = 0x31 = '1' ✓
     *   - 0x15 + 29 = 0x32 = '2' ✓
     *   - 0x16 + 29 = 0x33 = '3' ✓
     */
    private fun decodeControlCodeText(text: String): String {
        val decoded = StringBuilder()

        for (char in text) {
            val code = char.code

            when (code) {
                // Skip null bytes (0x00) - these are part of the 2-byte CID encoding
                0x00 -> continue

                // All other characters: add 29 to get the actual character
                else -> {
                    val actualCode = code + 29
                    // Make sure it's in valid character range
                    if (actualCode in 0x20..0x7E || actualCode == 0x0A || actualCode == 0x0D) {
                        decoded.append(actualCode.toChar())
                    } else {
                        // For characters outside printable ASCII, keep for debugging
                        decoded.append('?')
                    }
                }
            }
        }

        return decoded.toString()
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

                // Use custom strategy to extract raw glyph codes (with +29 offset encoding)
                val strategy = RawGlyphExtractionStrategy()
                val processor = PdfCanvasProcessor(strategy)
                processor.processPageContent(page)
                val rawText = strategy.resultantText

                // Decode the text from control codes to readable characters
                val decodedText = decodeControlCodeText(rawText)

                // Split into lines for easier processing
                val lines = decodedText.lines()

                DebugConfig.debugPrint("M4Parser: Page $pageNum has ${lines.size} lines")

                // Print decoded text for debugging
                DebugConfig.debugPrint("M4Parser: ===== DECODED TEXT =====")
                lines.take(10).forEachIndexed { index, line ->
                    DebugConfig.debugPrint("  Line $index: ${line.take(200)}${if (line.length > 200) "..." else ""}")
                }
                DebugConfig.debugPrint("M4Parser: ========================")

                // TODO: Parse the decoded text and create BusTimetable objects
                // Call your parsing function here
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
