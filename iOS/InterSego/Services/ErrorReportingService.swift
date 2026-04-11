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
}
