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

import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.SeasonalAvailability
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.pdfparsing.CapableParser
import com.github.bfollon.intersego.services.pdfparsing.PDFParsingException
import com.github.bfollon.intersego.services.pdfparsing.PDFTextDecoder
import com.github.bfollon.intersego.services.pdfparsing.ParserCapabilities
import com.github.bfollon.intersego.services.pdfparsing.ParserMode
import com.github.bfollon.intersego.services.pdfparsing.RouteStopsProvider
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteTab
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.data.RouteViewStop
import com.github.bfollon.intersego.data.SwapAction
import com.github.bfollon.intersego.services.pdfparsing.AnnotatedTime
import com.github.bfollon.intersego.services.pdfparsing.TimeModifier
import com.github.bfollon.intersego.services.pdfparsing.TimetableParserUtils
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import java.io.File
import java.time.LocalTime
import java.time.Month
import java.util.UUID

/**
 * Parser for M6 route (Segovia ↔ Torrecaballeros).
 *
 * Handles weekday, Saturday, and Sunday schedules including partial journeys
 * (rows with fewer anchor times than the route has clusters) and mixed-direction
 * lines (adjacent outbound/return times on the same PDF text line).
 */
class M6Parser : CapableParser, RouteStopsProvider {

    override val capabilities = ParserCapabilities(
        supportedRoutes = setOf("M6"),
        mode = ParserMode.PRODUCTION,
        version = "0.4"
    )

    private data class ParsingState(
        val journeyBuilder: List<LocalTime>,
        val isReversed: Boolean = true,
        val section: DayType = DayType.WEEKDAY,
        val currentAnnotation: TimeModifier? = null,
        /** Directions (isReversed values) that have already seen their first *** journey on Sunday. */
        val sundaySeasonalFirstSeen: Set<Boolean> = emptySet(),

        val routes: Map<UUID, List<BusTimetable>> = emptyMap(),
    )

    data class StopCluster(val stops: List<BusStop>) {
        init {
            require(stops.isNotEmpty())
        }
    }

    enum class ClusterAlignment { FROM_START, FROM_END }

    data class Route(
        val id: UUID = UUID.randomUUID(),
        val clusters: List<StopCluster>,
        val alignment: ClusterAlignment = ClusterAlignment.FROM_START
    ) {
        val stops: List<BusStop> get() = clusters.flatMap { it.stops }

        fun reversed(): Route = Route(
            clusters = clusters.reversed().map { StopCluster(it.stops.reversed()) },
            alignment = when (alignment) {
                ClusterAlignment.FROM_START -> ClusterAlignment.FROM_END
                ClusterAlignment.FROM_END -> ClusterAlignment.FROM_START
            }
        )
    }

