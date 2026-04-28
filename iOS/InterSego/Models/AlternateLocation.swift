/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// A named sub-location of a `BusStop` where specific trips depart or arrive
/// instead of the stop's primary coordinates.
struct AlternateLocation: Codable, Hashable {
    let id: String
    let name: String
    let coordinates: String // "lat, lon"

    var resolvedLatitude: Double? {
        let parts = coordinates.split(separator: ",")
        guard parts.count >= 2 else { return nil }
        return Double(parts[0].trimmingCharacters(in: .whitespaces))
    }

    var resolvedLongitude: Double? {
        let parts = coordinates.split(separator: ",")
        guard parts.count >= 2 else { return nil }
        return Double(parts[1].trimmingCharacters(in: .whitespaces))
    }
}
