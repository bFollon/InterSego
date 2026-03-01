package com.github.bfollon.linecapp.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Month

class BusTimetableTest : FunSpec({

    fun timetable(
        departures: List<DepartureTime>,
        dayType: DayType = DayType.WEEKDAY,
        direction: String? = "Ida"
    ) = BusTimetable(
        routeId = "M4",
        stopId = "stop-1",
        dayType = dayType,
        departures = departures,
        direction = direction
    )

    context("seasonalDepartures") {
        test("filters summer-only departures in July") {
            val tt = timetable(
                listOf(
                    DepartureTime(7, 0, seasonalAvailability = SeasonalAvailability.YEAR_ROUND),
                    DepartureTime(8, 0, seasonalAvailability = SeasonalAvailability.SUMMER_ONLY),
                    DepartureTime(9, 0, seasonalAvailability = SeasonalAvailability.SCHOOL_ONLY),
                )
            )
            val result = tt.seasonalDepartures(Month.JULY)
            result shouldHaveSize 2
            result.map { it.hour } shouldBe listOf(7, 8)
        }

        test("filters school-only departures in October") {
            val tt = timetable(
                listOf(
                    DepartureTime(7, 0, seasonalAvailability = SeasonalAvailability.YEAR_ROUND),
                    DepartureTime(8, 0, seasonalAvailability = SeasonalAvailability.SUMMER_ONLY),
                    DepartureTime(9, 0, seasonalAvailability = SeasonalAvailability.SCHOOL_ONLY),
                )
            )
            val result = tt.seasonalDepartures(Month.OCTOBER)
            result shouldHaveSize 2
            result.map { it.hour } shouldBe listOf(7, 9)
        }
    }

    context("firstDeparture and lastDeparture") {
        test("returns correct first and last") {
            val tt = timetable(
                listOf(DepartureTime(14, 0), DepartureTime(7, 0), DepartureTime(9, 30))
            )
            tt.firstDeparture.shouldNotBeNull().hour shouldBe 7
            tt.lastDeparture.shouldNotBeNull().hour shouldBe 14
        }

        test("returns null for empty departures") {
            val tt = timetable(emptyList())
            tt.firstDeparture.shouldBeNull()
            tt.lastDeparture.shouldBeNull()
        }

        test("respects seasonal filtering") {
            val tt = timetable(
                listOf(
                    DepartureTime(6, 0, seasonalAvailability = SeasonalAvailability.SUMMER_ONLY),
                    DepartureTime(7, 0, seasonalAvailability = SeasonalAvailability.SCHOOL_ONLY),
                    DepartureTime(22, 0, seasonalAvailability = SeasonalAvailability.SUMMER_ONLY),
                )
            )
            // In October (school), only the 7:00 departure runs
            tt.seasonalDepartures(Month.OCTOBER) shouldHaveSize 1
        }
    }

    context("getNextDepartures") {
        test("returns future departures up to limit") {
            val tt = timetable(
                listOf(
                    DepartureTime(7, 0), DepartureTime(8, 0), DepartureTime(9, 0),
                    DepartureTime(10, 0), DepartureTime(11, 0), DepartureTime(12, 0)
                )
            )
            val next = tt.getNextDepartures(8, 30, limit = 3)
            next shouldHaveSize 3
            next.map { it.hour } shouldBe listOf(9, 10, 11)
        }

        test("returns empty when all departures are past") {
            val tt = timetable(listOf(DepartureTime(7, 0), DepartureTime(8, 0)))
            tt.getNextDepartures(22, 0).shouldBeEmpty()
        }

        test("includes departure at exact current time") {
            val tt = timetable(listOf(DepartureTime(9, 0), DepartureTime(10, 0)))
            val next = tt.getNextDepartures(9, 0)
            next shouldHaveSize 2
        }
    }

    context("hasRemainingDepartures") {
        test("true when future departures exist") {
            val tt = timetable(listOf(DepartureTime(7, 0), DepartureTime(22, 0)))
            tt.hasRemainingDepartures(12, 0) shouldBe true
        }

        test("false when all are past") {
            val tt = timetable(listOf(DepartureTime(7, 0), DepartureTime(8, 0)))
            tt.hasRemainingDepartures(22, 0) shouldBe false
        }
    }

    context("getDisplayDescription") {
        test("weekday with direction") {
            val tt = timetable(emptyList(), dayType = DayType.WEEKDAY, direction = "Ida")
            tt.getDisplayDescription() shouldBe "Laborables - Ida"
        }

        test("weekend without direction") {
            val tt = timetable(emptyList(), dayType = DayType.WEEKEND, direction = null)
            tt.getDisplayDescription() shouldBe "Fines de semana"
        }

        test("saturday") {
            val tt = timetable(emptyList(), dayType = DayType.SATURDAY, direction = null)
            tt.getDisplayDescription() shouldBe "Sábado"
        }

        test("sunday") {
            val tt = timetable(emptyList(), dayType = DayType.SUNDAY, direction = null)
            tt.getDisplayDescription() shouldBe "Domingo"
        }

        test("holiday") {
            val tt = timetable(emptyList(), dayType = DayType.HOLIDAY, direction = null)
            tt.getDisplayDescription() shouldBe "Festivos"
        }
    }
})
