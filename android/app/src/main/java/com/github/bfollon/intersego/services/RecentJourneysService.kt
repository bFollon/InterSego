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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A remembered origin/destination pair for the journey planner — this is a commuting app, people repeat journeys. */
@Serializable
data class RecentJourney(
    val originStopId: String,
    val originName: String,
    val destinationStopId: String,
    val destinationName: String,
)

/** Persists the last few origin/destination pairs searched, most-recent first. */
object RecentJourneysService {
    private const val PREFS_NAME = "recent_journeys"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 5

    private val json = Json { ignoreUnknownKeys = true }

    fun recent(context: Context): List<RecentJourney> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RecentJourney>>(raw) }.getOrDefault(emptyList())
    }

    fun record(context: Context, journey: RecentJourney) {
        val existing = recent(context).filterNot {
            it.originStopId == journey.originStopId && it.destinationStopId == journey.destinationStopId
        }
        val updated = (listOf(journey) + existing).take(MAX_ENTRIES)
        prefs(context).edit().putString(KEY_ENTRIES, json.encodeToString(updated)).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
