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
 * A user-marked favorite stop, identified by the specific stop + route + direction (view)
 * combo shown on NextDepartureScreen at the time it was favorited.
 */
@Serializable
data class FavoriteStop(
    val stopId: String,
    val stopName: String,
    val routeId: String,
    val routeNumber: String,
    val viewId: String,
    val direction: String,
    val mergedDirectionLabel: String? = null,
) {
    /** Composite key that uniquely identifies a stop+route+direction combo. */
    val matchKey: String
        get() = matchKey(stopId, routeId, viewId)

    companion object {
        fun matchKey(stopId: String, routeId: String, viewId: String): String =
            "$stopId|$routeId|$viewId"
    }
}
