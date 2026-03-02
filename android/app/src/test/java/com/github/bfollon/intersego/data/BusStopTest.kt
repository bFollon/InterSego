package com.github.bfollon.intersego.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BusStopTest : FunSpec({

    context("hasCoordinates") {
        test("true when coordinates string is valid") {
            val stop = BusStop(name = "Test", coordinates = "40.9, -4.1")
            stop.hasCoordinates shouldBe true
        }

        test("false when coordinates string is malformed") {
            val stop = BusStop(name = "Test", coordinates = "invalid")
            stop.hasCoordinates shouldBe false
        }
    }

    context("resolvedLatitude and resolvedLongitude") {
        test("parses lat/lon from coordinates string") {
            val stop = BusStop(name = "Test", coordinates = "40.9487, -4.1171")
            stop.resolvedLatitude shouldBe 40.9487
            stop.resolvedLongitude shouldBe -4.1171
        }
    }

    context("displayName") {
        test("includes stop code when present") {
            val stop = BusStop(name = "Plaza Mayor", coordinates = "40.9, -4.1", stopCode = "001")
            stop.displayName shouldBe "Plaza Mayor (001)"
        }

        test("just name when no stop code") {
            val stop = BusStop(name = "Plaza Mayor", coordinates = "40.9, -4.1")
            stop.displayName shouldBe "Plaza Mayor"
        }
    }

    context("routeCount") {
        test("returns number of routes served") {
            val stop = BusStop(name = "Test", coordinates = "40.9, -4.1", routesServed = listOf("M1", "M2", "M3"))
            stop.routeCount shouldBe 3
        }

        test("zero when no routes") {
            val stop = BusStop(name = "Test", coordinates = "40.9, -4.1")
            stop.routeCount shouldBe 0
        }
    }
})
