/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.util.Log

/**
 * Global debug configuration for the entire Android application
 * Android equivalent of iOS DebugConfig
 */
object DebugConfig {

    private const val TAG = "InterSego"

    /**
     * Default debug setting when no environment variable is set
     * Change this value to enable/disable debug logging by default
     */
    private const val DEFAULT_DEBUG_ENABLED = true // Enable by default for development

    /**
     * Master debug flag - controls all debug output in the application
     * TODO: Can be extended to read from BuildConfig.DEBUG or system properties
     */
    var isDebugEnabled: Boolean = DEFAULT_DEBUG_ENABLED

    /**
     * Detailed logging flag - controls verbose debug output that might impact performance
     * Should be used for detailed parsing logs, coordinate extractions, etc.
     * Disabled by default to avoid performance impact in production
     */
    var isDetailedLoggingEnabled: Boolean = false

    /**
     * Conditional debug print - only prints when debug is enabled
     * Uses Android Log.d() for debug level logging
     * @param message The message to print
     */
    fun debugPrint(message: String) {
        if (isDebugEnabled) {
            Log.d(TAG, "[DEBUG] $message")
        }
    }

    /**
     * Conditional debug print with tag - only prints when debug is enabled
     * @param tag Custom tag for this log message
     * @param message The message to print
     */
    fun debugPrint(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.d("$TAG-$tag", "[DEBUG] $message")
        }
    }

    /**
     * Debug print for errors - always logs errors regardless of debug flag
     * @param message The error message to print
     * @param throwable Optional exception to log
     */
    fun debugError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[ERROR] $message", throwable)
    }

    /**
     * Debug print for warnings
     * @param message The warning message to print
     */
    fun debugWarn(message: String) {
        if (isDebugEnabled) {
            Log.w(TAG, "[WARN] $message")
        }
    }

}
