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
import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType

class TimetableService(private val context: Context) {

    private val cachedTimetables = mutableMapOf<String, List<BusTimetable>>()
    private val routeDataService = RouteDataService(context)

    suspend fun loadTimetables(routeId: String, forceRefresh: Boolean = false): List<BusTimetable> {
        val isDebugParser = routeDataService.isDebugParser(routeId)
        val shouldForceRefresh = forceRefresh || isDebugParser

        if (isDebugParser) {
            DebugConfig.debugPrint("TimetableService: DEBUG parser detected for $routeId - bypassing cache")
        }

        DebugConfig.debugPrint("TimetableService: Loading timetables for route $routeId (forceRefresh: $shouldForceRefresh)")

        if (!shouldForceRefresh && cachedTimetables.containsKey(routeId)) {
            DebugConfig.debugPrint("TimetableService: Using memory cache for route $routeId")
            return cachedTimetables[routeId]!!
        }

        DebugConfig.debugPrint("TimetableService: Loading JSON for route $routeId")

        return try {
            val timetables = routeDataService.parseTimetables(routeId)
            cachedTimetables[routeId] = timetables
            DebugConfig.debugPrint("TimetableService: Successfully loaded ${timetables.size} timetables for $routeId")
            timetables
        } catch (e: Exception) {
            DebugConfig.debugError("TimetableService: Failed to load JSON for $routeId", e)
            emptyList()
        }
    }

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

    fun isLoaded(routeId: String): Boolean = cachedTimetables.containsKey(routeId)

    fun clearCache() {
        cachedTimetables.clear()
        DebugConfig.debugPrint("TimetableService: Cache cleared")
    }

    fun clearCacheForRoute(routeId: String) {
        cachedTimetables.remove(routeId)
        DebugConfig.debugPrint("TimetableService: Cleared cache for route $routeId")
    }

    fun getCurrentDayType(): DayType {
        val calendar = java.util.Calendar.getInstance()
        return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SATURDAY -> DayType.SATURDAY
            java.util.Calendar.SUNDAY -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }
    }

    fun getCurrentTime(): Pair<Int, Int> {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) to calendar.get(java.util.Calendar.MINUTE)
    }
}
