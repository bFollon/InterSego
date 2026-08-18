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
import com.github.bfollon.intersego.data.Journey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

/**
 * Loads route/transfer data (IO, Android-dependent) and hands it to the pure
 * [JourneyPlannerService], resolving day-type/season against the query date via the same
 * [TimetableQueryUtils] logic every other date-aware screen uses.
 */
object JourneySearchCoordinator {

    suspend fun search(
        context: Context,
        originPhysicalStopId: String,
        destinationPhysicalStopId: String,
        date: LocalDate,
        departAfterMin: Int,
        arriveBeforeMin: Int? = null,
    ): List<Journey> = withContext(Dispatchers.IO) {
        val loader = TimetableLoader(context)
        val routeDataService = RouteDataService(context)
        val routes = routeDataService.getSupportedRoutes().mapNotNull { routeId ->
            runCatching { loader.loadJourneyRouteData(routeId) }.getOrNull()
        }
        val transfers = runCatching { TransfersLoader(context).load() }.getOrDefault(emptyList())

        val calendar = date.toNoonCalendar()
        val dayTypes = TimetableQueryUtils.dayTypesForDate(calendar)
        val month = date.month
        val weekday = calendar.get(Calendar.DAY_OF_WEEK)
        val nowMin = if (date == LocalDate.now()) {
            val now = java.time.LocalTime.now()
            now.hour * 60 + now.minute
        } else null

        val query = JourneyQuery(
            origin = originPhysicalStopId,
            destination = destinationPhysicalStopId,
            dayTypes = dayTypes,
            month = month,
            weekday = weekday,
            departAfterMin = departAfterMin,
            arriveBeforeMin = arriveBeforeMin,
            nowMin = nowMin,
        )
        JourneyPlannerService.findJourneys(routes, transfers, query)
    }
}
