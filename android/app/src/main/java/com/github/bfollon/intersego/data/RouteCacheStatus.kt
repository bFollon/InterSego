/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.data

import kotlinx.serialization.Serializable

/**
 * Represents the cache status for a specific bus route
 * Used for debugging and UI display of cache information
 */
@Serializable
data class RouteCacheStatus(
    val routeId: String,
    val isCached: Boolean,
    val downloadDate: Long? = null,
    val fileSize: Long? = null,
    val lastChecked: Long? = null,
    val needsUpdate: Boolean
)
