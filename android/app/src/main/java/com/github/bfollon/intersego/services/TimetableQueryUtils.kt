/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.github.bfollon.intersego.data.BusTimetable
import com.github.bfollon.intersego.data.DayType
import java.util.Calendar

/**
 * Utility for querying timetables based on date and optional filters.
 */
object TimetableQueryUtils {
    /**
     * Returns the set of DayTypes applicable on the given date.
     * - Saturday → {SATURDAY, WEEKEND}
     * - Sunday → {SUNDAY, WEEKEND, HOLIDAY}
     * - A festivo (per [isHoliday]) on any other weekday → {SUNDAY, WEEKEND, HOLIDAY}, same as Sunday
     * - Weekday → {WEEKDAY}
     *
     * [isHoliday] defaults to [HolidayService.isHoliday] but is overridable so this stays a pure,
     * testable function — it never reaches into the service singleton itself. If no calendar has
     * been loaded (offline first launch), [isHoliday] returns false for every date and this falls
     * back to plain weekday/Saturday/Sunday resolution.
     *
     * @param date The calendar date to query (defaults to today)
     * @return Set of applicable DayTypes
     */
    fun dayTypesForDate(
        date: Calendar = Calendar.getInstance(),
        isHoliday: (Calendar) -> Boolean = HolidayService::isHoliday
    ): Set<DayType> {
        val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek != Calendar.SUNDAY && isHoliday(date)) {
            return setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
        }
        return when (dayOfWeek) {
            Calendar.SATURDAY -> setOf(DayType.SATURDAY, DayType.WEEKEND)
            Calendar.SUNDAY -> setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
            else -> setOf(DayType.WEEKDAY)
        }
    }

    /**
     * Single-value day type for the given date — SATURDAY/SUNDAY/WEEKDAY only. HOLIDAY is
     * folded into SUNDAY here since a festivo already means Sunday service; convenience for
     * callers that need one value (e.g. trip-identity keys) rather than the filterable set
     * from [dayTypesForDate].
     */
    fun primaryDayType(
        date: Calendar = Calendar.getInstance(),
        isHoliday: (Calendar) -> Boolean = HolidayService::isHoliday
    ): DayType {
        val types = dayTypesForDate(date, isHoliday)
        return when {
            DayType.SUNDAY in types -> DayType.SUNDAY
            DayType.SATURDAY in types -> DayType.SATURDAY
            else -> DayType.WEEKDAY
        }
    }

    /**
     * Filters timetables matching the given date and optional criteria.
     *
     * @param timetables The list of timetables to filter
     * @param date The calendar date to filter for
     * @param routeId Optional route ID to filter by; if provided, only matching routes are returned
     * @param stopId Optional stop ID to filter by; if provided, only matching stops are returned
     * @param direction Optional direction to filter by; if provided, only matching directions are returned
     * @return BusTimetable records matching the criteria. Caller should call `.seasonalDepartures(month:weekday:)`
     *   on each record to further filter by seasonal availability (school-only, summer-only, etc.)
     */
    fun filterTimetables(
        timetables: List<BusTimetable>,
        date: Calendar,
        routeId: String? = null,
        stopId: String? = null,
        direction: String? = null
    ): List<BusTimetable> {
        val dayTypes = dayTypesForDate(date)
        return timetables.filter { timetable ->
            dayTypes.contains(timetable.dayType)
                && (routeId == null || timetable.routeId == routeId)
                && (stopId == null || timetable.stopId == stopId)
                && (direction == null || timetable.direction == direction)
        }
    }
}
