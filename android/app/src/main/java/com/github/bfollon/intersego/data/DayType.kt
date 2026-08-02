/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.data

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
