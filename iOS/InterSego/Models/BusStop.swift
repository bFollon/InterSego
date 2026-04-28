/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

struct BusStop: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let area: String?
    let details: String?
    let coordinates: String // "40.948406, -4.116411"
    /// Optional coordinate used for route planning when the bus travels through a different
    /// point than the physical stop (e.g. stop is just after a turn the bus doesn't make).
    /// Falls back to `coordinates` when nil.
    let routingCoordinates: String?
    let routesServed: [String]
    let stopCode: String?
    /// Sub-locations used by specific departures instead of the primary coordinates.
    let alternates: [AlternateLocation]

    init(id: String, name: String, area: String? = nil,
         details: String? = nil, coordinates: String, routingCoordinates: String? = nil,
         routesServed: [String] = [], stopCode: String? = nil,
         alternates: [AlternateLocation] = [])
    {
        self.id = id
        self.name = name
        self.area = area
        self.details = details
        self.coordinates = coordinates
        self.routingCoordinates = routingCoordinates
        self.routesServed = routesServed
        self.stopCode = stopCode
        self.alternates = alternates
    }

    func alternateLocation(id: String) -> AlternateLocation? {
        alternates.first { $0.id == id }
    }

    private var parsedCoordinates: (lat: Double, lon: Double)? {
        let parts = coordinates.split(separator: ",")
        guard parts.count >= 2,
              let lat = Double(parts[0].trimmingCharacters(in: .whitespaces)),
              let lon = Double(parts[1].trimmingCharacters(in: .whitespaces))
        else { return nil }
        return (lat, lon)
    }

    private func parseCoordString(_ s: String) -> (lat: Double, lon: Double)? {
        let parts = s.split(separator: ",")
        guard parts.count >= 2,
              let lat = Double(parts[0].trimmingCharacters(in: .whitespaces)),
              let lon = Double(parts[1].trimmingCharacters(in: .whitespaces))
        else { return nil }
        return (lat, lon)
    }

    var resolvedLatitude: Double? {
        parsedCoordinates?.lat
    }

    var resolvedLongitude: Double? {
        parsedCoordinates?.lon
    }

    var hasCoordinates: Bool {
        parsedCoordinates != nil
    }

    /// Latitude used for route planning. Falls back to `resolvedLatitude` when no routing override is set.
    var routingLatitude: Double? {
        routingCoordinates.flatMap { parseCoordString($0) }?.lat ?? resolvedLatitude
    }

    /// Longitude used for route planning. Falls back to `resolvedLongitude` when no routing override is set.
    var routingLongitude: Double? {
        routingCoordinates.flatMap { parseCoordString($0) }?.lon ?? resolvedLongitude
    }

    var displayName: String {
        if let stopCode {
            return "\(name) (\(stopCode))"
        }
        return name
    }
}
