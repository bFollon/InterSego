/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.linecapp.services

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Manages persistent coordinate caching for bus stop addresses
 * Equivalent to iOS CoordinateCache using UserDefaults
 */
object CoordinateCache {

    private const val CACHE_KEY = "bus_stop_coordinates_cache"
    private const val CACHE_VERSION_KEY = "coordinate_cache_version"
    private const val CURRENT_CACHE_VERSION = 1
    internal const val CACHE_EXPIRY_DAYS = 30L

    private lateinit var sharedPreferences: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Initialize the cache with application context
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences("coordinate_cache", Context.MODE_PRIVATE)

        // Check cache version and clear if outdated
        val currentVersion = sharedPreferences.getInt(CACHE_VERSION_KEY, 0)
        if (currentVersion != CURRENT_CACHE_VERSION) {
            DebugConfig.debugPrint("🔄 Cache version mismatch, clearing coordinate cache")
            clearAll()
            sharedPreferences.edit()
                .putInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION)
                .apply()
        }
    }

    /**
     * Get cached coordinates for an address
     */
    fun getCoordinates(address: String): Location? {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ CoordinateCache not initialized")
            return null
        }

        val cacheJson = sharedPreferences.getString(CACHE_KEY, null) ?: return null

        return try {
            val cache = json.decodeFromString<Map<String, CachedCoordinate>>(cacheJson)
            val cached = cache[address]

            if (cached != null && !cached.isExpired) {
                Location("cache").apply {
                    latitude = cached.latitude
                    longitude = cached.longitude
                }
            } else {
                null
            }
        } catch (exception: Exception) {
            DebugConfig.debugPrint("❌ Failed to decode coordinate cache: ${exception.message}")
            null
        }
    }

    /**
     * Cache coordinates for an address
     */
    fun setCoordinates(location: Location, address: String) {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ CoordinateCache not initialized")
            return
        }

        val cache = loadCache().toMutableMap()
        cache[address] = CachedCoordinate(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis()
        )
        saveCache(cache)
    }

    /**
     * Clear expired cache entries
     */
    fun cleanupExpiredEntries() {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ CoordinateCache not initialized")
            return
        }

        val cache = loadCache().toMutableMap()
        val originalCount = cache.size

        cache.entries.removeAll { it.value.isExpired }

        if (cache.size != originalCount) {
            saveCache(cache)
            DebugConfig.debugPrint("🧹 Cleaned up ${originalCount - cache.size} expired coordinate cache entries")
        }
    }

    /**
     * Clear all cached coordinates
     */
    fun clearAll() {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ CoordinateCache not initialized")
            return
        }

        sharedPreferences.edit()
            .remove(CACHE_KEY)
            .apply()
        DebugConfig.debugPrint("🗑️ Cleared all coordinate cache")
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): Pair<Int, String> {
        if (!::sharedPreferences.isInitialized) {
            return 0 to "0B"
        }

        val cache = loadCache()
        val count = cache.size
        val sizeEstimate = count * 150 // Rough estimate: 150 bytes per entry
        val sizeString = when {
            sizeEstimate < 1024 -> "${sizeEstimate}B"
            sizeEstimate < 1024 * 1024 -> "${sizeEstimate / 1024}KB"
            else -> "${sizeEstimate / (1024 * 1024)}MB"
        }
        return count to sizeString
    }

    /**
     * Load cache from SharedPreferences
     */
    private fun loadCache(): Map<String, CachedCoordinate> {
        val cacheJson = sharedPreferences.getString(CACHE_KEY, null) ?: return emptyMap()

        return try {
            json.decodeFromString<Map<String, CachedCoordinate>>(cacheJson)
        } catch (exception: Exception) {
            DebugConfig.debugPrint("❌ Failed to load coordinate cache: ${exception.message}")
            emptyMap()
        }
    }

    /**
     * Save cache to SharedPreferences
     */
    private fun saveCache(cache: Map<String, CachedCoordinate>) {
        try {
            val cacheJson = json.encodeToString(cache)
            sharedPreferences.edit()
                .putString(CACHE_KEY, cacheJson)
                .apply()
        } catch (exception: Exception) {
            DebugConfig.debugPrint("❌ Failed to save coordinate cache: ${exception.message}")
        }
    }
}

/**
 * Represents a cached coordinate with expiration
 */
@Serializable
data class CachedCoordinate(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
) {
    /**
     * Check if this cached coordinate has expired
     */
    val isExpired: Boolean
        get() {
            val expiryTime = timestamp + (CoordinateCache.CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000)
            return System.currentTimeMillis() > expiryTime
        }
}
