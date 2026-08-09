/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Loads timetable data and route structure from bundled JSON assets.
///
/// Reads `Timetables/{routeId.lowercased()}.json` from the app bundle and interprets the
/// trip-major schema defined in docs/TIMETABLE_JSON_REFACTOR.md.
///
/// - `load(_:)` returns `[BusTimetable]`, the same contract as the old `buildStaticTimetables()`.
/// - `loadRoutesForId(_:)`, `loadRouteVariants(_:dayType:)`, `loadRouteViews(_:dayType:)`,
///   `loadRouteEntries(_:today:)` replace the route-structure methods previously hardcoded in
///   each parser. Parser classes become thin wrappers that delegate everything here.
struct TimetableLoader {

    // MARK: - JSON schema types

    private struct TimetableFile: Decodable {
        let routeId: String
        let version: String
        let route: JsonRoute
        let stops: [JsonStop]
        let variants: [JsonVariant]
        let routeDisplay: JsonRouteDisplay
        let timetables: [JsonTimetableSection]
    }

    private struct JsonRoute: Decodable {
        let number: String
        let name: String
        let origin: String
        let destination: String
        let routeType: String
        let isCircular: Bool
        let displayOrder: Int
        let pdfUrl: String?
    }

    private struct JsonStop: Decodable {
        let id: String
        let name: String
        let area: String?
        let lat: Double
        let lon: Double
        let alternates: [JsonAlternate]?
        let physicalStopId: String?
        let timeIsEstimated: Bool?
    }

    private struct JsonAlternate: Decodable {
        let id: String
        let name: String
        let lat: Double
        let lon: Double
    }

    private struct JsonVariant: Decodable {
        let id: String
        let label: String
        let direction: String?
        let stopSequence: [String]
        let swapTargetId: String?
        let departureLabel: String?
        let extendedSectionLabel: String?
        let extendedStopIds: [String]?
    }

    private struct JsonRouteDisplay: Decodable {
        let type: String
        let tabsLabel: String?
        let mergedDirectionLabel: String?
        let tabs: [JsonRouteTab]?
        let tabGroups: [JsonTabGroup]?
        let entries: [JsonRouteEntry]
    }

    private struct JsonRouteTab: Decodable {
        let label: String
        let variantId: String
    }

    private struct JsonTabGroup: Decodable {
        let tabs: [JsonRouteTab]
        let appliesTo: [String]
    }

    private struct JsonRouteEntry: Decodable {
        let id: String
        let label: String
        let variantId: String
        let dayType: String
        let viewIds: [String]?
    }

    private struct JsonTimetableSection: Decodable {
        let variantId: String
        let dayType: String
        let trips: [JsonTrip]
    }

    private struct JsonTrip: Decodable {
        let season: String?
        let variantLabel: String?
        let departures: [DepartureValue]

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            season = try container.decodeIfPresent(String.self, forKey: .season)
            variantLabel = try container.decodeIfPresent(String.self, forKey: .variantLabel)
            departures = try container.decode([DepartureValue].self, forKey: .departures)
        }

