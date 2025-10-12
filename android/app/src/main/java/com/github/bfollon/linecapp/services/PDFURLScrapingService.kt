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

package com.github.bfollon.linecapp.services

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
     * Linecar-specific: Extracts PDFs matching pattern "SEGOVIA-***.pdf"
     * Route ID is extracted from the filename after "SEGOVIA-" (e.g., "SEGOVIA-M1" -> "M1")
     */
    private fun extractPDFDataFromHTML(htmlContent: String): List<ScrapedPDFData> {
        val scrapedData = mutableListOf<ScrapedPDFData>()

        try {
            // Pattern specifically for Linecar PDFs: must contain "SEGOVIA-" and end with ".pdf"
            val linecarPattern = Regex("""href="([^"]*SEGOVIA-[^"]*\.pdf)"""", RegexOption.IGNORE_CASE)
            val matches = linecarPattern.findAll(htmlContent)
            DebugConfig.debugPrint("$TAG: Found ${matches.count()} Linecar PDF links in HTML")

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
     * Examples:
     * - "https://www.linecar.es/.../SEGOVIA-M1.pdf" -> "M1"
     * - "https://www.linecar.es/.../SEGOVIA-M2-LABORABLES.pdf" -> "M2"
     * - "https://www.linecar.es/.../SEGOVIA-M10.pdf" -> "M10"
     */
    private fun extractRouteIdFromLinecarURL(url: String): String {
        return try {
            // Extract filename from URL
            val filename = url.substringAfterLast("/")

            // Pattern: SEGOVIA-{ROUTE_ID} (may have additional parts after route ID)
            // Match: SEGOVIA- followed by alphanumeric characters until next dash or dot
            val routePattern = Regex("""SEGOVIA-([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
            val match = routePattern.find(filename)

            if (match != null) {
                match.groupValues[1].uppercase()
            } else {
                DebugConfig.debugWarn("$TAG: Could not match route pattern in filename: $filename")
                ""
            }
        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error extracting route ID from URL", e)
            ""
        }
    }

    /**
     * Determine the route ID based on PDF URL and surrounding context
     */
    private fun determineRouteIdFromURL(pdfUrl: String, htmlContent: String): String {
        // First try to determine from the URL itself
        val urlLower = pdfUrl.lowercase()

        // TODO: Customize these patterns based on your bus routes
        // Example patterns:
        when {
            urlLower.contains("l1") || urlLower.contains("linea-1") -> return "L1"
            urlLower.contains("l2") || urlLower.contains("linea-2") -> return "L2"
            urlLower.contains("l3") || urlLower.contains("linea-3") -> return "L3"
            urlLower.contains("l4") || urlLower.contains("linea-4") -> return "L4"
        }

        // If URL doesn't contain route info, look in surrounding context
        // Find the context around this URL in the HTML
        val contextStart = maxOf(0, htmlContent.indexOf(pdfUrl) - 200)
        val contextEnd = minOf(htmlContent.length, htmlContent.indexOf(pdfUrl) + pdfUrl.length + 200)
        val context = htmlContent.substring(contextStart, contextEnd).lowercase()

        // TODO: Define route keywords based on your bus routes
        val routeKeywords = mapOf(
            "l1" to "L1",
            "línea 1" to "L1",
            "linea 1" to "L1",
            "l2" to "L2",
            "línea 2" to "L2",
            "linea 2" to "L2",
            "l3" to "L3",
            "línea 3" to "L3",
            "linea 3" to "L3"
        )

        routeKeywords.forEach { (keyword, routeId) ->
            if (context.contains(keyword)) {
                return routeId
            }
        }

        return ""
    }

    /**
     * Determine the route ID based on link text and surrounding context
     */
    private fun determineRouteIdFromText(linkText: String, htmlContent: String): String {
        // Check if the link text itself contains route information
        // TODO: Define route keywords based on your bus routes
        val routeKeywords = mapOf(
            "l1" to "L1",
            "línea 1" to "L1",
            "linea 1" to "L1",
            "l2" to "L2",
            "línea 2" to "L2",
            "linea 2" to "L2",
            "l3" to "L3",
            "línea 3" to "L3",
            "linea 3" to "L3"
        )

        val searchText = linkText.lowercase()

        routeKeywords.forEach { (keyword, routeId) ->
            if (searchText.contains(keyword)) {
                return routeId
            }
        }

        // If link text doesn't contain route info, look in surrounding context
        // Find the context around this link in the HTML
        val contextStart = maxOf(0, htmlContent.indexOf(linkText) - 200)
        val contextEnd = minOf(htmlContent.length, htmlContent.indexOf(linkText) + linkText.length + 200)
        val context = htmlContent.substring(contextStart, contextEnd).lowercase()

        routeKeywords.forEach { (keyword, routeId) ->
            if (context.contains(keyword)) {
                return routeId
            }
        }

        return ""
    }

    /**
     * Alternative extraction method that looks for route sections using regex
     */
    private fun extractPDFsByRouteSections(htmlContent: String): List<ScrapedPDFData> {
        val scrapedData = mutableListOf<ScrapedPDFData>()

        try {
            // Look for headings that might indicate bus routes using regex
            val headingPattern = Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", RegexOption.IGNORE_CASE)
            val headings = headingPattern.findAll(htmlContent)

            headings.forEach { headingMatch ->
                val headingText = headingMatch.groupValues[1].trim().lowercase()

                // TODO: Customize route detection based on your bus routes
                val routeId = when {
                    headingText.contains("l1") || headingText.contains("línea 1") || headingText.contains("linea 1") -> "L1"
                    headingText.contains("l2") || headingText.contains("línea 2") || headingText.contains("linea 2") -> "L2"
                    headingText.contains("l3") || headingText.contains("línea 3") || headingText.contains("linea 3") -> "L3"
                    else -> null
                }

                if (routeId != null) {
                    // Look for PDF links in the section after this heading
                    val sectionStart = headingMatch.range.last
                    val sectionEnd = minOf(htmlContent.length, sectionStart + 1000) // Look in next 1000 chars
                    val section = htmlContent.substring(sectionStart, sectionEnd)

                    val pdfPattern = Regex("""<a[^>]*href="([^"]*\.pdf)"[^>]*>""", RegexOption.IGNORE_CASE)
                    val pdfMatches = pdfPattern.findAll(section)

                    pdfMatches.forEach { pdfMatch ->
                        val pdfUrl = pdfMatch.groupValues[1]
                        val absoluteUrl = if (pdfUrl.startsWith("http")) {
                            pdfUrl
                        } else if (pdfUrl.startsWith("/")) {
                            "https://avilabus.es$pdfUrl" // TODO: Update with actual bus website domain
                        } else {
                            "https://avilabus.es/$pdfUrl" // TODO: Update with actual bus website domain
                        }

                        if (absoluteUrl.isNotEmpty()) {
                            scrapedData.add(
                                ScrapedPDFData(
                                    routeId = routeId,
                                    pdfUrl = absoluteUrl,
                                    lastUpdated = extractLastUpdatedDateFromHTML(htmlContent)
                                )
                            )
                        }
                    }
                }
            }

        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error in alternative PDF extraction", e)
        }

        return scrapedData
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
