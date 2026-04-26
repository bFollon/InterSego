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
        let stops: [JsonStop]
        let variants: [JsonVariant]
        let routeDisplay: JsonRouteDisplay
        let timetables: [JsonTimetableSection]
    }

    private struct JsonStop: Decodable {
        let id: String
        let name: String
        let lat: Double
        let lon: Double
        let alternates: [JsonAlternate]?
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
        let stopSequence: [String]
        let swapTargetId: String?
    }

    private struct JsonRouteDisplay: Decodable {
        let type: String
        let tabsLabel: String?
        let mergedDirectionLabel: String?
        let tabs: [JsonRouteTab]?
        let entries: [JsonRouteEntry]
    }

    private struct JsonRouteTab: Decodable {
        let label: String
        let variantId: String
    }

    private struct JsonRouteEntry: Decodable {
        let id: String
        let label: String
        let variantId: String
        let dayType: String
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
                return RouteVariant(id: variant.id, label: variant.label, stops: stops, direction: variant.label)
            }
    }

    /// RouteViews for a given day type, or nil when the route has no service that day.
    func loadRouteViews(_ routeId: String, dayType: DayType) throws -> [RouteView]? {
        let file = try loadFile(routeId)
        let stopsById = stopsIndex(file)
        let views = buildRouteViews(for: dayType, file: file, stopsById: stopsById)
        return views.isEmpty ? nil : views
    }

    /// RouteSelectorEntries built from routeDisplay.entries.
    func loadRouteEntries(_ routeId: String, today: Date) throws -> [RouteSelectorEntry] {
        let file = try loadFile(routeId)
        let stopsById = stopsIndex(file)
        return file.routeDisplay.entries.map { entry in
            let dayType = parseDayType(entry.dayType)
            let views = buildRouteViews(for: dayType, file: file, stopsById: stopsById)
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

    /// Stop dictionary keyed by stop ID — exposed for parsers that still build their own views
    /// (e.g. M6 with tabs+swap hybrid display).
    func loadBusStopsById(_ routeId: String) throws -> [String: BusStop] {
        let file = try loadFile(routeId)
        return stopsIndex(file)
    }

    // MARK: - Route structure helpers

    private func stopsIndex(_ file: TimetableFile) -> [String: BusStop] {
        Dictionary(file.stops.map { ($0.id, buildBusStop(from: $0)) }, uniquingKeysWith: { first, _ in first })
    }

    private func buildBusStop(from jsonStop: JsonStop) -> BusStop {
        BusStop(
            id: jsonStop.id,
            name: jsonStop.name,
            coordinates: "\(jsonStop.lat), \(jsonStop.lon)",
            alternates: (jsonStop.alternates ?? []).map {
                AlternateLocation(id: $0.id, name: $0.name, coordinates: "\($0.lat), \($0.lon)")
            }
        )
    }

    /// Returns the set of variant IDs that should be shown for a given day type, including
    /// swap targets so that both directions are available when needed.
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
        for dayType: DayType, file: TimetableFile, stopsById: [String: BusStop]
    ) -> [RouteView] {
        let relevant = relevantVariantIds(for: dayType, in: file)
        guard !relevant.isEmpty else { return [] }

        let isTabsType = file.routeDisplay.type == "tabs"
        let routeTabs = file.routeDisplay.tabs?.map { RouteTab(label: $0.label, viewId: $0.variantId) }

        return file.variants
            .filter { relevant.contains($0.id) }
            .map { variant in
                let stops = variant.stopSequence.compactMap { stopsById[$0] }.map { RouteViewStop(stop: $0) }
                let swapAction = isTabsType ? nil : variant.swapTargetId.map { SwapAction(targetViewId: $0) }
                return RouteView(
                    id: variant.id,
                    label: variant.label,
                    stops: stops,
                    direction: variant.label,
                    swapAction: swapAction,
                    tabs: routeTabs,
                    tabsLabel: file.routeDisplay.tabsLabel,
                    mergedDirectionLabel: isTabsType ? file.routeDisplay.mergedDirectionLabel : nil
                )
            }
    }

    private func dayTypeString(_ dayType: DayType) -> String {
        switch dayType {
        case .weekday:  return "weekday"
        case .saturday: return "saturday"
        case .sunday:   return "sunday"
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
                    direction: variant.label
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
