package com.github.bfollon.intersego.services

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.lang.reflect.Method

class PDFURLScrapingServiceTest : FunSpec({

    // Access private methods via reflection for testing
    val extractRouteIdMethod: Method = PDFURLScrapingService::class.java
        .getDeclaredMethod("extractRouteIdFromLinecarURL", String::class.java)
        .also { it.isAccessible = true }

    val extractPDFDataMethod: Method = PDFURLScrapingService::class.java
        .getDeclaredMethod("extractPDFDataFromHTML", String::class.java)
        .also { it.isAccessible = true }

    fun extractRouteId(url: String): String =
        extractRouteIdMethod.invoke(PDFURLScrapingService, url) as String

    @Suppress("UNCHECKED_CAST")
    fun extractPDFData(html: String): List<PDFURLScrapingService.ScrapedPDFData> =
        extractPDFDataMethod.invoke(PDFURLScrapingService, html) as List<PDFURLScrapingService.ScrapedPDFData>

    context("extractRouteIdFromLinecarURL") {
        test("old format: SEGOVIA-M1.pdf") {
            extractRouteId("https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M1.pdf") shouldBe "M1"
        }

        test("old format with suffix: SEGOVIA-M2-LABORABLES.pdf") {
            extractRouteId("https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M2-LABORABLES.pdf") shouldBe "M2"
        }

        test("new format: M4.pdf") {
            extractRouteId("https://www.linecar.es/wp-content/uploads/2025/10/M4.pdf") shouldBe "M4"
        }

        test("new format with date suffix: M5-septiembre-2024.pdf") {
            extractRouteId("https://www.linecar.es/wp-content/uploads/2024/09/M5-septiembre-2024.pdf") shouldBe "M5"
        }

        test("case insensitive: m4.pdf") {
            extractRouteId("https://www.linecar.es/wp-content/uploads/2025/10/m4.pdf") shouldBe "M4"
        }

        test("unknown format returns empty") {
            extractRouteId("https://www.linecar.es/wp-content/uploads/random.pdf") shouldBe ""
        }
    }

    context("extractPDFDataFromHTML") {
        test("extracts PDF links from simple HTML") {
            val html = """
                <html>
                <body>
                <a href="https://www.linecar.es/wp-content/uploads/2025/10/M4.pdf">M4</a>
                <a href="https://www.linecar.es/wp-content/uploads/2025/12/M6.pdf">M6</a>
                </body>
                </html>
            """.trimIndent()

            val result = extractPDFData(html)
            result shouldHaveSize 2
            result.map { it.routeId }.toSet() shouldBe setOf("M4", "M6")
        }

        test("handles relative URLs") {
            val html = """<a href="/wp-content/uploads/2025/10/M4.pdf">M4</a>"""
            val result = extractPDFData(html)
            result shouldHaveSize 1
            result[0].pdfUrl.startsWith("https://www.linecar.es/") shouldBe true
        }

        test("deduplicates URLs") {
            val html = """
                <a href="https://www.linecar.es/M4.pdf">link1</a>
                <a href="https://www.linecar.es/M4.pdf">link2</a>
            """.trimIndent()
            val result = extractPDFData(html)
            result shouldHaveSize 1
        }

        test("ignores non-route PDFs") {
            val html = """<a href="https://www.linecar.es/random-document.pdf">doc</a>"""
            val result = extractPDFData(html)
            result shouldHaveSize 0
        }

        test("empty HTML returns empty") {
            extractPDFData("") shouldHaveSize 0
        }
    }
})
