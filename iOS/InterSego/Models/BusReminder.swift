/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

struct BusReminder: Identifiable, Equatable {
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
    /// Fire time-of-day: departure time − lead minutes. For daily reminders, only the
    /// time component is meaningful (date reflects creation day).
    let fireDate: Date
    /// Non-nil when the departure has a seasonal availability restriction
    let seasonalNote: String?
    /// True when this reminder fires every day (smart-skips days the bus doesn't run).
    let isDaily: Bool
    /// Seasonal availability stored so the scheduler can skip non-running days.
    let seasonalAvailability: SeasonalAvailability?
    /// Day type this departure belongs to (used for smart-skip day-of-week check).
    let dayType: DayType?
    /// Server-assigned UUID returned after a successful POST /reminders. Nil until synced.
    var serverId: String?

    // CodingKeys in the struct body so Swift can synthesize encode(to:)
    enum CodingKeys: String, CodingKey {
        case id, routeId, routeNumber, stopId, stopName, direction
        case departureHour, departureMinute, leadMinutes, fireDate, seasonalNote
        case isDaily, seasonalAvailability, dayType, serverId
    }

    init(
        id: String, routeId: String, routeNumber: String, stopId: String, stopName: String,
        direction: String, departureHour: Int, departureMinute: Int, leadMinutes: Int,
        fireDate: Date, seasonalNote: String?,
        isDaily: Bool = false, seasonalAvailability: SeasonalAvailability? = nil,
        dayType: DayType? = nil, serverId: String? = nil
    ) {
        self.id = id
        self.routeId = routeId
        self.routeNumber = routeNumber
        self.stopId = stopId
        self.stopName = stopName
        self.direction = direction
        self.departureHour = departureHour
        self.departureMinute = departureMinute
        self.leadMinutes = leadMinutes
        self.fireDate = fireDate
        self.seasonalNote = seasonalNote
        self.isDaily = isDaily
        self.seasonalAvailability = seasonalAvailability
        self.dayType = dayType
        self.serverId = serverId
    }

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

// MARK: - Codable (backward-compatible: old JSON without isDaily/seasonalAvailability is accepted)

extension BusReminder: Codable {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        routeId = try c.decode(String.self, forKey: .routeId)
        routeNumber = try c.decode(String.self, forKey: .routeNumber)
        stopId = try c.decode(String.self, forKey: .stopId)
        stopName = try c.decode(String.self, forKey: .stopName)
        direction = try c.decode(String.self, forKey: .direction)
        departureHour = try c.decode(Int.self, forKey: .departureHour)
        departureMinute = try c.decode(Int.self, forKey: .departureMinute)
        leadMinutes = try c.decode(Int.self, forKey: .leadMinutes)
        fireDate = try c.decode(Date.self, forKey: .fireDate)
        seasonalNote = try? c.decode(String.self, forKey: .seasonalNote)
        isDaily = (try? c.decode(Bool.self, forKey: .isDaily)) ?? false
        seasonalAvailability = try? c.decode(SeasonalAvailability.self, forKey: .seasonalAvailability)
        dayType = try? c.decode(DayType.self, forKey: .dayType)
        serverId = try? c.decode(String.self, forKey: .serverId)
    }
}
