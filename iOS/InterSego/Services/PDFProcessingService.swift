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

import Foundation

/// Coordinator service for PDF processing.
///
/// Responsibilities:
/// - Register PDF parsing strategies for different routes
/// - Download PDFs using PDFCacheManager
/// - Parse PDFs using the appropriate strategy
/// - Return parsed timetables
///
/// Uses Strategy Pattern to handle different PDF layouts per route.
actor PDFProcessingService {
    static let shared = PDFProcessingService()

    private var parsers: [String: BusTimetableParser] = [:]

    private init() {
        let allParsers: [BusTimetableParser] = [M1Parser(), M2Parser(), M3Parser(), M4Parser(), M5Parser(), M6Parser(), M7Parser()]
        for parser in allParsers {
            if let capable = parser as? CapableParser {
                for routeId in capable.capabilities.supportedRoutes {
                    parsers[routeId] = parser
                    let modeLabel = capable.capabilities.mode == .debug ? "DEBUG" : "PRODUCTION"
                    DebugConfig.debugPrint("PDFProcessingService: Registered \(modeLabel) parser for \(routeId)")
                }
            }
        }
        DebugConfig.debugPrint("PDFProcessingService: Initialized with \(parsers.count) parsers")
    }

    func parseTimetables(routeId: String) async throws -> [BusTimetable] {
        DebugConfig.debugPrint("PDFProcessingService: Parsing timetables for route \(routeId)")

        guard let parser = parsers[routeId] else {
            DebugConfig.debugError("PDFProcessingService: No parser found for route \(routeId)")
            throw PDFParsingError("No parser available for route \(routeId)")
        }

        guard let pdfUrl = await PDFURLRepository.shared.getURL(routeId: routeId) else {
            DebugConfig.debugError("PDFProcessingService: No URL found for route \(routeId)")
            throw PDFParsingError("No PDF URL available for route \(routeId)")
        }

        DebugConfig.debugPrint("PDFProcessingService: Getting effective PDF (cached or download if needed)")

        guard let pdfFile = await PDFCacheManager.shared.getEffectivePDFFile(routeId: routeId, pdfUrl: pdfUrl) else {
            DebugConfig.debugError("PDFProcessingService: Failed to get PDF for \(routeId)")
            throw PDFParsingError("Failed to get PDF for route \(routeId)")
        }

        DebugConfig.debugPrint("PDFProcessingService: Using PDF at \(pdfFile.path)")

        let timetables = try parser.parse(pdfPath: pdfFile.path, routeId: routeId)
        DebugConfig.debugPrint("PDFProcessingService: Successfully parsed \(timetables.count) timetables")
        return timetables
    }

    func hasParserFor(routeId: String) -> Bool {
        parsers[routeId] != nil
    }

    func getSupportedRoutes() -> [String] {
        Array(parsers.keys)
    }

    func getParserVersion(routeId: String) -> String {
        (parsers[routeId] as? CapableParser)?.capabilities.version ?? "1.0"
    }

    func getParserMode(routeId: String) -> ParserMode? {
        (parsers[routeId] as? CapableParser)?.capabilities.mode
    }

    func isDebugParser(routeId: String) -> Bool {
        getParserMode(routeId: routeId) == .debug
    }

    func getRoutesForNavigation(routeId: String) -> [[BusStop]] {
        guard let parser = parsers[routeId] as? RouteStopsProvider else { return [] }
        return parser.getRoutesForId(routeId)
    }

    func getRouteVariants(routeId: String, dayType: DayType) -> [RouteVariant] {
        guard let parser = parsers[routeId] as? RouteStopsProvider else { return [] }
        return parser.getRouteVariants(routeId, dayType: dayType)
    }

    /// Get the flat list of selectable route entries.
    /// Every supported route returns at least one entry.
    func getRouteEntries(routeId: String, today: Date = Date()) -> [RouteSelectorEntry] {
        guard let parser = parsers[routeId] as? RouteStopsProvider else { return [] }
        return parser.getRouteEntries(routeId, today: today)
    }

    func getRouteViews(routeId: String, dayType: DayType) -> [RouteView] {
        if let parser = parsers[routeId] as? RouteStopsProvider,
           let views = parser.getRouteViews(routeId, dayType: dayType) {
            return views
        }

        let variants = getRouteVariants(routeId: routeId, dayType: dayType)
        return variants.enumerated().map { (index, variant) in
            let swapTargetId = variants.count == 2 ? variants[1 - index].id : nil
            return RouteView(
                id: variant.id,
                label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction,
                departureLabel: variant.departureLabel,
                swapAction: swapTargetId.map { SwapAction(targetViewId: $0) }
            )
        }
    }
}
