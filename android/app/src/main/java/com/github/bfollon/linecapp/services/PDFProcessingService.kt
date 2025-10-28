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

package com.github.bfollon.linecapp.services

import android.content.Context
import com.github.bfollon.linecapp.data.BusTimetable
import com.github.bfollon.linecapp.repositories.PDFURLRepository
import com.github.bfollon.linecapp.services.pdfparsing.BusTimetableParser
import com.github.bfollon.linecapp.services.pdfparsing.PDFParsingException
import com.github.bfollon.linecapp.services.pdfparsing.strategies.M4Parser
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

    init {
        // Register all available parsers
        registerParser(M4Parser())

        DebugConfig.debugPrint("PDFProcessingService: Initialized with ${parsers.size} parsers")
    }

    /**
     * Register a parser strategy
     */
    private fun registerParser(parser: BusTimetableParser) {
        // Find all routes this parser can handle
        // For now, we'll register based on known route IDs
        // In the future, this could be more dynamic
        if (parser.canParse("M4")) {
            parsers["M4"] = parser
            DebugConfig.debugPrint("PDFProcessingService: Registered parser for M4")
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
            throw e
        }
    }

    /**
     * Check if a parser is available for a route
     */
    fun hasParserFor(routeId: String): Boolean {
        return parsers.containsKey(routeId)
    }

    /**
     * Get list of routes that have parsers
     */
    fun getSupportedRoutes(): List<String> {
        return parsers.keys.toList()
    }
}
