package com.github.bfollon.linecapp.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class DepartureTimeTest : FunSpec({

    context("construction") {
        test("valid hours and minutes") {
            val dt = DepartureTime(14, 30)
            dt.hour shouldBe 14
            dt.minute shouldBe 30
        }

        test("boundary values 0:00 and 23:59") {
            DepartureTime(0, 0).hour shouldBe 0
            DepartureTime(23, 59).minute shouldBe 59
        }

        test("invalid hour throws") {
            shouldThrow<IllegalArgumentException> { DepartureTime(24, 0) }
            shouldThrow<IllegalArgumentException> { DepartureTime(-1, 0) }
        }

        test("invalid minute throws") {
            shouldThrow<IllegalArgumentException> { DepartureTime(12, 60) }
            shouldThrow<IllegalArgumentException> { DepartureTime(12, -1) }
        }

        test("optional notes and seasonal availability") {
            val dt = DepartureTime(8, 0, notes = "Solo laborables", seasonalAvailability = SeasonalAvailability.SUMMER_ONLY)
            dt.notes shouldBe "Solo laborables"
            dt.seasonalAvailability shouldBe SeasonalAvailability.SUMMER_ONLY
        }

        test("defaults") {
            val dt = DepartureTime(8, 0)
            dt.notes.shouldBeNull()
            dt.seasonalAvailability shouldBe SeasonalAvailability.YEAR_ROUND
            dt.variantLabel.shouldBeNull()
        }
    }

    context("toDisplayString") {
        test("pads single digit hours and minutes") {
            DepartureTime(7, 5).toDisplayString() shouldBe "07:05"
        }

        test("midnight") {
            DepartureTime(0, 0).toDisplayString() shouldBe "00:00"
        }

        test("noon") {
            DepartureTime(12, 0).toDisplayString() shouldBe "12:00"
        }

        test("double digit values") {
            DepartureTime(14, 30).toDisplayString() shouldBe "14:30"
        }
    }

    context("toMinutesSinceMidnight") {
        test("midnight is 0") {
            DepartureTime(0, 0).toMinutesSinceMidnight() shouldBe 0
        }

        test("1:30 is 90") {
            DepartureTime(1, 30).toMinutesSinceMidnight() shouldBe 90
        }

        test("23:59 is 1439") {
            DepartureTime(23, 59).toMinutesSinceMidnight() shouldBe 1439
        }
    }

    context("isPast and isFuture") {
        test("departure before current time is past") {
            DepartureTime(8, 0).isPast(9, 0) shouldBe true
        }

        test("departure after current time is not past") {
            DepartureTime(10, 0).isPast(9, 0) shouldBe false
        }

        test("same time is not past (isFuture)") {
            DepartureTime(9, 0).isPast(9, 0) shouldBe false
            DepartureTime(9, 0).isFuture(9, 0) shouldBe true
        }

        test("isFuture is inverse of isPast") {
            val dt = DepartureTime(14, 30)
            dt.isFuture(14, 30) shouldBe !dt.isPast(14, 30)
            dt.isFuture(15, 0) shouldBe !dt.isPast(15, 0)
        }
    }

    context("minutesUntil") {
        test("positive when departure is in the future") {
            DepartureTime(10, 0).minutesUntil(9, 0) shouldBe 60
        }

        test("negative when departure is in the past") {
            DepartureTime(8, 0).minutesUntil(9, 30) shouldBe -90
        }

        test("zero when same time") {
            DepartureTime(12, 0).minutesUntil(12, 0) shouldBe 0
        }
    }

    context("compareTo") {
        test("earlier departure is less than later") {
            DepartureTime(7, 0) shouldBeLessThan DepartureTime(8, 0)
        }

        test("later departure is greater than earlier") {
            DepartureTime(14, 30) shouldBeGreaterThan DepartureTime(7, 0)
        }

        test("same time is equal") {
            DepartureTime(8, 0).compareTo(DepartureTime(8, 0)) shouldBe 0
        }

        test("sorting works correctly") {
            val times = listOf(DepartureTime(14, 0), DepartureTime(7, 30), DepartureTime(9, 0))
            val sorted = times.sorted()
            sorted.map { it.hour } shouldBe listOf(7, 9, 14)
        }
    }

    context("fromString") {
        test("parses valid HH:MM") {
            val dt = DepartureTime.fromString("14:30")
            dt.shouldNotBeNull()
            dt.hour shouldBe 14
            dt.minute shouldBe 30
        }

        test("parses single-digit hour") {
            val dt = DepartureTime.fromString("7:05")
            dt.shouldNotBeNull()
            dt.hour shouldBe 7
            dt.minute shouldBe 5
        }

        test("returns null for invalid format") {
            DepartureTime.fromString("abc").shouldBeNull()
            DepartureTime.fromString("12").shouldBeNull()
            DepartureTime.fromString("12:34:56").shouldBeNull()
        }

        test("returns null for out of range values") {
            DepartureTime.fromString("25:00").shouldBeNull()
            DepartureTime.fromString("7:60").shouldBeNull()
        }

        test("handles whitespace in parts") {
            val dt = DepartureTime.fromString(" 8 : 30 ")
            dt.shouldNotBeNull()
            dt.hour shouldBe 8
            dt.minute shouldBe 30
        }
    }
})
