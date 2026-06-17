/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.data

import kotlinx.serialization.Serializable

@Serializable
data class ServiceAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: String,              // "info" | "warning" | "critical"
    val affectedRoutes: List<String>? = null,
    val startsAt: String,
    val endsAt: String,
)
