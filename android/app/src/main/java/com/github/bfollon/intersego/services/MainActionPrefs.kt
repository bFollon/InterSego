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
 * The action bound to Landing's configurable main card (replaces the "Líneas de bus" slot).
 */
enum class MainLandingAction(val id: String, val label: String) {
    ROUTES("routes", "Líneas de bus"),
    ROUTE_PLANNER("route_planner", "Planificar viaje"),
    REMINDERS("reminders", "Mis recordatorios"),
    ANOTHER_DAY("another_day", "Consultar otro día");

    companion object {
        fun fromId(id: String?): MainLandingAction =
            entries.find { it.id == id } ?: ROUTES
    }
}

/**
 * Manages the user's choice of Landing's configurable main action.
 * Singleton pattern matching GuidedModePrefs.
 */
object MainActionPrefs {
    private const val PREFS_NAME = "main_action"
    private const val KEY_ACTION = "main_action_id"

    private lateinit var sharedPreferences: SharedPreferences

    /**
     * Initialize the preferences with application context.
     * Must be called in MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        DebugConfig.debugPrint("✅ MainActionPrefs initialized")
    }

    fun getMainAction(): MainLandingAction {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ MainActionPrefs not initialized")
            return MainLandingAction.ROUTES
        }
        return MainLandingAction.fromId(sharedPreferences.getString(KEY_ACTION, null))
    }

    fun setMainAction(action: MainLandingAction) {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ MainActionPrefs not initialized")
            return
        }
        sharedPreferences.edit()
            .putString(KEY_ACTION, action.id)
            .apply()
        DebugConfig.debugPrint("🎯 Main landing action set to ${action.id}")
    }
}
