/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.github.bfollon.intersego.data.BusReminder
import com.github.bfollon.intersego.services.AnalyticsService
import com.github.bfollon.intersego.services.ReminderService
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    reminderService: ReminderService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("reminder_tutorial", android.content.Context.MODE_PRIVATE) }

    var reminders by remember { mutableStateOf(reminderService.getReminders()) }
    var leadMinutes by remember { mutableStateOf(reminderService.getDefaultLeadMinutes()) }
    var dailyLeadMinutes by remember { mutableStateOf(reminderService.getDailyLeadMinutes()) }
    // Mirrors of the last-saved values — used to detect unsaved changes
    var savedLeadMinutes by remember { mutableStateOf(leadMinutes) }
    var savedDailyLeadMinutes by remember { mutableStateOf(dailyLeadMinutes) }
    val hasUnsavedChanges = leadMinutes != savedLeadMinutes || dailyLeadMinutes != savedDailyLeadMinutes
    var showTutorial by remember {
        val shown = prefs.getBoolean("tutorial_shown", false)
        mutableStateOf(!shown)
    }

    if (showTutorial) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                prefs.edit { putBoolean("tutorial_shown", true) }
                showTutorial = false
            },
            sheetState = sheetState
        ) {
            ReminderTutorialSheet(onDismiss = {
                prefs.edit { putBoolean("tutorial_shown", true) }
                showTutorial = false
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        AnalyticsService.track("tutorial_reopened", mapOf("tutorial" to "reminders"))
                        showTutorial = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Ayuda",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // One-off lead time card
            LeadTimeCard(
                title = "Aviso puntual",
                subtitle = "Antelación para recordatorios puntuales (campana rápida).",
                minutes = leadMinutes,
                onDecrease = { leadMinutes = maxOf(1, leadMinutes - 5) },
                onIncrease = { leadMinutes = minOf(60, leadMinutes + 5) }
            )

            // Daily lead time card
            LeadTimeCard(
                title = "Aviso diario",
                subtitle = "Antelación para recordatorios diarios (campana mantenida).",
                minutes = dailyLeadMinutes,
                isDaily = true,
                onDecrease = { dailyLeadMinutes = maxOf(1, dailyLeadMinutes - 5) },
                onIncrease = { dailyLeadMinutes = minOf(60, dailyLeadMinutes + 5) }
            )

            // Save button — always visible, disabled when nothing has changed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    enabled = hasUnsavedChanges,
                    onClick = {
                        if (leadMinutes != savedLeadMinutes) {
                            reminderService.setDefaultLeadMinutes(leadMinutes)
                            reminderService.rescheduleOneOff(leadMinutes)
                        }
                        if (dailyLeadMinutes != savedDailyLeadMinutes) {
                            reminderService.setDailyLeadMinutes(dailyLeadMinutes)
                            reminderService.rescheduleDaily(dailyLeadMinutes)
                        }
                        savedLeadMinutes = leadMinutes
                        savedDailyLeadMinutes = dailyLeadMinutes
                        reminders = reminderService.getReminders()
                    }
                ) {
                    Text("Guardar")
                }
            }

            if (reminders.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Sin recordatorios activos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pulsa la campana para un aviso puntual o mantenla para un recordatorio diario.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onCancel = {
                                AnalyticsService.track("reminder_cancelled", mapOf("type" to if (reminder.isDaily) "daily" else "one_off"))
                                reminderService.cancelReminder(reminder.id)
                                reminders = reminderService.getReminders()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: BusReminder,
    onCancel: () -> Unit
) {
    val fireTimeFormatted = remember(reminder.fireDateMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reminder.fireDateMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Línea ${reminder.routeNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = reminder.stopName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Sale a las ${reminder.departureDisplayString}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                reminder.journeyLabel?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Viaje: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (reminder.isDaily) "Cada día a las $fireTimeFormatted"
                               else "Aviso a las $fireTimeFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (reminder.isDaily) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Diario",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                reminder.seasonalNote?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = "Cancelar recordatorio",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun LeadTimeCard(
    title: String,
    subtitle: String,
    minutes: Int,
    isDaily: Boolean = false,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (isDaily) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$minutes min antes de la salida",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    IconButton(onClick = onDecrease, enabled = minutes > 1) {
                        Icon(Icons.Default.Remove, contentDescription = "Reducir")
                    }
                    IconButton(onClick = onIncrease, enabled = minutes < 60) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar")
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
