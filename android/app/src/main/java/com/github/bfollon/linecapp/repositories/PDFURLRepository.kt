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

package com.github.bfollon.linecapp.repositories

import android.content.Context
import android.content.SharedPreferences
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.NetworkMonitor
import com.github.bfollon.linecapp.services.PDFURLScrapingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Repository for managing bus timetable PDF URLs with scraping, persistence, and validation
 *
 * Responsibilities:
 * - Scrape PDF URLs from bus company website
 * - Persist scraped URLs to SharedPreferences
 * - Validate URLs with HEAD requests
 * - Provide self-healing URL resolution
 */
class PDFURLRepository private constructor(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    // OkHttp client for HEAD requests
    private val headClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // In-memory cache for HEAD request results (until app restart)
    private val validationCache = mutableMapOf<String, URLValidationResult>()

    companion object {
        private const val PREFS_NAME = "pdf_url_repository"
        private const val SCRAPED_URLS_KEY = "scraped_urls"
        private const val LAST_SCRAPE_KEY = "last_scrape_timestamp"

        // Hardcoded fallback URLs for bus routes
        // Note: These may become outdated. The scraping service should provide current URLs.
        // Actual routes: M1-M8 (no M9, M10, M11, M12)
        private val FALLBACK_URLS = mapOf(
            "M1" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M1.pdf",
            "M2" to "https://www.linecar.es/wp-content/uploads/2024/09/M2-septiembre-2024.pdf",
            "M3" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M3.pdf",
            "M4" to "https://www.linecar.es/wp-content/uploads/2025/10/M4.pdf",
            "M5" to "https://www.linecar.es/wp-content/uploads/2024/09/M5-septiembre-2024.pdf",
            "M6" to "https://www.linecar.es/wp-content/uploads/2025/12/M6.pdf",
            "M7" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M7.pdf",
            "M8" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M8.pdf"
        )

        /**
         * Normalize route ID to lookup key
         * Handles display names vs storage keys
         */
        private fun normalizeRouteId(routeId: String): String {
            return when {
                routeId.contains("Línea 1", ignoreCase = true) || routeId.contains("Linea 1", ignoreCase = true) -> "L1"
                routeId.contains("Línea 2", ignoreCase = true) || routeId.contains("Linea 2", ignoreCase = true) -> "L2"
                routeId.contains("Línea 3", ignoreCase = true) || routeId.contains("Linea 3", ignoreCase = true) -> "L3"
                routeId.contains("Línea 4", ignoreCase = true) || routeId.contains("Linea 4", ignoreCase = true) -> "L4"
                else -> routeId.uppercase()
            }
        }

        @Volatile
        private var INSTANCE: PDFURLRepository? = null

        fun getInstance(context: Context): PDFURLRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PDFURLRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * Data class for persisted scraped URLs
     */
    @Serializable
    data class ScrapedURLData(
        val urls: Map<String, String>,
        val timestamp: Long
    )

    /**
     * Result of URL validation
     */
    sealed class URLValidationResult {
        data class Valid(val url: String) : URLValidationResult()
        data class Invalid(val url: String, val statusCode: Int) : URLValidationResult()
        data class Error(val url: String, val message: String) : URLValidationResult()
    }

    /**
     * Result of URL resolution with self-healing
     */
    sealed class URLResolutionResult {
        data class Success(val url: String) : URLResolutionResult()
        data class Updated(val oldUrl: String, val newUrl: String) : URLResolutionResult()
        data class Failed(val message: String) : URLResolutionResult()
    }

    // MARK: - Persistence

    /**
     * Load persisted scraped URLs from SharedPreferences
     */
    private fun loadPersistedURLs(): Map<String, String> {
        val json = sharedPreferences.getString(SCRAPED_URLS_KEY, null) ?: return emptyMap()

        return try {
            val data = this.json.decodeFromString<ScrapedURLData>(json)
            DebugConfig.debugPrint("📂 PDFURLRepository: Loaded ${data.urls.size} persisted URLs")
            data.urls
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to load persisted URLs", e)
            emptyMap()
        }
    }

    /**
     * Persist scraped URLs to SharedPreferences
     */
    private fun persistURLs(urls: Map<String, String>) {
        try {
            val data = ScrapedURLData(
                urls = urls,
                timestamp = System.currentTimeMillis()
            )
            val jsonString = json.encodeToString(data)

            sharedPreferences.edit()
                .putString(SCRAPED_URLS_KEY, jsonString)
                .putLong(LAST_SCRAPE_KEY, System.currentTimeMillis())
                .apply()

            DebugConfig.debugPrint("💾 PDFURLRepository: Persisted ${urls.size} URLs to storage")
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to persist URLs", e)
        }
    }

    // MARK: - URL Validation

    /**
     * Validate a URL with a HEAD request
     * Returns validation result and caches it in memory
     */
    suspend fun validateURL(url: String, useCache: Boolean = true): URLValidationResult = withContext(Dispatchers.IO) {
        // Check in-memory cache first
        if (useCache && validationCache.containsKey(url)) {
            val cached = validationCache[url]!!
            DebugConfig.debugPrint("✅ PDFURLRepository: Using cached validation for $url")
            return@withContext cached
        }

        try {
            DebugConfig.debugPrint("🔍 PDFURLRepository: Validating URL with HEAD request: $url")

            val request = Request.Builder()
                .url(url)
                .head()
                .addHeader("User-Agent", "LineCapp-Android/1.0")
                .build()

            val response = headClient.newCall(request).execute()

            val result = if (response.isSuccessful) {
                DebugConfig.debugPrint("✅ PDFURLRepository: URL is valid (${response.code})")
                URLValidationResult.Valid(url)
            } else {
                DebugConfig.debugWarn("❌ PDFURLRepository: URL returned ${response.code}")
                URLValidationResult.Invalid(url, response.code)
            }

            // Cache the result
            validationCache[url] = result
            result

        } catch (e: Exception) {
            DebugConfig.debugError("PDFURLRepository: Error validating URL", e)
            val result = URLValidationResult.Error(url, e.message ?: "Unknown error")
            validationCache[url] = result
            result
        }
    }

    // MARK: - Scraping

    /**
     * Scrape fresh URLs from bus company website
     */
    suspend fun scrapeURLs(): Map<String, String> = withContext(Dispatchers.IO) {
        DebugConfig.debugPrint("🌐 PDFURLRepository: Starting fresh URL scraping...")

        val scrapedData = PDFURLScrapingService.scrapePDFURLs()

        if (scrapedData.isEmpty()) {
            DebugConfig.debugWarn("PDFURLRepository: Scraping returned no results")
            return@withContext emptyMap()
        }

        val urlMap = scrapedData.associate { it.routeId to it.pdfUrl }

        // Persist the scraped URLs
        persistURLs(urlMap)

        DebugConfig.debugPrint("✅ PDFURLRepository: Scraped and persisted ${urlMap.size} URLs")
        urlMap
    }

    /**
     * Validate all persisted URLs with HEAD requests
     * Returns map of valid URLs
     */
    suspend fun validatePersistedURLs(): Map<String, String> = withContext(Dispatchers.IO) {
        val persistedURLs = loadPersistedURLs()

        if (persistedURLs.isEmpty()) {
            DebugConfig.debugPrint("📭 PDFURLRepository: No persisted URLs to validate")
            return@withContext emptyMap()
        }

        DebugConfig.debugPrint("🔍 PDFURLRepository: Validating ${persistedURLs.size} persisted URLs...")

        val validURLs = mutableMapOf<String, String>()

        for ((routeId, url) in persistedURLs) {
            when (val result = validateURL(url, useCache = false)) {
                is URLValidationResult.Valid -> {
                    validURLs[routeId] = url
                    DebugConfig.debugPrint("✅ $routeId: Valid")
                }
                is URLValidationResult.Invalid -> {
                    DebugConfig.debugWarn("❌ $routeId: Invalid (${result.statusCode})")
                }
                is URLValidationResult.Error -> {
                    DebugConfig.debugWarn("⚠️ $routeId: Error (${result.message})")
                }
            }
        }

        DebugConfig.debugPrint("✅ PDFURLRepository: ${validURLs.size}/${persistedURLs.size} URLs are valid")
        validURLs
    }

    // MARK: - URL Resolution

    /**
     * Get the best URL for a bus route (persisted > fallback)
     */
    fun getURL(routeId: String): String {
        val normalizedId = normalizeRouteId(routeId)
        val persistedURLs = loadPersistedURLs()
        val url = persistedURLs[normalizedId] ?: FALLBACK_URLS[normalizedId]

        if (url == null) {
            DebugConfig.debugError("No URL found for route: $routeId (normalized: $normalizedId)", null)
            return ""
        }

        return url
    }

    /**
     * Resolve URL with self-healing:
     * 1. Check persisted URL is valid (HEAD request)
     * 2. If invalid (404), scrape fresh URLs
     * 3. Return new URL or fail
     */
    suspend fun resolveURLWithHealing(routeId: String): URLResolutionResult = withContext(Dispatchers.IO) {
        val normalizedId = normalizeRouteId(routeId)

        // Check if we're online
        if (!NetworkMonitor.isOnline()) {
            val url = getURL(normalizedId)
            DebugConfig.debugPrint("📡 PDFURLRepository: Offline, using stored URL for $normalizedId")
            return@withContext URLResolutionResult.Success(url)
        }

        val currentURL = getURL(normalizedId)

        DebugConfig.debugPrint("🔄 PDFURLRepository: Resolving URL for $normalizedId with self-healing")

        // Validate current URL
        when (val validation = validateURL(currentURL)) {
            is URLValidationResult.Valid -> {
                DebugConfig.debugPrint("✅ PDFURLRepository: Current URL is valid")
                return@withContext URLResolutionResult.Success(currentURL)
            }
            is URLValidationResult.Invalid -> {
                if (validation.statusCode == 404) {
                    DebugConfig.debugWarn("🔄 PDFURLRepository: URL returned 404, attempting self-healing...")

                    // Scrape fresh URLs
                    val freshURLs = scrapeURLs()
                    val newURL = freshURLs[normalizedId]

                    if (newURL != null && newURL != currentURL) {
                        DebugConfig.debugPrint("✅ PDFURLRepository: Found new URL: $newURL")
                        return@withContext URLResolutionResult.Updated(currentURL, newURL)
                    } else if (newURL != null) {
                        DebugConfig.debugWarn("⚠️ PDFURLRepository: Scraped URL is same as old URL")
                        return@withContext URLResolutionResult.Failed("No se puede acceder al horario en este momento. Inténtalo más tarde.")
                    } else {
                        DebugConfig.debugError("PDFURLRepository: Could not find new URL for $normalizedId", null)
                        return@withContext URLResolutionResult.Failed("No se puede acceder al horario en este momento. Inténtalo más tarde.")
                    }
                } else {
                    DebugConfig.debugWarn("PDFURLRepository: URL returned ${validation.statusCode}")
                    return@withContext URLResolutionResult.Failed("No se puede acceder al horario (Error ${validation.statusCode})")
                }
            }
            is URLValidationResult.Error -> {
                DebugConfig.debugError("PDFURLRepository: Error validating URL", null)
                return@withContext URLResolutionResult.Failed("Error de red. Inténtalo más tarde.")
            }
        }
    }

    // MARK: - Initialization

    /**
     * Initialize repository - scrape and validate URLs
     * Called during splash screen
     */
    suspend fun initializeURLs(): Boolean = withContext(Dispatchers.IO) {
        DebugConfig.debugPrint("🚀 PDFURLRepository: Initializing URLs...")

        // Check if we're online
        if (!NetworkMonitor.isOnline()) {
            DebugConfig.debugPrint("📡 PDFURLRepository: Offline, skipping initialization")
            return@withContext true // Success (will use persisted/fallback URLs)
        }

        // First, validate persisted URLs
        val validPersistedURLs = validatePersistedURLs()

        // If all routes have valid persisted URLs, we're done
        val allRoutes = FALLBACK_URLS.keys
        if (validPersistedURLs.keys.containsAll(allRoutes)) {
            DebugConfig.debugPrint("✅ PDFURLRepository: All persisted URLs are valid, no scraping needed")
            return@withContext true
        }

        // Otherwise, scrape fresh URLs
        DebugConfig.debugPrint("🌐 PDFURLRepository: Some URLs are invalid, scraping fresh URLs...")
        val scrapedURLs = scrapeURLs()

        if (scrapedURLs.isEmpty()) {
            DebugConfig.debugWarn("⚠️ PDFURLRepository: Scraping failed, will use fallback URLs")
            return@withContext false
        }

        DebugConfig.debugPrint("✅ PDFURLRepository: Initialization complete")
        return@withContext true
    }

    // MARK: - Utilities

    /**
     * Clear all cached data (for debugging)
     */
    fun clearCache() {
        sharedPreferences.edit().clear().apply()
        validationCache.clear()
        DebugConfig.debugPrint("🗑️ PDFURLRepository: Cleared all cached data")
    }

    /**
     * Get repository status for debugging
     */
    fun getStatus(): String {
        val persistedURLs = loadPersistedURLs()
        val lastScrape = sharedPreferences.getLong(LAST_SCRAPE_KEY, 0L)
        val lastScrapeDate = if (lastScrape > 0) {
            java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastScrape))
        } else {
            "Never"
        }

        return buildString {
            appendLine("PDFURLRepository Status:")
            appendLine("Persisted URLs: ${persistedURLs.size}")
            appendLine("Last scrape: $lastScrapeDate")
            appendLine("Validation cache: ${validationCache.size} entries")
            appendLine()
            persistedURLs.forEach { (routeId, url) ->
                appendLine("$routeId: ${url.substringAfterLast("/")}")
            }
        }
    }
}
