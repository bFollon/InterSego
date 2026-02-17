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

package com.github.bfollon.linecapp.services.pdfparsing

/**
 * Parser operating modes
 */
enum class ParserMode {
    /**
     * Debug mode: Prints raw PDF content, returns empty timetables.
     * Used for analyzing PDF structure during development.
     */
    DEBUG,

    /**
     * Production mode: Full parsing with real timetable extraction.
     */
    PRODUCTION
}

/**
 * Metadata about a parser's capabilities
 *
 * @property supportedRoutes Set of route IDs this parser can handle (e.g., "M4", "M6")
 * @property mode Operating mode (DEBUG or PRODUCTION)
 * @property version Parser version for tracking updates
 */
data class ParserCapabilities(
    val supportedRoutes: Set<String>,
    val mode: ParserMode,
    val version: String = "1.0"
)

/**
 * Enhanced parser interface that declares capabilities
 *
 * Parsers implementing this interface can be automatically discovered
 * and registered by PDFProcessingService without hardcoding route IDs.
 *
 * Example:
 * ```
 * class M6Parser : CapableParser {
 *     override val capabilities = ParserCapabilities(
 *         supportedRoutes = setOf("M6"),
 *         mode = ParserMode.DEBUG
 *     )
 *
 *     override fun canParse(routeId: String) = routeId in capabilities.supportedRoutes
 *     override fun parse(...) { /* implementation */ }
 * }
 * ```
 */
interface CapableParser : BusTimetableParser {
    val capabilities: ParserCapabilities
}
