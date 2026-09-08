/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// A user-favorited stop+route+direction combination, for quick access from "Más opciones".
/// Identity is the triple (stopId, routeId, viewId) — not just the stop — since a physical
/// stop can be served by multiple routes/directions and favoriting must be precise about which.
struct FavoriteStop: Identifiable, Codable, Equatable {
    let stopId: String
    let stopName: String
    let routeId: String
    let routeNumber: String
    let viewId: String
    let direction: String
    let mergedDirectionLabel: String?

    var id: String { matchKey }

    /// Composite key that uniquely identifies a stop+route+direction favorite.
    static func matchKey(stopId: String, routeId: String, viewId: String) -> String {
        "\(stopId)|\(routeId)|\(viewId)"
    }

    var matchKey: String {
        FavoriteStop.matchKey(stopId: stopId, routeId: routeId, viewId: viewId)
    }

    /// Display label for the direction, preferring the merged-directions label (e.g. M4 circular).
    var directionLabel: String {
        mergedDirectionLabel ?? direction
    }
}
