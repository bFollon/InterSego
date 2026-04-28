/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// A boarding event returned by `GET /boardings`.
///
/// The server stores these as-is; all trip matching and ETA computation
/// happen on the client using local timetable data.
struct BoardingEvent: Codable, Identifiable {
    let id: String
    let stopId: String
    let routeId: String
    let direction: String
    /// Synthetic trip key: "{routeId}|{direction}|{dayType}|{HH:MM}"
    let tripKey: String
    /// ISO 8601 UTC timestamp of when the user boarded.
    let boardedAt: String
    /// ISO 8601 UTC scheduled departure at the boarding stop. May be absent.
    let scheduledDepartureTime: String?
    let expiresAt: String

    // MARK: - Derived date helpers

    private static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let isoFormatterNoFrac: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private static func parseISO(_ string: String) -> Date? {
        isoFormatter.date(from: string) ?? isoFormatterNoFrac.date(from: string)
    }

    var boardedAtDate: Date? { BoardingEvent.parseISO(boardedAt) }

    var scheduledDepartureDate: Date? {
        scheduledDepartureTime.flatMap { BoardingEvent.parseISO($0) }
    }

    /// Positive = bus was late; negative = bus was early.
    var latenessSeconds: TimeInterval? {
        guard let boarded = boardedAtDate, let scheduled = scheduledDepartureDate else { return nil }
        return boarded.timeIntervalSince(scheduled)
    }
}
