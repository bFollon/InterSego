package com.github.bfollon.intersego.services.pdfparsing.strategies

import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.SeasonalAvailability
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class M4ParserTest : FunSpec({

    val parser = M4Parser()
    val pdfPath = javaClass.classLoader!!.getResource("M4.pdf")?.file
        ?: error("M4.pdf test fixture not found in src/test/resources/")

    context("canParse") {
        test("M4 is supported") {
            parser.canParse("M4") shouldBe true
        }

        test("m4 case insensitive") {
            parser.canParse("m4") shouldBe true
        }

        test("M6 is not supported") {
            parser.canParse("M6") shouldBe false
        }

        test("empty string is not supported") {
            parser.canParse("") shouldBe false
        }
    }

    context("parse") {
        // Parse once, reuse across tests
        val timetables = parser.parse(pdfPath, "M4")

        test("returns non-empty timetables") {
            timetables.shouldNotBeEmpty()
        }

        test("correct number of timetables (4 groups: 2x16 regular + 2x17 reverse)") {
            // 4 groups: regular weekday (16), regular weekend (16), reverse weekday (17), reverse weekend (17)
            timetables.size shouldBe 2 * 16 + 2 * 17
        }

        test("contains both directions") {
            val directions = timetables.mapNotNull { it.direction }.toSet()
            directions shouldBe setOf("Lastrilla → Sotillo", "Sotillo → Lastrilla")
        }

        test("contains both day types") {
            val dayTypes = timetables.map { it.dayType }.toSet()
            dayTypes shouldBe setOf(DayType.WEEKDAY, DayType.WEEKEND)
        }

        test("all departure times have valid hours and minutes") {
            timetables.flatMap { it.departures }.forEach { dep ->
                (dep.hour in 0..23) shouldBe true
                (dep.minute in 0..59) shouldBe true
            }
        }

        test("departures are sorted within each timetable") {
            timetables.forEach { tt ->
                val minutes = tt.departures.map { it.toMinutesSinceMidnight() }
                minutes shouldBe minutes.sorted()
            }
        }

        test("timetables with departures have reasonable counts") {
            val nonEmpty = timetables.filter { it.departures.isNotEmpty() }
            nonEmpty.shouldNotBeEmpty()
            nonEmpty.forEach { tt ->
                tt.departures.size shouldBeGreaterThan 0
            }
        }

        test("seasonal departures are present") {
            val allDeps = timetables.flatMap { it.departures }
            val summerOnly = allDeps.filter { it.seasonalAvailability == SeasonalAvailability.SUMMER_ONLY }
            val schoolOnly = allDeps.filter { it.seasonalAvailability == SeasonalAvailability.SCHOOL_ONLY }
            summerOnly.shouldNotBeEmpty()
            schoolOnly.shouldNotBeEmpty()
        }

        test("all timetables have routeId M4") {
            timetables.forEach { it.routeId shouldBe "M4" }
        }
    }

    context("getRoutesForId") {
        test("M4 returns 2 route lists") {
            val routes = parser.getRoutesForId("M4")
            routes.size shouldBe 2
        }

        test("regular route has 16 stops, reverse has 17") {
            val routes = parser.getRoutesForId("M4")
            routes[0].size shouldBe 16
            routes[1].size shouldBe 17
        }

        test("unsupported route returns empty") {
            parser.getRoutesForId("M6") shouldBe emptyList()
        }
    }

    context("getRouteVariants") {
        test("returns 2 variants for weekday") {
            val variants = parser.getRouteVariants("M4", DayType.WEEKDAY)
            variants.size shouldBe 2
        }

        test("variant labels contain direction info") {
            val variants = parser.getRouteVariants("M4", DayType.WEEKDAY)
            variants.any { "Lastrilla" in it.label && "Sotillo" in it.label } shouldBe true
            variants.any { "Sotillo" in it.label && "Lastrilla" in it.label } shouldBe true
        }

        test("unsupported route returns empty") {
            parser.getRouteVariants("M1", DayType.WEEKDAY) shouldBe emptyList()
        }
    }
})
