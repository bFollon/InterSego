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
import com.aptabase.Aptabase
import com.aptabase.InitOptions

/**
 * Service for product analytics via Aptabase (self-hosted).
 *
 * Initialization is gated on user consent. If the user has not opted in,
 * the SDK is never started and track() calls are no-ops.
 * Equivalent to iOS AnalyticsService.
 */
object AnalyticsService {

    private var initialized = false

    /**
     * Initialize the Aptabase SDK.
     * Must be called after MonitoringPreferencesService is initialized.
     */
    fun initialize(context: Context) {
        if (!MonitoringPreferencesService.hasUserOptedInToAnalytics()) {
            DebugConfig.debugPrint("AnalyticsService: skipping init (user has not opted in)")
            return
        }

        Aptabase.instance.initialize(context, Secrets.aptabaseKey, InitOptions(host = Secrets.aptabaseHost))
        initialized = true
        DebugConfig.debugPrint("AnalyticsService: Aptabase initialized")
    }

    /**
     * Track a named event with optional properties.
     * No-ops if the SDK was not initialized (user has not opted in).
     */
    fun track(eventName: String, props: Map<String, Any> = emptyMap()) {
        if (!initialized) return
        if (props.isEmpty()) {
            Aptabase.instance.trackEvent(eventName)
        } else {
            Aptabase.instance.trackEvent(eventName, props)
        }
        DebugConfig.debugPrint("AnalyticsService: tracked '$eventName'")
    }
}
