package com.github.bfollon.linecapp.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BusStopTest : FunSpec({

    context("hasCoordinates") {
        test("true when both lat and lon are set") {
            val stop = BusStop(name = "Test", address = "Addr", latitude = 40.9, longitude = -4.1)
            stop.hasCoordinates shouldBe true
        }

        test("false when latitude is null") {
            val stop = BusStop(name = "Test", address = "Addr", latitude = null, longitude = -4.1)
            stop.hasCoordinates shouldBe false
        }

        test("false when longitude is null") {
            val stop = BusStop(name = "Test", address = "Addr", latitude = 40.9, longitude = null)
            stop.hasCoordinates shouldBe false
        }

        test("false when both are null") {
            val stop = BusStop(name = "Test", address = "Addr")
            stop.hasCoordinates shouldBe false
        }
    }

    context("displayName") {
        test("includes stop code when present") {
            val stop = BusStop(name = "Plaza Mayor", address = "Addr", stopCode = "001")
            stop.displayName shouldBe "Plaza Mayor (001)"
        }

        test("just name when no stop code") {
            val stop = BusStop(name = "Plaza Mayor", address = "Addr")
            stop.displayName shouldBe "Plaza Mayor"
        }
    }

    context("routeCount") {
        test("returns number of routes served") {
            val stop = BusStop(name = "Test", address = "Addr", routesServed = listOf("M1", "M2", "M3"))
            stop.routeCount shouldBe 3
        }

        test("zero when no routes") {
            val stop = BusStop(name = "Test", address = "Addr")
            stop.routeCount shouldBe 0
        }
    }
})
