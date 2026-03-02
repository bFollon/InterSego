/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.intersego.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service for scraping PDF URLs from the bus company website
 * This prevents PDF URLs from becoming stale by fetching the latest links at app startup
 * Uses OkHttp and regex patterns - much lighter than Jsoup!
 */
object PDFURLScrapingService {

    private const val TAG = "PDFURLScrapingService"
    private const val BASE_URL = "https://www.linecar.es/metropolitano/segovia/"

    // Cache for scraped PDF URLs by route ID
    private val scrapedURLs = mutableMapOf<String, String>()

    // Flag to track if scraping has completed
    @Volatile
    private var scrapingCompleted = false

    // OkHttp client for making requests
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Scraped PDF URL data for each bus route
     */
    data class ScrapedPDFData(
        val routeId: String,
        val pdfUrl: String,
        val lastUpdated: String? = null
    )

    /**
     * Get the scraped URL for a specific bus route
     * Returns the scraped URL if available, otherwise returns null
     */
    fun getScrapedURL(routeId: String): String? {
        return if (scrapingCompleted) {
            scrapedURLs[routeId]
        } else {
            null
        }
    }

    /**
     * Check if scraping has completed
     */
    fun isScrapingCompleted(): Boolean {
        return scrapingCompleted
    }

    /**
     * Scrape the bus company website to extract current PDF URLs
     * This runs on the IO dispatcher to avoid blocking the main thread
     */
    suspend fun scrapePDFURLs(): List<ScrapedPDFData> = withContext(Dispatchers.IO) {
        try {
            DebugConfig.debugPrint("$TAG: Starting PDF URL scraping from $BASE_URL")

            // Make HTTP request
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:13.0) Gecko/13.0 Firefox/13.0")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                DebugConfig.debugError("$TAG: HTTP request failed: ${response.code}")
                return@withContext emptyList()
            }

            val htmlContent = response.body?.string() ?: ""
            DebugConfig.debugPrint("$TAG: Successfully fetched HTML content (${htmlContent.length} chars)")

            // Extract PDF links using regex patterns
            val scrapedData = extractPDFDataFromHTML(htmlContent)

            DebugConfig.debugPrint("$TAG: Successfully scraped ${scrapedData.size} PDF URLs")

            // Cache the scraped URLs for later use
            scrapedData.forEach { data ->
                scrapedURLs[data.routeId] = data.pdfUrl
                DebugConfig.debugPrint("$TAG: Found PDF for ${data.routeId}: ${data.pdfUrl}")
                data.lastUpdated?.let {
                    DebugConfig.debugPrint("$TAG: Last updated: $it")
                }
            }

            // Mark scraping as completed
            scrapingCompleted = true
            DebugConfig.debugPrint("$TAG: Scraping completed, ${scrapedURLs.size} URLs cached")

