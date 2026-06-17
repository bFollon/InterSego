/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.github.bfollon.intersego.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeviceTokenService {

    private const val TAG = "DeviceTokenService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun register(token: String) {
        val serverUrl = BuildConfig.BOARDING_SERVER_URL
        val apiKey = BuildConfig.SERVER_API_KEY
        val body = JSONObject().apply {
            put("token", token)
            put("platform", "android")
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$serverUrl/device-tokens")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                DebugConfig.debugPrint("$TAG: register → HTTP ${response.code}")
            }
        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: register failed", e)
        }
    }
}
