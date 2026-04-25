package com.github.bfollon.intersego.services.pdfparsing.strategies

import android.content.Context
import com.github.bfollon.intersego.data.DayType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class M6ParserTest : FunSpec({

    val parser = M6Parser(mockk<Context>())

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
        test("returns 3 variants for weekday") {
            val variants = parser.getRouteVariants("M6", DayType.WEEKDAY)
            variants.size shouldBe 3
        }

        test("weekday variants contain outbound and inbound directions") {
            val variants = parser.getRouteVariants("M6", DayType.WEEKDAY)
            variants.any { "Segovia" in it.label && "Torrecaballeros" in it.label } shouldBe true
            variants.any { "Torrecaballeros" in it.label && "Segovia" in it.label } shouldBe true
        }

        test("returns 2 variants for saturday") {
            val variants = parser.getRouteVariants("M6", DayType.SATURDAY)
            variants.size shouldBe 2
        }

        test("returns 2 variants for sunday") {
            val variants = parser.getRouteVariants("M6", DayType.SUNDAY)
            variants.size shouldBe 2
        }

        test("unsupported route returns empty") {
            parser.getRouteVariants("M4", DayType.WEEKDAY) shouldBe emptyList()
        }
    }
})
