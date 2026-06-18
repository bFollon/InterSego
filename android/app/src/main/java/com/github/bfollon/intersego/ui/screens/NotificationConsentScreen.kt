/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.bfollon.intersego.services.DeviceTokenService
import com.github.bfollon.intersego.services.NotificationPreferencesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class SeverityOption(val value: String, val title: String, val subtitle: String)

private val severityOptions = listOf(
    SeverityOption("info",     "Todas",              "Informativas, advertencias y críticas"),
    SeverityOption("warning",  "Solo importantes",   "Advertencias y alertas críticas"),
    SeverityOption("critical", "Solo críticas",      "Únicamente interrupciones graves del servicio"),
    SeverityOption("none",     "Desactivar alertas", "No recibir notificaciones de alertas"),
)

@Composable
fun NotificationConsentScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedSeverity by remember { mutableStateOf("info") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val effectiveSeverity = if (granted) selectedSeverity else "none"
        NotificationPreferencesService.saveChoice(context, effectiveSeverity)
        val prefs = context.getSharedPreferences("fcm_prefs", android.content.Context.MODE_PRIVATE)
        val token = prefs.getString("fcm_token", null)
        if (token != null) {
            CoroutineScope(Dispatchers.IO).launch {
                DeviceTokenService.register(token, context, effectiveSeverity)
            }
        }
        onDismiss()
    }

    fun activate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // Already granted (Android < 13 or permission already accepted)
        NotificationPreferencesService.saveChoice(context, selectedSeverity)
        val prefs = context.getSharedPreferences("fcm_prefs", android.content.Context.MODE_PRIVATE)
        val token = prefs.getString("fcm_token", null)
        if (token != null) {
            CoroutineScope(Dispatchers.IO).launch {
                DeviceTokenService.register(token, context, selectedSeverity)
            }
        }
        onDismiss()
    }

    fun dismiss() {
        NotificationPreferencesService.saveChoice(context, "none")
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(40.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Notificaciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "InterSego puede avisarte sobre recordatorios de autobús y alertas de servicio como huelgas o desvíos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                Text(
                    text = "¿Qué alertas de servicio quieres recibir?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column {
                    severityOptions.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSeverity = option.value }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedSeverity == option.value,
                                onClick = { selectedSeverity = option.value }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index < severityOptions.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
                        }
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = { activate() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Activar notificaciones", fontWeight = FontWeight.Medium)
                }

                TextButton(
                    onClick = { dismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ahora no",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Puedes cambiarlo en Ajustes cuando quieras",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
