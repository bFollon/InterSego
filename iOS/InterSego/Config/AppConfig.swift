/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// App-level configuration constants.
///
/// Secrets (API key, DSN, analytics) live in `Secrets.swift` (gitignored).
/// See `Secrets.swift.template` for the full list.
enum AppConfig {
    /// Base URL of the InterSego server (no trailing slash).
    static let boardingServerURL = "https://intersego.bfollon.dev"
}
