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

import com.github.bfollon.linecapp.data.BusTimetable

/**
 * Interface for bus timetable PDF parsing strategies
 *
 * Each route may have a different PDF layout, so we use the Strategy Pattern
 * to implement route-specific parsers.
 *
 * **Enhanced Pattern with CapableParser:**
 * Parsers can implement [CapableParser] to declare their capabilities (supported routes,
 * operating mode). This enables automatic discovery and registration without hardcoding.
 *
 * @see CapableParser
 * @see RouteStopsProvider
 */
interface BusTimetableParser {

    /**
     * Parse a PDF file and extract bus timetables
     *
     * @param pdfPath Path to the PDF file to parse
     * @param routeId Route ID for the timetable
     * @return List of bus timetables extracted from the PDF
     * @throws PDFParsingException If parsing fails
     */
    fun parse(pdfPath: String, routeId: String): List<BusTimetable>

    /**
     * Check if this parser can handle the given route
     *
     * @param routeId Route ID to check
     * @return true if this parser supports the route
     */
    fun canParse(routeId: String): Boolean
}

/**
 * Exception thrown when PDF parsing fails
 */
class PDFParsingException(message: String, cause: Throwable? = null) : Exception(message, cause)
