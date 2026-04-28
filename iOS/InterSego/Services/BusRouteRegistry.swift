/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

enum BusRouteRegistry {
    static func knownRoutes() -> [BusRoute] {
        TimetableLoader().loadAllRoutes()
    }
}
