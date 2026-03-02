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

enum TimeModifier: String, CaseIterable {
    case tripleAsterisk = "***"
    case doubleAsterisk = "**"
    case arrow = "→"
    case pound = "#"
}

struct AnnotatedTime {
    let hour: Int
    let minute: Int
    let modifier: TimeModifier?

    init(hour: Int, minute: Int, modifier: TimeModifier? = nil) {
        self.hour = hour
        self.minute = minute
        self.modifier = modifier
    }
}

enum TimetableParserUtils {

    private static let timePattern = try! NSRegularExpression(pattern: #"\d{1,2}:\d{2}"#)

    private static var modifierAlternatives: String {
        TimeModifier.allCases
            .map { NSRegularExpression.escapedPattern(for: $0.rawValue) }
            .joined(separator: "|")
    }

    private static let annotatedTimePattern: NSRegularExpression = {
        let pattern = "(?:(\(modifierAlternatives))\\s*)?(\\d{1,2}:\\d{2})(?:(\(modifierAlternatives)))?"
        return try! NSRegularExpression(pattern: pattern)
    }()

    static func hasTimes(_ line: String) -> Bool {
        let range = NSRange(line.startIndex..., in: line)
        return timePattern.firstMatch(in: line, range: range) != nil
    }

    static func extractTimes(_ line: String) -> [(hour: Int, minute: Int)] {
        let range = NSRange(line.startIndex..., in: line)
        let matches = timePattern.matches(in: line, range: range)
        return matches.compactMap { match in
            guard let matchRange = Range(match.range, in: line) else { return nil }
            let timeStr = String(line[matchRange])
            return parseTime(timeStr)
        }
    }

    static func extractAnnotatedTimes(_ line: String) -> [AnnotatedTime] {
        let range = NSRange(line.startIndex..., in: line)
        let matches = annotatedTimePattern.matches(in: line, range: range)
        return matches.compactMap { match in
            guard let timeRange = Range(match.range(at: 2), in: line) else { return nil }
            let timeStr = String(line[timeRange])
            guard let time = parseTime(timeStr) else { return nil }

            var modifierSymbol: String?
            if let prefixRange = Range(match.range(at: 1), in: line) {
                modifierSymbol = String(line[prefixRange])
            } else if let suffixRange = Range(match.range(at: 3), in: line) {
                modifierSymbol = String(line[suffixRange])
            }

            let modifier = modifierSymbol.flatMap { sym in
                TimeModifier.allCases.first { $0.rawValue == sym }
            }

            return AnnotatedTime(hour: time.hour, minute: time.minute, modifier: modifier)
        }
    }

    static func sortTimes(_ times: [(hour: Int, minute: Int)]) -> [(hour: Int, minute: Int)] {
        times.sorted { ($0.hour * 60 + $0.minute) < ($1.hour * 60 + $1.minute) }
    }

    static func detectDayType(_ line: String) -> DayType? {
        let upper = line.uppercased()
        if upper.contains("LUNES A VIERNES") {
            return .weekday
        } else if upper.contains("SÁBADOS") || upper.contains("SABADOS") {
            return .saturday
        } else if upper.contains("DOMINGOS") {
            return .sunday
        }
        return nil
    }

    private static func parseTime(_ timeStr: String) -> (hour: Int, minute: Int)? {
        let parts = timeStr.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]) else { return nil }
        return (hour, minute)
    }
}
