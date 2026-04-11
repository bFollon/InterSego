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

package com.github.bfollon.intersego.services

import android.content.Context
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteVariant
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.data.RouteViewStop
import com.github.bfollon.intersego.data.SwapAction
import com.github.bfollon.intersego.repositories.PDFURLRepository
import com.github.bfollon.intersego.services.pdfparsing.BusTimetableParser
import com.github.bfollon.intersego.services.pdfparsing.CapableParser
import com.github.bfollon.intersego.services.pdfparsing.ParserMode
import com.github.bfollon.intersego.services.pdfparsing.PDFParsingException
import com.github.bfollon.intersego.services.pdfparsing.RouteStopsProvider
import com.github.bfollon.intersego.services.pdfparsing.strategies.M1Parser
import com.github.bfollon.intersego.services.pdfparsing.strategies.M2Parser
import com.github.bfollon.intersego.services.pdfparsing.strategies.M3Parser
import com.github.bfollon.intersego.services.pdfparsing.strategies.M4Parser
import com.github.bfollon.intersego.services.pdfparsing.strategies.M5Parser
import com.github.bfollon.intersego.services.pdfparsing.strategies.M6Parser
import com.github.bfollon.intersego.services.pdfparsing.strategies.M7Parser
import com.github.bfollon.intersego.services.pdfparsing.strategies.M8Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinator service for PDF processing
 *
 * Responsibilities:
 * - Register PDF parsing strategies for different routes
 * - Download PDFs using PDFDownloadService
 * - Parse PDFs using the appropriate strategy
 * - Return parsed timetables
 *
 * Uses Strategy Pattern to handle different PDF layouts per route.
 */
class PDFProcessingService(private val context: Context) {

    private val pdfCacheManager = PDFCacheManager.getInstance(context)
    private val pdfUrlRepository = PDFURLRepository.getInstance(context)

    // Map of route IDs to their parsing strategies
    private val parsers = mutableMapOf<String, BusTimetableParser>()

    // Reverse index: stopId → list of routeIds that serve that stop
    private val stopToRoutes: Map<String, List<String>> by lazy { buildStopToRoutes() }

    init {
        // Register all available parsers
        registerParser(M1Parser())
        registerParser(M2Parser())
        registerParser(M3Parser())
        registerParser(M4Parser())
        registerParser(M5Parser())
        registerParser(M6Parser())
        registerParser(M7Parser())
        registerParser(M8Parser())

        DebugConfig.debugPrint("PDFProcessingService: Initialized with ${parsers.size} parsers")
    }

    private fun buildStopToRoutes(): Map<String, List<String>> {
        val index = mutableMapOf<String, MutableList<String>>()
        parsers.forEach { (routeId, parser) ->
            if (parser is RouteStopsProvider) {
                parser.getRoutesForId(routeId).flatten().forEach { stop ->
                    index.getOrPut(stop.id) { mutableListOf() }.let {
                        if (!it.contains(routeId)) it.add(routeId)
                    }
                }
            }
        }
        return index
    }

    /**
     * Register a parser strategy
     *
     * If parser implements CapableParser, it automatically registers for all
     * routes declared in its capabilities. Otherwise, falls back to legacy
     * manual registration.
     */
    private fun registerParser(parser: BusTimetableParser) {
        if (parser is CapableParser) {
            // Dynamic registration based on capabilities
            parser.capabilities.supportedRoutes.forEach { routeId ->
                parsers[routeId] = parser
                val modeLabel = when (parser.capabilities.mode) {
                    ParserMode.DEBUG -> "DEBUG"
                    ParserMode.PRODUCTION -> "PRODUCTION"
                }
                DebugConfig.debugPrint("PDFProcessingService: Registered $modeLabel parser for $routeId")
            }
        } else {
            // Legacy parser - manual registration fallback
            DebugConfig.debugWarn("PDFProcessingService: Parser ${parser::class.simpleName} does not implement CapableParser")
        }
    }

