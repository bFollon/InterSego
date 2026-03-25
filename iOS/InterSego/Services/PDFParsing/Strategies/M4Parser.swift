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

/// Parser for M4 route (La Lastrilla - El Sotillo)
///
/// PDF Structure:
/// - Two sections: "LUNES A VIERNES LABORABLES" (Weekdays) and "SÁBADOS" (Saturdays)
/// - Table format with stops as columns and departure times as rows
/// - Times formatted as HH:MM (e.g., "7:10", "14:46")
/// - Some rows marked with * for July/August only
class M4Parser: CapableParser, RouteStopsProvider {

    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M4"]),
        mode: .production,
        version: "1.2"
    )

    // MARK: - Directions

    private static let directionRegular = "Lastrilla → Sotillo"
    private static let directionReverse = "Sotillo → Lastrilla"

    private static let timeWithAsteriskPattern = try! NSRegularExpression(pattern: #"\d{1,2}:\d{2}\s*\*"#)

    // MARK: - Stops

    private enum Stops {
        static let azoguejo = BusStop(
            id: "m4-azoguejo",
            name: "Azoguejo",
            area: "Segovia capital",
            coordinates: "40.948406, -4.116411"
        )
        static let delicias = BusStop(
            id: "m4-delicias",
            name: "Delicias",
            area: "Segovia capital",
            coordinates: "40.954500, -4.108889"
        )
        static let gasolinera = BusStop(
            id: "m4-gasolinera",
            name: "Gasolinera",
            area: "La Lastrilla",
            coordinates: "40.965944, -4.106072",
            routingCoordinates: "40.965897, -4.106276"
        )
        static let pension = BusStop(
            id: "m4-pension",
            name: "Pensión",
            area: "La Lastrilla",
            coordinates: "40.969289, -4.107552"
        )
        static let poligono = BusStop(
            id: "m4-poligono",
            name: "Polígono",
            area: "La Lastrilla",
            coordinates: "40.972010, -4.108356"
        )
        static let ctraValladolid33 = BusStop(
            id: "m4-ctra-valladolid-33",
            name: "Carretera de Valladolid",
            area: "La Lastrilla",
            coordinates: "40.970464, -4.104010"
        )
        static let leopoldoMoreno = BusStop(
            id: "m4-leopoldo-moreno",
            name: "Leopoldo Moreno",
            area: "La Lastrilla",
            coordinates: "40.967679, -4.102850"
        )
        static let colegio = BusStop(
            id: "m4-colegio",
            name: "Colegio",
            area: "La Lastrilla",
            coordinates: "40.966693, -4.102033"
        )
        static let parroqSotillo = BusStop(
            id: "m4-parroq-sotillo",
            name: "Parroquia el Sotillo",
            area: "El Sotillo",
            coordinates: "40.963449, -4.095073"
        )
        static let hotelAvSotillo = BusStop(
            id: "m4-hotel-av-sotillo",
            name: "Hotel Avenida del Sotillo",
            area: "El Sotillo",
            coordinates: "40.965769, -4.097825"
        )
        static let maspalomas = BusStop(
            id: "m4-maspalomas",
            name: "Calle Maspalomas",
            area: "El Sotillo",
            coordinates: "40.965714, -4.094892"
        )
        static let centroBoal = BusStop(
            id: "m4-centro-boal",
            name: "Centro Cultural Julio Boal",
            area: "El Sotillo",
            coordinates: "40.967592, -4.091377"
        )
        static let paseoCabanillas = BusStop(
            id: "m4-paseo-cabanillas",
            name: "Colegio Madres Concepcionistas",
            area: "El Sotillo",
            coordinates: "40.962806, -4.092689"
        )
        static let rafaelDeLasHeras = BusStop(
            id: "m4-rafael-de-las-heras",
            name: "Rafael de las Heras",
            area: "El Sotillo",
            coordinates: "40.961939, -4.096711"
        )
        static let ventaMagullo = BusStop(
            id: "m4-venta-magullo",
            name: "Venta Magullo",
            area: "El Sotillo",
            coordinates: "40.960876, -4.100906"
        )
    }

    static let m4RegularRoute: [BusStop] = [
        Stops.azoguejo,
        Stops.delicias,
        Stops.gasolinera,
        Stops.pension,
        Stops.poligono,
        Stops.ctraValladolid33,
        Stops.leopoldoMoreno,
        Stops.colegio,
        // El Sotillo
        Stops.hotelAvSotillo,
        Stops.maspalomas,
        Stops.centroBoal,
        Stops.paseoCabanillas,
        Stops.parroqSotillo,
        Stops.rafaelDeLasHeras,
        Stops.ventaMagullo,

        Stops.azoguejo
    ]

    static let m4ReverseRoute: [BusStop] = [
        Stops.azoguejo,
        Stops.delicias,
        // El Sotillo
        Stops.hotelAvSotillo,
        Stops.maspalomas,
        Stops.centroBoal,
        Stops.paseoCabanillas,
        Stops.parroqSotillo,
        Stops.rafaelDeLasHeras,
        Stops.ventaMagullo,
        // La Lastrilla
        Stops.gasolinera,
        Stops.pension,
        Stops.poligono,
        Stops.ctraValladolid33,
        Stops.leopoldoMoreno,
        Stops.colegio,
        Stops.parroqSotillo,

        Stops.azoguejo
    ]

    // MARK: - Parsing State

    private struct ParsingState {
        var currentDayType: DayType
        var incompleteJourney: [(hour: Int, minute: Int)]
        var isSummerSection: Bool
        var regularRouteWeekdayTimetables: [BusTimetable]
        var regularRouteWeekendTimetables: [BusTimetable]
        var reverseRouteWeekdayTimetables: [BusTimetable]
        var reverseRouteWeekendTimetables: [BusTimetable]

        var seasonal: SeasonalAvailability {
            isSummerSection ? .yearRound : .schoolOnly
        }
    }

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains { $0.caseInsensitiveCompare(routeId) == .orderedSame }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else { return [] }
        // Drop last stop (Azoguejo arrival) — it's a terminus, not a departure stop
        return [Array(Self.m4RegularRoute.dropLast()), Array(Self.m4ReverseRoute.dropLast())]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else { return [] }
        return [
            RouteVariant(
                id: "regular",
                label: Self.directionRegular,
                stops: Array(Self.m4RegularRoute.dropLast()),
                direction: Self.directionRegular
            ),
            RouteVariant(
                id: "reverse",
                label: Self.directionReverse,
                stops: Array(Self.m4ReverseRoute.dropLast()),
                direction: Self.directionReverse
            ),
        ]
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else { return nil }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        return variants.enumerated().map { index, variant in
            let swapTargetId = variants.count == 2 ? variants[1 - index].id : nil
            return RouteView(
                id: variant.id, label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction, departureLabel: variant.departureLabel,
                swapAction: swapTargetId.map { SwapAction(targetViewId: $0) }
            )
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else { return [] }
        let dow = Calendar.current.component(.weekday, from: today)
        let isWeekday = dow != 7 && dow != 1
        let isWeekend = !isWeekday
        guard let weekdayViews = getRouteViews(routeId, dayType: .weekday),
              let weekendViews = getRouteViews(routeId, dayType: .weekend) else { return [] }
        return [
            RouteSelectorEntry(id: "entry-lv", label: "Lunes a Viernes", views: weekdayViews, initialViewId: "regular", timetableDayType: .weekday, isActiveToday: isWeekday),
            RouteSelectorEntry(id: "entry-fds", label: "Fin de semana", views: weekendViews, initialViewId: "regular", timetableDayType: .weekend, isActiveToday: isWeekend)
        ]
    }

    func parse(pdfPath: String, routeId: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M4Parser: Starting PDF parsing for \(pdfPath)")

        guard FileManager.default.fileExists(atPath: pdfPath) else {
            throw PDFParsingError("PDF file not found: \(pdfPath)")
        }

        let extractedText = PDFTextExtractor.extractText(from: pdfPath, tag: "M4Parser")
        let lines = extractedText.components(separatedBy: "\n")

        DebugConfig.debugPrint("M4Parser: Extracted \(lines.count) lines")

        #if DEBUG
        if DebugConfig.isDebugEnabled {
            DebugConfig.debugPrint("M4Parser: ===== EXTRACTED TEXT =====")
            for (index, line) in lines.enumerated() {
                if !line.isEmpty {
                    DebugConfig.debugPrint("  Line \(index): \(line)")
                }
            }
            DebugConfig.debugPrint("M4Parser: ==========================")
        }
        #endif

        let timetables = parseTimeTable(lines)

        DebugConfig.debugPrint("M4Parser: Finished parsing, created \(timetables.count) timetables")
        return timetables
    }

    // MARK: - Internal Parsing

    private func detectDayType(_ line: String) -> DayType? {
        guard let dayType = TimetableParserUtils.detectDayType(line) else { return nil }
        switch dayType {
        case .saturday, .sunday:
            return .weekend
        default:
            return dayType
        }
    }

    private func createInitialTimetables(stops: [BusStop], dayType: DayType, direction: String) -> [BusTimetable] {
        stops.map { stop in
            BusTimetable(
                routeId: "M4",
                stopId: stop.id,
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
        var state = ParsingState(
            currentDayType: .weekday,
            incompleteJourney: [],
            isSummerSection: false,
            regularRouteWeekdayTimetables: createInitialTimetables(stops: Self.m4RegularRoute, dayType: .weekday, direction: Self.directionRegular),
            regularRouteWeekendTimetables: createInitialTimetables(stops: Self.m4RegularRoute, dayType: .weekend, direction: Self.directionRegular),
            reverseRouteWeekdayTimetables: createInitialTimetables(stops: Self.m4ReverseRoute, dayType: .weekday, direction: Self.directionReverse),
            reverseRouteWeekendTimetables: createInitialTimetables(stops: Self.m4ReverseRoute, dayType: .weekend, direction: Self.directionReverse)
        )

        for line in lines {
            let newDayType = detectDayType(line)
            let isSummerMarker = line.uppercased().contains("JULIO Y AGOSTO")
            let hasTimes = TimetableParserUtils.hasTimes(line)

            if let newDayType = newDayType {
                state.currentDayType = newDayType
                state.isSummerSection = false
            } else if isSummerMarker && !hasTimes {
                // iOS PDFKit sometimes puts "JULIO Y AGOSTO" on a separate line (like Android's iText7).
                // When it does, just set the flag; times will be on the next line.
                state.isSummerSection = true
            } else if hasTimes {
                // Determine seasonal: if this line has the JULIO marker, it's summer-only.
                // If not, it's school-only (the marker doesn't bleed from previous lines).
                state.isSummerSection = isSummerMarker

                if hasAsteriskTimes(line) {
                    let times = TimetableParserUtils.sortTimes(state.incompleteJourney + TimetableParserUtils.extractTimes(line))

                    let isWeekday = state.currentDayType == .weekday
                    let currentTimetables = isWeekday ? state.reverseRouteWeekdayTimetables : state.reverseRouteWeekendTimetables
                    let reverseCount = Self.m4ReverseRoute.count

                    switch times.count {
                    case reverseCount:
                        let updated = updateTimetables(currentTimetables, times: times, seasonal: state.seasonal)
                        if isWeekday {
                            state.reverseRouteWeekdayTimetables = updated
                        } else {
                            state.reverseRouteWeekendTimetables = updated
                        }
                        state.incompleteJourney = []

                    case reverseCount - 1:
                        let lastIndex = reverseCount - 1
                        let filtered = currentTimetables.enumerated().filter { $0.offset != lastIndex }.map { $0.element }
                        let updated = updateTimetables(filtered, times: times, seasonal: state.seasonal)
                        var merged = currentTimetables
                        for i in 0..<reverseCount {
                            if i != lastIndex {
                                let sourceIndex = i < lastIndex ? i : i - 1
                                merged[i] = updated[sourceIndex]
                            }
                        }
                        if isWeekday {
                            state.reverseRouteWeekdayTimetables = merged
                        } else {
                            state.reverseRouteWeekendTimetables = merged
                        }
                        state.incompleteJourney = []

                    default:
                        let lastIndex = reverseCount - 1
                        let schoolIndex = Self.m4ReverseRoute.firstIndex(of: Stops.paseoCabanillas) ?? -1
                        let filtered = currentTimetables.enumerated()
                            .filter { $0.offset != lastIndex && $0.offset != schoolIndex }
                            .map { $0.element }
                        let updated = updateTimetables(filtered, times: times, seasonal: state.seasonal)
                        var merged = currentTimetables
                        var srcIdx = 0
                        for i in 0..<reverseCount {
                            if i != lastIndex && i != schoolIndex {
                                merged[i] = updated[srcIdx]
                                srcIdx += 1
                            }
                        }
                        if isWeekday {
                            state.reverseRouteWeekdayTimetables = merged
                        } else {
                            state.reverseRouteWeekendTimetables = merged
                        }
                        state.incompleteJourney = []
                    }

                } else {
                    let times = TimetableParserUtils.sortTimes(state.incompleteJourney + TimetableParserUtils.extractTimes(line))

                    let isWeekday = state.currentDayType == .weekday
                    let currentTimetables = isWeekday ? state.regularRouteWeekdayTimetables : state.regularRouteWeekendTimetables
                    let regularCount = Self.m4RegularRoute.count

                    switch times.count {
                    case regularCount:
                        let updated = updateTimetables(currentTimetables, times: times, seasonal: state.seasonal)
                        if isWeekday {
                            state.regularRouteWeekdayTimetables = updated
                        } else {
                            state.regularRouteWeekendTimetables = updated
                        }
                        state.incompleteJourney = []

                    case regularCount - 1:
                        let schoolIndex = Self.m4RegularRoute.firstIndex(of: Stops.paseoCabanillas) ?? -1
                        let filtered = currentTimetables.filter { $0.stopId != Stops.paseoCabanillas.id }
                        let updated = updateTimetables(filtered, times: times, seasonal: state.seasonal)
                        var merged = currentTimetables
                        for i in 0..<regularCount {
                            if i == schoolIndex {
                                continue
                            } else if i < schoolIndex {
                                merged[i] = updated[i]
                            } else {
                                merged[i] = updated[i - 1]
                            }
                        }
                        if isWeekday {
                            state.regularRouteWeekdayTimetables = merged
                        } else {
                            state.regularRouteWeekendTimetables = merged
                        }
                        state.incompleteJourney = []

                    default:
                        DebugConfig.debugPrint("Incomplete route, accumulating...")
                        state.incompleteJourney = times
                    }
                }
            }
        }

        // Drop last stop from each group — it's the return to Azoguejo (arrival, not departure)
        let allTimetables = state.regularRouteWeekdayTimetables.dropLast() +
            state.regularRouteWeekendTimetables.dropLast() +
            state.reverseRouteWeekdayTimetables.dropLast() +
            state.reverseRouteWeekendTimetables.dropLast()

        let sortedTimetables = allTimetables.map { timetable in
            var sorted = timetable
            sorted.departures = timetable.departures.sorted { ($0.hour * 60 + $0.minute) < ($1.hour * 60 + $1.minute) }
            return sorted
        }

        DebugConfig.debugPrint("Created \(sortedTimetables.count) timetables")

        #if DEBUG
        if DebugConfig.isDebugEnabled {
            sortedTimetables.prefix(5).forEach { DebugConfig.debugPrint("\($0)") }
            sortedTimetables.filter { $0.direction == Self.directionReverse }.prefix(5).forEach {
                DebugConfig.debugPrint("\($0)")
            }
        }
        #endif

        return sortedTimetables
    }

    private func hasAsteriskTimes(_ line: String) -> Bool {
        let range = NSRange(line.startIndex..., in: line)
        return Self.timeWithAsteriskPattern.firstMatch(in: line, range: range) != nil
    }
}
