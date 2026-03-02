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
import os

enum DebugConfig {
    #if DEBUG
    static var isDebugEnabled = true
    #else
    static var isDebugEnabled = false
    #endif

    static var isDetailedLoggingEnabled = false

    private static let logger = Logger(subsystem: "com.github.bfollon.intersego", category: "InterSego")

    static func debugPrint(_ message: String) {
        if isDebugEnabled {
            logger.debug("[DEBUG] \(message)")
        }
    }

    static func debugError(_ message: String, error: Error? = nil) {
        if let error {
            logger.error("[ERROR] \(message): \(error.localizedDescription)")
        } else {
            logger.error("[ERROR] \(message)")
        }
    }

    static func debugWarn(_ message: String) {
        logger.warning("[WARN] \(message)")
    }
}
