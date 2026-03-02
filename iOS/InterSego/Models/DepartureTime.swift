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

struct DepartureTime: Codable, Comparable, Hashable {
    let hour: Int           // 0-23
    let minute: Int         // 0-59
    let notes: String?
    let seasonalAvailability: SeasonalAvailability
    let variantLabel: String?

    init(hour: Int, minute: Int, notes: String? = nil,
         seasonalAvailability: SeasonalAvailability = .yearRound,
         variantLabel: String? = nil) {
        precondition(hour >= 0 && hour <= 23, "Hour must be between 0 and 23, got \(hour)")
        precondition(minute >= 0 && minute <= 59, "Minute must be between 0 and 59, got \(minute)")
        self.hour = hour
        self.minute = minute
        self.notes = notes
        self.seasonalAvailability = seasonalAvailability
        self.variantLabel = variantLabel
    }

    var displayString: String {
        String(format: "%02d:%02d", hour, minute)
    }

    var minutesSinceMidnight: Int {
        hour * 60 + minute
    }

    func isPast(currentHour: Int, currentMinute: Int) -> Bool {
        let currentMinutes = currentHour * 60 + currentMinute
        return minutesSinceMidnight < currentMinutes
    }

    func isFuture(currentHour: Int, currentMinute: Int) -> Bool {
        !isPast(currentHour: currentHour, currentMinute: currentMinute)
    }

    func minutesUntil(currentHour: Int, currentMinute: Int) -> Int {
        let currentMinutes = currentHour * 60 + currentMinute
        return minutesSinceMidnight - currentMinutes
    }

    static func < (lhs: DepartureTime, rhs: DepartureTime) -> Bool {
        lhs.minutesSinceMidnight < rhs.minutesSinceMidnight
    }

    static func fromString(_ timeString: String) -> DepartureTime? {
        let parts = timeString.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0].trimmingCharacters(in: .whitespaces)),
              let minute = Int(parts[1].trimmingCharacters(in: .whitespaces)),
              hour >= 0, hour <= 23, minute >= 0, minute <= 59 else {
            return nil
        }
        return DepartureTime(hour: hour, minute: minute)
    }
}
