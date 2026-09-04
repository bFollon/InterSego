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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import com.github.bfollon.intersego.BuildConfig

data class WhatsNewEntry(
    val icon: ImageVector,
    val title: String,
    val body: String,
    /** The version this entry shipped in, e.g. "2.11.0" — see `versionName` in app/build.gradle.kts. */
    val version: String,
)

/**
 * Version-*range*-gated "what's new" notice, shown once per app update.
 *
 * Unlike the per-feature tutorials (`RemindersTutorial`, `LiveUpdateTutorialSheet`), which key
 * off "has this screen been visited before", this keys off "has this app version been seen
 * before" — so it surfaces on first launch after an update, before the user has to go looking
 * for whatever changed (e.g. a feature moving to a different screen).
 *
 * `entries` is append-only: each release adds new version-tagged entries, older ones are never
 * removed. A user who skips versions sees everything they missed ([entriesToShow]), not just
 * whatever shipped in the version they happen to update to — capped at [MAX_ENTRIES_TO_SHOW] so
 * someone who hasn't updated in a very long time doesn't get a wall of old announcements.
 */
object WhatsNewService {
    private const val PREFS_NAME = "whats_new_prefs"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

    /** Upper bound on how many past entries to show at once, oldest-missed dropped first. */
    private const val MAX_ENTRIES_TO_SHOW = 5

    /**
     * All announcements ever shipped, oldest first. Append new ones here on every release that
     * warrants an announcement — never remove or overwrite past entries.
     */
    val entries: List<WhatsNewEntry> = listOf(
        WhatsNewEntry(
            icon = Icons.Filled.Route,
            title = "Planifica tu viaje",
            body = "Nuevo en “Más opciones”: dinos de dónde a dónde quieres ir y te mostramos las mejores combinaciones de autobuses, con transbordos incluidos. Elige salir a una hora concreta o llegar antes de una hora límite.",
            version = "2.11.0",
        ),
        WhatsNewEntry(
            icon = Icons.Filled.Tune,
            title = "Ajusta tu margen de conexión",
            body = "En Configuración → “Planifica tu viaje” puedes personalizar la espera máxima en un transbordo y los márgenes de seguridad que usa el planificador de rutas al buscar combinaciones.",
            version = "2.13.0",
        ),
        WhatsNewEntry(
            icon = Icons.Filled.Dashboard,
            title = "Personaliza tu pantalla de inicio",
            body = "En Configuración → “Acción principal” puedes elegir qué opción aparece en la tarjeta principal de inicio: Líneas de bus, Planificar viaje, Mis recordatorios o Consultar otro día. Las demás siguen disponibles en “Más opciones”.",
            version = "2.14.0",
        ),
    )

    /**
     * Entries the user hasn't seen yet: `version > lastSeenVersion` and `version <= currentVersion`,
     * sorted ascending and capped to the most recent [MAX_ENTRIES_TO_SHOW].
     */
    fun entriesToShow(context: Context): List<WhatsNewEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeen = prefs.getString(KEY_LAST_SEEN_VERSION, null) ?: "0.0.0"
        val currentVersion = BuildConfig.VERSION_NAME
        val inRange = entries.filter {
            compareSemVer(it.version, lastSeen) > 0 && compareSemVer(it.version, currentVersion) <= 0
        }
        return inRange.sortedWith { a, b -> compareSemVer(a.version, b.version) }
            .takeLast(MAX_ENTRIES_TO_SHOW)
    }

    /**
     * True if there's at least one unseen entry to announce on this device.
     *
     * No last-seen version recorded means one of two things: a genuinely fresh install (in
     * which case there's nothing to announce — the user never saw the old layout), or an
     * existing install upgrading into the very first version that ships this mechanism (in
     * which case they *should* see whatever they missed). [MonitoringPreferencesService.hasUserMadeAnalyticsChoice]
     * distinguishes the two: that choice is only ever made once, on a screen every install has
     * gone through since long before this feature existed, so its presence means the app has
     * run on this device before.
     */
    fun shouldShow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_SEEN_VERSION, null) == null) {
            val isExistingInstall = MonitoringPreferencesService.hasUserMadeAnalyticsChoice()
            if (!isExistingInstall) {
                markAsSeen(context)
                return false
            }
        }
        return entriesToShow(context).isNotEmpty()
    }

    fun markAsSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_SEEN_VERSION, BuildConfig.VERSION_NAME)
        }
    }
}

/**
 * Compares two "MAJOR.MINOR.PATCH" SemVer strings numerically, not lexicographically
 * (`"3.10.0" > "3.9.0"`, which plain string comparison would get wrong). Missing or
 * non-numeric components are treated as 0.
 */
private fun compareSemVer(lhs: String, rhs: String): Int {
    val (lMajor, lMinor, lPatch) = semVerComponents(lhs)
    val (rMajor, rMinor, rPatch) = semVerComponents(rhs)
    if (lMajor != rMajor) return lMajor.compareTo(rMajor)
    if (lMinor != rMinor) return lMinor.compareTo(rMinor)
    return lPatch.compareTo(rPatch)
}

private fun semVerComponents(version: String): Triple<Int, Int, Int> {
    val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
    return Triple(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
}
