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
    let hour: Int // 0-23
    let minute: Int // 0-59
    let notes: String?
    let seasonalAvailability: SeasonalAvailability
    let variantLabel: String?
    /// ID of an `AlternateLocation` on the parent stop. `nil` = departs from primary coordinates.
    let alternateLocationId: String?

    init(hour: Int, minute: Int, notes: String? = nil,
         seasonalAvailability: SeasonalAvailability = .yearRound,
         variantLabel: String? = nil,
         alternateLocationId: String? = nil)
    {
        precondition(hour >= 0 && hour <= 23, "Hour must be between 0 and 23, got \(hour)")
        precondition(minute >= 0 && minute <= 59, "Minute must be between 0 and 59, got \(minute)")
        self.hour = hour
        self.minute = minute
        self.notes = notes
        self.seasonalAvailability = seasonalAvailability
        self.variantLabel = variantLabel
        self.alternateLocationId = alternateLocationId
    }

    func alternateLocationName(in stop: BusStop) -> String? {
        alternateLocationId.flatMap { stop.alternateLocation(id: $0)?.name }
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

    func shouldShowVariantLabel(selectedVariantLabel: String?) -> Bool {
        guard let label = variantLabel, !label.isEmpty else { return false }
        if let selected = selectedVariantLabel {
            return label != selected
        }
        return label != "Regular"
    }

    /// Generate departure time lists for each stop in a cluster.
    ///
    /// Given anchor times (PDF times for the first stop) and a stop count,
    /// returns one departure list per cluster stop. Stop at index `i` gets
    /// each anchor time plus `i * offsetMinutes`.
    ///
    /// Example: `clusterDepartures([t(8,30), t(16,0)], stopCount: 4, offsetMinutes: 2)` returns:
    ///   [0] = [8:30, 16:00]   (anchor + 0)
    ///   [1] = [8:32, 16:02]   (anchor + 2)
    ///   [2] = [8:34, 16:04]   (anchor + 4)
    ///   [3] = [8:36, 16:06]   (anchor + 6)
    static func clusterDepartures(
        _ anchorTimes: [DepartureTime],
        stopCount: Int,
        offsetMinutes: Int = 2,
    ) -> [[DepartureTime]] {
        (0 ..< stopCount).map { index in
            anchorTimes.map { anchor in
                let totalMinutes = anchor.hour * 60 + anchor.minute + index * offsetMinutes
                return DepartureTime(
                    hour: (totalMinutes / 60) % 24,
                    minute: totalMinutes % 60,
                    seasonalAvailability: anchor.seasonalAvailability,
                )
            }
        }
    }

    static func fromString(_ timeString: String) -> DepartureTime? {
        let parts = timeString.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0].trimmingCharacters(in: .whitespaces)),
              let minute = Int(parts[1].trimmingCharacters(in: .whitespaces)),
              hour >= 0, hour <= 23, minute >= 0, minute <= 59
        else {
            return nil
        }
        return DepartureTime(hour: hour, minute: minute)
    }
}
