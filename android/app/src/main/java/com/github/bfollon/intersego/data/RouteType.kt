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
 * Type of bus route
 */
@Serializable
enum class RouteType {
    /**
     * Urban bus routes within Segovia city
     */
    URBAN,

    /**
     * Interurban routes between cities and towns
     */
    INTERURBAN
}