        private enum CodingKeys: String, CodingKey {
            case season, variantLabel, departures
        }
    }

    // A departure element can be null, an integer (HHMM), or an object.
    private enum DepartureValue: Decodable {
        case absent
        case hhmm(Int)
        case detailed(hhmm: Int, season: String?, variantLabel: String?, alternateId: String?)

        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            if container.decodeNil() {
                self = .absent
                return
            }
            if let raw = try? container.decode(Int.self) {
                self = .hhmm(raw)
                return
            }
            let obj = try container.decode(DetailedDeparture.self)
            self = .detailed(hhmm: obj.hhmm, season: obj.season, variantLabel: obj.variantLabel, alternateId: obj.alternateId)
        }

        private struct DetailedDeparture: Decodable {
            let hhmm: Int
            let season: String?
            let variantLabel: String?
            let alternateId: String?
        }
    }

    // MARK: - File loading

    private func loadFile(_ routeId: String) throws -> TimetableFile {
        // Disk cache takes priority over bundle assets.
        if let cacheURL = TimetableCacheService.cacheURL(for: routeId),
           FileManager.default.fileExists(atPath: cacheURL.path) {
            let data = try Data(contentsOf: cacheURL)
            return try JSONDecoder().decode(TimetableFile.self, from: data)
        }
        guard let url = Bundle.main.url(
            forResource: routeId.lowercased(),
            withExtension: "json",
            subdirectory: "Timetables"
        ) else {
            throw TimetableLoaderError.fileNotFound(routeId)
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(TimetableFile.self, from: data)
    }

    // MARK: - Public API: timetables

    func load(_ routeId: String) throws -> [BusTimetable] {
        let file = try loadFile(routeId)
        return buildTimetables(from: file)
    }

    // MARK: - Public API: route structure

    /// All stop sequences for this route, one per variant. Used by the closest-stop finder.
    func loadRoutesForId(_ routeId: String) throws -> [[BusStop]] {
        let file = try loadFile(routeId)
        let stopsById = stopsIndex(file)
        return file.variants.map { variant in
            variant.stopSequence.compactMap { stopsById[$0] }
        }
    }

    /// Named variants for a given day type. Returns empty when the route has no service that day.
    func loadRouteVariants(_ routeId: String, dayType: DayType) throws -> [RouteVariant] {
        let file = try loadFile(routeId)
        let stopsById = stopsIndex(file)
        let relevant = relevantVariantIds(for: dayType, in: file)
        return file.variants
            .filter { relevant.contains($0.id) }
            .map { variant in
                let stops = variant.stopSequence.compactMap { stopsById[$0] }
                return RouteVariant(id: variant.id, label: variant.label, stops: stops, direction: variant.direction ?? variant.label, departureLabel: variant.departureLabel)
            }
    }

    /// RouteViews for a given day type, or nil when the route has no service that day.
    func loadRouteViews(_ routeId: String, dayType: DayType) throws -> [RouteView]? {
        let file = try loadFile(routeId)
        let stopsById = stopsIndex(file)
        let views = buildRouteViews(variantIds: relevantVariantIds(for: dayType, in: file), file: file, stopsById: stopsById)
        return views.isEmpty ? nil : views
    }

    /// RouteSelectorEntries built from routeDisplay.entries.
    func loadRouteEntries(_ routeId: String, today: Date) throws -> [RouteSelectorEntry] {
        let file = try loadFile(routeId)
        let stopsById = stopsIndex(file)
        return file.routeDisplay.entries.map { entry in
            let dayType = parseDayType(entry.dayType)
            let variantIds = entry.viewIds.map { Set($0) } ?? relevantVariantIds(for: dayType, in: file)
            let views = buildRouteViews(variantIds: variantIds, file: file, stopsById: stopsById)
            return RouteSelectorEntry(
                id: entry.id,
                label: entry.label,
                views: views,
                initialViewId: entry.variantId,
                timetableDayType: dayType,
                isActiveToday: isActiveToday(entry.dayType, today: today)
            )
        }
    }

    func getVersion(_ routeId: String) throws -> String {
        try loadFile(routeId).version
    }

    /// Raw stop id -> physicalStopId (defaults to itself when the JSON has no override).
    func loadPhysicalStopIds(_ routeId: String) throws -> [String: String] {
        let file = try loadFile(routeId)
        return Dictionary(uniqueKeysWithValues: file.stops.map { ($0.id, $0.physicalStopId ?? $0.id) })
    }

    /// Pure (no UIKit/SwiftUI dependency) trip-major view of this route, for the journey
    /// planner's connection extraction. See `docs/JOURNEY_PLANNER.md` and `JourneyRouteData`.
    func loadJourneyRouteData(_ routeId: String) throws -> JourneyRouteData {
        let file = try loadFile(routeId)
        let stops = file.stops.map {
            JourneyStop(id: $0.id, physicalStopId: $0.physicalStopId ?? $0.id, isEstimated: $0.timeIsEstimated ?? false)
        }
        let variants = file.variants.map { JourneyVariant(id: $0.id, stopSequence: $0.stopSequence) }
        let timetables = file.timetables.map { section -> JourneyTimetableSection in
            let trips = section.trips.map { trip -> JourneyTrip in
                let tripSeason = parseSeason(trip.season)
                let departures = trip.departures.map { makeJourneyDeparture(from: $0, tripSeason: tripSeason) }
                return JourneyTrip(departures: departures)
            }
            return JourneyTimetableSection(variantId: section.variantId, dayType: parseDayType(section.dayType), trips: trips)
        }
        return JourneyRouteData(routeId: routeId, stops: stops, variants: variants, timetables: timetables)
    }

    private func makeJourneyDeparture(from value: DepartureValue, tripSeason: SeasonalAvailability) -> JourneyDeparture? {
        switch value {
        case .absent:
            return nil
        case .hhmm(let raw):
            return JourneyDeparture(minutesOfDay: (raw / 100) * 60 + (raw % 100), season: tripSeason)
        case .detailed(let raw, let season, _, _):
            return JourneyDeparture(minutesOfDay: (raw / 100) * 60 + (raw % 100), season: season.map { parseSeason($0) } ?? tripSeason)
        }
    }

    func loadBusStopsById(_ routeId: String) throws -> [String: BusStop] {
        let file = try loadFile(routeId)
        return stopsIndex(file)
    }

    func loadRoute(_ routeId: String) throws -> BusRoute {
        let file = try loadFile(routeId)
        let r = file.route
        let type: RouteType = r.routeType == "INTERURBAN" ? .interurban : .urban
        return BusRoute(
            id: routeId.uppercased(),
            number: r.number,
            name: r.name,
            origin: r.origin,
            destination: r.destination,
            pdfURL: r.pdfUrl ?? "",
            routeType: type,
            isCircular: r.isCircular
        )
    }

    /// Scans all timetable JSON files in the bundle and the disk cache, and returns
    /// routes sorted by displayOrder. Routes that exist only on disk (discovered via
    /// the server's route manifest, see `TimetableCacheService`) are included
    /// alongside bundled ones.
    func loadAllRoutes() -> [BusRoute] {
        var routeIds = Set<String>()
        if let urls = Bundle.main.urls(forResourcesWithExtension: "json", subdirectory: "Timetables") {
            routeIds.formUnion(urls.map { $0.deletingPathExtension().lastPathComponent.uppercased() })
        }
        if let cacheDir = TimetableCacheService.cacheDirectory(),
           let files = try? FileManager.default.contentsOfDirectory(at: cacheDir, includingPropertiesForKeys: nil) {
            routeIds.formUnion(
                files
                    .filter { $0.pathExtension == "json" }
                    .map { $0.deletingPathExtension().lastPathComponent.uppercased() }
            )
        }
        return routeIds
            .compactMap { routeId -> (BusRoute, Int)? in
                guard let file = try? loadFile(routeId) else { return nil }
                let r = file.route
                let type: RouteType = r.routeType == "INTERURBAN" ? .interurban : .urban
                let route = BusRoute(
                    id: routeId,
                    number: r.number,
                    name: r.name,
                    origin: r.origin,
                    destination: r.destination,
                    pdfURL: r.pdfUrl ?? "",
                    routeType: type,
                    isCircular: r.isCircular
                )
                return (route, r.displayOrder)
            }
            .sorted { $0.1 < $1.1 }
            .map { $0.0 }
    }

    // MARK: - Route structure helpers

    private func stopsIndex(_ file: TimetableFile) -> [String: BusStop] {
        Dictionary(file.stops.map { ($0.id, buildBusStop(from: $0)) }, uniquingKeysWith: { first, _ in first })
    }

    private func buildBusStop(from jsonStop: JsonStop) -> BusStop {
        BusStop(
            id: jsonStop.id,
            name: jsonStop.name,
            area: jsonStop.area,
            coordinates: "\(jsonStop.lat), \(jsonStop.lon)",
            alternates: (jsonStop.alternates ?? []).map {
                AlternateLocation(id: $0.id, name: $0.name, coordinates: "\($0.lat), \($0.lon)")
            }
        )
    }

    private func relevantVariantIds(for dayType: DayType, in file: TimetableFile) -> Set<String> {
        let dayStr = dayTypeString(dayType)
        let fromEntries = file.routeDisplay.entries
            .filter { $0.dayType == dayStr }
            .map { $0.variantId }
        guard !fromEntries.isEmpty else { return [] }

        var ids = Set(fromEntries)
        let variantsById = Dictionary(file.variants.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        for variantId in fromEntries {
            if let target = variantsById[variantId]?.swapTargetId {
                ids.insert(target)
            }
        }
        return ids
    }

    private func buildRouteViews(
        variantIds: Set<String>, file: TimetableFile, stopsById: [String: BusStop]
    ) -> [RouteView] {
        guard !variantIds.isEmpty else { return [] }

        let isTabsType = file.routeDisplay.type == "tabs"
        let globalTabs = file.routeDisplay.tabs?.map { RouteTab(label: $0.label, viewId: $0.variantId) }

        return file.variants
            .filter { variantIds.contains($0.id) }
            .map { variant in
                let extendedIds = Set(variant.extendedStopIds ?? [])
                let stops = variant.stopSequence.compactMap { stopsById[$0] }.map { stop in
                    RouteViewStop(stop: stop, isExtendedOnly: extendedIds.contains(stop.id))
                }
                let swapAction = isTabsType ? nil : variant.swapTargetId.map { SwapAction(targetViewId: $0) }
                let variantTabs = file.routeDisplay.tabGroups?
                    .first { $0.appliesTo.contains(variant.id) }
                    .map { $0.tabs.map { RouteTab(label: $0.label, viewId: $0.variantId) } }
                    ?? (isTabsType ? globalTabs : nil)
                return RouteView(
                    id: variant.id,
                    label: variant.label,
                    stops: stops,
                    direction: variant.direction ?? variant.label,
                    departureLabel: variant.departureLabel,
                    swapAction: swapAction,
                    tabs: variantTabs,
                    tabsLabel: file.routeDisplay.tabsLabel,
                    extendedSectionLabel: variant.extendedSectionLabel,
                    mergedDirectionLabel: isTabsType ? file.routeDisplay.mergedDirectionLabel : nil
                )
            }
    }

    private func dayTypeString(_ dayType: DayType) -> String {
        switch dayType {
        case .weekday:  return "weekday"
        case .saturday: return "saturday"
        case .sunday:   return "sunday"
        case .weekend:  return "saturday"
        case .holiday:  return "sunday"
        }
    }

    private func isActiveToday(_ dayTypeStr: String, today: Date) -> Bool {
        let dow = Calendar.current.component(.weekday, from: today) // 1=Sun, 7=Sat
        switch dayTypeStr {
        case "weekday":  return dow != 1 && dow != 7
        case "saturday": return dow == 7
        case "sunday":   return dow == 1
        default:         return false
        }
    }

    // MARK: - Timetable builder (unchanged)

    private func buildTimetables(from file: TimetableFile) -> [BusTimetable] {
        let variantsById = Dictionary(uniqueKeysWithValues: file.variants.map { ($0.id, $0) })
        let stopsById = Dictionary(uniqueKeysWithValues: file.stops.map { ($0.id, $0) })
        var result: [BusTimetable] = []

        for section in file.timetables {
            guard let variant = variantsById[section.variantId] else { continue }
            let dayType = parseDayType(section.dayType)
            let stopSequence = variant.stopSequence
            var depsByStop: [[DepartureTime]] = Array(repeating: [], count: stopSequence.count)

            for trip in section.trips {
                let tripSeason = parseSeason(trip.season)
                for (i, departure) in trip.departures.enumerated() {
                    guard i < stopSequence.count else { continue }
                    let parentStop = stopsById[stopSequence[i]]
                    if let dt = makeDepartureTime(from: departure, tripSeason: tripSeason, tripVariantLabel: trip.variantLabel, parentStop: parentStop) {
                        depsByStop[i].append(dt)
                    }
                }
            }

            for (i, stopId) in stopSequence.enumerated() {
                result.append(BusTimetable(
                    routeId: file.routeId,
                    stopId: stopId,
                    dayType: dayType,
                    departures: depsByStop[i],
                    direction: variant.direction ?? variant.label
                ))
            }
        }

        return result
    }

    private func parseDayType(_ value: String) -> DayType {
        switch value {
        case "weekday":  return .weekday
        case "saturday": return .saturday
        case "sunday":   return .sunday
        default:         return .weekday
        }
    }

    private func parseSeason(_ value: String?) -> SeasonalAvailability {
        switch value {
        case nil, "yearRound":  return .yearRound
        case "schoolOnly":      return .schoolOnly
        case "summerOnly":      return .summerOnly
        case "juneToSept":      return .juneToSeptOnly
        case "monFriOnly":      return .monFriOnly
        case "friOnly":         return .friOnly
        default:                return .yearRound
        }
    }

    private func makeDepartureTime(
        from value: DepartureValue,
        tripSeason: SeasonalAvailability,
        tripVariantLabel: String? = nil,
        parentStop: JsonStop? = nil
    ) -> DepartureTime? {
        switch value {
        case .absent:
            return nil
        case .hhmm(let raw):
            return DepartureTime(hour: raw / 100, minute: raw % 100, seasonalAvailability: tripSeason, variantLabel: tripVariantLabel)
        case .detailed(let raw, let season, let variantLabel, let alternateId):
            let resolvedAlternateId = alternateId.flatMap { id in
                parentStop?.alternates?.contains { $0.id == id } == true ? id : nil
            }
            return DepartureTime(
                hour: raw / 100,
                minute: raw % 100,
                seasonalAvailability: season.map { parseSeason($0) } ?? tripSeason,
                variantLabel: variantLabel ?? tripVariantLabel,
                alternateLocationId: resolvedAlternateId
            )
        }
    }
}

enum TimetableLoaderError: Error {
    case fileNotFound(String)
}