    /**
     * Parse timetables for a route
     *
     * @param routeId Route ID to parse
     * @return List of timetables parsed from the PDF
     * @throws PDFParsingException If parsing fails
     */
    suspend fun parseTimetables(routeId: String): List<BusTimetable> = withContext(Dispatchers.IO) {
        DebugConfig.debugPrint("PDFProcessingService: Parsing timetables for route $routeId")

        // Find the appropriate parser
        val parser = parsers[routeId]
        if (parser == null) {
            DebugConfig.debugError("PDFProcessingService: No parser found for route $routeId", null)
            throw PDFParsingException("No parser available for route $routeId")
        }

        // Get the PDF URL
        val pdfUrl = pdfUrlRepository.getURL(routeId)
        if (pdfUrl.isEmpty()) {
            DebugConfig.debugError("PDFProcessingService: No URL found for route $routeId", null)
            throw PDFParsingException("No PDF URL available for route $routeId")
        }

        DebugConfig.debugPrint("PDFProcessingService: Getting effective PDF (cached or download if needed)")

        // Get effective PDF file (uses version-aware caching with automatic update detection)
        val pdfFile = pdfCacheManager.getEffectivePDFFile(routeId, pdfUrl)
        if (pdfFile == null) {
            DebugConfig.debugError("PDFProcessingService: Failed to get PDF for $routeId", null)
            AnalyticsService.track("pdf_download_failed", mapOf("route" to routeId, "url" to pdfUrl))
            ErrorReportingService.captureMessage("PDF download failed for route $routeId — url=$pdfUrl")
            throw PDFParsingException("Failed to get PDF for route $routeId")
        }

        DebugConfig.debugPrint("PDFProcessingService: Using PDF at ${pdfFile.absolutePath}")

        // Parse the PDF
        try {
            val timetables = parser.parse(pdfFile.absolutePath, routeId)
            DebugConfig.debugPrint("PDFProcessingService: Successfully parsed ${timetables.size} timetables")
            return@withContext timetables
        } catch (e: PDFParsingException) {
            DebugConfig.debugError("PDFProcessingService: Parsing failed", e)
            AnalyticsService.track("pdf_parse_failed", mapOf("route" to routeId, "url" to pdfUrl, "file" to pdfFile.absolutePath))
            ErrorReportingService.captureError(e, mapOf("route" to routeId, "url" to pdfUrl, "file" to pdfFile.absolutePath))
            throw e
        }
    }

    /**
     * Check if a parser is available for a route
     */
    fun hasParserFor(routeId: String): Boolean = parsers.containsKey(routeId)

    /**
     * Get list of routes that have parsers
     */
    fun getSupportedRoutes(): List<String> {
        return parsers.keys.toList()
    }

    /**
     * Get all route IDs that serve a given stop.
     *
     * @param stopId Canonical stop ID (from BusStopRegistry)
     * @return Sorted list of route IDs (e.g. ["M6", "M7"])
     */
    fun getRoutesForStop(stopId: String): List<String> =
        stopToRoutes[stopId]?.sorted() ?: emptyList()

    /**
     * Get the operating mode of a parser for a route
     *
     * @param routeId Route ID to query
     * @return ParserMode if parser exists and is CapableParser, null otherwise
     */
    fun getParserMode(routeId: String): ParserMode? {
        return (parsers[routeId] as? CapableParser)?.capabilities?.mode
    }

    /**
     * Check if a parser is in DEBUG mode
     *
     * @param routeId Route ID to check
     * @return true if parser is in DEBUG mode, false otherwise
     */
    fun isDebugParser(routeId: String): Boolean {
        return getParserMode(routeId) == ParserMode.DEBUG
    }

    /**
     * Get the version string of a parser for a route
     *
     * @param routeId Route ID to query
     * @return Version string if parser exists and is CapableParser, null otherwise
     */
    fun getParserVersion(routeId: String): String? {
        return (parsers[routeId] as? CapableParser)?.capabilities?.version
    }

    /**
     * Get route stop definitions from parser
     *
     * @param routeId Route ID to query
     * @return List of route variations (regular, reverse), each containing BusStop list
     */
    fun getRoutesForNavigation(routeId: String): List<List<BusStop>> {
        val parser = parsers[routeId]
        return if (parser is RouteStopsProvider) {
            parser.getRoutesForId(routeId)
        } else {
            emptyList()
        }
    }

    /**
     * Get route variants for a given route ID and day type.
     *
     * @param routeId Route ID to query
     * @param dayType Day type to filter variants for
     * @return List of RouteVariant for display in route selector
     */
    fun getRouteVariants(routeId: String, dayType: DayType): List<RouteVariant> {
        val parser = parsers[routeId]
        return if (parser is RouteStopsProvider) {
            parser.getRouteVariants(routeId, dayType)
        } else {
            emptyList()
        }
    }

    /**
     * Get rich route views for display.
     *
     * If the parser provides RouteView objects directly, uses those.
     * Otherwise, converts RouteVariant objects into plain RouteView objects
     * with a swap action between the first two variants.
     */
    /**
     * Get the flat list of selectable route entries.
     * Every supported route returns at least one entry.
     */
    fun getRouteEntries(routeId: String, today: java.util.Date = java.util.Date()): List<RouteSelectorEntry> {
        val parser = parsers[routeId]
        return if (parser is RouteStopsProvider) parser.getRouteEntries(routeId, today) else emptyList()
    }

    fun getRouteViews(routeId: String, dayType: DayType): List<RouteView> {
        val parser = parsers[routeId]
        if (parser is RouteStopsProvider) {
            val views = parser.getRouteViews(routeId, dayType)
            if (views != null) return views
        }

        // Fallback: convert RouteVariant to RouteView
        val variants = getRouteVariants(routeId, dayType)
        return variants.mapIndexed { index, variant ->
            val swapTargetId = if (variants.size == 2) variants[1 - index].id else null
            RouteView(
                id = variant.id,
                label = variant.label,
                stops = variant.stops.map { RouteViewStop(it) },
                direction = variant.direction,
                departureLabel = variant.departureLabel,
                swapAction = swapTargetId?.let { SwapAction(it) }
            )
        }
    }
}
