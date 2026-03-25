/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
        case .yearRound: return nil
        case .monFriOnly: return "Lun-Vie"
        case .friOnly: return "Viernes"
        case .juneToSeptOnly: return "Jun-Sep"
        case .summerOnly: return "Jul-Ago"
        case .schoolOnly: return "Escolar"
        }
    }

    func runsIn(month: Int, weekday: Int? = nil, summerMonths: Set<Int> = defaultSummerMonths) -> Bool {
        switch self {
        case .yearRound: return true
        case .summerOnly: return summerMonths.contains(month)
        case .juneToSeptOnly: return Self.juneToSeptMonths.contains(month)
        case .schoolOnly: return !summerMonths.contains(month)
        case .monFriOnly: return weekday == nil || weekday == 2 || weekday == 6 // Mon=2, Fri=6
        case .friOnly: return weekday == nil || weekday == 6
        }
    }
}
