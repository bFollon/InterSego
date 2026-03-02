package com.github.bfollon.intersego.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.Calendar

class ScheduleDateTest : FunSpec({

    context("parse") {
        test("full format with year") {
            val date = ScheduleDate.parse("lunes, 15 de julio de 2025")
            date.shouldNotBeNull()
            date.dayOfWeek shouldBe "lunes"
            date.day shouldBe 15
            date.month shouldBe "julio"
            date.year shouldBe 2025
        }

        test("without year infers current year") {
            val date = ScheduleDate.parse("martes, 3 de marzo")
            date.shouldNotBeNull()
            date.day shouldBe 3
            date.month shouldBe "marzo"
            date.year shouldBe ScheduleDate.getCurrentYear()
        }

        test("january 1-2 without year infers next year") {
            val date = ScheduleDate.parse("miércoles, 1 de enero")
            date.shouldNotBeNull()
            date.day shouldBe 1
            date.month shouldBe "enero"
            date.year shouldBe ScheduleDate.getCurrentYear() + 1
        }

        test("returns null for invalid format") {
            ScheduleDate.parse("not a date").shouldBeNull()
            ScheduleDate.parse("15 de julio de 2025").shouldBeNull() // missing day of week
        }

        test("all day-of-week names are accepted") {
            val days = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
            days.forEach { day ->
                val result = ScheduleDate.parse("$day, 10 de mayo de 2025")
                result.shouldNotBeNull()
                result.dayOfWeek shouldBe day
            }
        }
    }

    context("monthToNumber") {
        test("all 12 Spanish months") {
            val expected = mapOf(
                "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
                "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
                "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12
            )
            expected.forEach { (name, number) ->
                ScheduleDate.monthToNumber(name) shouldBe number
            }
        }

        test("case insensitive") {
            ScheduleDate.monthToNumber("ENERO") shouldBe 1
            ScheduleDate.monthToNumber("Julio") shouldBe 7
        }

        test("unknown month returns null") {
            ScheduleDate.monthToNumber("January").shouldBeNull()
        }
    }

    context("toDisplayString") {
        test("with year") {
            val date = ScheduleDate("lunes", 15, "julio", 2025)
            date.toDisplayString() shouldBe "lunes, 15 de julio de 2025"
        }

        test("without year") {
            val date = ScheduleDate("martes", 3, "marzo", null)
            date.toDisplayString() shouldBe "martes, 3 de marzo"
        }
    }

    context("fromCalendar") {
        test("round-trip consistency") {
            val cal = Calendar.getInstance().apply {
                set(2025, Calendar.JULY, 15) // July 15, 2025 is a Tuesday
            }
            val date = ScheduleDate.fromCalendar(cal)
            date.day shouldBe 15
            date.month shouldBe "julio"
            date.year shouldBe 2025
        }
    }

    context("matchesCalendar") {
        test("positive match") {
            val cal = Calendar.getInstance().apply {
                set(2025, Calendar.JULY, 15)
            }
            val date = ScheduleDate("martes", 15, "julio", 2025)
            date.matchesCalendar(cal) shouldBe true
        }

        test("negative match - wrong day") {
            val cal = Calendar.getInstance().apply {
                set(2025, Calendar.JULY, 16)
            }
            val date = ScheduleDate("martes", 15, "julio", 2025)
            date.matchesCalendar(cal) shouldBe false
        }

        test("null year matches any year") {
            val cal = Calendar.getInstance().apply {
                set(2025, Calendar.MARCH, 10)
            }
            val date = ScheduleDate("lunes", 10, "marzo", null)
            date.matchesCalendar(cal) shouldBe true
        }
    }
})
