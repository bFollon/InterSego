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
 * User-tunable parameters for [JourneyPlannerService]'s connection search (max transfer wait,
 * transfer buffers). Singleton pattern matching [GuidedModePrefs].
 */
object TripPlannerPrefs {
    private const val PREFS_NAME = "trip_planner"
    private const val KEY_MAX_WAIT_MIN = "max_wait_min"
    private const val KEY_BUFFER_SAME_STOP_TRANSCRIBED = "buffer_same_stop_transcribed"
    private const val KEY_BUFFER_SAME_STOP_ESTIMATED = "buffer_same_stop_estimated"
    private const val KEY_BUFFER_WALK_TRANSCRIBED = "buffer_walk_transcribed"
    private const val KEY_BUFFER_WALK_ESTIMATED = "buffer_walk_estimated"

    const val DEFAULT_MAX_WAIT_MIN = 90
    const val DEFAULT_BUFFER_SAME_STOP_TRANSCRIBED = 15
    const val DEFAULT_BUFFER_SAME_STOP_ESTIMATED = 15
    const val DEFAULT_BUFFER_WALK_TRANSCRIBED = 15
    const val DEFAULT_BUFFER_WALK_ESTIMATED = 15

    /** Below this, a transfer buffer is shown with a "tight margin" warning. */
    const val RECOMMENDED_MIN_BUFFER = 15

    private lateinit var sharedPreferences: SharedPreferences

    /** Initialize the preferences with application context. Must be called in MainActivity.onCreate() */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        DebugConfig.debugPrint("✅ TripPlannerPrefs initialized")
    }

    fun getMaxWaitMin(): Int = getInt(KEY_MAX_WAIT_MIN, DEFAULT_MAX_WAIT_MIN)
    fun setMaxWaitMin(value: Int) = putInt(KEY_MAX_WAIT_MIN, value)

    fun getBufferSameStopTranscribed(): Int = getInt(KEY_BUFFER_SAME_STOP_TRANSCRIBED, DEFAULT_BUFFER_SAME_STOP_TRANSCRIBED)
    fun setBufferSameStopTranscribed(value: Int) = putInt(KEY_BUFFER_SAME_STOP_TRANSCRIBED, value)

    fun getBufferSameStopEstimated(): Int = getInt(KEY_BUFFER_SAME_STOP_ESTIMATED, DEFAULT_BUFFER_SAME_STOP_ESTIMATED)
    fun setBufferSameStopEstimated(value: Int) = putInt(KEY_BUFFER_SAME_STOP_ESTIMATED, value)

    fun getBufferWalkTranscribed(): Int = getInt(KEY_BUFFER_WALK_TRANSCRIBED, DEFAULT_BUFFER_WALK_TRANSCRIBED)
    fun setBufferWalkTranscribed(value: Int) = putInt(KEY_BUFFER_WALK_TRANSCRIBED, value)

    fun getBufferWalkEstimated(): Int = getInt(KEY_BUFFER_WALK_ESTIMATED, DEFAULT_BUFFER_WALK_ESTIMATED)
    fun setBufferWalkEstimated(value: Int) = putInt(KEY_BUFFER_WALK_ESTIMATED, value)

    /** Resets all trip-planner tuning parameters to their app defaults. */
    fun resetToDefaults() {
        setMaxWaitMin(DEFAULT_MAX_WAIT_MIN)
        setBufferSameStopTranscribed(DEFAULT_BUFFER_SAME_STOP_TRANSCRIBED)
        setBufferSameStopEstimated(DEFAULT_BUFFER_SAME_STOP_ESTIMATED)
        setBufferWalkTranscribed(DEFAULT_BUFFER_WALK_TRANSCRIBED)
        setBufferWalkEstimated(DEFAULT_BUFFER_WALK_ESTIMATED)
    }

    private fun getInt(key: String, default: Int): Int {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ TripPlannerPrefs not initialized")
            return default
        }
        return sharedPreferences.getInt(key, default)
    }

    private fun putInt(key: String, value: Int) {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ TripPlannerPrefs not initialized")
            return
        }
        sharedPreferences.edit().putInt(key, value).apply()
    }
}
