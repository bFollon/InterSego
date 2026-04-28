/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

enum SeasonalAvailability: String, Codable {
    case yearRound = "YEAR_ROUND"
    case schoolOnly = "SCHOOL_ONLY"
    /// Runs July–August only (default summer months, used by M4).
    case summerOnly = "SUMMER_ONLY"
    /// Runs June–September only (M1 summer service: 13 Jun – 13 Sep, approximated as full months).
    case juneToSeptOnly = "JUNE_TO_SEPT_ONLY"
    /// Runs on Mondays and Fridays only (L Y V annotation in M1).
    case monFriOnly = "MON_FRI_ONLY"
    /// Runs on Fridays only (# annotation in M1).
    case friOnly = "FRI_ONLY"

    static let defaultSummerMonths: Set<Int> = [7, 8] // July, August
    static let juneToSeptMonths: Set<Int> = [6, 7, 8, 9] // June–September

    /// Returns true if this departure runs in the given `month` (1–12) and optional `weekday`
    /// (Calendar.weekday: Sunday=1, Monday=2, …, Saturday=7).
    ///
    /// When `weekday` is nil, day-of-week restrictions (`.monFriOnly`, `.friOnly`) are treated
    /// as unrestricted — useful for callers that only have month context.
    /// Short Spanish label shown in the UI alongside a departure time, or nil for year-round service.
    var displayLabel: String? {
        switch self {
        case .yearRound: nil
        case .monFriOnly: "Lun-Vie"
        case .friOnly: "Viernes"
        case .juneToSeptOnly: "Jun-Sep"
        case .summerOnly: "Jul-Ago"
        case .schoolOnly: "Escolar"
        }
    }

    func runsIn(month: Int, weekday: Int? = nil, summerMonths: Set<Int> = defaultSummerMonths) -> Bool {
        switch self {
        case .yearRound: true
        case .summerOnly: summerMonths.contains(month)
        case .juneToSeptOnly: Self.juneToSeptMonths.contains(month)
        case .schoolOnly: !summerMonths.contains(month)
        case .monFriOnly: weekday == nil || weekday == 2 || weekday == 6 // Mon=2, Fri=6
        case .friOnly: weekday == nil || weekday == 6
        }
    }
}
