/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

struct BusRoute: Codable, Identifiable, Hashable {
    let id: String
    let number: String // "1", "2", "40"
    let name: String // "Centro - San Lorenzo"
    let origin: String
    let destination: String
    let pdfURL: String
    let routeType: RouteType
    let isCircular: Bool
    let color: String?
    let active: Bool

    init(id: String = UUID().uuidString, number: String, name: String,
         origin: String, destination: String, pdfURL: String,
         routeType: RouteType, isCircular: Bool = false, color: String? = nil, active: Bool = true)
    {
        self.id = id
        self.number = number
        self.name = name
        self.origin = origin
        self.destination = destination
        self.pdfURL = pdfURL
        self.routeType = routeType
        self.isCircular = isCircular
        self.color = color
        self.active = active
    }

    var displayName: String {
        "Línea \(number): \(name)"
    }

    var shortName: String {
        "L\(number)"
    }
}
