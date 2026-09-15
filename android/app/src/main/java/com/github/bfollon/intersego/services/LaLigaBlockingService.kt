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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Detects whether our server is unreachable because of LaLiga's court-authorized IP
 * blocking (used against Cloudflare-hosted piracy sites during football matches, which
 * collaterally blocks unrelated sites sharing the same IPs — including our own tunnel).
 *
 * This is deliberately separate from [NetworkMonitor]: the device can have perfectly good
 * internet while our own server is unreachable, and that combination is what this service
 * distinguishes from a generic offline state.
 *
 * [onServerUnreachable]/[onServerReachable] are called by [TimetableCacheService.fetchManifest]
 * — our most frequent call to our own server — whenever it observes a request success or
 * failure, since this service has no server calls of its own to hook a failure into.
 */
object LaLigaBlockingService {

    private const val TAG = "LaLigaBlockingService"
    private const val STATUS_URL = "https://hayahora.futbol/estado/blocked-any.txt"
    private const val MIN_RECHECK_INTERVAL_MS = 2 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var lastCheckAtMs = 0L

    @Volatile
    var isLikelyBlocked: Boolean = false
        private set

    /** Call when a request to our own server succeeds — clears any stale blocked state. */
    fun onServerReachable() {
        isLikelyBlocked = false
    }

    /**
     * Call when a request to our own server fails. If the device is online, this checks
     * hayahora.futbol's live list of blocked IPs (rate-limited to once per
     * [MIN_RECHECK_INTERVAL_MS]) to see whether a LaLiga blocking wave is the likely cause.
     */
    suspend fun onServerUnreachable() {
        if (!NetworkMonitor.isOnline()) {
            isLikelyBlocked = false
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastCheckAtMs < MIN_RECHECK_INTERVAL_MS) return
        lastCheckAtMs = now
        isLikelyBlocked = checkHayAhoraFutbol()
    }

    /**
     * Checks whether our own server's IP is among those currently blocked, rather than just
     * whether *some* blocking is active — a resolved-and-matched IP is strong evidence,
     * while an empty/unreachable blocklist is strong evidence against.
     *
     * If our own hostname can't be resolved at all, we have no way to confirm a specific
     * match, so we fall back to correlation (blocklist non-empty) as the best available signal.
     */
    private suspend fun checkHayAhoraFutbol(): Boolean = withContext(Dispatchers.IO) {
        val blockedIps = fetchBlockedIps() ?: return@withContext false
        if (blockedIps.isEmpty()) return@withContext false

        val resolvedIps = resolveServerIps()
        if (resolvedIps.isNullOrEmpty()) {
            DebugConfig.debugWarn("$TAG: could not resolve our own server IP, falling back to correlation")
            true
        } else {
            resolvedIps.any { it in blockedIps }
        }
    }

    private fun fetchBlockedIps(): Set<String>? = try {
        val request = Request.Builder().url(STATUS_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
        }
    } catch (e: Exception) {
        DebugConfig.debugError("$TAG: hayahora.futbol check failed", e)
        null
    }

    private fun resolveServerIps(): Set<String>? = try {
        val host = URI(BuildConfig.BOARDING_SERVER_URL).host
        host?.let { InetAddress.getAllByName(it).mapNotNull { addr -> addr.hostAddress }.toSet() }
    } catch (e: Exception) {
        DebugConfig.debugError("$TAG: could not resolve our own server host", e)
        null
    }
}
