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
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Loads and caches the festivo calendar ([docs/HOLIDAY_CALENDAR.md]).
 *
 * Offline-first: [initialize] loads whatever is available (disk cache, then bundled
 * asset) synchronously into memory with no network involved, so a device that has
 * never been online still resolves plain weekday/Saturday/Sunday correctly — a
 * missing calendar is not an error, [isHoliday] just returns false for every date.
 * [refresh] is the network half, mirroring [TimetableCacheService]/[PolylineCacheService]:
 * GET /api/holidays, ETag/If-None-Match, disk cache at [Context.getFilesDir]/holidays/all.json.
 *
 * Unlike timetables there's no per-screen in-memory cache to evict — [refresh] simply
 * replaces the in-memory date set when a fetch lands, and every future [isHoliday] call
 * sees the update immediately.
 *
 * Bundles a copy of the current year's calendar (`assets/holidays/2026.json`) as a
 * cold-start floor: it will go stale year over year, but it means a fresh install with
 * no connectivity yet isn't wrong about this year's festivos. Out of scope here: wiring
 * this into actual day-type resolution (`TimetableQueryUtils.dayTypesForDate`) — that's
 * a separate card.
 */
object HolidayService {

    private const val TAG = "HolidayService"
    private const val PREFS_NAME = "holiday_cache"
    private const val ETAG_KEY = "etag_holidays"
    private const val ASSET_DIR = "holidays"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val serverUrl get() = BuildConfig.BOARDING_SERVER_URL
    private val apiKey get() = BuildConfig.SERVER_API_KEY

    @Serializable
    private data class HolidayEntry(val date: String, val name: String = "", val scope: String = "")

    @Serializable
    private data class HolidayYearFile(val year: Int = 0, val holidays: List<HolidayEntry> = emptyList())

    @Volatile
    private var holidayDates: Set<String> = emptySet()

    private fun cacheFile(context: Context): File = File(context.filesDir, "$ASSET_DIR/all.json")

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun storedEtag(context: Context): String? = prefs(context).getString(ETAG_KEY, null)

    private fun saveEtag(context: Context, etag: String) =
        prefs(context).edit().putString(ETAG_KEY, etag).apply()

    /** Server responds with a JSON array, one entry per known year. */
    private fun datesFromYearArray(text: String): Set<String> =
        json.decodeFromString<List<HolidayYearFile>>(text)
            .flatMap { it.holidays }
            .map { it.date }
            .toSet()

    /** Bundled/`resources/holidays/{year}.json` assets are a single year object each. */
    private fun datesFromBundleAsset(text: String): Set<String> =
        json.decodeFromString<HolidayYearFile>(text).holidays.map { it.date }.toSet()

    private fun loadFromDisk(context: Context): Set<String>? {
        val file = cacheFile(context)
        if (!file.exists()) return null
        return try {
            datesFromYearArray(file.readText()).ifEmpty { null }
        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: failed to parse disk cache", e)
            null
        }
    }

    private fun loadFromBundle(context: Context): Set<String> {
        val files = context.assets.list(ASSET_DIR)?.filter { it.endsWith(".json") } ?: emptyList()
        return files.flatMap { name ->
            try {
                val text = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
                datesFromBundleAsset(text)
            } catch (e: Exception) {
                DebugConfig.debugError("$TAG: failed to parse bundled asset $name", e)
                emptyList()
            }
        }.toSet()
    }

    /**
     * Loads whatever holiday data is available (disk cache, else bundled asset) into
     * memory. No network access — safe to call unconditionally at startup, offline or not.
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        holidayDates = loadFromDisk(context) ?: loadFromBundle(context)
        DebugConfig.debugPrint("$TAG: loaded ${holidayDates.size} holiday date(s) into memory")
    }

    /**
     * Fetches the latest calendar from the server and, on success, replaces the
     * in-memory date set and disk cache. Call only when online; failures are logged
     * and otherwise silent — [isHoliday] keeps serving whatever [initialize] loaded.
     */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url("$serverUrl/api/holidays")
                .addHeader("Authorization", "Bearer $apiKey")
            storedEtag(context)?.let { requestBuilder.addHeader("If-None-Match", it) }
            val response = client.newCall(requestBuilder.build()).execute()
            DebugConfig.debugPrint("$TAG: GET /api/holidays → HTTP ${response.code}")
            when (response.code) {
                200 -> {
                    val bytes = response.body?.bytes() ?: return@withContext false
                    val dates = datesFromYearArray(bytes.toString(Charsets.UTF_8))
                    val file = cacheFile(context)
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                    response.header("ETag")?.let { saveEtag(context, it) }
                    holidayDates = dates
                    DebugConfig.debugPrint("$TAG: updated, ${dates.size} holiday date(s)")
                    true
                }
                304 -> {
                    DebugConfig.debugPrint("$TAG: unchanged (304)")
                    false
                }
                else -> {
                    DebugConfig.debugWarn("$TAG: fetch failed HTTP ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: fetch error", e)
            false
        }
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** True if [date] (defaults to today) is a known festivo. Never throws; false if no calendar is loaded. */
    fun isHoliday(date: Calendar = Calendar.getInstance()): Boolean =
        holidayDates.contains(isoFormat.format(date.time))
}
