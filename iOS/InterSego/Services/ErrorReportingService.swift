/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation
import Sentry

/// Service for error reporting via Bugsink (Sentry-compatible).
///
/// Initialization is gated on user consent. If the user has not opted in,
/// Sentry is never started and captureError/captureMessage calls are no-ops.
/// Equivalent to Android ErrorReportingService.
class ErrorReportingService {
    static let shared = ErrorReportingService()

    private init() {}

    /// Initialize the Sentry SDK.
    /// Must be called only if the user has opted in to error reporting.
    func initialize() {
        guard MonitoringPreferencesService.shared.hasUserOptedIn() else {
            DebugConfig.debugPrint("ErrorReportingService: skipping init (user has not opted in)")
            return
        }

        #if DEBUG
        let environment = "debug"
        #else
        let environment = "production"
        #endif

        SentrySDK.start { options in
            options.dsn = AppConfig.sentryDSN
            options.debug = false
            options.environment = environment
        }

        DebugConfig.debugPrint("ErrorReportingService: Sentry initialized")
    }

    /// Capture an error and send it to Bugsink.
    /// Safe to call even if Sentry is not initialized.
    func captureError(_ error: Error, context: [String: Any] = [:]) {
        let event = Event(error: error)
        if !context.isEmpty {
            event.context = ["context": context]
        }
        SentrySDK.capture(event: event)
        DebugConfig.debugPrint("ErrorReportingService: captured error: \(error.localizedDescription)")
    }

    /// Capture a plain message at error level.
    /// Safe to call even if Sentry is not initialized.
    func captureMessage(_ message: String) {
        SentrySDK.capture(message: message)
        DebugConfig.debugPrint("ErrorReportingService: captured message: \(message)")
    }

    /// Submit user feedback (bug report) to BugSink.
    /// Returns false if the user has not opted into error reporting — caller should fall back to email.
    func submitFeedback(name: String, email: String, message: String) async -> Bool {
        guard MonitoringPreferencesService.shared.hasUserOptedIn() else {
            DebugConfig.debugPrint("ErrorReportingService: submitFeedback skipped (user has not opted in)")
            return false
        }

        let userName = name.isEmpty ? "Anonymous" : name
        let userEmail = email.isEmpty ? "not provided" : email

        let fullMessage = """
        === User Bug Report ===
        Name: \(userName)
        Email: \(userEmail)

        Message:
        \(message)
        """

        let event = Event()
        event.message = SentryMessage(formatted: fullMessage)
        event.fingerprint = ["user-bug-report", String(message.hashValue)]
        SentrySDK.capture(event: event)

        DebugConfig.debugPrint("ErrorReportingService: submitted user feedback")
        return true
    }
}
