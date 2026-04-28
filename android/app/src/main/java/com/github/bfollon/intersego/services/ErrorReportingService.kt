/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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

    /**
     * Submit user feedback (bug report) to BugSink.
     * Safe to call even if Sentry is not initialized.
     */
    fun submitFeedback(name: String, email: String, message: String) {
        val userName = name.ifBlank { "Anonymous" }
        val userEmail = email.ifBlank { "not provided" }

        val messageHash = message.hashCode().toString()

        Sentry.configureScope { scope ->
            scope.fingerprint = listOf("user-bug-report", messageHash)
        }

        val fullMessage = buildString {
            appendLine("=== User Bug Report ===")
            appendLine("Name: $userName")
            appendLine("Email: $userEmail")
            appendLine()
            appendLine("Message:")
            appendLine(message)
        }

        Sentry.captureMessage(fullMessage, io.sentry.SentryLevel.WARNING)
        DebugConfig.debugPrint("ErrorReportingService: submitted user feedback")
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
