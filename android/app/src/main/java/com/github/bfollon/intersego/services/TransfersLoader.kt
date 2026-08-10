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
import com.github.bfollon.intersego.data.TransferEdge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Loads `assets/transfers.json` — the walking-transfer table between distinct physical stops. */
class TransfersLoader(private val context: Context) {

    @Serializable
    private data class TransfersFile(
        val version: String,
        val transfers: List<TransferEntry>
    )

    @Serializable
    private data class TransferEntry(
        val from: String,
        val to: String,
        val meters: Int,
        val walkMinutes: Int
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<TransferEdge> {
        val text = context.assets.open("transfers.json").bufferedReader().readText()
        val file: TransfersFile = json.decodeFromString(text)
        return file.transfers.map { TransferEdge(it.from, it.to, it.meters, it.walkMinutes) }
    }
}
