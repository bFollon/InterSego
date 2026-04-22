package com.github.bfollon.intersego.services.pdfparsing.strategies

import android.content.Context
import com.github.bfollon.intersego.data.DayType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class M4ParserTest : FunSpec({

    val parser = M4Parser(mockk<Context>())

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
            variants.any { "Lastrilla" in it.label } shouldBe true
            variants.any { "Sotillo" in it.label } shouldBe true
        }

        test("unsupported route returns empty") {
            parser.getRouteVariants("M1", DayType.WEEKDAY) shouldBe emptyList()
        }
    }
})
