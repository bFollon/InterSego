/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.github.bfollon.intersego.BuildConfig
import com.github.bfollon.intersego.data.BoardingEvent
import com.github.bfollon.intersego.data.BoardingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Handles all network communication with the InterSego boarding notification server.
 *
 * The server is a dumb repository — it stores boarding events and returns them.
 * All trip matching and ETA computation happen on the client using local timetable data.
 */
object BoardingService {

    private const val TAG = "BoardingService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val serverUrl get() = BuildConfig.BOARDING_SERVER_URL
    private val apiKey get() = BuildConfig.BOARDING_API_KEY

    /**
     * Submit a boarding event to the server.
     *
     * Returns [Result.success] on HTTP 2xx, [Result.failure] on any network or server error.
     * Does not throw.
     */
    suspend fun postBoarding(request: BoardingRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(BoardingRequest.serializer(), request)
                    .toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$serverUrl/boardings")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()
                val response = client.newCall(httpRequest).execute()
                DebugConfig.debugPrint("$TAG: postBoarding → HTTP ${response.code}")
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("HTTP ${response.code}"))
            } catch (e: Exception) {
                DebugConfig.debugError("$TAG: postBoarding failed", e)
                Result.failure(e)
            }
        }

    /**
     * Fetch all active (non-expired) boarding events from the server.
     *
     * Returns [Result.success] with the list on HTTP 2xx, [Result.failure] on error.
     * Does not throw.
     */
    suspend fun fetchBoardings(): Result<List<BoardingEvent>> =
        withContext(Dispatchers.IO) {
            try {
                val httpRequest = Request.Builder()
                    .url("$serverUrl/boardings")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                val response = client.newCall(httpRequest).execute()
                DebugConfig.debugPrint("$TAG: fetchBoardings → HTTP ${response.code}")
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "[]"
                    Result.success(json.decodeFromString<List<BoardingEvent>>(responseBody))
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                DebugConfig.debugError("$TAG: fetchBoardings failed", e)
                Result.failure(e)
            }
        }
}
