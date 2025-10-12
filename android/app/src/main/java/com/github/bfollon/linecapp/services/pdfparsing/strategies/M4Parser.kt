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
 * TODO: Implement actual parsing logic to extract timetables from PDF text
 */
class M4Parser : BusTimetableParser {

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
                val pageText = PdfTextExtractor.getTextFromPage(pdfDocument.getPage(pageNum))

                // Split into lines for easier processing
                val lines = pageText.lines()

                DebugConfig.debugPrint("M4Parser: Page $pageNum has ${lines.size} lines")

                // TODO: Implement your parsing logic here
                // You have access to:
                // - lines: List<String> - all lines from the PDF page
                // - pageText: String - full text of the page
                // - routeId: String - the route ID (M4)
                // - dayType can be determined from section headers

                // Example: Print first few lines for debugging
                lines.take(10).forEachIndexed { index, line ->
                    DebugConfig.debugPrint("  Line $index: $line")
                }

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
