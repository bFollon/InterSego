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
/// Update `boardingServerURL` and `boardingAPIKey` once the server is deployed
/// and the Cloudflare Tunnel domain is known.
enum AppConfig {
    /// Base URL of the boarding notification server (no trailing slash).
    static let boardingServerURL = "https://intersego.bfollon.dev"
}
