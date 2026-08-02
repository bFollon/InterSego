/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.github.bfollon.intersego.data.DayType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Calendar

class TimetableQueryUtilsTest : FunSpec({

    fun calendar(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply { set(year, month - 1, day, 12, 0, 0) }

    context("dayTypesForDate") {
        test("plain Tuesday with no calendar loaded resolves to WEEKDAY") {
            // 2026-08-04 is a Tuesday
            val tuesday = calendar(2026, 8, 4)
            TimetableQueryUtils.dayTypesForDate(tuesday, isHoliday = { false }) shouldBe setOf(DayType.WEEKDAY)
        }

        test("Saturday with no calendar loaded resolves to SATURDAY + WEEKEND") {
            // 2026-08-08 is a Saturday
            val saturday = calendar(2026, 8, 8)
            TimetableQueryUtils.dayTypesForDate(saturday, isHoliday = { false }) shouldBe
                setOf(DayType.SATURDAY, DayType.WEEKEND)
        }

        test("a festivo on a Tuesday resolves to SUNDAY + WEEKEND + HOLIDAY, same as an actual Sunday") {
            val festivoTuesday = calendar(2026, 8, 4)
            TimetableQueryUtils.dayTypesForDate(festivoTuesday, isHoliday = { true }) shouldBe
                setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
        }

        test("a festivo that falls on a Sunday does not double-count — same set either way") {
            // 2026-08-09 is a Sunday
            val sunday = calendar(2026, 8, 9)
            val plainSunday = TimetableQueryUtils.dayTypesForDate(sunday, isHoliday = { false })
            val festivoSunday = TimetableQueryUtils.dayTypesForDate(sunday, isHoliday = { true })
            plainSunday shouldBe setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
            festivoSunday shouldBe plainSunday
        }

        test("no calendar loaded (isHoliday always false) falls back to plain weekday logic") {
            val anyWeekday = calendar(2026, 8, 5) // Wednesday
            TimetableQueryUtils.dayTypesForDate(anyWeekday, isHoliday = { false }) shouldBe setOf(DayType.WEEKDAY)
        }
    }

    context("primaryDayType") {
        test("a festivo on a weekday resolves to SUNDAY") {
            val festivoWednesday = calendar(2026, 8, 5)
            TimetableQueryUtils.primaryDayType(festivoWednesday, isHoliday = { true }) shouldBe DayType.SUNDAY
        }

        test("a plain Saturday resolves to SATURDAY") {
            val saturday = calendar(2026, 8, 8)
            TimetableQueryUtils.primaryDayType(saturday, isHoliday = { false }) shouldBe DayType.SATURDAY
        }

        test("a plain weekday resolves to WEEKDAY") {
            val tuesday = calendar(2026, 8, 4)
            TimetableQueryUtils.primaryDayType(tuesday, isHoliday = { false }) shouldBe DayType.WEEKDAY
        }
    }
})
