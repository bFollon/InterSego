package com.github.bfollon.linecapp.services

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class TileCacheServiceTest : FunSpec({

    context("osmUrlToRelativePath") {
        test("extracts zoom, x, y from standard OSM tile URL") {
            osmUrlToRelativePath("https://tile.openstreetmap.org/18/128456/98012.png") shouldBe "18/128456/98012.png"
        }

        test("works with HTTP scheme") {
            osmUrlToRelativePath("http://tile.openstreetmap.org/15/100/200.png") shouldBe "15/100/200.png"
        }

        test("handles zoom level 0") {
            osmUrlToRelativePath("https://tile.openstreetmap.org/0/0/0.png") shouldBe "0/0/0.png"
        }

        test("handles maximum zoom level 19") {
            osmUrlToRelativePath("https://tile.openstreetmap.org/19/524287/524287.png") shouldBe "19/524287/524287.png"
        }

        test("works with alternate OSM tile subdomains") {
            osmUrlToRelativePath("https://a.tile.openstreetmap.org/18/100/200.png") shouldBe "18/100/200.png"
            osmUrlToRelativePath("https://b.tile.openstreetmap.org/18/100/200.png") shouldBe "18/100/200.png"
            osmUrlToRelativePath("https://c.tile.openstreetmap.org/18/100/200.png") shouldBe "18/100/200.png"
        }

        test("returns null for non-OSM URLs") {
            osmUrlToRelativePath("https://maps.google.com/tile/18/100/200.png").shouldBeNull()
        }

        test("returns null for URLs without .png extension") {
            osmUrlToRelativePath("https://tile.openstreetmap.org/18/100/200.jpg").shouldBeNull()
        }

        test("returns null for empty string") {
            osmUrlToRelativePath("").shouldBeNull()
        }

        test("returns null for malformed URLs") {
            osmUrlToRelativePath("not-a-url").shouldBeNull()
            osmUrlToRelativePath("https://tile.openstreetmap.org/").shouldBeNull()
            osmUrlToRelativePath("https://tile.openstreetmap.org/18/100.png").shouldBeNull()
        }

        test("Segovia area tile coordinates produce correct path") {
            // Real tile coordinates for Segovia at zoom 18
            val url = "https://tile.openstreetmap.org/18/128150/98220.png"
            osmUrlToRelativePath(url) shouldBe "18/128150/98220.png"
        }

        test("deduplication: same URL always produces same path") {
            val url = "https://tile.openstreetmap.org/18/128150/98220.png"
            osmUrlToRelativePath(url) shouldBe osmUrlToRelativePath(url)
        }

        test("adjacent tiles produce different paths") {
            val center = osmUrlToRelativePath("https://tile.openstreetmap.org/18/100/200.png")
            val right = osmUrlToRelativePath("https://tile.openstreetmap.org/18/101/200.png")
            val below = osmUrlToRelativePath("https://tile.openstreetmap.org/18/100/201.png")

            center shouldBe "18/100/200.png"
            right shouldBe "18/101/200.png"
            below shouldBe "18/100/201.png"
        }
    }
})