    companion object {
        private const val DIRECTION_OUTBOUND = "Segovia → Torrecaballeros"
        private const val DIRECTION_INBOUND = "Torrecaballeros → Segovia"
        private const val ESTIMATED_MINUTES_PER_CLUSTER_STOP = 2
        private const val ESTIMATED_TORRECAB_TO_DELICIAS_MINUTES = 15L

        val SUMMER_MONTHS: Set<Month> = setOf(Month.JULY, Month.AUGUST)

        private val FOOTNOTE_PATTERN = Regex(
            """PERIODO LECTIVO|VACACIONES ESCOLARES""", RegexOption.IGNORE_CASE
        )

        private object Stops {
            val AZOGUEJO = BusStop(
                id = "m6-azoguejo",
                name = "Azoguejo",
                area = "Segovia capital",
                coordinates = "40.948502, -4.115979"
            )
            val DELICIAS = BusStop(
                id = "m6-delicias",
                name = "Delicias",
                coordinates = "40.954500, -4.108889",
                area = "Segovia capital"
            )
            val MONTECORREDORES = BusStop(
                id = "m6-montecorredores",
                name = "Montecorredores",
                coordinates = "40.952000, -4.097278",
                area = "Segovia capital"
            )
            val SANCRIS = BusStop(
                id = "m6-sancris",
                name = "San Cristóbal de Segovia",
                coordinates = "40.952056, -4.081139",
                area = "San Cristóbal de segovia",
            )
            val SANCRIS_IGLESIA = BusStop(
                id = "m6-sancris-iglesia",
                name = "Iglesia",
                area = "San Cristóbal de segovia",
                coordinates = "40.951733, -4.077499",
            )
            val SANCRIS_ROTONDA = BusStop(
                id = "m6-sancris-rotonda",
                name = "Rotonda",
                area = "San Cristóbal de segovia",
                coordinates = "40.951224, -4.073449",
            )
            val SONSOTO = BusStop(
                id = "m6-sonsoto",
                name = "Potro",
                area = "Sonsoto",
                coordinates = "40.954774, -4.040524",
            )
            val SONSOTO_2 = BusStop(
                id = "m6-sonsoto-2",
                name = "Sonsoto 2",
                details = "Junto a C/ Peñas lisas",
                area = "Sonsoto",
                coordinates = "40.957470, -4.039154",
            )
            val TRESCASAS = BusStop(
                id = "m6-trescasas",
                name = "Plaza de la constitución",
                area = "Trescasas",
                coordinates = "40.961834, -4.037367",
            )
            val TRESCASAS_2 = BusStop(
                id = "m6-trescasas-2",
                name = "Trescasas 2",
                area = "Trescasas",
                coordinates = "40.963899, -4.034776",
            )
            val CABANILLAS = BusStop(
                id = "m6-cabanillas",
                name = "Cabanillas",
                coordinates = "40.974402, -4.028241",
                area = "Cabanillas",
            )
            val TORRECABALLEROS = BusStop(
                id = "m6-torrecaballeros",
                name = "Torrecaballeros",
                coordinates = "40.991880, -4.022848",
                area = "Torrecaballeros",
            )
            val TORRECABALLEROS_2 = BusStop(
                id = "m6-torrecaballeros-2",
                name = "Torrecaballeros 2",
                details = "Junto a la taberna del Rancho",
                coordinates = "40.995364, -4.021688",
                area = "Torrecaballeros",
            )
            val TORRECABALLEROS_3 = BusStop(
                id = "m6-torrecaballeros-3",
                name = "Torrecaballeros 3",
                details = "En carretera hacia Turégano",
                coordinates = "40.999144, -4.020855",
                area = "Torrecaballeros",
            )
            val ANDRES_LAGUNA = BusStop(
                id = "m6-andres-laguna",
                name = "IES Andres Laguna",
                coordinates = "40.939106, -4.115582",
                area = "Segovia capital"
            )
            val LA_PISTA = BusStop(
                id = "m6-la-pista",
                name = "Glorieta La Pista",
                details = "Glorieta del Ballenoil",
                coordinates = "40.937354, -4.111411",
                area = "Segovia capital"
            )
            val HERMANITAS = BusStop(
                id = "m6-hermanitas",
                name = "Residencia Hermanitas de los pobres",
                coordinates = "40.944234, -4.110012",
                area = "Segovia capital"
            )
            val ESTACION_BUS = BusStop(
                id = "m6-estacion-bus",
                name = "Estación de Autobuses de Segovia",
                coordinates = "40.944768, -4.121823",
                area = "Segovia capital"
            )
            val PLAZA_TOROS = BusStop(
                id = "m6-plaza-toros",
                name = "Plaza de Toros",
                coordinates = "40.942093, -4.107603",
                area = "Segovia capital"
            )
            val PALAZUELOS = BusStop(
                id = "m6-palazuelos",
                name = "Palazuelos",
                coordinates = "40.931068, -4.064340",
                area = "Palazuelos"
            )
            val PALAZUELOS_COLEGIO = BusStop(
                id = "m6-palazuelos-colegio",
                name = "Colegio",
                area = "Palazuelos",
                coordinates = "40.933921, -4.063495",
            )
            val TABANERA = BusStop(
                id = "m6-tabanera",
                name = "Tabanera",
                coordinates = "40.934336, -4.067014",
                area = "Tabanera"
            )
            val TABANERA_2 = BusStop(
                id = "m6-tabanera-2",
                name = "Tabanera 2",
                coordinates = "40.937491, -4.065818",
                area = "Tabanera"
            )

            val JARDINILLOS = BusStop(
                id = "m6-jardinillos",
                name = "Jardinillos de San Roque",
                details = "Frente a Policía Nacional",
                coordinates = "40.944361, -4.120831",
                area = "Segovia"
            )
        }

        object Routes {
            object Weekday {
                val regular = Route(
                    clusters = listOf(
                        StopCluster(listOf(Stops.AZOGUEJO, Stops.DELICIAS, Stops.MONTECORREDORES)),
                        StopCluster(
                            listOf(
                                Stops.SANCRIS,
                                Stops.SANCRIS_IGLESIA,
                                Stops.SANCRIS_ROTONDA
                            )
                        ),
                        StopCluster(listOf(Stops.SONSOTO, Stops.SONSOTO_2)),
                        StopCluster(listOf(Stops.TRESCASAS, Stops.TRESCASAS_2)),
                        StopCluster(listOf(Stops.CABANILLAS)),
                        StopCluster(
                            listOf(
                                Stops.TORRECABALLEROS,
                                Stops.TORRECABALLEROS_2,
                                Stops.TORRECABALLEROS_3
                            )
                        ),
                    )
                )
                val reversed = regular.reversed()

                val extended = Route(
                    clusters = listOf(
                        StopCluster(
                            listOf(
                                Stops.ANDRES_LAGUNA,
                                Stops.LA_PISTA,
                                Stops.HERMANITAS,
                                Stops.AZOGUEJO,
                                Stops.DELICIAS,
                                Stops.MONTECORREDORES
                            )
                        ),
                    ) + regular.clusters.drop(1)
                )
                val extendedReversed = extended.reversed()

                val circular = Route(
                    clusters = listOf(
                        StopCluster(
                            listOf(
                                Stops.ESTACION_BUS,
                                Stops.ANDRES_LAGUNA,
                                Stops.LA_PISTA,
                                Stops.PLAZA_TOROS
                            )
                        ),
                        StopCluster(listOf(Stops.PALAZUELOS, Stops.PALAZUELOS_COLEGIO)),
                        StopCluster(listOf(Stops.TABANERA, Stops.TABANERA_2)),
                        // SANCRIS anchor removed on this variant; IGLESIA serves as anchor
                        StopCluster(listOf(Stops.SANCRIS_IGLESIA, Stops.SANCRIS_ROTONDA)),
                        StopCluster(listOf(Stops.SONSOTO, Stops.SONSOTO_2)),
                        StopCluster(listOf(Stops.TRESCASAS, Stops.TRESCASAS_2)),
                        StopCluster(listOf(Stops.CABANILLAS)),
                        StopCluster(
                            listOf(
                                Stops.TORRECABALLEROS,
                                Stops.TORRECABALLEROS_2,
                                Stops.TORRECABALLEROS_3
                            )
                        ),
                        StopCluster(listOf(Stops.DELICIAS, Stops.AZOGUEJO)),
                    )
                )
            }

            object Saturday {
                val regular = Weekday.circular
                    .copy(id = UUID.randomUUID(), clusters = Weekday.circular.clusters.dropLast(1))

                // Same as busStation but the return leg adds Jardinillos after Azoguejo
                val reversed = Route(
                    clusters = regular.clusters.dropLast(1) + listOf(
                        StopCluster(listOf(Stops.AZOGUEJO, Stops.JARDINILLOS))
                    ),
                    alignment = ClusterAlignment.FROM_END
                )
            }

            object Sunday {
                val regular = Weekday.circular
                    .copy(id = UUID.randomUUID(), clusters = Weekday.circular.clusters.dropLast(1))

                val reversed = regular.reversed()
            }
        }

    }

