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

struct BusReminder: Codable, Identifiable, Equatable {
    let id: String
    let routeId: String
    let routeNumber: String
    let stopId: String
    let stopName: String
    let direction: String
    let departureHour: Int
    let departureMinute: Int
    /// Lead time snapshot at scheduling time (minutes before departure)
    let leadMinutes: Int
    /// Absolute fire date: departure time − lead minutes, on the day the reminder was set
    let fireDate: Date
    /// Non-nil when the departure has a seasonal availability restriction
    let seasonalNote: String?

    var departureDisplayString: String {
        String(format: "%02d:%02d", departureHour, departureMinute)
    }

    /// Composite key that uniquely identifies a departure slot (route + stop + direction + time)
    static func matchKey(routeId: String, stopId: String, direction: String, hour: Int, minute: Int) -> String {
        "\(routeId)|\(stopId)|\(direction)|\(hour):\(minute)"
    }

    var matchKey: String {
        BusReminder.matchKey(
            routeId: routeId, stopId: stopId, direction: direction,
            hour: departureHour, minute: departureMinute
        )
    }
}
