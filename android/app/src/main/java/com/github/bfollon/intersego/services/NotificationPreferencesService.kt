/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.content.Context

object NotificationPreferencesService {

    private const val PREFS_NAME  = "notification_prefs"
    private const val KEY_CHOICE  = "notification_choice_made"
    private const val KEY_SEVERITY = "alert_min_severity"

    fun hasUserMadeNotificationChoice(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHOICE, false)

    fun getAlertMinSeverity(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SEVERITY, "info") ?: "info"

    fun saveChoice(context: Context, minSeverity: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_CHOICE, true)
            putString(KEY_SEVERITY, minSeverity)
            apply()
        }
    }
}
