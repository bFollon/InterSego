/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.data

import java.util.Calendar
import kotlinx.serialization.Serializable

/**
 * Type of day for timetable schedules
 */
@Serializable
enum class DayType {
    /**
     * Monday through Friday (laborables)
     */
    WEEKDAY,

    SATURDAY,
    SUNDAY,

    /**
     * Saturday and Sunday (fines de semana)
     */
    WEEKEND,

    /**
     * Public holidays (festivos)
     */
    HOLIDAY
}

/**
 * Returns true if this day type applies on the given [Calendar.DAY_OF_WEEK] value.
 * HOLIDAY is treated as Sunday for scheduling purposes.
 */
fun DayType.matchesCalendarDay(dayOfWeek: Int): Boolean = when (this) {
    DayType.WEEKDAY -> dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
    DayType.SATURDAY -> dayOfWeek == Calendar.SATURDAY
    DayType.SUNDAY -> dayOfWeek == Calendar.SUNDAY
    DayType.WEEKEND -> dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    DayType.HOLIDAY -> dayOfWeek == Calendar.SUNDAY
}
