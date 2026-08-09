package com.github.bfollon.intersego.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Month

class SeasonalAvailabilityTest : FunSpec({

    context("YEAR_ROUND") {
        test("runs in every month") {
            Month.entries.forEach { month ->
                SeasonalAvailability.YEAR_ROUND.runsIn(month) shouldBe true
            }
        }
    }

    context("SUMMER_ONLY") {
        test("runs in July and August") {
            SeasonalAvailability.SUMMER_ONLY.runsIn(Month.JULY) shouldBe true
            SeasonalAvailability.SUMMER_ONLY.runsIn(Month.AUGUST) shouldBe true
        }

        test("does not run in non-summer months") {
            val nonSummer = Month.entries - setOf(Month.JULY, Month.AUGUST)
            nonSummer.forEach { month ->
                SeasonalAvailability.SUMMER_ONLY.runsIn(month) shouldBe false
            }
        }
    }

    context("SCHOOL_ONLY") {
        test("does not run in July and August") {
            SeasonalAvailability.SCHOOL_ONLY.runsIn(Month.JULY) shouldBe false
            SeasonalAvailability.SCHOOL_ONLY.runsIn(Month.AUGUST) shouldBe false
        }

        test("runs in school months") {
            SeasonalAvailability.SCHOOL_ONLY.runsIn(Month.OCTOBER) shouldBe true
            SeasonalAvailability.SCHOOL_ONLY.runsIn(Month.JANUARY) shouldBe true
            SeasonalAvailability.SCHOOL_ONLY.runsIn(Month.MARCH) shouldBe true
        }
    }

    context("custom summer months") {
        test("SUMMER_ONLY with custom set") {
            val custom = setOf(Month.JUNE, Month.JULY, Month.AUGUST, Month.SEPTEMBER)
            SeasonalAvailability.SUMMER_ONLY.runsIn(Month.JUNE, summerMonths = custom) shouldBe true
            SeasonalAvailability.SUMMER_ONLY.runsIn(Month.SEPTEMBER, summerMonths = custom) shouldBe true
            SeasonalAvailability.SUMMER_ONLY.runsIn(Month.MAY, summerMonths = custom) shouldBe false
        }

        test("SCHOOL_ONLY with custom set") {
            val custom = setOf(Month.JUNE, Month.JULY, Month.AUGUST, Month.SEPTEMBER)
            SeasonalAvailability.SCHOOL_ONLY.runsIn(Month.JUNE, summerMonths = custom) shouldBe false
            SeasonalAvailability.SCHOOL_ONLY.runsIn(Month.MAY, summerMonths = custom) shouldBe true
        }
    }
})
