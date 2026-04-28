/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.data

/**
 * Represents the progress state of a PDF update operation
 * Used for UI feedback during cache updates
 */
sealed class UpdateProgressState {
    data object Checking : UpdateProgressState()
    data object Downloading : UpdateProgressState()
    data object Downloaded : UpdateProgressState()
    data object UpToDate : UpdateProgressState()
    data class Error(val message: String) : UpdateProgressState()
}
