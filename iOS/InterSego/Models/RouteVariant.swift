/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

struct RouteVariant: Identifiable, Hashable {
    let id: String
    let label: String
    let stops: [BusStop]
    let direction: String
    let departureLabel: String?

    init(id: String, label: String, stops: [BusStop], direction: String,
         departureLabel: String? = nil)
    {
        self.id = id
        self.label = label
        self.stops = stops
        self.direction = direction
        self.departureLabel = departureLabel
    }
}
