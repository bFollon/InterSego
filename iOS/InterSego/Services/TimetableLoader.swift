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

/// Loads timetable data from bundled JSON assets and produces [BusTimetable] objects.
///
/// Reads `Timetables/{routeId.lowercased()}.json` from the app bundle, interprets the
/// trip-major schema defined in docs/TIMETABLE_JSON_REFACTOR.md, and returns the same
/// [BusTimetable] contract previously fulfilled by each parser's buildStaticTimetables().
struct TimetableLoader {

    // MARK: - JSON schema types

    private struct TimetableFile: Decodable {
        let routeId: String
        let version: String
        let stops: [JsonStop]
        let variants: [JsonVariant]
        let timetables: [JsonTimetableSection]
    }

    private struct JsonStop: Decodable {
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

    private struct JsonTimetableSection: Decodable {
        let variantId: String
        let dayType: String
        let trips: [JsonTrip]
    }

    private struct JsonTrip: Decodable {
        let season: String?
        let departures: [DepartureValue]

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            season = try container.decodeIfPresent(String.self, forKey: .season)
            departures = try container.decode([DepartureValue].self, forKey: .departures)
        }

        private enum CodingKeys: String, CodingKey {
            case season, departures
        }
    }

    // A departure element can be null, an integer (HHMM), or an object.
    private enum DepartureValue: Decodable {
        case absent
        case hhmm(Int)
        case detailed(hhmm: Int, season: String?, variantLabel: String?)

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
            self = .detailed(hhmm: obj.hhmm, season: obj.season, variantLabel: obj.variantLabel)
        }

        private struct DetailedDeparture: Decodable {
            let hhmm: Int
            let season: String?
            let variantLabel: String?
        }
    }

    // MARK: - Public API

    func load(_ routeId: String) throws -> [BusTimetable] {
        guard let url = Bundle.main.url(
            forResource: routeId.lowercased(),
            withExtension: "json",
            subdirectory: "Timetables"
        ) else {
            throw TimetableLoaderError.fileNotFound(routeId)
        }
        let data = try Data(contentsOf: url)
        let file = try JSONDecoder().decode(TimetableFile.self, from: data)
        return buildTimetables(from: file)
    }

    // MARK: - Private

    private func buildTimetables(from file: TimetableFile) -> [BusTimetable] {
        let variantsById = Dictionary(uniqueKeysWithValues: file.variants.map { ($0.id, $0) })
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
                    if let dt = makeDepartureTime(from: departure, tripSeason: tripSeason) {
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
        tripSeason: SeasonalAvailability
    ) -> DepartureTime? {
        switch value {
        case .absent:
            return nil
        case .hhmm(let raw):
            return DepartureTime(hour: raw / 100, minute: raw % 100, seasonalAvailability: tripSeason)
        case .detailed(let raw, let season, let variantLabel):
            return DepartureTime(
                hour: raw / 100,
                minute: raw % 100,
                seasonalAvailability: season.map { parseSeason($0) } ?? tripSeason,
                variantLabel: variantLabel
            )
        }
    }
}

enum TimetableLoaderError: Error {
    case fileNotFound(String)
}
