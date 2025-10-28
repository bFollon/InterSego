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
import com.github.bfollon.linecapp.data.BusTimetable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * High-performance cache service for parsed bus timetables
 * Dramatically reduces app startup time by avoiding PDF re-parsing
 * Adapted from FarmaciasDeGuardia ScheduleCacheService
 */
class TimetableCacheService(private val context: Context) {

    private val cacheDir = File(context.filesDir, "TimetableCache")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = true
    }

    init {
        // Ensure cache directory exists
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            DebugConfig.debugPrint("TimetableCacheService: Created timetable cache directory: ${cacheDir.absolutePath}")
        }
    }

    /**
     * Check if cached timetables exist and are still valid for a route
     */
    fun isCacheValid(routeId: String): Boolean {
        val cacheFile = getCacheFile(routeId)
        val metadataFile = getMetadataFile(routeId)

        if (!cacheFile.exists() || !metadataFile.exists()) {
            return false
        }

        try {
            val metadata = json.decodeFromString<CacheMetadata>(metadataFile.readText())
            val pdfFile = File(context.filesDir, "pdfs/${routeId.lowercase()}.pdf")

            // Check if PDF file exists and hasn't been modified since cache was created
            if (!pdfFile.exists()) {
                DebugConfig.debugPrint("TimetableCacheService: PDF file not found for route $routeId, cache invalid")
                return false
            }

            val pdfLastModified = pdfFile.lastModified()
            val cacheIsValid = pdfLastModified <= metadata.pdfLastModified

            if (cacheIsValid) {
                DebugConfig.debugPrint("TimetableCacheService: Cache valid for route $routeId (PDF: $pdfLastModified, Cache: ${metadata.pdfLastModified})")
            } else {
                DebugConfig.debugPrint("TimetableCacheService: Cache invalid for route $routeId - PDF newer than cache")
            }

            return cacheIsValid

        } catch (e: Exception) {
            DebugConfig.debugError("TimetableCacheService: Error checking cache validity for route $routeId", e)
            return false
        }
    }

    /**
     * Load cached timetables for a route (if valid)
     */
    fun loadCachedTimetables(routeId: String): List<BusTimetable>? {
        if (!isCacheValid(routeId)) {
            return null
        }

        val cacheFile = getCacheFile(routeId)

        try {
            val startTime = System.currentTimeMillis()
            val cachedData = json.decodeFromString<CachedTimetables>(cacheFile.readText())
            val loadTime = System.currentTimeMillis() - startTime

            DebugConfig.debugPrint("TimetableCacheService: Loaded ${cachedData.timetables.size} cached timetables for route $routeId in ${loadTime}ms")
            return cachedData.timetables

        } catch (e: Exception) {
            DebugConfig.debugError("TimetableCacheService: Error loading cached timetables for route $routeId", e)
            // If cache is corrupted, delete it
            deleteCacheFiles(routeId)
            return null
        }
    }

    /**
     * Save parsed timetables to cache
     */
    fun saveTimetablesToCache(routeId: String, timetables: List<BusTimetable>) {
        try {
            val startTime = System.currentTimeMillis()

            // Create cache data
            val cachedData = CachedTimetables(
                routeId = routeId,
                timetables = timetables,
                cacheTimestamp = System.currentTimeMillis()
            )

            // Save timetables to cache file
            val cacheFile = getCacheFile(routeId)
            cacheFile.writeText(json.encodeToString(cachedData))

            // Save metadata
            val pdfFile = File(context.filesDir, "pdfs/${routeId.lowercase()}.pdf")
            val metadata = CacheMetadata(
                routeId = routeId,
                timetableCount = timetables.size,
                cacheTimestamp = System.currentTimeMillis(),
                pdfLastModified = if (pdfFile.exists()) pdfFile.lastModified() else System.currentTimeMillis()
            )

            val metadataFile = getMetadataFile(routeId)
            metadataFile.writeText(json.encodeToString(metadata))

            val saveTime = System.currentTimeMillis() - startTime
            val cacheSize = cacheFile.length() / 1024 // KB

            DebugConfig.debugPrint("TimetableCacheService: Cached ${timetables.size} timetables for route $routeId in ${saveTime}ms (${cacheSize}KB)")

        } catch (e: Exception) {
            DebugConfig.debugError("TimetableCacheService: Error saving timetables to cache for route $routeId", e)
        }
    }

    /**
     * Clear cache for a specific route
     */
    fun clearRouteCache(routeId: String) {
        deleteCacheFiles(routeId)
        DebugConfig.debugPrint("TimetableCacheService: Cleared cache for route $routeId")
    }

    /**
     * Clear all cached timetables
     */
    fun clearAllCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            DebugConfig.debugPrint("TimetableCacheService: Cleared all timetable caches")
        } catch (e: Exception) {
            DebugConfig.debugError("TimetableCacheService: Error clearing all caches", e)
        }
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()

        try {
            val files = cacheDir.listFiles() ?: emptyArray()
            val cacheFiles = files.filter { it.extension == "json" && !it.name.endsWith(".meta.json") }
            val metadataFiles = files.filter { it.name.endsWith(".meta.json") }

            stats["cacheDirectory"] = cacheDir.absolutePath
            stats["cacheFileCount"] = cacheFiles.size
            stats["metadataFileCount"] = metadataFiles.size
            stats["totalCacheSize"] = files.sumOf { it.length() }

            // Per-route stats
            cacheFiles.forEach { cacheFile ->
                val routeId = cacheFile.nameWithoutExtension
                try {
                    val cachedData = json.decodeFromString<CachedTimetables>(cacheFile.readText())
                    stats["${routeId}_timetableCount"] = cachedData.timetables.size
                    stats["${routeId}_cacheSize"] = cacheFile.length()
                    stats["${routeId}_cacheAge"] = System.currentTimeMillis() - cachedData.cacheTimestamp
                } catch (e: Exception) {
                    stats["${routeId}_error"] = e.message ?: "Unknown error"
                }
            }

        } catch (e: Exception) {
            stats["error"] = e.message ?: "Unknown error"
        }

        return stats
    }

    // Private helper methods

    private fun getCacheFile(routeId: String): File {
        return File(cacheDir, "$routeId.json")
    }

    private fun getMetadataFile(routeId: String): File {
        return File(cacheDir, "$routeId.meta.json")
    }

    private fun deleteCacheFiles(routeId: String) {
        try {
            getCacheFile(routeId).delete()
            getMetadataFile(routeId).delete()
        } catch (e: Exception) {
            DebugConfig.debugError("TimetableCacheService: Error deleting cache files for route $routeId", e)
        }
    }

    // Data classes for serialization

    @Serializable
    private data class CachedTimetables(
        val routeId: String,
        val timetables: List<BusTimetable>,
        val cacheTimestamp: Long
    )

    @Serializable
    private data class CacheMetadata(
        val routeId: String,
        val timetableCount: Int,
        val cacheTimestamp: Long,
        val pdfLastModified: Long
    )
}
