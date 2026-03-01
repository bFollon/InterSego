package com.github.bfollon.linecapp.services.pdfparsing

import com.github.bfollon.linecapp.data.DayType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalTime

class TimetableParserUtilsTest : FunSpec({

    context("hasTimes") {
        test("true for line with times") {
            TimetableParserUtils.hasTimes("7:40 8:15 14:30") shouldBe true
        }

        test("true for single time") {
            TimetableParserUtils.hasTimes("departs at 7:40") shouldBe true
        }

        test("false for line without times") {
            TimetableParserUtils.hasTimes("LUNES A VIERNES") shouldBe false
        }

        test("false for empty string") {
            TimetableParserUtils.hasTimes("") shouldBe false
        }
    }

    context("extractTimes") {
        test("single time") {
            val times = TimetableParserUtils.extractTimes("7:40")
            times shouldHaveSize 1
            times[0] shouldBe LocalTime.of(7, 40)
        }

        test("multiple times") {
            val times = TimetableParserUtils.extractTimes("7:40 8:15 14:30")
            times shouldHaveSize 3
            times shouldBe listOf(LocalTime.of(7, 40), LocalTime.of(8, 15), LocalTime.of(14, 30))
        }

        test("times mixed with text") {
            val times = TimetableParserUtils.extractTimes("JULIO Y AGOSTO 7:40* 7:43 14:30")
            times shouldHaveSize 3
        }

        test("no times returns empty") {
            TimetableParserUtils.extractTimes("no times here").shouldBeEmpty()
        }
    }

    context("extractAnnotatedTimes") {
        test("plain times have no modifier") {
            val result = TimetableParserUtils.extractAnnotatedTimes("7:20 7:30")
            result shouldHaveSize 2
            result.forEach { it.modifier.shouldBeNull() }
        }

        test("arrow prefix") {
            val result = TimetableParserUtils.extractAnnotatedTimes("→07:45 →07:50")
            result shouldHaveSize 2
            result.forEach { it.modifier shouldBe TimeModifier.ARROW }
        }

        test("double asterisk suffix") {
            val result = TimetableParserUtils.extractAnnotatedTimes("7:40**")
            result shouldHaveSize 1
            result[0].modifier shouldBe TimeModifier.DOUBLE_ASTERISK
            result[0].time shouldBe LocalTime.of(7, 40)
        }

        test("triple asterisk suffix") {
            val result = TimetableParserUtils.extractAnnotatedTimes("9:00***")
            result shouldHaveSize 1
            result[0].modifier shouldBe TimeModifier.TRIPLE_ASTERISK
        }

        test("pound prefix") {
            val result = TimetableParserUtils.extractAnnotatedTimes("#8:30")
            result shouldHaveSize 1
            result[0].modifier shouldBe TimeModifier.POUND
        }

        test("mixed modifiers on one line") {
            val result = TimetableParserUtils.extractAnnotatedTimes("7:20 →07:45 7:40**")
            result shouldHaveSize 3
            result[0].modifier.shouldBeNull()
            result[1].modifier shouldBe TimeModifier.ARROW
            result[2].modifier shouldBe TimeModifier.DOUBLE_ASTERISK
        }

        test("arrow prefix with space") {
            val result = TimetableParserUtils.extractAnnotatedTimes("→ 07:45")
            result shouldHaveSize 1
            result[0].modifier shouldBe TimeModifier.ARROW
        }
    }

    context("sortTimes") {
        test("sorts chronologically") {
            val times = listOf(LocalTime.of(14, 30), LocalTime.of(7, 40), LocalTime.of(9, 0))
            TimetableParserUtils.sortTimes(times) shouldBe listOf(
                LocalTime.of(7, 40), LocalTime.of(9, 0), LocalTime.of(14, 30)
            )
        }

        test("already sorted remains unchanged") {
            val times = listOf(LocalTime.of(7, 0), LocalTime.of(8, 0))
            TimetableParserUtils.sortTimes(times) shouldBe times
        }

        test("empty list") {
            TimetableParserUtils.sortTimes(emptyList()).shouldBeEmpty()
        }
    }

    context("detectDayType") {
        test("LUNES A VIERNES returns WEEKDAY") {
            TimetableParserUtils.detectDayType("LUNES A VIERNES LABORABLES") shouldBe DayType.WEEKDAY
        }

        test("case insensitive") {
            TimetableParserUtils.detectDayType("lunes a viernes") shouldBe DayType.WEEKDAY
        }

        test("SÁBADOS with accent returns SATURDAY") {
            TimetableParserUtils.detectDayType("SÁBADOS") shouldBe DayType.SATURDAY
        }

        test("SABADOS without accent returns SATURDAY") {
            TimetableParserUtils.detectDayType("SABADOS") shouldBe DayType.SATURDAY
        }

        test("DOMINGOS returns SUNDAY") {
            TimetableParserUtils.detectDayType("DOMINGOS Y FESTIVOS") shouldBe DayType.SUNDAY
        }

        test("unrelated text returns null") {
            TimetableParserUtils.detectDayType("JULIO Y AGOSTO").shouldBeNull()
            TimetableParserUtils.detectDayType("Azoguejo").shouldBeNull()
            TimetableParserUtils.detectDayType("").shouldBeNull()
        }
    }
})
