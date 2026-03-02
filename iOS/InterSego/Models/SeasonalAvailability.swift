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
    case summerOnly = "SUMMER_ONLY"

    static let defaultSummerMonths: Set<Int> = [7, 8] // July, August

    func runsIn(month: Int, summerMonths: Set<Int> = defaultSummerMonths) -> Bool {
        switch self {
        case .yearRound:
            return true
        case .summerOnly:
            return summerMonths.contains(month)
        case .schoolOnly:
            return !summerMonths.contains(month)
        }
    }
}
