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
     * The PDF uses 2-byte CID encoding where characters are interleaved with 0x00 bytes
     *
     * Based on analysis:
     * - 0x00 bytes are separator characters (skip them)
     * - 0x03 = ':' (time separator)
     * - Standard ASCII digits and letters mostly work correctly
     * - Need to map special character codes
     */
    private fun decodeControlCodeText(text: String): String {
        val decoded = StringBuilder()

        for (char in text) {
            val code = char.code

            val decodedChar = when (code) {
                // Skip null bytes (0x00) - these are part of the 2-byte CID encoding
                0x00 -> continue

                // Time separator
                0x03 -> ':'

                // Standard printable characters (keep as-is)
                in 0x20..0x7E -> char  // Space through tilde (~)

                // Newline and carriage return
                0x0A -> '\n'
                0x0D -> '\r'

                // Unknown characters - for debugging, keep them
                else -> char
            }

            decoded.append(decodedChar)
        }

        return decoded.toString()
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

                // Inspect fonts on this page
                val resources = page.resources
                val fontDictionary = resources?.getResource(PdfName.Font) as? PdfDictionary

                if (fontDictionary != null) {
                    DebugConfig.debugPrint("M4Parser: Fonts found on page $pageNum:")
                    fontDictionary.keySet().forEach { fontName ->
                        val fontObject = fontDictionary.get(fontName)
                        DebugConfig.debugPrint("  Font: $fontName")
                        DebugConfig.debugPrint("    Object: $fontObject")

                        // Try to get font details
                        if (fontObject is PdfDictionary) {
                            val baseFont = fontObject.get(PdfName.BaseFont)
                            val encoding = fontObject.get(PdfName.Encoding)
                            val subtype = fontObject.get(PdfName.Subtype)
                            val toUnicode = fontObject.get(PdfName.ToUnicode)

                            DebugConfig.debugPrint("    BaseFont: $baseFont")
                            DebugConfig.debugPrint("    Encoding: $encoding")
                            DebugConfig.debugPrint("    Subtype: $subtype")
                            DebugConfig.debugPrint("    ToUnicode: $toUnicode")

                            // Try to read ToUnicode CMap
                            if (toUnicode is PdfStream) {
                                try {
                                    val cmapBytes = toUnicode.getBytes()
                                    val cmapString = String(cmapBytes, Charsets.UTF_8)
                                    DebugConfig.debugPrint("    ToUnicode CMap (first 500 chars):")
                                    DebugConfig.debugPrint(cmapString.take(500))
                                } catch (e: Exception) {
                                    DebugConfig.debugWarn("    Failed to read ToUnicode CMap: ${e.message}")
                                }
                            }
                        }
                    }
                } else {
                    DebugConfig.debugPrint("M4Parser: No fonts found on page $pageNum")
                }

                // Use custom strategy to extract raw glyph codes
                val strategy = RawGlyphExtractionStrategy()
                val processor = PdfCanvasProcessor(strategy)
                processor.processPageContent(page)
                val rawText = strategy.resultantText

                // Decode the text from control codes to readable characters
                val decodedText = decodeControlCodeText(rawText)

                // Split into lines for easier processing
                val lines = decodedText.lines()

                DebugConfig.debugPrint("M4Parser: Page $pageNum has ${lines.size} lines")

                // Print raw glyph codes (BEFORE decoding)
                if (rawText.isNotEmpty()) {
                    val first100 = rawText.take(100)
                    val codes = first100.map { it.code }
                    DebugConfig.debugPrint("M4Parser: First 100 RAW glyph codes: ${codes.joinToString(" ") { "0x%02x".format(it) }}")
                }

                // Print decoded lines
                DebugConfig.debugPrint("M4Parser: ===== DECODED TEXT =====")
                lines.take(20).forEachIndexed { index, line ->
                    DebugConfig.debugPrint("  Line $index: $line")
                }
                DebugConfig.debugPrint("M4Parser: ========================")

                // TODO: Implement your parsing logic here
                // You have access to:
                // - lines: List<String> - all DECODED lines from the PDF page
                // - decodedText: String - full decoded text of the page
                // - routeId: String - the route ID (M4)
                // - dayType can be determined from section headers

                // TODO: Parse the lines and create BusTimetable objects
                // Example structure:
                // val weekdayTimetable = BusTimetable(
                //     routeId = routeId,
                //     stopId = "M4_STOP_ID",
                //     date = ScheduleDate.today(),
                //     departures = listOf(...), // Your parsed departure times
                //     dayType = DayType.WEEKDAY
                // )
                // timetables.add(weekdayTimetable)
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
