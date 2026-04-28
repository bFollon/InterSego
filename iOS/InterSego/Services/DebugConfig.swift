/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
