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
import com.github.bfollon.linecapp.services.pdfparsing.CapableParser
import com.github.bfollon.linecapp.services.pdfparsing.ParserCapabilities
import com.github.bfollon.linecapp.services.pdfparsing.ParserMode
import com.github.bfollon.linecapp.services.pdfparsing.PDFParsingException
import com.github.bfollon.linecapp.services.pdfparsing.PDFTextDecoder
import com.github.bfollon.linecapp.services.pdfparsing.RouteStopsProvider
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.File

/**
 * Parser for M6 route (DEBUG MODE)
 *
 * This is a skeleton parser that logs all PDF text for debugging purposes.
 * It returns an empty list of timetables.
 *
 * TODO: Implement full parsing logic once PDF structure is analyzed
 */
class M6Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M6"),
        mode = ParserMode.DEBUG,
        version = "0.1-debug"
    )

    companion object {
        // Minimal route definition for future implementation
        // TODO: Define actual M6 route stops after analyzing PDF
        private object Stops {
            val AZOGUEJO = BusStop(
                name = "Azoguejo",
                address = "Pl. Artillería, 40001 Segovia"
            )
        }

        val m6Route = listOf(
            Stops.AZOGUEJO
        )
    }

    override fun canParse(routeId: String): Boolean {
        return capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M6Parser: Starting DEBUG parsing for $pdfPath")
        DebugConfig.debugPrint("M6Parser: ⚠️ DEBUG MODE - Logging PDF text only, returning empty list")

        val file = File(pdfPath)
        if (!file.exists()) {
            throw PDFParsingException("PDF file not found: $pdfPath")
        }

        val pdfReader = PdfReader(file)
        val pdfDocument = PdfDocument(pdfReader)

        try {
            // Extract and print all text lines
            for (pageNum in 1..pdfDocument.numberOfPages) {
                DebugConfig.debugPrint("M6Parser: ===== PAGE $pageNum =====")
                val page = pdfDocument.getPage(pageNum)

                // Try standard iText extraction first
                var extractedText = PdfTextExtractor.getTextFromPage(page)

                // Check if text needs decoding (broken PDF encoding)
                if (PDFTextDecoder.needsDecoding(extractedText)) {
                    DebugConfig.debugPrint("M6Parser: Detected broken encoding, using custom decoder...")

                    // Use raw glyph extraction for broken PDFs
                    val strategy = PDFTextDecoder.RawGlyphExtractionStrategy()
                    val processor = PdfCanvasProcessor(strategy)
                    processor.processPageContent(page)
                    val rawText = strategy.resultantText

                    // Decode with +29 character offset (old Linecar PDFs)
                    extractedText = PDFTextDecoder.decodeWithCharacterOffset(rawText, offset = 29)
                    DebugConfig.debugPrint("M6Parser: Successfully decoded broken PDF")
                }

                val lines = extractedText.lines()

                DebugConfig.debugPrint("M6Parser: Page $pageNum has ${lines.size} lines")
                DebugConfig.debugPrint("M6Parser: Total text length: ${extractedText.length} characters")

                // Log all non-empty lines with line numbers
                lines.forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        DebugConfig.debugPrint("  Line $index: $line")
                    }
                }
            }

            DebugConfig.debugPrint("M6Parser: ===== END OF PDF =====")
            DebugConfig.debugPrint("M6Parser: DEBUG MODE - Returning empty list (no timetables)")

            // Return empty list - this is a debug parser
            return emptyList()

        } catch (e: Exception) {
            DebugConfig.debugError("M6Parser: Error parsing PDF", e)
            throw PDFParsingException("Failed to parse M6 PDF: ${e.message}", e)
        } finally {
            // Always close PDF resources, even on exception
            pdfDocument.close()
        }
    }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        return if (routeId.equals("M6", ignoreCase = true)) {
            listOf(m6Route)
        } else {
            emptyList()
        }
    }
}
