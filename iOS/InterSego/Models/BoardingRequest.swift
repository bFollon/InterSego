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

/// Outbound POST body for `POST /boardings`.
struct BoardingRequest: Encodable {
    let stopId: String
    let routeId: String
    let direction: String
    let tripKey: String
    let boardedAt: String
    let scheduledDepartureTime: String?

    // MARK: - Factory

    /// Build a boarding request from the data available in NextDepartureView at tap time.
    ///
    /// - Parameters:
    ///   - stop: The stop where the user is boarding.
    ///   - routeId: Route ID from the current `TaggedDeparture`.
    ///   - direction: Active direction string from the view's `@State`.
    ///   - dayType: Resolved day type (never `.weekend` or `.holiday`).
    ///   - departure: The scheduled departure the user is boarding.
    static func make(
        stop: BusStop,
        routeId: String,
        direction: String,
        dayType: DayType,
        departure: DepartureTime
    ) -> BoardingRequest {
        let utcFormatter = ISO8601DateFormatter()
        utcFormatter.formatOptions = [.withInternetDateTime]
        utcFormatter.timeZone = TimeZone(identifier: "UTC")!

        let now = Date()

        // Build scheduledDepartureTime: today + departure.hour:minute in local TZ → UTC
        var components = Calendar.current.dateComponents([.year, .month, .day], from: now)
        components.hour = departure.hour
        components.minute = departure.minute
        components.second = 0
        let scheduledLocal = Calendar.current.date(from: components)
        let scheduledISO = scheduledLocal.map { utcFormatter.string(from: $0) }

        let tripKey = "\(routeId)|\(direction)|\(dayType.rawValue)|\(departure.displayString)"

        return BoardingRequest(
            stopId: stop.id,
            routeId: routeId,
            direction: direction,
            tripKey: tripKey,
            boardedAt: utcFormatter.string(from: now),
            scheduledDepartureTime: scheduledISO,
        )
    }
}
