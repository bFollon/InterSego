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
 * Manages preferences for guided mode (direction picker) feature.
 * Singleton pattern matching CoordinateCache.
 */
object GuidedModePrefs {
    private const val PREFS_NAME = "guided_mode"
    private const val KEY_ENABLED = "guided_mode_enabled"
    private const val KEY_TUTORIAL_SHOWN = "tutorial_shown"
    private const val KEY_LAST_VIEW_PREFIX = "last_view_"

    private lateinit var sharedPreferences: SharedPreferences

    /**
     * Initialize the preferences with application context.
     * Must be called in MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        DebugConfig.debugPrint("✅ GuidedModePrefs initialized")
    }

    /**
     * Check if guided mode is enabled
     */
    fun isGuidedModeEnabled(): Boolean {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ GuidedModePrefs not initialized")
            return false
        }
        return sharedPreferences.getBoolean(KEY_ENABLED, true)
    }

    /**
     * Set guided mode enabled/disabled
     */
    fun setGuidedModeEnabled(enabled: Boolean) {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ GuidedModePrefs not initialized")
            return
        }
        sharedPreferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        DebugConfig.debugPrint("🎯 Guided mode ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Check if the direction picker tutorial has been shown
     */
    fun isTutorialShown(): Boolean {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ GuidedModePrefs not initialized")
            return false
        }
        return sharedPreferences.getBoolean(KEY_TUTORIAL_SHOWN, false)
    }

    /**
     * Mark the direction picker tutorial as shown
     */
    fun setTutorialShown() {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ GuidedModePrefs not initialized")
            return
        }
        sharedPreferences.edit()
            .putBoolean(KEY_TUTORIAL_SHOWN, true)
            .apply()
        DebugConfig.debugPrint("✅ Guided mode tutorial marked as shown")
    }

    /**
     * Get the last selected view ID for a given stop and route.
     * Returns null if no previous selection exists.
     */
    fun getLastViewId(stopId: String, routeId: String): String? {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ GuidedModePrefs not initialized")
            return null
        }
        val key = "$KEY_LAST_VIEW_PREFIX${stopId}_$routeId"
        return sharedPreferences.getString(key, null)
    }

    /**
     * Save the last selected view ID for a given stop and route
     */
    fun saveLastViewId(stopId: String, routeId: String, viewId: String) {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ GuidedModePrefs not initialized")
            return
        }
        val key = "$KEY_LAST_VIEW_PREFIX${stopId}_$routeId"
        sharedPreferences.edit()
            .putString(key, viewId)
            .apply()
        DebugConfig.debugPrint("💾 Saved last view $viewId for stop $stopId, route $routeId")
    }
}
