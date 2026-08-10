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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import com.github.bfollon.intersego.BuildConfig

data class WhatsNewEntry(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/**
 * Version-gated "what's new" notice, shown once per app update.
 *
 * Unlike the per-feature tutorials (`RemindersTutorial`, `LiveUpdateTutorialSheet`), which key
 * off "has this screen been visited before", this keys off "has this app version been seen
 * before" — so it surfaces on first launch after an update, before the user has to go looking
 * for whatever changed (e.g. a feature moving to a different screen).
 *
 * `entries` should be updated (and cleared once shipped) alongside each version bump that
 * warrants an announcement — see `versionName` in app/build.gradle.kts.
 */
object WhatsNewService {
    private const val PREFS_NAME = "whats_new_prefs"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

    /** The entries to show for the current version. Empty once there's nothing to announce. */
    val entries: List<WhatsNewEntry> = listOf(
        WhatsNewEntry(
            icon = Icons.Filled.Route,
            title = "Planifica tu viaje",
            body = "Nuevo en “Más opciones”: dinos de dónde a dónde quieres ir y te mostramos las mejores combinaciones de autobuses, con transbordos incluidos. Elige salir a una hora concreta o llegar antes de una hora límite."
        ),
    )

    /**
     * True if the current version hasn't been announced yet on this device.
     *
     * No last-seen version recorded means one of two things: a genuinely fresh install (in
     * which case there's nothing to announce — the user never saw the old layout), or an
     * existing install upgrading into the very first version that ships this mechanism (in
     * which case they *should* see it). [MonitoringPreferencesService.hasUserMadeAnalyticsChoice]
     * distinguishes the two: that choice is only ever made once, on a screen every install has
     * gone through since long before this feature existed, so its presence means the app has
     * run on this device before.
     */
    fun shouldShow(context: Context): Boolean {
        if (entries.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeen = prefs.getString(KEY_LAST_SEEN_VERSION, null)
            ?: run {
                val isExistingInstall = MonitoringPreferencesService.hasUserMadeAnalyticsChoice()
                if (!isExistingInstall) {
                    markAsSeen(context)
                }
                return isExistingInstall
            }
        return lastSeen != BuildConfig.VERSION_NAME
    }

    fun markAsSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_SEEN_VERSION, BuildConfig.VERSION_NAME)
        }
    }
}
