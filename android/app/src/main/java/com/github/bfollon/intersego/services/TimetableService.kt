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

package com.github.bfollon.intersego.services

import android.content.Context
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType

/**
 * Service for loading and managing bus timetables
 * Adapted from FarmaciasDeGuardia ScheduleService
 *
 * Implements three-tier caching pattern:
 * 1. Memory cache (fastest)
 * 2. Persistent cache via TimetableCacheService (fast)
 * 3. PDF parsing (slowest - Phase 7)
 */
class TimetableService(private val context: Context) {

    // Memory cache for loaded timetables (tier 1)
    private val cachedTimetables = mutableMapOf<String, List<BusTimetable>>()

    // Persistent cache service (tier 2)
    private val cacheService = TimetableCacheService(context)

    // PDF processing service (tier 3)
    private val pdfProcessingService = PDFProcessingService(context)

    /**
     * Load timetables for a specific route
     * @param routeId The route ID to load timetables for
     * @param forceRefresh Whether to bypass cache and reload
     * @return List of bus timetables for the route
     */
    suspend fun loadTimetables(routeId: String, forceRefresh: Boolean = false): List<BusTimetable> {
        // Check if parser is in DEBUG mode - if so, always force refresh (bypass cache)
        val isDebugParser = pdfProcessingService.isDebugParser(routeId)
        val shouldForceRefresh = forceRefresh || isDebugParser

        if (isDebugParser) {
            DebugConfig.debugPrint("TimetableService: DEBUG parser detected for $routeId - bypassing cache")
        }

        DebugConfig.debugPrint("TimetableService: Loading timetables for route $routeId (forceRefresh: $shouldForceRefresh)")

        // Check memory cache first (tier 1)
        if (!shouldForceRefresh && cachedTimetables.containsKey(routeId)) {
            DebugConfig.debugPrint("TimetableService: Using memory cache for route $routeId")
            return cachedTimetables[routeId]!!
        }

        // Check persistent cache (tier 2)
        if (!shouldForceRefresh) {
            val cachedData = cacheService.loadCachedTimetables(routeId)
            if (cachedData != null) {
                DebugConfig.debugPrint("TimetableService: Using persistent cache for route $routeId")
                cachedTimetables[routeId] = cachedData
                return cachedData
            }
        }

        // Tier 3: Parse PDF (slowest, source of truth)
        DebugConfig.debugPrint("TimetableService: Parsing PDF for route $routeId")

        try {
            // Parse the PDF
            val timetables = pdfProcessingService.parseTimetables(routeId)

            // Cache in both memory and persistent storage
            cachedTimetables[routeId] = timetables
            cacheService.saveTimetablesToCache(routeId, timetables)

            DebugConfig.debugPrint("TimetableService: Successfully loaded and cached ${timetables.size} timetables for $routeId")

            return timetables

        } catch (e: Exception) {
            DebugConfig.debugError("TimetableService: Failed to parse PDF for $routeId", e)

            // Don't cache failures - allow retries on next attempt
            // Return empty list to prevent crashes in UI
            return emptyList()
        }
    }

    /**
     * Find the current relevant timetable for a given day type
     * @param timetables List of timetables to search in
     * @param dayType The type of day to filter by
     * @return Timetable matching the day type, or null if not found
     */
    fun findTimetableForDayType(timetables: List<BusTimetable>, dayType: DayType): BusTimetable? {
        return timetables.firstOrNull { it.dayType == dayType }
            .also { timetable ->
                if (timetable != null) {
                    DebugConfig.debugPrint("TimetableService: Found timetable for $dayType with ${timetable.departures.size} departures")
                } else {
                    DebugConfig.debugPrint("TimetableService: No timetable found for $dayType")
                }
            }
    }

    /**
     * Get next departures from current time
     * @param timetables List of timetables to search
     * @param dayType Current day type
     * @param currentHour Current hour (0-23)
     * @param currentMinute Current minute (0-59)
     * @param limit Maximum number of departures to return
     * @return List of upcoming departure times
     */
    fun getNextDepartures(
        timetables: List<BusTimetable>,
        dayType: DayType,
        currentHour: Int,
        currentMinute: Int,
        limit: Int = 5
    ): List<com.github.bfollon.intersego.data.DepartureTime> {
        val timetable = findTimetableForDayType(timetables, dayType) ?: return emptyList()
        return timetable.getNextDepartures(currentHour, currentMinute, limit)
    }

    /**
     * Check if timetables are already loaded for a route (memory cache only)
     */
    fun isLoaded(routeId: String): Boolean = cachedTimetables.containsKey(routeId)

    /**
     * Clear all cached timetables (both memory and persistent)
     */
    suspend fun clearCache() {
        cachedTimetables.clear()
        cacheService.clearAllCache()
        DebugConfig.debugPrint("TimetableService: All caches cleared")
    }

    /**
     * Clear cache for a specific route
     */
    suspend fun clearCacheForRoute(routeId: String) {
        cachedTimetables.remove(routeId)
        cacheService.clearRouteCache(routeId)
        DebugConfig.debugPrint("TimetableService: Cleared cache for route $routeId")
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        val memoryStats = buildString {
            append("Memory: ${cachedTimetables.size} routes loaded")
            if (cachedTimetables.isNotEmpty()) {
                append(" (")
                append(cachedTimetables.entries.joinToString(", ") { (routeId, timetables) ->
                    "$routeId: ${timetables.size} timetables"
                })
                append(")")
            }
        }

        val persistentStats = cacheService.getCacheStats()

        return "$memoryStats\nPersistent: $persistentStats"
    }

    /**
     * Get current day type based on system date
     * Useful for determining which timetable to show
     */
    fun getCurrentDayType(): DayType {
        val calendar = java.util.Calendar.getInstance()
        return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SATURDAY -> DayType.SATURDAY
            java.util.Calendar.SUNDAY -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }
    }

    /**
     * Get current time components for departure calculations
     */
    fun getCurrentTime(): Pair<Int, Int> {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        return hour to minute
    }
}
