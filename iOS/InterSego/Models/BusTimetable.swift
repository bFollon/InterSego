/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
         dayType: DayType, departures: [DepartureTime], direction: String? = nil)
    {
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
                .prefix(limit),
        )
    }

    func hasRemainingDepartures(currentHour: Int, currentMinute: Int) -> Bool {
        seasonalDepartures().contains { $0.isFuture(currentHour: currentHour, currentMinute: currentMinute) }
    }

    func displayDescription() -> String {
        let dayTypeStr = switch dayType {
        case .weekday: "Laborables"
        case .weekend: "Fines de semana"
        case .holiday: "Festivos"
        case .saturday: "Sábado"
        case .sunday: "Domingo"
        }

        if let direction {
            return "\(dayTypeStr) - \(direction)"
        }
        return dayTypeStr
    }
}