            scrapedData

        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error scraping PDF URLs", e)
            emptyList()
        }
    }

    /**
     * Extract PDF data from HTML content using regex patterns
     * Much simpler and lighter than DOM parsing!
     *
     * Linecar-specific: Extracts ALL .pdf files and tries to determine route ID from filename
     * Supports both formats:
     * - Old format: "SEGOVIA-M4.pdf" -> "M4"
     * - New format: "M4.pdf" -> "M4"
     * - New format with dates: "M5-septiembre-2024.pdf" -> "M5"
     */
    private fun extractPDFDataFromHTML(htmlContent: String): List<ScrapedPDFData> {
        val scrapedData = mutableListOf<ScrapedPDFData>()

        try {
            // Pattern for ALL PDF links (no SEGOVIA requirement)
            val pdfPattern = Regex("""href="([^"]*\.pdf)"""", RegexOption.IGNORE_CASE)
            val matches = pdfPattern.findAll(htmlContent)
            DebugConfig.debugPrint("$TAG: Found ${matches.count()} PDF links in HTML")

            // Convert to set to remove duplicates, then back to list
            val uniquePdfUrls = matches.map { it.groupValues[1] }.toSet()
            DebugConfig.debugPrint("$TAG: After removing duplicates: ${uniquePdfUrls.size} unique PDF URLs")

            // Process each unique PDF link
            uniquePdfUrls.forEach { pdfUrl ->
                // Convert relative URLs to absolute
                val absoluteUrl = if (pdfUrl.startsWith("http")) {
                    pdfUrl
                } else if (pdfUrl.startsWith("/")) {
                    "https://www.linecar.es$pdfUrl"
                } else {
                    "https://www.linecar.es/metropolitano/segovia/$pdfUrl"
                }

                // Extract route ID from filename
                // Pattern: "SEGOVIA-M1.pdf" -> "M1", "SEGOVIA-M2-LABORABLES.pdf" -> "M2"
                val routeId = extractRouteIdFromLinecarURL(absoluteUrl)

                if (routeId.isNotEmpty()) {
                    scrapedData.add(
                        ScrapedPDFData(
                            routeId = routeId,
                            pdfUrl = absoluteUrl,
                            lastUpdated = extractLastUpdatedDateFromHTML(htmlContent)
                        )
                    )
                    DebugConfig.debugPrint("$TAG: ✅ Extracted route $routeId from $absoluteUrl")
                } else {
                    DebugConfig.debugWarn("$TAG: Could not extract route ID from: $absoluteUrl")
                }
            }

        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error extracting PDF data from HTML", e)
        }

        return scrapedData
    }

    /**
     * Extract route ID from Linecar PDF URL
     * Handles multiple filename formats:
     * - Old format: "https://www.linecar.es/.../SEGOVIA-M1.pdf" -> "M1"
     * - Old format with suffix: "https://www.linecar.es/.../SEGOVIA-M2-LABORABLES.pdf" -> "M2"
     * - New format: "https://www.linecar.es/.../M4.pdf" -> "M4"
     * - New format with date: "https://www.linecar.es/.../M5-septiembre-2024.pdf" -> "M5"
     */
    private fun extractRouteIdFromLinecarURL(url: String): String {
        return try {
            // Extract filename from URL
            val filename = url.substringAfterLast("/")

            // Try pattern 1: SEGOVIA-{ROUTE_ID} (old format)
            val segoviaPattern = Regex("""SEGOVIA-([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
            val segoviaMatch = segoviaPattern.find(filename)
            if (segoviaMatch != null) {
                return segoviaMatch.groupValues[1].uppercase()
            }

            // Try pattern 2: M{number}.pdf (new format)
            val simplePattern = Regex("""^(M[0-9]+)\.pdf$""", RegexOption.IGNORE_CASE)
            val simpleMatch = simplePattern.find(filename)
            if (simpleMatch != null) {
                return simpleMatch.groupValues[1].uppercase()
            }

            // Try pattern 3: M{number}-{anything}.pdf (new format with suffix)
            val suffixPattern = Regex("""^(M[0-9]+)-.*\.pdf$""", RegexOption.IGNORE_CASE)
            val suffixMatch = suffixPattern.find(filename)
            if (suffixMatch != null) {
                return suffixMatch.groupValues[1].uppercase()
            }

            DebugConfig.debugWarn("$TAG: Could not match any route pattern in filename: $filename")
            ""
        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error extracting route ID from URL", e)
            ""
        }
    }

    /**
     * Extract the last updated date from the HTML content using regex
     */
    private fun extractLastUpdatedDateFromHTML(htmlContent: String): String? {
        try {
            // Look for text that might contain update dates
            val updatePatterns = listOf(
                "actualización",
                "última actualización",
                "fecha de actualización",
                "actualizado",
                "updated"
            )

            updatePatterns.forEach { pattern ->
                val regex = Regex("$pattern[^\\d]*(\\d{1,2}[^\\d]*\\d{4})", RegexOption.IGNORE_CASE)
                val match = regex.find(htmlContent)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }

        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error extracting last updated date", e)
        }

        return null
    }

    /**
     * Print scraped data to console for debugging
     */
    fun printScrapedData(data: List<ScrapedPDFData>) {
        DebugConfig.debugPrint("$TAG: ===== SCRAPED PDF URLS =====")
        if (data.isEmpty()) {
            DebugConfig.debugPrint("$TAG: No PDF URLs found")
        } else {
            data.forEachIndexed { index, pdfData ->
                DebugConfig.debugPrint("$TAG: ${index + 1}. ${pdfData.routeId}")
                DebugConfig.debugPrint("$TAG:    URL: ${pdfData.pdfUrl}")
                pdfData.lastUpdated?.let {
                    DebugConfig.debugPrint("$TAG:    Last Updated: $it")
                }
            }
        }
        DebugConfig.debugPrint("$TAG: ============================")
    }
}
