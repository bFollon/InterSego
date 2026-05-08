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
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Fetches timetable JSON files from the server and caches them to disk.
 *
 * Uses ETag-based conditional requests (If-None-Match / 304) to avoid re-downloading
 * unchanged routes. Updated routes are flagged via [hasPendingUpdate] so that
 * [TimetableService] can evict its in-memory cache on the next access.
 *
 * Disk layout: [Context.getFilesDir]/timetables/{routeId}.json
 * ETag storage: SharedPreferences "timetable_cache", key "etag_{routeId}"
 */
object TimetableCacheService {

    private const val TAG = "TimetableCacheService"
    private const val PREFS_NAME = "timetable_cache"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val serverUrl get() = BuildConfig.BOARDING_SERVER_URL
    private val apiKey get() = BuildConfig.SERVER_API_KEY

    private val pendingUpdates = Collections.synchronizedSet(mutableSetOf<String>())

    private fun bundleRouteIds(context: Context): List<String> =
        (context.assets.list("timetables") ?: emptyArray())
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }

    fun cacheFile(context: Context, routeId: String): File =
        File(context.filesDir, "timetables/${routeId.lowercase()}.json")

    fun hasPendingUpdate(routeId: String): Boolean = routeId.lowercase() in pendingUpdates

    fun clearPendingUpdate(routeId: String) { pendingUpdates.remove(routeId.lowercase()) }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun storedEtag(context: Context, routeId: String): String? =
        prefs(context).getString("etag_${routeId.lowercase()}", null)

    private fun saveEtag(context: Context, routeId: String, etag: String) =
        prefs(context).edit().putString("etag_${routeId.lowercase()}", etag).apply()

    private suspend fun fetchRoute(context: Context, routeId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$serverUrl/api/timetables/$routeId")
                    .addHeader("Authorization", "Bearer $apiKey")
                storedEtag(context, routeId)?.let { requestBuilder.addHeader("If-None-Match", it) }
                val response = client.newCall(requestBuilder.build()).execute()
                DebugConfig.debugPrint("$TAG: GET /api/timetables/$routeId → HTTP ${response.code}")
                when (response.code) {
                    200 -> {
                        val bytes = response.body?.bytes() ?: return@withContext false
                        val file = cacheFile(context, routeId)
                        file.parentFile?.mkdirs()
                        file.writeBytes(bytes)
                        response.header("ETag")?.let { saveEtag(context, routeId, it) }
                        pendingUpdates.add(routeId.lowercase())
                        DebugConfig.debugPrint("$TAG: Updated $routeId (${bytes.size} bytes)")
                        true
                    }
                    304 -> {
                        DebugConfig.debugPrint("$TAG: $routeId unchanged (304)")
                        false
                    }
                    else -> {
                        DebugConfig.debugWarn("$TAG: $routeId fetch failed HTTP ${response.code}")
                        false
                    }
                }
            } catch (e: Exception) {
                DebugConfig.debugError("$TAG: $routeId fetch error", e)
                false
            }
        }

    /**
     * Fetches all routes in parallel. Updated routes are flagged via [hasPendingUpdate].
     * Safe to call on any dispatcher — internally runs on [Dispatchers.IO].
     */
    suspend fun fetchAllRoutes(context: Context) = coroutineScope {
        val routeIds = bundleRouteIds(context)
        DebugConfig.debugPrint("$TAG: Starting fetch for ${routeIds.size} routes")
        routeIds.map { routeId -> async { fetchRoute(context, routeId) } }.awaitAll()
        DebugConfig.debugPrint("$TAG: Fetch complete, ${pendingUpdates.size} route(s) updated")
    }
}
