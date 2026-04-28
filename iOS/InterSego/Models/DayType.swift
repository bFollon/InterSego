/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

enum DayType: String, Codable {
    case weekday = "WEEKDAY"
    case saturday = "SATURDAY"
    case sunday = "SUNDAY"
    case weekend = "WEEKEND"
    case holiday = "HOLIDAY"
}
