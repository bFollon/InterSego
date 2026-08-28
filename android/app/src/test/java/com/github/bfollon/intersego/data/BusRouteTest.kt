package com.github.bfollon.intersego.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BusRouteTest : FunSpec({

    val route = BusRoute(
        number = "4",
        name = "La Lastrilla - El Sotillo",
        origin = "La Lastrilla",
        destination = "El Sotillo",
        sourceURL = "https://example.com/M4.pdf",
        routeType = RouteType.INTERURBAN
    )

    context("displayName") {
        test("includes line number and name") {
            route.displayName shouldBe "Línea 4: La Lastrilla - El Sotillo"
        }
    }

    context("shortName") {
        test("uses L prefix") {
            route.shortName shouldBe "L4"
        }

        test("multi-digit number") {
            val r = route.copy(number = "40")
            r.shortName shouldBe "L40"
        }
    }

    context("properties") {
        test("defaults") {
            route.active shouldBe true
            route.color shouldBe null
        }

        test("route type") {
            route.routeType shouldBe RouteType.INTERURBAN
            val urban = route.copy(routeType = RouteType.URBAN)
            urban.routeType shouldBe RouteType.URBAN
        }
    }
})
