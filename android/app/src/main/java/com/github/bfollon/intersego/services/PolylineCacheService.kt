/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.content.Context
import android.content.SharedPreferences
import com.github.bfollon.intersego.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fetches route polyline JSON files from the server and caches them to disk.
 *
 * Uses ETag-based conditional requests (If-None-Match / 304) to avoid re-downloading
 * unchanged polylines. [PolylineLoader] checks the disk cache before falling back to
 * the bundled asset, so a successful fetch is picked up the next time a route map is
 * opened — no in-memory cache to evict, unlike [TimetableCacheService]'s pendingUpdates.
 *
 * Disk layout: [Context.getFilesDir]/route_polylines/{routeId}-{viewId}.json
 * ETag storage: SharedPreferences "polyline_cache", key "etag_{routeId}-{viewId}" (lowercase)
 */
object PolylineCacheService {

    private const val TAG = "PolylineCacheService"
    private const val PREFS_NAME = "polyline_cache"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val serverUrl get() = BuildConfig.BOARDING_SERVER_URL
    private val apiKey get() = BuildConfig.SERVER_API_KEY

    private fun bundlePolylineIds(context: Context): List<String> =
        (context.assets.list("route_polylines") ?: emptyArray())
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }

    fun cacheFile(context: Context, id: String): File =
        File(context.filesDir, "route_polylines/$id.json")

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun storedEtag(context: Context, id: String): String? =
        prefs(context).getString("etag_${id.lowercase()}", null)

    private fun saveEtag(context: Context, id: String, etag: String) =
        prefs(context).edit().putString("etag_${id.lowercase()}", etag).apply()

    internal suspend fun fetchPolyline(context: Context, id: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$serverUrl/api/polylines/$id")
                    .addHeader("Authorization", "Bearer $apiKey")
                storedEtag(context, id)?.let { requestBuilder.addHeader("If-None-Match", it) }
                val response = client.newCall(requestBuilder.build()).execute()
                DebugConfig.debugPrint("$TAG: GET /api/polylines/$id → HTTP ${response.code}")
                when (response.code) {
                    200 -> {
                        val bytes = response.body?.bytes() ?: return@withContext false
                        val file = cacheFile(context, id)
                        file.parentFile?.mkdirs()
                        file.writeBytes(bytes)
                        response.header("ETag")?.let { saveEtag(context, id, it) }
                        DebugConfig.debugPrint("$TAG: Updated $id (${bytes.size} bytes)")
                        true
                    }
                    304 -> {
                        DebugConfig.debugPrint("$TAG: $id unchanged (304)")
                        false
                    }
                    else -> {
                        DebugConfig.debugWarn("$TAG: $id fetch failed HTTP ${response.code}")
                        false
                    }
                }
            } catch (e: Exception) {
                DebugConfig.debugError("$TAG: $id fetch error", e)
                false
            }
        }

    /**
     * Fetches all polylines in parallel.
     * Safe to call on any dispatcher — internally runs on [Dispatchers.IO].
     */
    suspend fun fetchAllPolylines(context: Context) = coroutineScope {
        val ids = bundlePolylineIds(context)
        DebugConfig.debugPrint("$TAG: Starting fetch for ${ids.size} polylines")
        val updated = ids.map { id -> async { fetchPolyline(context, id) } }.awaitAll()
        DebugConfig.debugPrint("$TAG: Fetch complete, ${updated.count { it }} polyline(s) updated")
    }
}
