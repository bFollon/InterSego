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
