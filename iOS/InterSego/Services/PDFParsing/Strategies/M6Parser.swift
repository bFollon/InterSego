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

/// Parser for M6 route (Segovia <-> Torrecaballeros).
///
/// Handles weekday, Saturday, and Sunday schedules including partial journeys
/// (rows with fewer anchor times than the route has clusters) and mixed-direction
/// lines (adjacent outbound/return times on the same PDF text line).
class M6Parser: CapableParser, RouteStopsProvider {

    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M6"]),
        mode: .production,
        version: "0.4"
    )

    // MARK: - Types

    struct StopCluster {
        let stops: [BusStop]

        init(_ stops: [BusStop]) {
            precondition(!stops.isEmpty)
            self.stops = stops
        }
    }

    enum ClusterAlignment {
        case fromStart
        case fromEnd
    }

    struct Route {
        let id: UUID
        let clusters: [StopCluster]
        let alignment: ClusterAlignment

        init(id: UUID = UUID(), clusters: [StopCluster], alignment: ClusterAlignment = .fromStart) {
            self.id = id
            self.clusters = clusters
            self.alignment = alignment
        }

        var stops: [BusStop] {
            clusters.flatMap { $0.stops }
        }

        func reversed() -> Route {
            Route(
                clusters: clusters.reversed().map { StopCluster($0.stops.reversed()) },
                alignment: alignment == .fromStart ? .fromEnd : .fromStart
            )
        }

        func copy(id: UUID? = nil, clusters: [StopCluster]? = nil, alignment: ClusterAlignment? = nil) -> Route {
            Route(
                id: id ?? self.id,
                clusters: clusters ?? self.clusters,
                alignment: alignment ?? self.alignment
            )
        }
    }

    // MARK: - Parsing State

    private struct ParsingState {
        var journeyBuilder: [(hour: Int, minute: Int)]
        var isReversed: Bool = true
        var section: DayType = .weekday
        var currentAnnotation: TimeModifier?
        var sundaySeasonalFirstSeen: Set<Bool> = []
        var routes: [UUID: [BusTimetable]] = [:]
    }

    // MARK: - Constants

    private static let directionOutbound = "Segovia → Torrecaballeros"
    private static let directionInbound = "Torrecaballeros → Segovia"
    private static let estimatedMinutesPerClusterStop = 2
    private static let estimatedTorrecabToDeliciasMinutes = 15

    private static let summerMonths: Set<Int> = [7, 8]

    private static let footnotePattern = try! NSRegularExpression(
        pattern: "PERIODO LECTIVO|VACACIONES ESCOLARES",
        options: .caseInsensitive
    )

    // MARK: - Stops

    private enum Stops {
        static let azoguejo = BusStop(name: "Azoguejo", area: "Segovia capital", coordinates: "40.948502, -4.115979")
        static let delicias = BusStop(name: "Delicias", area: "Segovia capital", coordinates: "40.954500, -4.108889")
        static let montecorredores = BusStop(name: "Montecorredores", area: "Segovia capital", coordinates: "40.952000, -4.097278")
        static let sanCris = BusStop(name: "San Cristóbal de Segovia", area: "San Cristóbal de segovia", coordinates: "40.952056, -4.081139")
        static let sanCrisIglesia = BusStop(name: "Iglesia", area: "San Cristóbal de segovia", coordinates: "40.951733, -4.077499")
        static let sanCrisRotonda = BusStop(name: "Rotonda", area: "San Cristóbal de segovia", coordinates: "40.951224, -4.073449")
        static let sonsoto = BusStop(name: "Potro", area: "Sonsoto", coordinates: "40.954774, -4.040524")
        static let sonsoto2 = BusStop(name: "Sonsoto 2", area: "Sonsoto", details: "Junto a C/ Peñas lisas", coordinates: "40.957470, -4.039154")
        static let trescasas = BusStop(name: "Plaza de la constitución", area: "Trescasas", coordinates: "40.961834, -4.037367")
        static let trescasas2 = BusStop(name: "Trescasas 2", area: "Trescasas", coordinates: "40.963899, -4.034776")
        static let cabanillas = BusStop(name: "Cabanillas", area: "Cabanillas", coordinates: "40.974402, -4.028241")
        static let torrecaballeros = BusStop(name: "Torrecaballeros", area: "Torrecaballeros", coordinates: "40.991880, -4.022848")
        static let torrecaballeros2 = BusStop(name: "Torrecaballeros 2", area: "Torrecaballeros", details: "Junto a la taberna del Rancho", coordinates: "40.995364, -4.021688")
        static let torrecaballeros3 = BusStop(name: "Torrecaballeros 3", area: "Torrecaballeros", details: "En carretera hacia Turégano", coordinates: "40.999144, -4.020855")
        static let andresLaguna = BusStop(name: "IES Andres Laguna", area: "Segovia capital", coordinates: "40.939106, -4.115582")
        static let laPista = BusStop(name: "Glorieta La Pista", area: "Segovia capital", details: "Glorieta del Ballenoil", coordinates: "40.937354, -4.111411")
        static let hermanitas = BusStop(name: "Residencia Hermanitas de los pobres", area: "Segovia capital", coordinates: "40.944234, -4.110012")
        static let estacionBus = BusStop(name: "Estación de Autobuses de Segovia", area: "Segovia capital", coordinates: "40.944768, -4.121823")
        static let plazaToros = BusStop(name: "Plaza de Toros", area: "Segovia capital", coordinates: "40.942093, -4.107603")
        static let palazuelos = BusStop(name: "Palazuelos", area: "Palazuelos", coordinates: "40.931068, -4.064340")
        static let palazuelosColegio = BusStop(name: "Colegio", area: "Palazuelos", coordinates: "40.933921, -4.063495")
        static let tabanera = BusStop(name: "Tabanera", area: "Tabanera", coordinates: "40.934336, -4.067014")
        static let tabanera2 = BusStop(name: "Tabanera 2", area: "Tabanera", coordinates: "40.937491, -4.065818")
        static let jardinillos = BusStop(name: "Jardinillos de San Roque", area: "Segovia", details: "Frente a Policía Nacional", coordinates: "40.944361, -4.120831")
    }

    // MARK: - Routes

    enum Routes {
        enum Weekday {
            static let regular = Route(
                clusters: [
                    StopCluster([Stops.azoguejo, Stops.delicias, Stops.montecorredores]),
                    StopCluster([Stops.sanCris, Stops.sanCrisIglesia, Stops.sanCrisRotonda]),
                    StopCluster([Stops.sonsoto, Stops.sonsoto2]),
                    StopCluster([Stops.trescasas, Stops.trescasas2]),
                    StopCluster([Stops.cabanillas]),
                    StopCluster([Stops.torrecaballeros, Stops.torrecaballeros2, Stops.torrecaballeros3]),
                ]
            )
            static let reversed = regular.reversed()

            static let extended = Route(
                clusters: [
                    StopCluster([
                        Stops.andresLaguna, Stops.laPista, Stops.hermanitas,
                        Stops.azoguejo, Stops.delicias, Stops.montecorredores
                    ]),
                ] + Array(regular.clusters.dropFirst())
            )
            static let extendedReversed = extended.reversed()

            static let circular = Route(
                clusters: [
                    StopCluster([Stops.estacionBus, Stops.andresLaguna, Stops.laPista, Stops.plazaToros]),
                    StopCluster([Stops.palazuelos, Stops.palazuelosColegio]),
                    StopCluster([Stops.tabanera, Stops.tabanera2]),
                    StopCluster([Stops.sanCrisIglesia, Stops.sanCrisRotonda]),
                    StopCluster([Stops.sonsoto, Stops.sonsoto2]),
                    StopCluster([Stops.trescasas, Stops.trescasas2]),
                    StopCluster([Stops.cabanillas]),
                    StopCluster([Stops.torrecaballeros, Stops.torrecaballeros2, Stops.torrecaballeros3]),
                    StopCluster([Stops.delicias, Stops.azoguejo]),
                ]
            )
        }

        enum Saturday {
            static let regular = Weekday.circular.copy(
                id: UUID(),
                clusters: Array(Weekday.circular.clusters.dropLast())
            )

            static let reversed = Route(
                clusters: Array(regular.clusters.dropLast()) + [
                    StopCluster([Stops.azoguejo, Stops.jardinillos])
                ],
                alignment: .fromEnd
            )
        }

        enum Sunday {
            static let regular = Weekday.circular.copy(
                id: UUID(),
                clusters: Array(Weekday.circular.clusters.dropLast())
            )

            static let reversed = regular.reversed()
        }
    }

    // MARK: - All routes by ID

    private let allRoutes: [Route]
    private let routeById: [UUID: Route]

    init() {
        allRoutes = [
            Routes.Weekday.regular,
            Routes.Weekday.reversed,
            Routes.Weekday.extended,
            Routes.Weekday.extendedReversed,
            Routes.Weekday.circular,
            Routes.Saturday.regular,
            Routes.Saturday.reversed,
            Routes.Sunday.regular,
            Routes.Sunday.reversed,
        ]
        routeById = Dictionary(uniqueKeysWithValues: allRoutes.map { ($0.id, $0) })
    }

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains { $0.caseInsensitiveCompare(routeId) == .orderedSame }
    }

    func parse(pdfPath: String, routeId: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M6Parser: Starting parsing for \(pdfPath)")

        guard FileManager.default.fileExists(atPath: pdfPath) else {
            throw PDFParsingError("PDF file not found: \(pdfPath)")
        }

        let extractedText = PDFTextExtractor.extractText(from: pdfPath, tag: "M6Parser")
        let lines = extractedText.components(separatedBy: "\n")

        DebugConfig.debugPrint("M6Parser: Extracted \(lines.count) lines")

        #if DEBUG
        if DebugConfig.isDebugEnabled {
            for (index, line) in lines.enumerated() {
                if !line.isEmpty {
                    DebugConfig.debugPrint("  Line \(index): \(line)")
                }
            }
        }
        #endif

        let preprocessedLines = preprocessLines(lines)
        let reorderedLines = reorderSwappedLines(preprocessedLines)
        let timetables = parseTimeTable(reorderedLines)

        DebugConfig.debugPrint("M6Parser: Parsed \(timetables.count) timetables")
        return timetables
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M6") == .orderedSame else { return [] }
        return getRouteVariants(routeId, dayType: .weekday).map { $0.stops }
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard let views = getRouteViews(routeId, dayType: dayType) else { return [] }
        return views.map { view in
            RouteVariant(
                id: view.id,
                label: view.label,
                stops: view.stops.map { $0.stop },
                direction: view.direction,
                departureLabel: view.departureLabel
            )
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M6") == .orderedSame else { return nil }

        let extendedOnlyNames: Set<String> = [
            Stops.andresLaguna.name, Stops.laPista.name, Stops.hermanitas.name
        ]

        func extendedStops(_ route: Route) -> [RouteViewStop] {
            route.stops.map { stop in
                RouteViewStop(stop: stop, isExtendedOnly: extendedOnlyNames.contains(stop.name))
            }
        }

        func plainStops(_ route: Route) -> [RouteViewStop] {
            route.stops.map { RouteViewStop(stop: $0) }
        }

        switch dayType {
        case .weekday:
            let tabs = [
                RouteTab(label: "Regular", viewId: "weekday-unified"),
                RouteTab(label: "Circular", viewId: "weekday-circular")
            ]
            return [
                RouteView(
                    id: "weekday-unified",
                    label: "Segovia → Torrecaballeros",
                    stops: extendedStops(Routes.Weekday.extended),
                    direction: Self.directionOutbound,
                    swapAction: SwapAction(targetViewId: "weekday-unified-reversed"),
                    tabs: tabs,
                    extendedSectionLabel: "Ruta extendida"
                ),
                RouteView(
                    id: "weekday-unified-reversed",
                    label: "Torrecaballeros → Segovia",
                    stops: extendedStops(Routes.Weekday.extendedReversed),
                    direction: Self.directionInbound,
                    swapAction: SwapAction(targetViewId: "weekday-unified"),
                    tabs: tabs,
                    extendedSectionLabel: "Ruta extendida"
                ),
                RouteView(
                    id: "weekday-circular",
                    label: "Circular",
                    stops: plainStops(Routes.Weekday.circular),
                    direction: Self.directionOutbound,
                    departureLabel: "Circular",
                    tabs: tabs
                )
            ]

        case .saturday:
            return [
                RouteView(
                    id: "saturday-regular",
                    label: "Segovia → Torrecaballeros",
                    stops: plainStops(Routes.Saturday.regular),
                    direction: Self.directionOutbound,
                    departureLabel: "Sábado",
                    swapAction: SwapAction(targetViewId: "saturday-reversed")
                ),
                RouteView(
                    id: "saturday-reversed",
                    label: "Torrecaballeros → Segovia",
                    stops: plainStops(Routes.Saturday.reversed),
                    direction: Self.directionInbound,
                    departureLabel: "Sábado",
                    swapAction: SwapAction(targetViewId: "saturday-regular")
                )
            ]

        case .sunday:
            return [
                RouteView(
                    id: "sunday-regular",
                    label: "Segovia → Torrecaballeros",
                    stops: plainStops(Routes.Sunday.regular),
                    direction: Self.directionOutbound,
                    departureLabel: "Domingo",
                    swapAction: SwapAction(targetViewId: "sunday-reversed")
                ),
                RouteView(
                    id: "sunday-reversed",
                    label: "Torrecaballeros → Segovia",
                    stops: plainStops(Routes.Sunday.reversed),
                    direction: Self.directionInbound,
                    departureLabel: "Domingo",
                    swapAction: SwapAction(targetViewId: "sunday-regular")
                )
            ]

        default:
            return getRouteViews(routeId, dayType: .weekday)
        }
    }

    // MARK: - Line Preprocessing

    /// Fixes two PDFKit artifacts that affect the M6 circular route:
    ///
    /// 1. **Split mixed lines**: PDFKit sometimes attaches a time from a
    ///    differently-formatted cell (e.g. the highlighted `**21:20` departure)
    ///    to the following section-header line, producing `**21:20 SÁBADOS`.
    ///    Such lines are split into their time part and keyword part so that
    ///    `detectDayType` can fire correctly.
    ///
    /// 2. **Merge orphaned leading times**: After splitting, a lone `**HH:MM`
    ///    token may appear several lines after the `#`-prefixed circular row it
    ///    belongs to (PDFKit read the highlighted cell as a separate text block).
    ///    This pass scans backwards from each such orphan and prepends it to the
    ///    nearest preceding circular (`#`-annotated) time row.
    private func preprocessLines(_ lines: [String]) -> [String] {
        // Step 1: Split lines that contain both times and a day-type keyword.
        var expanded: [String] = []
        for line in lines {
            if TimetableParserUtils.hasTimes(line),
               TimetableParserUtils.detectDayType(line) != nil {
                let keywords = ["LUNES A VIERNES", "SÁBADOS", "SABADOS", "DOMINGOS"]
                let upper = line.uppercased()
                if let keyword = keywords.first(where: { upper.contains($0) }),
                   let range = line.range(of: keyword, options: .caseInsensitive) {
                    let timesPart = String(line[..<range.lowerBound]).trimmingCharacters(in: .whitespaces)
                    let keywordPart = String(line[range.lowerBound...]).trimmingCharacters(in: .whitespaces)
                    if !timesPart.isEmpty { expanded.append(timesPart) }
                    if !keywordPart.isEmpty { expanded.append(keywordPart) }
                    continue
                }
            }
            expanded.append(line)
        }

        // Step 2: Backward-merge orphaned single-** times into the preceding
        // circular (#) time row.
        var result = expanded
        var i = 0
        while i < result.count {
            let line = result[i]
            guard TimetableParserUtils.hasTimes(line) else { i += 1; continue }

            let annotated = TimetableParserUtils.extractAnnotatedTimes(line)
            let isOrphanedDoubleAsterisk = annotated.count == 1
                && annotated[0].modifier == .doubleAsterisk

            if isOrphanedDoubleAsterisk {
                var j = i - 1
                while j >= 0 && !TimetableParserUtils.hasTimes(result[j]) { j -= 1 }
                if j >= 0 {
                    let prevAnnotated = TimetableParserUtils.extractAnnotatedTimes(result[j])
                    if prevAnnotated.contains(where: { $0.modifier == .pound }) {
                        DebugConfig.debugPrint("M6Parser: Merging orphaned \(line) into preceding circular row")
                        result[j] = line + " " + result[j]
                        result.remove(at: i)
                        continue
                    }
                }
            }
            i += 1
        }

        return result
    }

    // MARK: - Line Reordering

    private func reorderSwappedLines(_ lines: [String]) -> [String] {
        var result: [String] = []
        var pending: String?

        for line in lines {
            if let p = pending {
                if TimetableParserUtils.hasTimes(line) {
                    let pendingTimes = TimetableParserUtils.extractTimes(p)
                    let lineTimes = TimetableParserUtils.extractTimes(line)
                    if let pFirst = pendingTimes.first, let lFirst = lineTimes.first,
                       (pFirst.hour * 60 + pFirst.minute) > (lFirst.hour * 60 + lFirst.minute) {
                        DebugConfig.debugPrint("M6Parser: Reordering swapped lines:\n  was: \(p)\n  now: \(line)")
                        result.append(line)
                        result.append(p)
                        pending = nil
                    } else {
                        result.append(p)
                        pending = line
                    }
                } else {
                    result.append(p)
                    result.append(line)
                    pending = nil
                }
            } else if TimetableParserUtils.hasTimes(line) {
                pending = line
            } else {
                result.append(line)
            }
        }

        if let p = pending {
            result.append(p)
        }

        return result
    }

    // MARK: - Timetable Creation

    private func createInitialTimetables(route: Route, dayType: DayType, direction: String) -> [BusTimetable] {
        route.stops.map { stop in
            BusTimetable(
                routeId: "M6",
                stopId: stop.name,
                dayType: dayType,
                departures: [],
                direction: direction
            )
        }
    }

    // MARK: - Parsing Target

    private func determineParsingTarget(_ state: ParsingState) -> UUID {
        switch state.section {
        case .weekday:
            if state.isReversed {
                return state.currentAnnotation == .arrow
                    ? Routes.Weekday.extendedReversed.id
                    : Routes.Weekday.reversed.id
            } else {
                switch state.currentAnnotation {
                case .arrow: return Routes.Weekday.extended.id
                case .doubleAsterisk, .pound: return Routes.Weekday.circular.id
                default: return Routes.Weekday.regular.id
                }
            }

        case .saturday:
            return state.isReversed ? Routes.Saturday.reversed.id : Routes.Saturday.regular.id

        case .sunday:
            return state.isReversed ? Routes.Sunday.reversed.id : Routes.Sunday.regular.id

        default:
            DebugConfig.debugError("M6Parser: Unexpected DayType: \(state.section)")
            return Routes.Weekday.regular.id
        }
    }

    // MARK: - Timetable Update with Clusters

    private func updateTimetables(
        route: Route,
        timetables: [BusTimetable],
        times: [(hour: Int, minute: Int)],
        variantLabel: String? = nil,
        seasonalAvailability: SeasonalAvailability = .yearRound
    ) -> [BusTimetable] {
        let activeClusters: [StopCluster]
        switch route.alignment {
        case .fromStart: activeClusters = Array(route.clusters.prefix(times.count))
        case .fromEnd: activeClusters = Array(route.clusters.suffix(times.count))
        }

        let activeStopCount = activeClusters.reduce(0) { $0 + $1.stops.count }

        let inactive: [BusTimetable]
        let active: [BusTimetable]
        switch route.alignment {
        case .fromStart:
            inactive = []
            active = Array(timetables.prefix(activeStopCount))
        case .fromEnd:
            inactive = Array(timetables.dropLast(activeStopCount))
            active = Array(timetables.suffix(activeStopCount))
        }

        var updated: [BusTimetable] = []
        var activeIndex = 0
        for (clusterIdx, cluster) in activeClusters.enumerated() {
            let anchorTime = times[clusterIdx]
            for posInCluster in 0..<cluster.stops.count {
                let totalMinutes = anchorTime.hour * 60 + anchorTime.minute
                    + posInCluster * Self.estimatedMinutesPerClusterStop
                var tt = active[activeIndex]
                tt.departures = tt.departures + [
                    DepartureTime(
                        hour: (totalMinutes / 60) % 24,
                        minute: totalMinutes % 60,
                        seasonalAvailability: seasonalAvailability,
                        variantLabel: variantLabel
                    )
                ]
                updated.append(tt)
                activeIndex += 1
            }
        }

        switch route.alignment {
        case .fromStart: return updated + Array(timetables.dropFirst(activeStopCount))
        case .fromEnd: return inactive + updated
        }
    }

    // MARK: - Helpers

    private func isFootnote(_ line: String) -> Bool {
        let range = NSRange(line.startIndex..., in: line)
        return Self.footnotePattern.firstMatch(in: line, range: range) != nil
    }

    private func routeLabel(_ id: UUID) -> String? {
        switch id {
        case Routes.Weekday.regular.id, Routes.Weekday.reversed.id: return "Regular"
        case Routes.Weekday.extended.id, Routes.Weekday.extendedReversed.id: return "Extendida"
        case Routes.Weekday.circular.id: return "Circular"
        case Routes.Saturday.regular.id, Routes.Saturday.reversed.id: return "Sábado"
        case Routes.Sunday.regular.id, Routes.Sunday.reversed.id: return "Domingo"
        default: return nil
        }
    }

    private func preprocessCircularLine(_ annotatedTimes: [AnnotatedTime]) -> [AnnotatedTime] {
        let isCircularLine = annotatedTimes.contains {
            $0.modifier == .pound || $0.modifier == .doubleAsterisk
        }
        guard isCircularLine else { return annotatedTimes }

        var outboundEnd = annotatedTimes.count
        for i in 1..<annotatedTimes.count {
            let prev = annotatedTimes[i - 1]
            let curr = annotatedTimes[i]
            if (curr.hour * 60 + curr.minute) <= (prev.hour * 60 + prev.minute) {
                outboundEnd = i
                break
            }
        }

        let outbound = Array(annotatedTimes.prefix(outboundEnd))
        guard let last = outbound.last else { return annotatedTimes }

        let totalMinutes = last.hour * 60 + last.minute + Self.estimatedTorrecabToDeliciasMinutes
        let estimatedArrival = AnnotatedTime(
            hour: (totalMinutes / 60) % 24,
            minute: totalMinutes % 60,
            modifier: last.modifier
        )

        return outbound + [estimatedArrival]
    }

    // MARK: - Main Parse Logic

    private func parseTimeTable(_ lines: [String]) -> [BusTimetable] {
        var state = ParsingState(
            journeyBuilder: [],
            routes: [
                Routes.Weekday.regular.id: createInitialTimetables(route: Routes.Weekday.regular, dayType: .weekday, direction: Self.directionOutbound),
                Routes.Weekday.reversed.id: createInitialTimetables(route: Routes.Weekday.reversed, dayType: .weekday, direction: Self.directionInbound),
                Routes.Weekday.extended.id: createInitialTimetables(route: Routes.Weekday.extended, dayType: .weekday, direction: Self.directionOutbound),
                Routes.Weekday.extendedReversed.id: createInitialTimetables(route: Routes.Weekday.extendedReversed, dayType: .weekday, direction: Self.directionInbound),
                Routes.Weekday.circular.id: createInitialTimetables(route: Routes.Weekday.circular, dayType: .weekday, direction: Self.directionOutbound),
                Routes.Saturday.regular.id: createInitialTimetables(route: Routes.Saturday.regular, dayType: .saturday, direction: Self.directionOutbound),
                Routes.Saturday.reversed.id: createInitialTimetables(route: Routes.Saturday.reversed, dayType: .saturday, direction: Self.directionInbound),
                Routes.Sunday.regular.id: createInitialTimetables(route: Routes.Sunday.regular, dayType: .sunday, direction: Self.directionOutbound),
                Routes.Sunday.reversed.id: createInitialTimetables(route: Routes.Sunday.reversed, dayType: .sunday, direction: Self.directionInbound),
            ]
        )

        func flush(_ flushState: inout ParsingState, times: [(hour: Int, minute: Int)]) {
            let target = determineParsingTarget(flushState)
            guard let route = routeById[target],
                  let currentTimetables = flushState.routes[target] else { return }

            let label = routeLabel(target)

            let isSundaySeasonal = flushState.section == .sunday
                && flushState.currentAnnotation == .tripleAsterisk

            let seasonal: SeasonalAvailability
            if isSundaySeasonal {
                if !flushState.sundaySeasonalFirstSeen.contains(flushState.isReversed) {
                    seasonal = .summerOnly
                } else {
                    seasonal = .schoolOnly
                }
            } else {
                seasonal = .yearRound
            }

            let updatedTimetables = updateTimetables(
                route: route,
                timetables: currentTimetables,
                times: times,
                variantLabel: label,
                seasonalAvailability: seasonal
            )

            if isSundaySeasonal {
                flushState.sundaySeasonalFirstSeen.insert(flushState.isReversed)
            }

            flushState.routes[target] = updatedTimetables
            flushState.isReversed = !flushState.isReversed
            flushState.currentAnnotation = nil
        }

        for line in lines {
            if isFootnote(line) {
                continue
            }

            if TimetableParserUtils.hasTimes(line) {
                let annotatedTimes = TimetableParserUtils.extractAnnotatedTimes(line)
                let processedTimes = preprocessCircularLine(annotatedTimes)

                var inlineBuilder: [(hour: Int, minute: Int)] = []
                var hasFlipped = false

                for annotatedTime in processedTimes {
                    if let mod = annotatedTime.modifier {
                        state.currentAnnotation = mod
                    }

                    let parsingTarget = determineParsingTarget(state)
                    let time = (hour: annotatedTime.hour, minute: annotatedTime.minute)

                    let isDirectionChange = !hasFlipped
                        && !inlineBuilder.isEmpty
                        && (time.hour * 60 + time.minute) <= (inlineBuilder.last!.hour * 60 + inlineBuilder.last!.minute)

                    if isDirectionChange {
                        // Flush outbound times
                        var outboundState = state
                        outboundState.isReversed = false
                        flush(&outboundState, times: inlineBuilder)
                        state = outboundState
                        if let mod = annotatedTime.modifier {
                            state.currentAnnotation = mod
                        }
                        inlineBuilder = [time]
                        hasFlipped = true
                    } else if let route = routeById[parsingTarget],
                              (inlineBuilder + [time]).count == route.clusters.count {
                        // Buffer full — flush complete journey
                        let flushTimes = inlineBuilder + [time]
                        flush(&state, times: flushTimes)
                        inlineBuilder = []
                    } else {
                        inlineBuilder.append(time)
                    }
                }

                // Flush partial journey remaining
                if !inlineBuilder.isEmpty {
                    flush(&state, times: inlineBuilder)
                }
            } else {
                if let dayType = TimetableParserUtils.detectDayType(line),
                   (dayType == .saturday || dayType == .sunday) {
                    state.section = dayType
                    state.isReversed = false
                }
            }
        }

        return state.routes.values.flatMap { $0 }
    }
}
