/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.services.DeviceTokenService
import com.github.bfollon.intersego.services.GuidedModePrefs
import com.github.bfollon.intersego.services.MonitoringPreferencesService
import com.github.bfollon.intersego.services.NotificationPreferencesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class SeverityOption(val value: String, val label: String)
private val severityOptions = listOf(
    SeverityOption("info",     "Todas"),
    SeverityOption("warning",  "Solo importantes"),
    SeverityOption("critical", "Solo críticas"),
    SeverityOption("none",     "Desactivadas"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var guidedModeEnabled by remember {
        mutableStateOf(GuidedModePrefs.isGuidedModeEnabled())
    }
    var errorsEnabled by remember {
        mutableStateOf(MonitoringPreferencesService.hasUserOptedIn())
    }
    var analyticsEnabled by remember {
        mutableStateOf(MonitoringPreferencesService.hasUserOptedInToAnalytics())
    }
    var alertMinSeverity by remember {
        mutableStateOf(NotificationPreferencesService.getAlertMinSeverity(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Section: Notificaciones
            Text(
                text = "Notificaciones",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    Text(
                        text = "Alertas de servicio",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
                    )
                    severityOptions.forEachIndexed { index, option ->
                        val onSelect = {
                            alertMinSeverity = option.value
                            NotificationPreferencesService.saveChoice(context, option.value)
                            val prefs = context.getSharedPreferences("fcm_prefs", android.content.Context.MODE_PRIVATE)
                            val token = prefs.getString("fcm_token", null)
                            if (token != null) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    DeviceTokenService.register(token, context, option.value)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onSelect)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = alertMinSeverity == option.value,
                                onClick = onSelect
                            )
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (index < severityOptions.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Modo guiado
            Text(
                text = "Modo guiado",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                SettingsToggleRow(
                    title = "Mostrar selector de dirección",
                    subtitle = "Muestra una pantalla para seleccionar la dirección del autobús antes de ver las salidas",
                    checked = guidedModeEnabled,
                    onCheckedChange = { newValue ->
                        guidedModeEnabled = newValue
                        GuidedModePrefs.setGuidedModeEnabled(newValue)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Privacidad
            Text(
                text = "Privacidad",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsToggleRow(
                        title = "Monitoreo de Errores",
                        subtitle = "Datos técnicos anónimos para detectar fallos.",
                        checked = errorsEnabled,
                        onCheckedChange = { newValue ->
                            errorsEnabled = newValue
                            MonitoringPreferencesService.setMonitoringEnabled(newValue)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(
                        title = "Analíticas de Uso",
                        subtitle = "Eventos de uso anónimos para mejorar la app.",
                        checked = analyticsEnabled,
                        onCheckedChange = { newValue ->
                            analyticsEnabled = newValue
                            MonitoringPreferencesService.setAnalyticsEnabled(newValue)
                        }
                    )
                    HorizontalDivider()
                    // Restart callout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Los cambios se aplican al reiniciar la app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
