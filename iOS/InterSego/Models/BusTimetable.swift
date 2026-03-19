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

struct BusTimetable: Codable, Identifiable {
    let id: String
    let routeId: String
    let stopId: String
    let dayType: DayType
    var departures: [DepartureTime]
    let direction: String?

    init(id: String = UUID().uuidString, routeId: String, stopId: String,
         dayType: DayType, departures: [DepartureTime], direction: String? = nil) {
        self.id = id
        self.routeId = routeId
        self.stopId = stopId
        self.dayType = dayType
        self.departures = departures
        self.direction = direction
    }

    /// Departures filtered for the current season (month) and optional day of week.
    /// - Parameters:
    ///   - month: calendar month 1–12 (defaults to today)
    ///   - weekday: Calendar.weekday value (Sun=1…Sat=7); nil disables day-of-week filtering
    func seasonalDepartures(month: Int? = nil, weekday: Int? = nil) -> [DepartureTime] {
        let currentMonth = month ?? Calendar.current.component(.month, from: Date())
        return departures.filter { $0.seasonalAvailability.runsIn(month: currentMonth, weekday: weekday) }
    }

    var firstDeparture: DepartureTime? {
        seasonalDepartures().min()
    }

    var lastDeparture: DepartureTime? {
        seasonalDepartures().max()
    }

    func getNextDepartures(currentHour: Int, currentMinute: Int, limit: Int = 5) -> [DepartureTime] {
        Array(
            seasonalDepartures()
                .filter { $0.isFuture(currentHour: currentHour, currentMinute: currentMinute) }
                .sorted()
                .prefix(limit)
        )
    }

    func hasRemainingDepartures(currentHour: Int, currentMinute: Int) -> Bool {
        seasonalDepartures().contains { $0.isFuture(currentHour: currentHour, currentMinute: currentMinute) }
    }

    func displayDescription() -> String {
        let dayTypeStr: String
        switch dayType {
        case .weekday: dayTypeStr = "Laborables"
        case .weekend: dayTypeStr = "Fines de semana"
        case .holiday: dayTypeStr = "Festivos"
        case .saturday: dayTypeStr = "Sábado"
        case .sunday: dayTypeStr = "Domingo"
        }

        if let direction {
            return "\(dayTypeStr) - \(direction)"
        }
        return dayTypeStr
    }
}
