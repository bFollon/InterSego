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

package com.github.bfollon.intersego.services

import android.content.Context
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

/**
 * Service for error reporting via Bugsink (Sentry-compatible).
 *
 * Initialization is gated on user consent. If the user has not opted in,
 * Sentry is never started and captureError/captureMessage calls are no-ops.
 * Equivalent to iOS ErrorReportingService.
 */
object ErrorReportingService {

    /**
     * Initialize the Sentry SDK.
     * Must be called after MonitoringPreferencesService is initialized.
     */
    fun initialize(context: Context) {
        if (!MonitoringPreferencesService.hasUserOptedIn()) {
            DebugConfig.debugPrint("ErrorReportingService: skipping init (user has not opted in)")
            return
        }

        SentryAndroid.init(context) { options ->
            options.dsn = Secrets.sentryDsn
            options.isDebug = false
            options.environment = if (isDebugBuild()) "debug" else "production"
        }

        DebugConfig.debugPrint("ErrorReportingService: Sentry initialized")
    }

    /**
     * Capture an exception and send it to Bugsink.
     * Safe to call even if Sentry is not initialized.
     */
    fun captureError(throwable: Throwable, context: Map<String, Any> = emptyMap()) {
        Sentry.withScope { scope ->
            context.forEach { (key, value) ->
                scope.setExtra(key, value.toString())
            }
            Sentry.captureException(throwable)
        }
        DebugConfig.debugPrint("ErrorReportingService: captured error: ${throwable.message}")
    }

    /**
     * Capture a plain message at error level.
     * Safe to call even if Sentry is not initialized.
     */
    fun captureMessage(message: String) {
        Sentry.captureMessage(message)
        DebugConfig.debugPrint("ErrorReportingService: captured message: $message")
    }

    private fun isDebugBuild(): Boolean {
        return try {
            val buildConfigClass = Class.forName("com.github.bfollon.intersego.BuildConfig")
            buildConfigClass.getField("DEBUG").getBoolean(null)
        } catch (_: Exception) {
            false
        }
    }
}
