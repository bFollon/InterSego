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
import androidx.core.content.edit
import com.github.bfollon.intersego.data.FavoriteStop
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages the user's favorite stops (stop + route + direction combos).
 * Singleton pattern matching GuidedModePrefs.
 */
object FavoriteStopsPrefs {
    private const val PREFS_NAME = "favorite_stops"
    private const val KEY_FAVORITES = "favorites"

    private lateinit var sharedPreferences: SharedPreferences

    /**
     * Initialize the preferences with application context.
     * Must be called in MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        DebugConfig.debugPrint("✅ FavoriteStopsPrefs initialized")
    }

    /** All favorites, most-recently-added first. */
    fun getAll(): List<FavoriteStop> {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ FavoriteStopsPrefs not initialized")
            return emptyList()
        }
        val json = sharedPreferences.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<FavoriteStop>>(json) }.getOrDefault(emptyList())
    }

    fun isFavorite(stopId: String, routeId: String, viewId: String): Boolean {
        val key = FavoriteStop.matchKey(stopId, routeId, viewId)
        return getAll().any { it.matchKey == key }
    }

    /** Adds the favorite (moving it to the front if already present), or removes it if already favorited. */
    fun toggle(favorite: FavoriteStop) {
        val current = getAll().toMutableList()
        val existingIndex = current.indexOfFirst { it.matchKey == favorite.matchKey }
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
        } else {
            current.add(0, favorite)
        }
        save(current)
    }

    fun remove(matchKey: String) {
        save(getAll().filterNot { it.matchKey == matchKey })
    }

    private fun save(favorites: List<FavoriteStop>) {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ FavoriteStopsPrefs not initialized")
            return
        }
        sharedPreferences.edit {
            putString(KEY_FAVORITES, Json.encodeToString(favorites))
        }
    }
}
