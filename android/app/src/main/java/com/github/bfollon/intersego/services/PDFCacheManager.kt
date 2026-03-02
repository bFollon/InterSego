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

import android.content.Context
import android.content.SharedPreferences
import com.github.bfollon.intersego.data.PDFVersion
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.RouteCacheStatus
import com.github.bfollon.intersego.data.UpdateProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages local caching and updating of bus timetable PDF files
 * Implements iOS PDFCacheManager functionality for Android
 */
class PDFCacheManager private constructor(private val context: Context) {

    private val pdfDownloadService = PDFDownloadService(context)
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    // OkHttp client for HEAD requests (version checking)
    private val headClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val PREFS_NAME = "pdf_cache_manager"
        private const val VERSION_STORAGE_KEY = "pdf_versions"
        private const val LAST_UPDATE_CHECK_KEY = "last_update_check"

        @Volatile
        private var INSTANCE: PDFCacheManager? = null

        fun getInstance(context: Context): PDFCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PDFCacheManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    // MARK: - Version Management

    /**
     * Get stored version info for a bus route
     */
    private fun getStoredVersion(routeId: String): PDFVersion? {
        val versionsJson = sharedPreferences.getString(VERSION_STORAGE_KEY, null) ?: return null

        return try {
            val versions = json.decodeFromString<Map<String, PDFVersion>>(versionsJson)
            versions[routeId]
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to decode version data", e)
            null
        }
    }

    /**
     * Get the download date for a cached bus route
     * Returns the timestamp when the PDF was downloaded, or null if not cached
     */
    fun getDownloadDate(routeId: String): Long? {
        return getStoredVersion(routeId)?.downloadDate
    }

    /**
     * Store version info for a bus route
     */
    private fun storeVersion(version: PDFVersion, routeId: String) {
        try {
            // Load existing versions
            val versionsJson = sharedPreferences.getString(VERSION_STORAGE_KEY, null)
            val versions = if (versionsJson != null) {
                json.decodeFromString<MutableMap<String, PDFVersion>>(versionsJson)
            } else {
                mutableMapOf()
            }

            // Update with new version
            versions[routeId] = version

            // Save back to SharedPreferences
            sharedPreferences.edit()
                .putString(VERSION_STORAGE_KEY, json.encodeToString(versions))
                .apply()

            DebugConfig.debugPrint("💾 PDFCacheManager: Stored version info for $routeId")
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to store version data", e)
        }
    }

    // MARK: - File Management

    /**
     * Get the local cache file for a bus route (if it exists)
     */
    fun cachedFileURL(routeId: String): File? {
        val fileName = cacheFileName(routeId)
        val pdfDir = File(context.filesDir, "pdfs")
        val file = File(pdfDir, fileName)

        return if (file.exists()) file else null
    }

    /**
     * Generate cache filename for a bus route
     */
    private fun cacheFileName(routeId: String): String {
        return "${routeId.lowercase().replace(" ", "-")}.pdf"
    }

    // MARK: - Version Checking

    /**
     * Check remote PDF version without downloading the full file
     */
    suspend fun checkRemoteVersion(url: String): PDFVersion? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head() // HEAD request - only get headers
                .addHeader("User-Agent", "InterSego-Android/1.0")
                .build()

            val response = headClient.newCall(request).execute()

            if (!response.isSuccessful) {
                DebugConfig.debugError("Failed to get remote PDF info: ${response.code}", null)
                return@withContext null
            }

            val lastModifiedStr = response.header("Last-Modified")
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            val etag = response.header("ETag")

            // Parse Last-Modified date
            val lastModified = lastModifiedStr?.let { parseHttpDate(it) }

