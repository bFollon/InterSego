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
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Parser for M1 route (Segovia – Garcillán via Polígono, Casino, Valverde, Abades, Martín Miguel)
 *
 * PDF Structure:
 * - Two tables rendered side by side: outbound (left, 8 columns) and inbound (right, 7 columns).
 * - Each decoded row = one departure: 15 times total (8 outbound stops + 7 inbound stops).
 *   Index 7 in each outbound row is the return-to-Segovia terminal and is not stored.
 * - Saturday section is appended inline in TEXT font after the last weekday row.
 *   Saturday times are 'H'-delimited: first 4 = outbound (Segovia→Abades),
 *   next 3 = inbound (Abades→Segovia).
 * - Two font types:
 *     Time font: 0xEC–0xF5 = digits 0–9 | 0x72 = row separator ('\n') |
 *                0x57 = colon placeholder | 0x8E/0xC1/0xC9 = '*' | 0xB7 = '#'
 *     Text font: standard +29 character-offset encoding (shared with M4/M6)
 * - '*' on a time = summer only (13 Jun – 13 Sep → SeasonalAvailability.SUMMER_ONLY)
 * - No Sunday service.
 *
 * Coordinates are placeholders (0.0, 0.0) pending real GPS data.
 */
class M1Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M1"),
        mode = ParserMode.DEBUG,
        version = "1.0"
    )

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Garcillán"
        private const val DIRECTION_INBOUND  = "Garcillán → Segovia"

        private object Stops {
            val SEGOVIA       = BusStop(name = "Segovia",              coordinates = "0.0, 0.0")
            val POLIGONO      = BusStop(name = "Polígono Industrial",  coordinates = "0.0, 0.0")
            val CASINO        = BusStop(name = "Casino",               coordinates = "0.0, 0.0")
            val VALVERDE      = BusStop(name = "Valverde de Majano",   coordinates = "0.0, 0.0")
            val ABADES        = BusStop(name = "Abades",               coordinates = "0.0, 0.0")
            val MARTIN_MIGUEL = BusStop(name = "Martín Miguel",        coordinates = "0.0, 0.0")
            val GARCILLAN     = BusStop(name = "Garcillán",            coordinates = "0.0, 0.0")
        }

        // Weekday outbound: 7 named stops (PDF has 8 columns; index 7 = return terminal, skipped)
        val m1WeekdayOutbound: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.POLIGONO, Stops.CASINO, Stops.VALVERDE,
            Stops.ABADES, Stops.MARTIN_MIGUEL, Stops.GARCILLAN
        )

        val m1WeekdayInbound: List<BusStop> = listOf(
            Stops.GARCILLAN, Stops.MARTIN_MIGUEL, Stops.ABADES, Stops.VALVERDE,
            Stops.CASINO, Stops.POLIGONO, Stops.SEGOVIA
        )

        // Saturday runs a shorter variant: Segovia ↔ Abades only
        val m1SaturdayOutbound: List<BusStop> = listOf(
            Stops.SEGOVIA, Stops.CASINO, Stops.VALVERDE, Stops.ABADES
        )

        val m1SaturdayInbound: List<BusStop> = listOf(
            Stops.ABADES, Stops.VALVERDE, Stops.SEGOVIA
        )

        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm")

        // Time token with optional suffix marker (* # I). The 'I' marker (seen on some inbound
        // times, meaning is unclear) is captured but treated as year-round.
        private val TIME_WITH_MARKER = Regex("""(\d{1,2}:\d{2})([*#I]?)""")

        // Saturday times are separated by 'H' in the decoded text font section.
        // Pattern captures the time (and optional '*') immediately before 'H'.
        private val SAT_TIME_WITH_H = Regex("""(\d{1,2}:\d{2})([*]?)H""")
    }

    override fun canParse(routeId: String): Boolean =
        capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M1", ignoreCase = true)) return emptyList()
        return listOf(m1WeekdayOutbound, m1WeekdayInbound)
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        if (!routeId.equals("M1", ignoreCase = true)) return emptyList()
        return when (dayType) {
            DayType.SATURDAY -> listOf(
                RouteVariant("outbound", DIRECTION_OUTBOUND, m1SaturdayOutbound, DIRECTION_OUTBOUND),
                RouteVariant("inbound",  DIRECTION_INBOUND,  m1SaturdayInbound,  DIRECTION_INBOUND)
            )
            DayType.SUNDAY -> emptyList()
            else -> listOf(
                RouteVariant("outbound", DIRECTION_OUTBOUND, m1WeekdayOutbound, DIRECTION_OUTBOUND),
                RouteVariant("inbound",  DIRECTION_INBOUND,  m1WeekdayInbound,  DIRECTION_INBOUND)
            )
        }
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M1Parser: Starting PDF parsing for $pdfPath")

        val file = File(pdfPath)
        if (!file.exists()) throw PDFParsingException("PDF file not found: $pdfPath")

        val pdfReader   = PdfReader(file)
        val pdfDocument = PdfDocument(pdfReader)

        try {
            val allLines = mutableListOf<String>()

            for (pageNum in 1..pdfDocument.numberOfPages) {
                val page = pdfDocument.getPage(pageNum)

                // M1 uses two font types that need different decoding strategies.
                // We bypass PDFTextDecoder.extractText() and decode the raw glyph stream
                // ourselves to handle both fonts in a single pass.
                val strategy = PDFTextDecoder.RawGlyphExtractionStrategy()
                PdfCanvasProcessor(strategy).processPageContent(page)
                val rawText = strategy.resultantText

                val decoded = decodeM1Text(rawText)

                // Normalise colon placeholder and compress whitespace.
                val normalised = decoded
                    .replace(Regex(""" t """), ":")
                    .replace(Regex("""(\d) (\d)"""), "$1$2")
                    .replace(Regex("""(\d) (\d)"""), "$1$2") // second pass for triple-digit runs
                    .replace(Regex(""" +"""), " ")

                allLines.addAll(normalised.lines())
            }

            val timetables = parseTimeTable(allLines)
            DebugConfig.debugPrint("M1Parser: Parsed ${timetables.size} timetables")
            return timetables

        } catch (e: Exception) {
            DebugConfig.debugError("M1Parser: Error parsing PDF", e)
            throw PDFParsingException("Failed to parse M1 PDF: ${e.message}", e)
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Decode M1 PDF raw glyph stream.
     *
     * The M1 PDF uses two font encodings:
     *   Time font  — codes 0xEC–0xF5 map to digits 0–9; 0x72 = row separator ('\n');
     *                0x57 = colon placeholder; 0x8E/0xC1/0xC9 = '*'; 0xB7 = '#'
     *   Text font  — standard +29 offset encoding (shared with other Linecar PDFs)
     *
     * 0x57 is the colon placeholder in the time font AND decodes to 't' via +29 in the
     * text font (0x57 + 29 = 0x74 = 't'). Both cases produce 't', which the caller
     * normalises to ':' via " t " → ":".
     */
    private fun decodeM1Text(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            val code = ch.code
            when {
                code == 0x00                              -> continue  // null byte (2-byte CID encoding)
                code in 0xEC..0xF5                        -> sb.append(('0' + (code - 0xEC)).toChar())
                code == 0x72                              -> sb.append('\n')
                code == 0x8E || code == 0xC1 || code == 0xC9 -> sb.append('*')
                code == 0xB7                              -> sb.append('#')
                else -> {
                    val shifted = code + 29
                    if (shifted in 0x20..0x7E || shifted == 0x0A || shifted == 0x0D) {
                        sb.append(shifted.toChar())
                    }
                    // Codes that map outside printable ASCII are discarded.
                }
            }
        }
        return sb.toString()
    }

    private fun parseTimeTable(lines: List<String>): List<BusTimetable> {
        // Departure accumulators per stop index.
        val outDepsWk  = Array(m1WeekdayOutbound.size)   { mutableListOf<DepartureTime>() }
        val inDepsWk   = Array(m1WeekdayInbound.size)    { mutableListOf<DepartureTime>() }
        val outDepsSat = Array(m1SaturdayOutbound.size)  { mutableListOf<DepartureTime>() }
        val inDepsSat  = Array(m1SaturdayInbound.size)   { mutableListOf<DepartureTime>() }

        // Buffer accumulates (time, seasonal) pairs from multiple decoded rows until
        // enough times are available to form a complete departure record (15 = 8 + 7).
        val buffer = mutableListOf<Pair<LocalTime, SeasonalAvailability>>()

        for (line in lines) {
            // The Saturday section (TEXT font, 'H'-delimited times) may appear inline
            // at the end of the last weekday row. Split off that portion before
            // adding weekday times to the buffer.
            val satStart = SAT_TIME_WITH_H.find(line)
            val weekdayPart = if (satStart != null) line.substring(0, satStart.range.first) else line
            val satPart     = if (satStart != null) line.substring(satStart.range.first)    else ""

            // --- Weekday times ---
            buffer.addAll(extractTimesWithSeasonal(weekdayPart))

            // Process complete rows: 8 outbound positions + 7 inbound positions = 15.
            // Outbound index 7 (return-to-Segovia terminal) is decoded but not stored.
            while (buffer.size >= 15) {
                val row = buffer.subList(0, 15).toList()
                buffer.subList(0, 15).clear()

                for (i in outDepsWk.indices) {  // 0..6
                    outDepsWk[i].add(DepartureTime(row[i].first.hour, row[i].first.minute,
                        seasonalAvailability = row[i].second))
                }
                for (i in inDepsWk.indices) {   // 0..6  → PDF indices 8..14
                    inDepsWk[i].add(DepartureTime(row[i + 8].first.hour, row[i + 8].first.minute,
                        seasonalAvailability = row[i + 8].second))
                }
            }

            // --- Saturday times (H-delimited) ---
            if (satPart.isNotEmpty()) {
                val satTimes = SAT_TIME_WITH_H.findAll(satPart).map { m ->
                    val timeStr  = m.groupValues[1]
                    val seasonal = if (m.groupValues[2] == "*") SeasonalAvailability.SUMMER_ONLY
                                   else                          SeasonalAvailability.YEAR_ROUND
                    Pair(LocalTime.parse(timeStr, TIME_FORMATTER), seasonal)
                }.toList()

                for (i in outDepsSat.indices) {
                    if (i < satTimes.size) {
                        outDepsSat[i].add(DepartureTime(satTimes[i].first.hour,
                            satTimes[i].first.minute, seasonalAvailability = satTimes[i].second))
                    }
                }
                for (i in inDepsSat.indices) {
                    val idx = i + m1SaturdayOutbound.size
                    if (idx < satTimes.size) {
                        inDepsSat[i].add(DepartureTime(satTimes[idx].first.hour,
                            satTimes[idx].first.minute, seasonalAvailability = satTimes[idx].second))
                    }
                }
            }
        }

        // Drain any remaining buffer: a leftover of 8 means an outbound-only departure;
        // 7 means an inbound-only departure.
        when (buffer.size) {
            8 -> for (i in outDepsWk.indices) {
                outDepsWk[i].add(DepartureTime(buffer[i].first.hour, buffer[i].first.minute,
                    seasonalAvailability = buffer[i].second))
            }
            7 -> for (i in inDepsWk.indices) {
                inDepsWk[i].add(DepartureTime(buffer[i].first.hour, buffer[i].first.minute,
                    seasonalAvailability = buffer[i].second))
            }
            else -> if (buffer.isNotEmpty()) {
                DebugConfig.debugPrint("M1Parser: ${buffer.size} leftover times after parsing — discarding")
            }
        }

        return buildTimetables(m1WeekdayOutbound,  DayType.WEEKDAY,   DIRECTION_OUTBOUND, outDepsWk)  +
               buildTimetables(m1WeekdayInbound,   DayType.WEEKDAY,   DIRECTION_INBOUND,  inDepsWk)   +
               buildTimetables(m1SaturdayOutbound, DayType.SATURDAY,  DIRECTION_OUTBOUND, outDepsSat) +
               buildTimetables(m1SaturdayInbound,  DayType.SATURDAY,  DIRECTION_INBOUND,  inDepsSat)
    }

    /** Extract (time, seasonal) pairs from a single decoded line, ignoring non-time content. */
    private fun extractTimesWithSeasonal(text: String): List<Pair<LocalTime, SeasonalAvailability>> =
        TIME_WITH_MARKER.findAll(text).mapNotNull { m ->
            try {
                val time     = LocalTime.parse(m.groupValues[1], TIME_FORMATTER)
                val seasonal = if (m.groupValues[2] == "*") SeasonalAvailability.SUMMER_ONLY
                               else                          SeasonalAvailability.YEAR_ROUND
                Pair(time, seasonal)
            } catch (_: Exception) { null }
        }.toList()

    private fun buildTimetables(
        stops: List<BusStop>,
        dayType: DayType,
        direction: String,
        deps: Array<MutableList<DepartureTime>>
    ): List<BusTimetable> = stops.mapIndexed { i, stop ->
        BusTimetable(
            routeId   = "M1",
            stopId    = stop.name,
            dayType   = dayType,
            direction = direction,
            departures = deps[i]
        )
    }
}