    override fun canParse(routeId: String): Boolean {
        return capabilities.supportedRoutes.any { it.equals(routeId, ignoreCase = true) }
    }

    override fun parse(pdfPath: String, routeId: String): List<BusTimetable> {
        DebugConfig.debugPrint("M6Parser: Starting parsing for $pdfPath")

        val file = File(pdfPath)
        if (!file.exists()) {
            throw PDFParsingException("PDF file not found: $pdfPath")
        }

        val pdfReader = PdfReader(file)
        val pdfDocument = PdfDocument(pdfReader)

        try {
            val allLines = mutableListOf<String>()

            for (pageNum in 1..pdfDocument.numberOfPages) {
                DebugConfig.debugPrint("M6Parser: ===== PAGE $pageNum =====")
                val page = pdfDocument.getPage(pageNum)

                val extractedText = PDFTextDecoder.extractText(page, tag = "M6Parser")

                val lines = extractedText.lines()
                DebugConfig.debugPrint("M6Parser: Page $pageNum has ${lines.size} lines")

                lines.forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        DebugConfig.debugPrint("  Line $index: $line")
                    }
                }

                allLines.addAll(lines)
            }

            DebugConfig.debugPrint("M6Parser: ===== END OF PDF =====")

            return parseM6Timetables(allLines)

        } catch (e: Exception) {
            DebugConfig.debugError("M6Parser: Error parsing PDF", e)
            throw PDFParsingException("Failed to parse M6 PDF: ${e.message}", e)
        } finally {
            // Always close PDF resources, even on exception
            pdfDocument.close()
        }
    }

    /**
     * Pre-sort consecutive time-containing lines that the PDF returned in reversed order.
     *
     * The parser expects regular-direction lines before their corresponding return-direction
     * lines. A regular journey always departs earlier, so its first extracted time is smaller.
     * When two adjacent time-containing lines arrive with the first line's earliest time
     * *after* the second line's earliest time, they are swapped.
     */
    private fun reorderSwappedLines(lines: List<String>): List<String> =
        lines.fold(Pair(emptyList<String>(), null as String?)) { (result, pending), line ->
            when {
                pending == null && TimetableParserUtils.hasTimes(line) ->
                    Pair(result, line)

                pending == null ->
                    Pair(result + line, null)

                TimetableParserUtils.hasTimes(line) &&
                        TimetableParserUtils.extractTimes(pending).firstOrNull()
                            ?.isAfter(
                                TimetableParserUtils.extractTimes(line).firstOrNull()
                                    ?: LocalTime.MAX
                            ) == true -> {
                    DebugConfig.debugPrint(
                        "M6Parser: Reordering swapped lines:" +
                                "\n  was: $pending" +
                                "\n  now: $line"
                    )
                    Pair(result + line + pending, null)
                }

                TimetableParserUtils.hasTimes(line) ->
                    Pair(result + pending, line)

                else ->
                    Pair(result + pending + line, null)
            }
        }.let { (result, pending) ->
            if (pending != null) result + pending else result
        }

    private fun parseM6Timetables(lines: List<String>): List<BusTimetable> {
        DebugConfig.debugPrint("M6Parser: Starting timetable parsing for ${lines.size} lines")

        val reorderedLines = reorderSwappedLines(lines)
        val timetables = parseTimeTable(reorderedLines)

        DebugConfig.debugPrint("M6Parser: Parsed ${timetables.size} timetables")
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
                stopId = stop.id,
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
                            Routes.Weekday.circular.id

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
                DebugConfig.debugError("M6Parser: Unexpected DayType in determineParsingTarget: ${state.section}", null)
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

    private fun detectDayType(line: String): DayType? = TimetableParserUtils.detectDayType(line)

    /**
     * Update timetables with new departure times using the cluster structure.
     * The first stop in each cluster (anchor) receives the PDF time directly.
     * Subsequent stops in the cluster receive anchor time + 5 min per position.
     *
     * Supports partial journeys: when times.size < route.clusters.size, the active
     * clusters are selected from the near end (alignment = FROM_START skips trailing
     * clusters; FROM_END skips leading clusters). Inactive stops are left unchanged.
     */
    private fun updateTimetables(
        route: Route,
        timetables: List<BusTimetable>,
        times: List<LocalTime>,
        variantLabel: String? = null,
        seasonalAvailability: SeasonalAvailability = SeasonalAvailability.YEAR_ROUND
    ): List<BusTimetable> {
        val activeClusters = when (route.alignment) {
            ClusterAlignment.FROM_START -> route.clusters.take(times.size)
            ClusterAlignment.FROM_END -> route.clusters.takeLast(times.size)
        }
        val activeStopCount = activeClusters.sumOf { it.stops.size }

        val (inactive, active) = when (route.alignment) {
            ClusterAlignment.FROM_START ->
                emptyList<BusTimetable>() to timetables.take(activeStopCount)
            ClusterAlignment.FROM_END ->
                timetables.dropLast(activeStopCount) to timetables.takeLast(activeStopCount)
        }

        val updated = activeClusters.zip(times).fold(
            Pair(emptyList<BusTimetable>(), active)
        ) { (result, remaining), (cluster, anchorTime) ->
            val updatedCluster = remaining.take(cluster.stops.size)
                .mapIndexed { posInCluster, timetable ->
                    val totalMinutes = anchorTime.hour * 60 + anchorTime.minute + posInCluster * ESTIMATED_MINUTES_PER_CLUSTER_STOP
                    timetable.copy(
                        departures = timetable.departures + DepartureTime(
                            totalMinutes / 60 % 24,
                            totalMinutes % 60,
                            variantLabel = variantLabel,
                            seasonalAvailability = seasonalAvailability
                        )
                    )
                }
            Pair(result + updatedCluster, remaining.drop(cluster.stops.size))
        }.first

        return when (route.alignment) {
            ClusterAlignment.FROM_START -> updated + timetables.drop(activeStopCount)
            ClusterAlignment.FROM_END -> inactive + updated
        }
    }

    private fun isFootnote(line: String): Boolean = FOOTNOTE_PATTERN.containsMatchIn(line)

    private fun ParsingState.processAnnotation(modifier: TimeModifier?): ParsingState =
        modifier?.let { this.copy(currentAnnotation = it) } ?: this

    /**
     * Circular route lines (marked with # or **) contain 16 times: 8 outbound
     * followed by 8 mirrored return-column times. The bus actually makes a single
     * journey (Segovia → Torrecaballeros → Delicias/Azoguejo), so we keep only
     * the outbound half and append an estimated arrival at the final cluster.
     */
    private fun preprocessCircularLine(
        annotatedTimes: List<AnnotatedTime>
    ): List<AnnotatedTime> {
        val isCircularLine = annotatedTimes.any {
            it.modifier == TimeModifier.POUND ||
            it.modifier == TimeModifier.DOUBLE_ASTERISK
        }
        if (!isCircularLine) return annotatedTimes

        val outboundEnd = annotatedTimes.indices.drop(1).firstOrNull { i ->
            !annotatedTimes[i].time.isAfter(annotatedTimes[i - 1].time)
        } ?: annotatedTimes.size
        val outbound = annotatedTimes.take(outboundEnd)
        val estimatedArrival = AnnotatedTime(
            time = outbound.last().time.plusMinutes(ESTIMATED_TORRECAB_TO_DELICIAS_MINUTES),
            modifier = outbound.last().modifier
        )
        return outbound + estimatedArrival
    }

    private fun routeLabel(id: UUID): String? = when (id) {
        Routes.Weekday.regular.id, Routes.Weekday.reversed.id -> "Regular"
        Routes.Weekday.extended.id, Routes.Weekday.extendedReversed.id -> "Extendida"
        Routes.Weekday.circular.id -> "Circular"
        Routes.Saturday.regular.id -> "Sábado"
        Routes.Saturday.reversed.id -> "Sábado"
        Routes.Sunday.regular.id -> "Domingo"
        Routes.Sunday.reversed.id -> "Domingo"
        else -> null
    }

    private fun parseTimeTable(lines: List<String>): List<BusTimetable> {
        val routeById: Map<UUID, Route> = listOf(
            Routes.Weekday.regular,
            Routes.Weekday.reversed,
            Routes.Weekday.extended,
            Routes.Weekday.extendedReversed,
            Routes.Weekday.circular,
            Routes.Saturday.regular,
            Routes.Saturday.reversed,
            Routes.Sunday.regular,
            Routes.Sunday.reversed,
        ).associateBy { it.id }

        val initialState = ParsingState(
            journeyBuilder = emptyList(),

            routes = mapOf(
                Routes.Weekday.regular.id to createInitialTimetables(
                    Routes.Weekday.regular,
                    DayType.WEEKDAY,
                    direction = DIRECTION_OUTBOUND
                ),
                Routes.Weekday.reversed.id to createInitialTimetables(
                    Routes.Weekday.reversed,
                    DayType.WEEKDAY,
                    direction = DIRECTION_INBOUND
                ),
                Routes.Weekday.extended.id to createInitialTimetables(
                    Routes.Weekday.extended,
                    DayType.WEEKDAY,
                    direction = DIRECTION_OUTBOUND
                ),
                Routes.Weekday.extendedReversed.id to createInitialTimetables(
                    Routes.Weekday.extendedReversed,
                    DayType.WEEKDAY,
                    direction = DIRECTION_INBOUND
                ),
                Routes.Weekday.circular.id to createInitialTimetables(
                    Routes.Weekday.circular,
                    DayType.WEEKDAY,
                    direction = DIRECTION_OUTBOUND
                ),
                Routes.Saturday.regular.id to createInitialTimetables(
                    Routes.Saturday.regular,
                    DayType.SATURDAY,
                    direction = DIRECTION_OUTBOUND
                ),
                Routes.Saturday.reversed.id to createInitialTimetables(
                    Routes.Saturday.reversed,
                    DayType.SATURDAY,
                    direction = DIRECTION_INBOUND
                ),
                Routes.Sunday.regular.id to createInitialTimetables(
                    Routes.Sunday.regular,
                    DayType.SUNDAY,
                    direction = DIRECTION_OUTBOUND
                ),
                Routes.Sunday.reversed.id to createInitialTimetables(
                    Routes.Sunday.reversed,
                    DayType.SUNDAY,
                    direction = DIRECTION_INBOUND
                ),
            ),
        )

        fun flush(state: ParsingState, times: List<LocalTime>): ParsingState {
            val target = determineParsingTarget(state)
            val route = routeById[target]!!
            val label = routeLabel(target)

            val isSundaySeasonal = state.section == DayType.SUNDAY
                    && state.currentAnnotation == TimeModifier.TRIPLE_ASTERISK

            val seasonal = if (isSundaySeasonal) {
                // *** journeys come in pairs per direction (outbound/return).
                // The first (earlier departure) is summer-only, the second is school-only.
                if (state.isReversed !in state.sundaySeasonalFirstSeen)
                    SeasonalAvailability.SUMMER_ONLY
                else
                    SeasonalAvailability.SCHOOL_ONLY
            } else {
                SeasonalAvailability.YEAR_ROUND
            }

            val updatedTimetables = updateTimetables(
                route = route,
                timetables = state.routes[target]!!,
                times = times,
                variantLabel = label,
                seasonalAvailability = seasonal
            )

            val newSeasonalSeen = if (isSundaySeasonal)
                state.sundaySeasonalFirstSeen + state.isReversed
            else
                state.sundaySeasonalFirstSeen

            return updateState(
                updatedTimetables = updatedTimetables,
                target = target,
                state = state
            ).copy(sundaySeasonalFirstSeen = newSeasonalSeen)
        }

        val newState = lines.fold(initialState) { state, line ->
            when {
                isFootnote(line) -> state

                TimetableParserUtils.hasTimes(line) -> {
                    val annotatedTimes = TimetableParserUtils.extractAnnotatedTimes(line)
                    val processedTimes = preprocessCircularLine(annotatedTimes)

                    processedTimes.fold(
                        Triple(state, emptyList<LocalTime>(), false) // (state, builder, hasFlipped)
                    ) { (state, inlineBuilder, hasFlipped), annotatedTime ->
                        val stateWithAnnotation = state.processAnnotation(annotatedTime.modifier)
                        val parsingTarget = determineParsingTarget(stateWithAnnotation)

                        // A backwards-or-equal step signals a direction change, but only once per
                        // line. hasFlipped guards against a second trigger caused by non-monotonic
                        // return-journey data (e.g. a PDF quirk like 14:35 14:30 14:35 14:50 in
                        // the return leg of line 11).
                        //
                        // Backwards jump → flush previous direction as a partial/full journey;
                        //   the current time carries forward into the new direction's builder.
                        //   flushState = state (pre-annotation) so the new annotation belongs
                        //   to the incoming direction, not the one being flushed.
                        //
                        // Buffer full → flush the complete journey including the current time.
                        //   flushState = stateWithAnnotation so the annotation is consumed here.
                        //
                        val isDirectionChange = !hasFlipped &&
                                inlineBuilder.isNotEmpty() &&
                                !annotatedTime.time.isAfter(inlineBuilder.last())

                        val (toFlush, flushState, nextBuilder) = when {
                            isDirectionChange -> {
                                // PDF outbound columns (left) are always extracted first.
                                // When a backwards jump splits the line, the accumulated
                                // times are always outbound → force isReversed=false.
                                val outboundState = state.copy(isReversed = false)
                                Triple(inlineBuilder, outboundState, listOf(annotatedTime.time))
                            }
                            (inlineBuilder + annotatedTime.time).size == routeById[parsingTarget]!!.clusters.size ->
                                Triple(inlineBuilder + annotatedTime.time, stateWithAnnotation, emptyList())
                            else ->
                                Triple(emptyList(), stateWithAnnotation, inlineBuilder + annotatedTime.time)
                        }

                        val newState = if (toFlush.isNotEmpty()) {
                            val flushed = flush(flushState, toFlush)
                            // Direction-change flush: annotation on the current time belongs to
                            // the incoming direction, not the one just flushed — carry it forward.
                            if (nextBuilder.isNotEmpty()) flushed.processAnnotation(annotatedTime.modifier) else flushed
                        } else stateWithAnnotation

                        Triple(newState, nextBuilder, hasFlipped || isDirectionChange)
                    }.let { (lineState, remainingBuilder, _) ->
                        // Flush any partial journey left at the end of the line.
                        // Handles routes where the partial journey has no following time on the
                        // same line to trigger the backwards-jump detection.
                        if (remainingBuilder.isNotEmpty()) {
                            flush(lineState, remainingBuilder)
                        } else lineState
                    }
                }

                else -> detectDayType(line)
                    ?.takeIf { it == DayType.SATURDAY || it == DayType.SUNDAY }
                    ?.let { state.copy(section = it, isReversed = false) }
                    ?: state
            }
        }

        return newState.routes.values.flatten()
    }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        if (!routeId.equals("M6", ignoreCase = true)) return emptyList()
        return getRouteVariants(routeId, DayType.WEEKDAY).map { it.stops }
    }

    override fun getRouteViews(routeId: String, dayType: DayType): List<RouteView>? {
        if (!routeId.equals("M6", ignoreCase = true)) return null

        val extendedOnlyIds = setOf(
            Stops.ANDRES_LAGUNA.id, Stops.LA_PISTA.id, Stops.HERMANITAS.id
        )

        fun extendedStops(route: Route): List<RouteViewStop> = route.stops.map { stop ->
            RouteViewStop(stop, isExtendedOnly = stop.id in extendedOnlyIds)
        }

        fun plainStops(route: Route): List<RouteViewStop> = route.stops.map { RouteViewStop(it) }

        return when (dayType) {
            DayType.WEEKDAY -> {
                val tabs = listOf(
                    RouteTab("Regular", "weekday-unified"),
                    RouteTab("Circular", "weekday-circular")
                )
                listOf(
                    RouteView(
                        id = "weekday-unified",
                        label = "Segovia → Torrecaballeros",
                        stops = extendedStops(Routes.Weekday.extended),
                        direction = DIRECTION_OUTBOUND,
                        departureLabel = null,
                        swapAction = SwapAction("weekday-unified-reversed"),
                        tabs = tabs,
                        extendedSectionLabel = "Ruta extendida"
                    ),
                    RouteView(
                        id = "weekday-unified-reversed",
                        label = "Torrecaballeros → Segovia",
                        stops = extendedStops(Routes.Weekday.extendedReversed),
                        direction = DIRECTION_INBOUND,
                        departureLabel = null,
                        swapAction = SwapAction("weekday-unified"),
                        tabs = tabs,
                        extendedSectionLabel = "Ruta extendida"
                    ),
                    RouteView(
                        id = "weekday-circular",
                        label = "Circular",
                        stops = plainStops(Routes.Weekday.circular),
                        direction = DIRECTION_OUTBOUND,
                        departureLabel = "Circular",
                        tabs = tabs
                    )
                )
            }

            DayType.SATURDAY -> listOf(
                RouteView(
                    id = "saturday-regular",
                    label = "Segovia → Torrecaballeros",
                    stops = plainStops(Routes.Saturday.regular),
                    direction = DIRECTION_OUTBOUND,
                    departureLabel = "Sábado",
                    swapAction = SwapAction("saturday-reversed")
                ),
                RouteView(
                    id = "saturday-reversed",
                    label = "Torrecaballeros → Segovia",
                    stops = plainStops(Routes.Saturday.reversed),
                    direction = DIRECTION_INBOUND,
                    departureLabel = "Sábado",
                    swapAction = SwapAction("saturday-regular")
                )
            )

            DayType.SUNDAY -> listOf(
                RouteView(
                    id = "sunday-regular",
                    label = "Segovia → Torrecaballeros",
                    stops = plainStops(Routes.Sunday.regular),
                    direction = DIRECTION_OUTBOUND,
                    departureLabel = "Domingo",
                    swapAction = SwapAction("sunday-reversed")
                ),
                RouteView(
                    id = "sunday-reversed",
                    label = "Torrecaballeros → Segovia",
                    stops = plainStops(Routes.Sunday.reversed),
                    direction = DIRECTION_INBOUND,
                    departureLabel = "Domingo",
                    swapAction = SwapAction("sunday-regular")
                )
            )

            else -> getRouteViews(routeId, DayType.WEEKDAY)
        }
    }

    override fun getRouteEntries(routeId: String, today: java.util.Date): List<RouteSelectorEntry> {
        if (!routeId.equals("M6", ignoreCase = true)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { time = today }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekday  = dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
        val isSaturday = dow == java.util.Calendar.SATURDAY
        val isSunday   = dow == java.util.Calendar.SUNDAY
        val weekdayViews  = getRouteViews(routeId, DayType.WEEKDAY)!!
        val saturdayViews = getRouteViews(routeId, DayType.SATURDAY)!!
        val sundayViews   = getRouteViews(routeId, DayType.SUNDAY)!!
        return listOf(
            RouteSelectorEntry("entry-lv-regular",  "L-V - Regular",  weekdayViews,  "weekday-unified",  DayType.WEEKDAY,  isWeekday),
            RouteSelectorEntry("entry-lv-circular", "L-V - Circular", weekdayViews,  "weekday-circular", DayType.WEEKDAY,  isWeekday),
            RouteSelectorEntry("entry-sabado",       "Sábado",         saturdayViews, "saturday-regular", DayType.SATURDAY, isSaturday),
            RouteSelectorEntry("entry-domingo",      "Domingo",        sundayViews,   "sunday-regular",   DayType.SUNDAY,   isSunday)
        )
    }

    override fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        val views = getRouteViews(routeId, dayType) ?: return emptyList()
        return views.map { view ->
            RouteVariant(
                id = view.id,
                label = view.label,
                stops = view.stops.map { it.stop },
                direction = view.direction,
                departureLabel = view.departureLabel
            )
        }
    }
}
