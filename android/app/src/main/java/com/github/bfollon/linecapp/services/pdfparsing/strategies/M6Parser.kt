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

import com.github.bfollon.linecapp.data.BusStop
import com.github.bfollon.linecapp.data.BusTimetable
import com.github.bfollon.linecapp.data.DayType
import com.github.bfollon.linecapp.data.DepartureTime
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.pdfparsing.CapableParser
import com.github.bfollon.linecapp.services.pdfparsing.PDFParsingException
import com.github.bfollon.linecapp.services.pdfparsing.PDFTextDecoder
import com.github.bfollon.linecapp.services.pdfparsing.ParserCapabilities
import com.github.bfollon.linecapp.services.pdfparsing.ParserMode
import com.github.bfollon.linecapp.services.pdfparsing.RouteStopsProvider
import com.github.bfollon.linecapp.services.pdfparsing.TimeModifier
import com.github.bfollon.linecapp.services.pdfparsing.TimetableParserUtils
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.File
import java.time.LocalTime
import java.util.UUID

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

    private data class ParsingState(
        val journeyBuilder: List<LocalTime>,
        val isReversed: Boolean = true,
        val section: DayType = DayType.WEEKDAY,
        val currentAnnotation: TimeModifier? = null,

        val routes: Map<UUID, List<BusTimetable>> = emptyMap(),
    )

    data class StopCluster(val stops: List<BusStop>) {
        init { require(stops.isNotEmpty()) }
    }

    data class Route(
        val id: UUID = UUID.randomUUID(),
        val clusters: List<StopCluster>
    ) {
        val stops: List<BusStop> get() = clusters.flatMap { it.stops }

        fun reversed(): Route = Route(
            clusters = clusters.reversed().map { StopCluster(it.stops.reversed()) }
        )
    }

    companion object {
        private object Stops {
            val AZOGUEJO = BusStop(
                name = "Azoguejo",
                address = "Pl. Artillería, 40001 Segovia"
            )
            val DELICIAS = BusStop(
                name = "Delicias",
                address = "",
                coordinates = "40.954500, -4.108889",
            )
            val MONTECORREDORES = BusStop(
                name = "Montecorredores",
                address = "",
                coordinates = "40.952000, -4.097278",
            )
            val SANCRIS = BusStop(
                name = "San Cristóbal de Segovia",
                address = "",
                coordinates = "40.952056, -4.081139",
            )
            val SANCRIS_IGLESIA = BusStop(
                name = "Iglesia",
                area = "San Cristóbal de segovia",
                address = "",
                coordinates = "40.951733, -4.077499",
            )
            val SANCRIS_ROTONDA = BusStop(
                name = "Rotonda",
                area = "San Cristóbal de segovia",
                address = "",
                coordinates = "40.951224, -4.073449",
            )
            val SONSOTO = BusStop(
                name = "Potro",
                area = "Sonsoto",
                address = "",
                coordinates = "40.954774, -4.040524",
            )
            val SONSOTO_2 = BusStop(
                name = "Sonsoto 2",
                details = "Junto a C/ Peñas lisas",
                area = "Sonsoto",
                address = "",
                coordinates = "40.957470, -4.039154",
            )
            val TRESCASAS = BusStop(
                name = "Plaza de la constitución",
                area = "Trescasas",
                address = "",
                coordinates = "40.961834, -4.037367",
            )
            val TRESCASAS_2 = BusStop(
                name = "Trescasas 2",
                area = "Trescasas",
                address = "",
                coordinates = "40.963899, -4.034776",
            )
            val CABANILLAS = BusStop(
                name = "Cabanillas",
                address = "",
                coordinates = "40.974402, -4.028241"
            )
            val TORRECABALLEROS = BusStop(
                name = "Torrecaballeros",
                address = "",
                coordinates = "40.991880, -4.022848"
            )
            val TORRECABALLEROS_2 = BusStop(
                name = "Torrecaballeros 2",
                details = "Junto a la taberna del Rancho",
                address = "",
                coordinates = "40.995364, -4.021688",
            )
            val TORRECABALLEROS_3 = BusStop(
                name = "Torrecaballeros 3",
                details = "En carretera hacia Turégano",
                address = "",
                coordinates = "40.999144, -4.020855",
            )
            val ANDRES_LAGUNA = BusStop(
                name = "IES Andres Laguna",
                address = "",
                coordinates = "40.939106, -4.115582",
            )
            val LA_PISTA = BusStop(
                name = "Glorieta La Pista",
                details = "Glorieta del Ballenoil",
                address = "",
                coordinates = "40.937354, -4.111411",
            )
            val HERMANITAS = BusStop(
                name = "Residencia Hermanitas de los pobres",
                address = "",
                coordinates = "40.944234, -4.110012",
            )
            val ESTACION_BUS = BusStop(
                name = "Estación de Autobuses de Segovia",
                address = "Pl. de la Estación de Autobuses, 3, 40002 Segovia",
                coordinates = "40.944768, -4.121823"
            )
            val PLAZA_TOROS = BusStop(
                name = "Plaza de Toros",
                address = "",
                coordinates = "40.942093, -4.107603",
            )
            val PALAZUELOS = BusStop(
                name = "Palazuelos",
                address = "",
                coordinates = "40.931068, -4.064340",
            )
            val PALAZUELOS_COLEGIO = BusStop(
                name = "Colegio",
                area = "Palazuelos",
                address = "",
                coordinates = "40.933921, -4.063495",
            )
            val TABANERA = BusStop(
                name = "Tabanera",
                address = "",
                coordinates = "40.934336, -4.067014",
            )
            val TABANERA_2 = BusStop(
                name = "Tabanera 2",
                address = "",
                coordinates = "40.937491, -4.065818",
            )

            val JARDINILLOS = BusStop(
                name = "Jardinillos de San Roque",
                details = "Frente a Policía Nacional",
                address = "",
                coordinates = "40.944361, -4.120831",
            )
        }

        object Routes {
            object Weekday {
                val regular = Route(
                    clusters = listOf(
                        StopCluster(listOf(Stops.AZOGUEJO, Stops.DELICIAS, Stops.MONTECORREDORES)),
                        StopCluster(listOf(Stops.SANCRIS, Stops.SANCRIS_IGLESIA, Stops.SANCRIS_ROTONDA)),
                        StopCluster(listOf(Stops.SONSOTO, Stops.SONSOTO_2)),
                        StopCluster(listOf(Stops.TRESCASAS, Stops.TRESCASAS_2)),
                        StopCluster(listOf(Stops.CABANILLAS)),
                        StopCluster(listOf(Stops.TORRECABALLEROS, Stops.TORRECABALLEROS_2, Stops.TORRECABALLEROS_3)),
                    )
                )
                val reversed = regular.reversed()

                val extended = Route(
                    clusters = listOf(
                        StopCluster(listOf(Stops.ANDRES_LAGUNA, Stops.LA_PISTA, Stops.HERMANITAS, Stops.AZOGUEJO, Stops.DELICIAS, Stops.MONTECORREDORES)),
                    ) + regular.clusters.drop(1)
                )
                val extendedReversed = extended.reversed()

                val busStation = Route(
                    clusters = listOf(
                        StopCluster(listOf(Stops.ESTACION_BUS, Stops.ANDRES_LAGUNA, Stops.LA_PISTA, Stops.PLAZA_TOROS)),
                        StopCluster(listOf(Stops.PALAZUELOS, Stops.PALAZUELOS_COLEGIO)),
                        StopCluster(listOf(Stops.TABANERA, Stops.TABANERA_2)),
                        // SANCRIS anchor removed on this variant; IGLESIA serves as anchor
                        StopCluster(listOf(Stops.SANCRIS_IGLESIA, Stops.SANCRIS_ROTONDA)),
                        StopCluster(listOf(Stops.SONSOTO, Stops.SONSOTO_2)),
                        StopCluster(listOf(Stops.TRESCASAS, Stops.TRESCASAS_2)),
                        StopCluster(listOf(Stops.CABANILLAS)),
                        StopCluster(listOf(Stops.TORRECABALLEROS, Stops.TORRECABALLEROS_2, Stops.TORRECABALLEROS_3)),
                    )
                )
            }

            object Saturday {
                val regular = Weekday.busStation

                // Same as busStation but the return leg adds Jardinillos after Azoguejo
                val reversed = Route(
                    clusters = regular.clusters.dropLast(1) + listOf(
                        StopCluster(listOf(Stops.AZOGUEJO, Stops.JARDINILLOS))
                    )
                )
            }

            object Sunday {
                val regular = Weekday.busStation

                val reversed = regular.reversed()
            }
        }

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

                parseM6Timetables(lines)
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

    /**
     * Parse M4 timetables from decoded PDF text
     *
     * @param lines The decoded text lines from the PDF
     * @param routeId The route ID (M4)
     * @return List of BusTimetable objects grouped by (stop, dayType)
     */
    private fun parseM6Timetables(lines: List<String>): List<BusTimetable> {
        DebugConfig.debugPrint("M4Parser: Starting timetable parsing for ${lines.size} lines")

        val timetables = parseTimeTable(lines)

        DebugConfig.debugPrint("M4Parser: Parsed ${timetables.size} timetables")
        return timetables
    }

    /**
     * Create initial empty timetables for a route.
     * Anchor vs approximate is encoded in the cluster structure, not in BusTimetable.
     */
    private fun createInitialTimetables(
        route: Route,
        dayType: DayType,
        direction: String
    ): List<BusTimetable> {
        return route.stops.map { stop ->
            BusTimetable(
                routeId = "M6",
                stopId = stop.name,
                dayType = dayType,
                direction = direction,
                departures = emptyList(),
            )
        }
    }

    private fun determineParsingTarget(state: ParsingState): UUID {
        return when (state.section) {
            DayType.WEEKDAY -> {
                if (state.isReversed) {
                    when (state.currentAnnotation) {
                        TimeModifier.ARROW -> Routes.Weekday.extendedReversed.id
                        else -> Routes.Weekday.reversed.id
                    }
                } else {
                    when (state.currentAnnotation) {
                        TimeModifier.ARROW -> Routes.Weekday.extended.id
                        TimeModifier.DOUBLE_ASTERISK, TimeModifier.POUND ->
                            Routes.Weekday.busStation.id

                        else -> Routes.Weekday.regular.id
                    }

                }
            }

            DayType.SATURDAY -> {
                if (state.isReversed) Routes.Saturday.reversed.id
                else Routes.Saturday.regular.id
            }

            DayType.SUNDAY -> {
                if (state.isReversed) Routes.Sunday.reversed.id
                else Routes.Sunday.regular.id
            }

            else -> {
                println("TODO ERROR")
                Routes.Weekday.regular.id
            }
        }
    }

    private fun updateState(
        updatedTimetables: List<BusTimetable>,
        target: UUID,
        state: ParsingState
    ): ParsingState = state.copy(
        routes = state.routes + (target to updatedTimetables),
        isReversed = !state.isReversed,
        currentAnnotation = null // Clear annotation since we are going to process a new time group
    )

    /**
     * Update timetables with new departure times using the cluster structure.
     * The first stop in each cluster (anchor) receives the PDF time directly.
     * Subsequent stops in the cluster receive anchor time + 5 min per position.
     */
    private fun updateTimetables(
        route: Route,
        timetables: List<BusTimetable>,
        times: List<LocalTime>
    ): List<BusTimetable> {
        return route.clusters.zip(times).fold(
            Pair(emptyList<BusTimetable>(), timetables)
        ) { (result, remaining), (cluster, anchorTime) ->
            val updatedCluster = remaining.take(cluster.stops.size)
                .mapIndexed { posInCluster, timetable ->
                    val totalMinutes = anchorTime.hour * 60 + anchorTime.minute + posInCluster * 5
                    timetable.copy(
                        departures = timetable.departures + DepartureTime(
                            totalMinutes / 60 % 24,
                            totalMinutes % 60
                        )
                    )
                }
            Pair(result + updatedCluster, remaining.drop(cluster.stops.size))
        }.first
    }

    private fun ParsingState.processAnnotation(modifier: TimeModifier?): ParsingState =
        modifier?.let { this.copy(currentAnnotation = it) } ?: this

    private fun parseTimeTable(lines: List<String>): List<BusTimetable> {
        val routeById: Map<UUID, Route> = listOf(
            Routes.Weekday.regular,
            Routes.Weekday.reversed,
            Routes.Weekday.extended,
            Routes.Weekday.extendedReversed,
            Routes.Weekday.busStation,
            Routes.Saturday.reversed,
            Routes.Sunday.reversed,
        ).associateBy { it.id }

        val initialState = ParsingState(
            journeyBuilder = emptyList(),

            routes = mapOf(
                Routes.Weekday.regular.id to createInitialTimetables(
                    Routes.Weekday.regular,
                    DayType.WEEKDAY,
                    direction = "Segovia -> Torrecaballeros"
                ),
                Routes.Weekday.reversed.id to createInitialTimetables(
                    Routes.Weekday.reversed,
                    DayType.WEEKDAY,
                    direction = "Torrecaballeros -> Segovia"
                ),
                Routes.Weekday.extended.id to createInitialTimetables(
                    Routes.Weekday.extended,
                    DayType.WEEKDAY,
                    direction = "Segovia -> Torrecaballeros"
                ),
                Routes.Weekday.extendedReversed.id to createInitialTimetables(
                    Routes.Weekday.extendedReversed,
                    DayType.WEEKDAY,
                    direction = "Torrecaballeros -> Segovia"
                ),
                Routes.Weekday.busStation.id to createInitialTimetables(
                    Routes.Weekday.busStation,
                    DayType.WEEKDAY,
                    direction = "Segovia -> Torrecaballeros"
                ),
                Routes.Saturday.regular.id to createInitialTimetables(
                    Routes.Saturday.regular,
                    DayType.SATURDAY,
                    direction = "Segovia -> Torrecaballeros"
                ),
                Routes.Saturday.reversed.id to createInitialTimetables(
                    Routes.Saturday.reversed,
                    DayType.SATURDAY,
                    direction = "Torrecaballeros -> Segovia"
                ),
                Routes.Sunday.regular.id to createInitialTimetables(
                    Routes.Sunday.regular,
                    DayType.SUNDAY,
                    direction = "Segovia -> Torrecaballeros"
                ),
                Routes.Sunday.reversed.id to createInitialTimetables(
                    Routes.Sunday.reversed,
                    DayType.SUNDAY,
                    direction = "Torrecaballeros -> Segovia"
                ),
            ),
        )

        val newState = lines.fold(initialState) { state, line ->
            when {
                TimetableParserUtils.hasTimes(line) -> {
                    val annotatedTimes = TimetableParserUtils.extractAnnotatedTimes(line)

                    annotatedTimes.fold(
                        Pair(
                            state,
                            emptyList<LocalTime>()
                        )
                    ) { (state, inlineBuilder), annotatedTime ->
                        val stateWithAnnotation = state.processAnnotation(annotatedTime.modifier)
                        val parsingTarget = determineParsingTarget(stateWithAnnotation)
                        val newBuilder = inlineBuilder + annotatedTime.time
                        if (newBuilder.size == routeById[parsingTarget]!!.clusters.size) {
                            Pair(
                                updateState(
                                    updatedTimetables = updateTimetables(
                                        route = routeById[parsingTarget]!!,
                                        timetables = stateWithAnnotation.routes[parsingTarget]!!,
                                        times = newBuilder
                                    ),
                                    target = parsingTarget,
                                    state = stateWithAnnotation
                                ),
                                emptyList() // Clear accumulated times since we are going to parse a new time group
                            )
                        } else Pair(stateWithAnnotation, newBuilder)
                    }.first
                }

                else -> state
            }
        }

        return emptyList()
    }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        return if (routeId.equals("M6", ignoreCase = true)) {
            listOf(Routes.Weekday.regular.stops)
        } else {
            emptyList()
        }
    }
}
