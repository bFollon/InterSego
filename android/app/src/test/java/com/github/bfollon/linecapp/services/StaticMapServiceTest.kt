package com.github.bfollon.linecapp.services

import com.github.bfollon.linecapp.data.BusStop
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class StaticMapServiceTest : FunSpec({

    context("getTileCoordinates") {
        test("known coordinates at zoom 18 produce valid tile numbers") {
            // Segovia, Spain: ~40.9487, -4.1171
            val (x, y) = StaticMapService.getTileCoordinates(40.9487, -4.1171, 18)
            // At zoom 18, tile count = 2^18 = 262144
            (x in 0 until 262144) shouldBe true
            (y in 0 until 262144) shouldBe true
        }

        test("equator at prime meridian at zoom 0") {
            val (x, y) = StaticMapService.getTileCoordinates(0.0, 0.0, 0)
            x shouldBe 0
            y shouldBe 0
        }

        test("zoom 1 produces 4 possible tiles") {
            val (x, y) = StaticMapService.getTileCoordinates(40.0, -4.0, 1)
            (x in 0..1) shouldBe true
            (y in 0..1) shouldBe true
        }

        test("higher zoom produces larger tile numbers") {
            val (x15, _) = StaticMapService.getTileCoordinates(40.9487, -4.1171, 15)
            val (x18, _) = StaticMapService.getTileCoordinates(40.9487, -4.1171, 18)
            (x18 > x15) shouldBe true
        }
    }

    context("getMarkerPixelPosition") {
        test("pixel position within 0-256 range") {
            val (tileX, tileY) = StaticMapService.getTileCoordinates(40.9487, -4.1171, 18)
            val (px, py) = StaticMapService.getMarkerPixelPosition(40.9487, -4.1171, 18, tileX, tileY)
            px.shouldBeBetween(0f, 256f, 0.01f)
            py.shouldBeBetween(0f, 256f, 0.01f)
        }

        test("different coordinates in same tile give different positions") {
            val (tileX, tileY) = StaticMapService.getTileCoordinates(40.9487, -4.1171, 18)
            val (px1, py1) = StaticMapService.getMarkerPixelPosition(40.9487, -4.1171, 18, tileX, tileY)
            val (px2, py2) = StaticMapService.getMarkerPixelPosition(40.9490, -4.1175, 18, tileX, tileY)
            // They should be different (though both in same tile)
            (px1 != px2 || py1 != py2) shouldBe true
        }
    }

    context("getStaticMapData") {
        test("returns data for stop with coordinates") {
            val stop = BusStop(
                name = "Plaza Mayor",
                coordinates = "40.9487, -4.1171"
            )
            val data = StaticMapService.getStaticMapData(stop)
            data.shouldNotBeNull()
            data.tileUrl.startsWith("https://tile.openstreetmap.org/") shouldBe true
            data.markerX.shouldBeBetween(0f, 256f, 0.01f)
            data.markerY.shouldBeBetween(0f, 256f, 0.01f)
        }

        test("returns null for stop with malformed coordinates") {
            val stop = BusStop(name = "No Coords", coordinates = "invalid")
            StaticMapService.getStaticMapData(stop) shouldBe null
        }
    }

    context("canShowMap") {
        test("true for stop with valid coordinates") {
            val stop = BusStop(name = "Test", coordinates = "40.9, -4.1")
            StaticMapService.canShowMap(stop) shouldBe true
        }

        test("false for stop with malformed coordinates") {
            val stop = BusStop(name = "Test", coordinates = "invalid")
            StaticMapService.canShowMap(stop) shouldBe false
        }
    }
})
