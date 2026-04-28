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
import android.content.SharedPreferences

/**
 * Manages user consent preferences for error reporting and analytics.
 * Must be initialized before ErrorReportingService or AnalyticsService.
 * Equivalent to iOS MonitoringPreferencesService.
 */
object MonitoringPreferencesService {

    private const val PREFS_NAME = "monitoring_preferences"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_CHOICE_MADE = "monitoring_choice_made"
    private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
    private const val KEY_ANALYTICS_CHOICE_MADE = "analytics_choice_made"

    private var sharedPreferences: SharedPreferences? = null

    fun initialize(context: Context) {
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        DebugConfig.debugPrint("MonitoringPreferencesService initialized")
    }

    fun hasUserOptedIn(): Boolean {
        val prefs = sharedPreferences ?: run {
            DebugConfig.debugWarn("MonitoringPreferencesService not initialized")
            return false
        }
        if (!hasUserMadeChoice()) return false
        return prefs.getBoolean(KEY_MONITORING_ENABLED, false)
    }

    fun hasUserMadeChoice(): Boolean {
        val prefs = sharedPreferences ?: return false
        return prefs.getBoolean(KEY_CHOICE_MADE, false)
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        val prefs = sharedPreferences ?: run {
            DebugConfig.debugError("MonitoringPreferencesService not initialized")
            return
        }
        prefs.edit().apply {
            putBoolean(KEY_MONITORING_ENABLED, enabled)
            putBoolean(KEY_CHOICE_MADE, true)
            apply()
        }
        DebugConfig.debugPrint("MonitoringPreferencesService: error reporting ${if (enabled) "enabled" else "disabled"}")
    }

    fun hasUserOptedInToAnalytics(): Boolean {
        val prefs = sharedPreferences ?: run {
            DebugConfig.debugWarn("MonitoringPreferencesService not initialized")
            return false
        }
        if (!hasUserMadeAnalyticsChoice()) return false
        return prefs.getBoolean(KEY_ANALYTICS_ENABLED, false)
    }

    fun hasUserMadeAnalyticsChoice(): Boolean {
        val prefs = sharedPreferences ?: return false
        return prefs.getBoolean(KEY_ANALYTICS_CHOICE_MADE, false)
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        val prefs = sharedPreferences ?: run {
            DebugConfig.debugError("MonitoringPreferencesService not initialized")
            return
        }
        prefs.edit().apply {
            putBoolean(KEY_ANALYTICS_ENABLED, enabled)
            putBoolean(KEY_ANALYTICS_CHOICE_MADE, true)
            apply()
        }
        DebugConfig.debugPrint("MonitoringPreferencesService: analytics ${if (enabled) "enabled" else "disabled"}")
    }
}
