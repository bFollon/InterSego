package com.github.bfollon.linecapp.services.pdfparsing.strategies

import com.github.bfollon.linecapp.data.DayType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class M6ParserTest : FunSpec({

    val parser = M6Parser()
    val pdfPath = javaClass.classLoader!!.getResource("M6.pdf")?.file
        ?: error("M6.pdf test fixture not found in src/test/resources/")

    context("canParse") {
        test("M6 is supported") {
            parser.canParse("M6") shouldBe true
        }

        test("m6 case insensitive") {
            parser.canParse("m6") shouldBe true
        }

        test("M4 is not supported") {
            parser.canParse("M4") shouldBe false
        }
    }

    context("parse") {
        val timetables = parser.parse(pdfPath, "M6")

        test("returns non-empty timetables") {
            timetables.shouldNotBeEmpty()
        }

        test("contains weekday, saturday, and sunday day types") {
            val dayTypes = timetables.map { it.dayType }.toSet()
            dayTypes shouldBe setOf(DayType.WEEKDAY, DayType.SATURDAY, DayType.SUNDAY)
        }

        test("all departure times have valid hours and minutes") {
            timetables.flatMap { it.departures }.forEach { dep ->
                (dep.hour in 0..23) shouldBe true
                (dep.minute in 0..59) shouldBe true
            }
        }

        test("departures are sorted within each timetable") {
            timetables.filter { it.departures.isNotEmpty() }.forEach { tt ->
                val minutes = tt.departures.map { it.toMinutesSinceMidnight() }
                minutes shouldBe minutes.sorted()
            }
        }

        test("all timetables have routeId M6") {
            timetables.forEach { it.routeId shouldBe "M6" }
        }

        test("contains both directions") {
            val directions = timetables.mapNotNull { it.direction }.toSet()
            ("Segovia → Torrecaballeros" in directions) shouldBe true
            ("Torrecaballeros → Segovia" in directions) shouldBe true
        }

        test("weekday timetables have departures") {
            val weekdayDeps = timetables
                .filter { it.dayType == DayType.WEEKDAY }
                .flatMap { it.departures }
            weekdayDeps.shouldNotBeEmpty()
        }

        test("saturday timetables have departures") {
            val satDeps = timetables
                .filter { it.dayType == DayType.SATURDAY }
                .flatMap { it.departures }
            satDeps.shouldNotBeEmpty()
        }

        test("sunday timetables have departures") {
            val sunDeps = timetables
                .filter { it.dayType == DayType.SUNDAY }
                .flatMap { it.departures }
            sunDeps.shouldNotBeEmpty()
        }
    }

    context("getRoutesForId") {
        test("M6 returns non-empty route lists") {
            val routes = parser.getRoutesForId("M6")
            routes.shouldNotBeEmpty()
        }

        test("unsupported route returns empty") {
            parser.getRoutesForId("M4") shouldBe emptyList()
        }
    }

    context("getRouteVariants") {
        test("returns variants for weekday") {
            val variants = parser.getRouteVariants("M6", DayType.WEEKDAY)
            variants.shouldNotBeEmpty()
        }

        test("returns variants for saturday") {
            val variants = parser.getRouteVariants("M6", DayType.SATURDAY)
            variants.shouldNotBeEmpty()
        }

        test("returns variants for sunday") {
            val variants = parser.getRouteVariants("M6", DayType.SUNDAY)
            variants.shouldNotBeEmpty()
        }
    }
})
