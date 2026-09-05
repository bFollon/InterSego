/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.github.bfollon.intersego.data.FeedbackCategory
import com.github.bfollon.intersego.services.AnalyticsService
import com.github.bfollon.intersego.services.ErrorReportingService
import com.github.bfollon.intersego.services.MonitoringPreferencesService

/**
 * Coordinates the "report error" / "send suggestion" flow:
 * if the user has opted into error reporting, present the in-app feedback
 * sheet (BugSink); otherwise offer to opt in or fall back to email.
 */
class FeedbackCoordinator {
    var presentedCategory by mutableStateOf<FeedbackCategory?>(null)
    var noConsentCategory by mutableStateOf<FeedbackCategory?>(null)

    fun trigger(category: FeedbackCategory, source: String) {
        AnalyticsService.track(
            "feedback_triggered",
            mapOf("category" to category.name.lowercase(), "source" to source)
        )
        if (MonitoringPreferencesService.hasUserOptedIn()) {
            presentedCategory = category
        } else {
            noConsentCategory = category
        }
    }
}

@Composable
fun rememberFeedbackCoordinator(): FeedbackCoordinator = remember { FeedbackCoordinator() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackDialogs(coordinator: FeedbackCoordinator) {
    val context = LocalContext.current

    coordinator.presentedCategory?.let { category ->
        FeedbackSheet(
            category = category,
            onDismiss = { coordinator.presentedCategory = null }
        )
    }

    coordinator.noConsentCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { coordinator.noConsentCategory = null },
            title = { Text("Informes de error desactivados") },
            text = { Text("Los informes de error están desactivados. Puedes activarlos para enviar el mensaje directamente, o contactar por email.") },
            confirmButton = {
                TextButton(onClick = {
                    MonitoringPreferencesService.setMonitoringEnabled(true)
                    ErrorReportingService.initialize(context)
                    coordinator.presentedCategory = category
                    coordinator.noConsentCategory = null
                }) {
                    Text("Activar informes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    openEmail(context, makeMailtoUrl(subject = category.mailtoSubject, body = category.mailtoBody))
                    coordinator.noConsentCategory = null
                }) {
                    Text("Enviar por email")
                }
            }
        )
    }
}

internal fun makeMailtoUrl(subject: String, body: String): String {
    val encodedSubject = java.net.URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
    val encodedBody = java.net.URLEncoder.encode(body, "UTF-8").replace("+", "%20")
    return "mailto:bfollon.dev@icloud.com?subject=$encodedSubject&body=$encodedBody"
}

internal fun openEmail(context: Context, mailtoUrl: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, mailtoUrl.toUri()))
}
