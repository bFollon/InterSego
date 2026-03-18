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

/// Parser for M1 route (TODO: add route name once confirmed from PDF)
///
/// PDF Structure:
/// - TODO: Document structure after analysing the extracted PDF lines
///
/// Status: DEBUG skeleton — prints extracted lines for analysis, returns no timetables.
class M1Parser: CapableParser, RouteStopsProvider {

    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M1"]),
        mode: .debug,
        version: "0.1"
    )

    // MARK: - Directions

    // TODO: Fill in direction labels once confirmed from PDF
    private static let directionRegular = "TODO: Regular direction"
    private static let directionReverse = "TODO: Reverse direction"

    // MARK: - Stops

    // TODO: Define BusStop instances once stop names and coordinates are gathered
    private enum Stops {
        // Example:
        // static let stopName = BusStop(
        //     name: "Stop Name",
        //     area: "Area Name",
        //     coordinates: "40.000000, -4.000000"
        // )
    }

    // TODO: Fill in stop lists once stops are defined above
    static let m1RegularRoute: [BusStop] = []
    static let m1ReverseRoute: [BusStop] = []

    // MARK: - Parsing State

    private struct ParsingState {
        var currentDayType: DayType
        var regularRouteWeekdayTimetables: [BusTimetable]
        var regularRouteWeekendTimetables: [BusTimetable]
        var reverseRouteWeekdayTimetables: [BusTimetable]
        var reverseRouteWeekendTimetables: [BusTimetable]
    }

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains { $0.caseInsensitiveCompare(routeId) == .orderedSame }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else { return [] }
        return [Self.m1RegularRoute, Self.m1ReverseRoute]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else { return [] }
        return [
            RouteVariant(
                id: "regular",
                label: Self.directionRegular,
                stops: Self.m1RegularRoute,
                direction: Self.directionRegular
            ),
            RouteVariant(
                id: "reverse",
                label: Self.directionReverse,
                stops: Self.m1ReverseRoute,
                direction: Self.directionReverse
            ),
        ]
    }

    func parse(pdfPath: String, routeId: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M1Parser: Starting PDF parsing for \(pdfPath)")

        guard FileManager.default.fileExists(atPath: pdfPath) else {
            throw PDFParsingError("PDF file not found: \(pdfPath)")
        }

        let extractedText = PDFTextExtractor.extractText(from: pdfPath, tag: "M1Parser")
        let lines = extractedText.components(separatedBy: "\n")

        DebugConfig.debugPrint("M1Parser: Extracted \(lines.count) lines")

        DebugConfig.debugPrint("M1Parser: ===== EXTRACTED TEXT =====")
        for (index, line) in lines.enumerated() {
            if !line.isEmpty {
                DebugConfig.debugPrint("  Line \(index): \(line)")
            }
        }
        DebugConfig.debugPrint("M1Parser: ==========================")

        let timetables = parseTimeTable(lines)

        DebugConfig.debugPrint("M1Parser: Finished parsing, created \(timetables.count) timetables")
        return timetables
    }

    // MARK: - Internal Parsing

    private func createInitialTimetables(stops: [BusStop], dayType: DayType, direction: String) -> [BusTimetable] {
        stops.map { stop in
            BusTimetable(
                routeId: "M1",
                stopId: stop.name,
                dayType: dayType,
                departures: [],
                direction: direction
            )
        }
    }

    private func updateTimetables(
        _ timetables: [BusTimetable],
        times: [(hour: Int, minute: Int)],
        seasonal: SeasonalAvailability
    ) -> [BusTimetable] {
        zip(timetables, times).map { (timetable, time) in
            var updated = timetable
            updated.departures = timetable.departures + [
                DepartureTime(hour: time.hour, minute: time.minute, seasonalAvailability: seasonal)
            ]
            return updated
        }
    }

    private func parseTimeTable(_ lines: [String]) -> [BusTimetable] {
        // TODO: Implement parsing logic once PDF structure is understood from the debug output above.
        // Use M4Parser as a reference for a simple line-per-journey approach.
        //
        // Suggested steps:
        // 1. Run the app with DEBUG mode and check logs for "M1Parser" tag
        // 2. Identify day type headers (LUNES A VIERNES, SÁBADOS, DOMINGOS)
        // 3. Identify time rows and how many times per row (= number of stops)
        // 4. Map the times per row to stop positions
        // 5. Fill in Stops above and m1RegularRoute / m1ReverseRoute
        // 6. Implement the state machine below (modelled on M4Parser)
        // 7. Change mode to .production when complete

        guard !Self.m1RegularRoute.isEmpty else {
            DebugConfig.debugPrint("M1Parser: Stops not yet defined — returning empty timetables (DEBUG skeleton)")
            return []
        }

        var state = ParsingState(
            currentDayType: .weekday,
            regularRouteWeekdayTimetables: createInitialTimetables(stops: Self.m1RegularRoute, dayType: .weekday, direction: Self.directionRegular),
            regularRouteWeekendTimetables: createInitialTimetables(stops: Self.m1RegularRoute, dayType: .weekend, direction: Self.directionRegular),
            reverseRouteWeekdayTimetables: createInitialTimetables(stops: Self.m1ReverseRoute, dayType: .weekday, direction: Self.directionReverse),
            reverseRouteWeekendTimetables: createInitialTimetables(stops: Self.m1ReverseRoute, dayType: .weekend, direction: Self.directionReverse)
        )

        for line in lines {
            if let newDayType = TimetableParserUtils.detectDayType(line) {
                state.currentDayType = newDayType
            } else if TimetableParserUtils.hasTimes(line) {
                // TODO: handle time rows here
            }
        }

        return state.regularRouteWeekdayTimetables +
            state.regularRouteWeekendTimetables +
            state.reverseRouteWeekdayTimetables +
            state.reverseRouteWeekendTimetables
    }
}