            PDFVersion(
                url = url,
                lastModified = lastModified,
                contentLength = contentLength,
                etag = etag
            )
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to check remote version for $url", e)
            null
        }
    }

    /**
     * Check if cached version is up to date
     */
    suspend fun isCacheUpToDate(routeId: String, pdfUrl: String, debugMode: Boolean = false): Boolean {
        val cachedFile = cachedFileURL(routeId)
        val cachedVersion = getStoredVersion(routeId)

        if (cachedFile == null || cachedVersion == null) {
            DebugConfig.debugPrint("🔍 PDFCacheManager: No cached file or version for $routeId")
            return false
        }

        val remoteVersion = checkRemoteVersion(pdfUrl) ?: run {
            DebugConfig.debugPrint("❌ PDFCacheManager: Failed to check remote version, assuming cache is valid")
            return true // If we can't check remote, assume cache is valid
        }

        if (debugMode) {
            DebugConfig.debugPrint("🔍 PDFCacheManager: Comparing versions for $routeId:")
            DebugConfig.debugPrint("   Cached ETag: ${cachedVersion.etag ?: "nil"}")
            DebugConfig.debugPrint("   Remote ETag: ${remoteVersion.etag ?: "nil"}")
            DebugConfig.debugPrint("   Cached Last-Modified: ${cachedVersion.lastModified ?: "nil"}")
            DebugConfig.debugPrint("   Remote Last-Modified: ${remoteVersion.lastModified ?: "nil"}")
            DebugConfig.debugPrint("   Cached Size: ${cachedVersion.contentLength ?: "nil"}")
            DebugConfig.debugPrint("   Remote Size: ${remoteVersion.contentLength ?: "nil"}")
        }

        // 1. First try Last-Modified (most reliable for this server)
        if (cachedVersion.lastModified != null && remoteVersion.lastModified != null) {
            val isMatch = cachedVersion.lastModified == remoteVersion.lastModified
            if (debugMode) {
                DebugConfig.debugPrint("   ✅ Last-Modified comparison: ${if (isMatch) "MATCH" else "DIFFERENT"}")
            }
            return isMatch
        }

        // 2. Then try Content-Length as backup
        if (cachedVersion.contentLength != null && remoteVersion.contentLength != null) {
            val isMatch = cachedVersion.contentLength == remoteVersion.contentLength
            if (debugMode) {
                DebugConfig.debugPrint("   ✅ Content-Length comparison: ${if (isMatch) "MATCH" else "DIFFERENT"}")
            }
            return isMatch
        }

        // 3. Finally try ETag (least reliable for this server)
        if (cachedVersion.etag != null && remoteVersion.etag != null) {
            val isMatch = cachedVersion.etag == remoteVersion.etag
            if (debugMode) {
                DebugConfig.debugPrint("   ✅ ETag comparison: ${if (isMatch) "MATCH" else "DIFFERENT"}")
            }
            return isMatch
        }

        // If no comparison criteria available, consider outdated
        DebugConfig.debugPrint("   ❌ No comparison criteria available, assuming outdated")
        return false
    }

    // MARK: - Download and Cache

    /**
     * Download and cache a PDF file for a bus route
     */
    suspend fun downloadAndCache(routeId: String, pdfUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val fileName = cacheFileName(routeId)

            // Force download
            val file = pdfDownloadService.downloadPDF(pdfUrl, fileName, forceDownload = true)
                ?: return@withContext null

            // Store version information
            val remoteVersion = checkRemoteVersion(pdfUrl)
            if (remoteVersion != null) {
                storeVersion(remoteVersion, routeId)
            }

            DebugConfig.debugPrint("✅ PDFCacheManager: Successfully cached PDF for $routeId")
            file
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to download and cache PDF for $routeId", e)
            null
        }
    }

    /**
     * Get the effective PDF file (cached if available and up-to-date, otherwise download)
     */
    suspend fun getEffectivePDFFile(routeId: String, pdfUrl: String): File? {
        // Check if we're offline first
        val isOnline = NetworkMonitor.isOnline()

        if (!isOnline) {
            // Offline: use cache if available, otherwise fail immediately
            val cachedFile = cachedFileURL(routeId)
            if (cachedFile != null) {
                DebugConfig.debugPrint("📡 PDFCacheManager: Offline - using cached file for $routeId")
                return cachedFile
            } else {
                DebugConfig.debugWarn("📡 PDFCacheManager: Offline and no cache available for $routeId")
                return null
            }
        }

        // Online: check if we have a valid cached version
        val isCacheValid = isCacheUpToDate(routeId, pdfUrl)

        return if (isCacheValid) {
            cachedFileURL(routeId) ?: downloadAndCache(routeId, pdfUrl)
        } else {
            // Cache is outdated or doesn't exist, download
            downloadAndCache(routeId, pdfUrl)
        }
    }

    // MARK: - Cache Management

    /**
     * Clear all cached PDF files and version info
     */
    fun clearCache() {
        pdfDownloadService.clearCache()

        // Clear version info
        sharedPreferences.edit()
            .remove(VERSION_STORAGE_KEY)
            .apply()

        DebugConfig.debugPrint("🗑️ PDFCacheManager: Cleared all version info and PDFs")
    }

    /**
     * Clear cache for a specific bus route
     */
    fun clearCache(routeId: String) {
        val fileName = cacheFileName(routeId)
        val pdfDir = File(context.filesDir, "pdfs")
        val file = File(pdfDir, fileName)

        if (file.exists()) {
            file.delete()
            DebugConfig.debugPrint("🗑️ PDFCacheManager: Removed cached file for $routeId")
        }

        // Remove version info for this route
        try {
            val versionsJson = sharedPreferences.getString(VERSION_STORAGE_KEY, null)
            if (versionsJson != null) {
                val versions = json.decodeFromString<MutableMap<String, PDFVersion>>(versionsJson)
                versions.remove(routeId)

                sharedPreferences.edit()
                    .putString(VERSION_STORAGE_KEY, json.encodeToString(versions))
                    .apply()
            }
        } catch (e: Exception) {
            DebugConfig.debugError("Error removing version info for $routeId", e)
        }
    }

    /**
     * Force download for a bus route (bypasses cache check)
     */
    suspend fun forceDownload(routeId: String, pdfUrl: String): File? {
        clearCache(routeId)
        return downloadAndCache(routeId, pdfUrl)
    }

    // MARK: - Public Interface

    /**
     * Initialize cache manager (call on app launch)
     */
    fun initialize() {
        DebugConfig.debugPrint("🚀 PDFCacheManager: Initialized")
        DebugConfig.debugPrint(getCacheInfo())
    }

    /**
     * Check if a cached file exists for the bus route
     */
    fun hasCachedFile(routeId: String): Boolean {
        return cachedFileURL(routeId) != null
    }

    /**
     * Get cache info for debugging
     */
    fun getCacheInfo(): String {
        val pdfDir = File(context.filesDir, "pdfs")

        val info = StringBuilder()
        info.append("PDFCacheManager Status:\n")
        info.append("PDF Directory: ${pdfDir.path}\n\n")

        val files = pdfDir.listFiles() ?: emptyArray()
        info.append("Total cached files: ${files.size}\n\n")

        files.forEach { file ->
            val routeId = file.nameWithoutExtension
            info.append("📄 $routeId: ✅\n")

            getStoredVersion(routeId)?.let { version ->
                val formatter = SimpleDateFormat("d/M/yy H:mm", Locale.getDefault())
                info.append("   Downloaded: ${formatter.format(Date(version.downloadDate))}\n")
                version.contentLength?.let { size ->
                    info.append("   Size: ${size / 1024} KB\n")
                }
            } ?: run {
                info.append("   No version info\n")
            }
            info.append("\n")
        }

        return info.toString()
    }

    // MARK: - Automatic Update Checking

    /**
     * Check if we should perform automatic PDF update check
     */
    private fun shouldCheckForUpdates(): Boolean {
        val lastCheck = sharedPreferences.getLong(LAST_UPDATE_CHECK_KEY, 0)

        if (lastCheck == 0L) {
            return true // Never checked before
        }

        // Check once per day
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return lastCheck < oneDayAgo
    }

    /**
     * Record that we performed an update check
     */
    private fun recordUpdateCheck() {
        sharedPreferences.edit()
            .putLong(LAST_UPDATE_CHECK_KEY, System.currentTimeMillis())
            .apply()
    }

    /**
     * Check all bus routes for PDF updates and download if needed
     */
    suspend fun checkForUpdatesIfNeeded(routes: List<BusRoute>) {
        if (!shouldCheckForUpdates()) {
            DebugConfig.debugPrint("📅 PDFCacheManager: Skipping update check - already checked today")
            return
        }

        DebugConfig.debugPrint("🔍 PDFCacheManager: Checking for PDF updates...")
        recordUpdateCheck()

        for (route in routes) {
            checkAndUpdateIfNeeded(route.id, route.pdfURL)
        }

        DebugConfig.debugPrint("✅ PDFCacheManager: Update check completed")
    }

    /**
     * Check a specific bus route and update if needed
     */
    private suspend fun checkAndUpdateIfNeeded(routeId: String, pdfUrl: String, debugMode: Boolean = false) {
        val isCacheValid = isCacheUpToDate(routeId, pdfUrl, debugMode)

        if (!isCacheValid) {
            val file = downloadAndCache(routeId, pdfUrl)
            if (file != null) {
                DebugConfig.debugPrint("📥 PDFCacheManager: Updated PDF for $routeId")
            } else {
                DebugConfig.debugPrint("❌ PDFCacheManager: Failed to update PDF for $routeId")
            }
        } else {
            DebugConfig.debugPrint("✅ PDFCacheManager: PDF for $routeId is up to date")
        }
    }

    /**
     * Force check for updates (ignores daily limit)
     */
    suspend fun forceCheckForUpdates(routes: List<BusRoute>) {
        DebugConfig.debugPrint("🔄 PDFCacheManager: Force checking for PDF updates...")
        recordUpdateCheck()

        for (route in routes) {
            checkAndUpdateIfNeeded(route.id, route.pdfURL, debugMode = true)
        }

        DebugConfig.debugPrint("✅ PDFCacheManager: Force update check completed")
    }

    /**
     * Force check for updates with progress callbacks for UI
     */
    suspend fun forceCheckForUpdatesWithProgress(
        routes: List<BusRoute>,
        progressCallback: suspend (String, UpdateProgressState) -> Unit
    ) {
        DebugConfig.debugPrint("🔄 PDFCacheManager: Force checking for PDF updates with progress...")
        recordUpdateCheck()

        for (route in routes) {
            checkAndUpdateIfNeededWithProgress(route.id, route.pdfURL, progressCallback)
        }

        DebugConfig.debugPrint("✅ PDFCacheManager: Force update check with progress completed")
    }

    /**
     * Check a specific bus route and update if needed with progress callbacks
     */
    private suspend fun checkAndUpdateIfNeededWithProgress(
        routeId: String,
        pdfUrl: String,
        progressCallback: suspend (String, UpdateProgressState) -> Unit
    ) {
        // Notify checking started
        progressCallback(routeId, UpdateProgressState.Checking)

        val isCacheValid = isCacheUpToDate(routeId, pdfUrl, debugMode = true)

        if (!isCacheValid) {
            // Notify download starting
            progressCallback(routeId, UpdateProgressState.Downloading)

            val file = downloadAndCache(routeId, pdfUrl)
            if (file != null) {
                DebugConfig.debugPrint("📥 PDFCacheManager: Updated PDF for $routeId")
                progressCallback(routeId, UpdateProgressState.Downloaded)
            } else {
                DebugConfig.debugPrint("❌ PDFCacheManager: Failed to update PDF for $routeId")
                progressCallback(routeId, UpdateProgressState.Error("Failed to download"))
            }
        } else {
            DebugConfig.debugPrint("✅ PDFCacheManager: PDF for $routeId is up to date")
            progressCallback(routeId, UpdateProgressState.UpToDate)
        }
    }

    /**
     * Get structured cache status for all bus routes
     */
    suspend fun getCacheStatus(routes: List<BusRoute>): List<RouteCacheStatus> = withContext(Dispatchers.IO) {
        val lastUpdateCheck = sharedPreferences.getLong(LAST_UPDATE_CHECK_KEY, 0L)
            .takeIf { it > 0 }

        routes.map { route ->
            val cachedFile = cachedFileURL(route.id)
            val storedVersion = getStoredVersion(route.id)

            val isCached = cachedFile != null
            val downloadDate = storedVersion?.downloadDate
            val fileSize = cachedFile?.length()
            val needsUpdate = if (isCached) {
                !isCacheUpToDate(route.id, route.pdfURL)
            } else {
                false
            }

            RouteCacheStatus(
                routeId = route.id,
                isCached = isCached,
                downloadDate = downloadDate,
                fileSize = fileSize,
                lastChecked = lastUpdateCheck,
                needsUpdate = needsUpdate
            )
        }
    }

    /**
     * Clear the last update check timestamp (for debugging)
     */
    fun clearLastUpdateCheck() {
        sharedPreferences.edit()
            .remove(LAST_UPDATE_CHECK_KEY)
            .apply()
        DebugConfig.debugPrint("🗑️ PDFCacheManager: Cleared last update check timestamp")
    }

    // MARK: - Helper Functions

    /**
     * Parse HTTP date string to timestamp
     */
    private fun parseHttpDate(dateString: String): Long? {
        val formats = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",     // RFC 1123
            "EEEE, dd-MMM-yy HH:mm:ss 'GMT'",     // RFC 850
            "EEE MMM d HH:mm:ss yyyy"             // ANSI C asctime()
        )

        for (format in formats) {
            try {
                val formatter = SimpleDateFormat(format, Locale.US)
                formatter.timeZone = java.util.TimeZone.getTimeZone("GMT")
                val date = formatter.parse(dateString)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next format
            }
        }

        return null
    }
}
