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
 * Category of user feedback, used to drive copy across the feedback form,
 * BugSink submission, and email fallback.
 */
enum class FeedbackCategory {
    BUG,
    SUGGESTION;

    val title: String
        get() = when (this) {
            BUG -> "Reportar error"
            SUGGESTION -> "Enviar sugerencia"
        }

    val prompt: String
        get() = when (this) {
            BUG -> "Describe el problema que has encontrado:"
            SUGGESTION -> "Cuéntanos tu idea o sugerencia:"
        }

    val fieldLabel: String
        get() = when (this) {
            BUG -> "Descripción del error"
            SUGGESTION -> "Sugerencia"
        }

    val fieldPlaceholder: String
        get() = when (this) {
            BUG -> "¿Qué ha pasado? ¿Cuándo?"
            SUGGESTION -> "¿Qué te gustaría que mejoráramos?"
        }

    val emptyFieldError: String
        get() = when (this) {
            BUG -> "Por favor, describe el error"
            SUGGESTION -> "Por favor, escribe tu sugerencia"
        }

    val submitButtonLabel: String
        get() = when (this) {
            BUG -> "Enviar informe"
            SUGGESTION -> "Enviar sugerencia"
        }

    val successTitle: String
        get() = when (this) {
            BUG -> "¡Error reportado!"
            SUGGESTION -> "¡Gracias por tu sugerencia!"
        }

    val successBody: String
        get() = when (this) {
            BUG -> "Gracias por ayudar a mejorar la app."
            SUGGESTION -> "La tendremos en cuenta para mejorar la app."
        }

    /** Header written into the BugSink message body. */
    val bugSinkHeader: String
        get() = when (this) {
            BUG -> "=== User Bug Report ==="
            SUGGESTION -> "=== User Suggestion ==="
        }

    /** Fingerprint tag used to group BugSink events by category. */
    val fingerprintTag: String
        get() = when (this) {
            BUG -> "user-bug-report"
            SUGGESTION -> "user-suggestion"
        }

    val mailtoSubject: String
        get() = when (this) {
            BUG -> "Reporte de error - InterSego"
            SUGGESTION -> "Sugerencias y mejoras - InterSego"
        }

    val mailtoBody: String
        get() = when (this) {
            BUG -> ""
            SUGGESTION -> "Me gustaría sugerir..."
        }
}
