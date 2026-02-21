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
        private object Stops {
            val AZOGUEJO = BusStop(
                name = "Azoguejo",
                address = "Pl. Artillería, 40001 Segovia"
            )
            val DELICIAS = BusStop(
                name = "Delicias",
                address = "",
                coordinates = "40.954500, -4.108889",
                isApproximate = true,
            )
            val MONTECORREDORES = BusStop(
                name = "Montecorredores",
                address = "",
                coordinates = "40.952000, -4.097278",
                isApproximate = true,
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
                isApproximate = true,
            )
            val SANCRIS_ROTONDA = BusStop(
                name = "Rotonda",
                area = "San Cristóbal de segovia",
                address = "",
                coordinates = "40.951224, -4.073449",
                isApproximate = true,
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
                isApproximate = true,
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
                isApproximate = true,
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
                isApproximate = true,
            )
            val TORRECABALLEROS_3 = BusStop(
                name = "Torrecaballeros 3",
                details = "En carretera hacia Turégano",
                address = "",
                coordinates = "40.999144, -4.020855",
                isApproximate = true,
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
                isApproximate = true,
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
                val regularRoute = setOf(
                    Stops.AZOGUEJO,
                    Stops.DELICIAS,
                    Stops.MONTECORREDORES,
                    Stops.SANCRIS,
                    Stops.SANCRIS_IGLESIA,
                    Stops.SANCRIS_ROTONDA,
                    Stops.SONSOTO,
                    Stops.SONSOTO_2,
                    Stops.TRESCASAS,
                    Stops.TRESCASAS_2,
                    Stops.CABANILLAS,
                    Stops.TORRECABALLEROS,
                    Stops.TORRECABALLEROS_2,
                    Stops.TORRECABALLEROS_3,
                )
                val regularReversed = regularRoute.reversed()

                val extendedRoute = setOf(
                    Stops.ANDRES_LAGUNA,
                    Stops.LA_PISTA,
                    Stops.HERMANITAS,
                ) + regularRoute

                val busStationRoute = setOf(
                    Stops.ESTACION_BUS,
                    Stops.ANDRES_LAGUNA,
                    Stops.LA_PISTA,
                    Stops.PLAZA_TOROS,
                    Stops.PALAZUELOS,
                    Stops.PALAZUELOS_COLEGIO,
                    Stops.TABANERA,
                    Stops.TABANERA_2
                ) + regularRoute -
                        Stops.AZOGUEJO -
                        Stops.DELICIAS -
                        Stops.MONTECORREDORES -
                        Stops.SANCRIS +
                        Stops.DELICIAS +
                        Stops.AZOGUEJO

                val extendedRouteReversed = extendedRoute.reversed()
            }

            object Saturday {
                val saturdayRoute = Weekday.busStationRoute

                val saturdayRouteReversed  = saturdayRoute.reversed() + Stops.JARDINILLOS
            }

            object Sunday {
                val sundayRoute = Weekday.busStationRoute

                val sundayRouteReversed = sundayRoute.reversed()
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

    private fun parseTimeTable(lines: List<String>): List<BusTimetable> {
        
    }

    override fun getRoutesForId(routeId: String): List<List<BusStop>> {
        return if (routeId.equals("M6", ignoreCase = true)) {
            listOf(m6Route)
        } else {
            emptyList()
        }
    }
}
