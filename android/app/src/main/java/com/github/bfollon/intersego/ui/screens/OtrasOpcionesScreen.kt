/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.services.AnalyticsService
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Menu screen reached from Landing's "Más opciones" card — groups secondary features
 * (reminders, trip planning, and future entries like "Cómo llegar") behind one grid, the
 * same style as Landing's own square-card grid.
 *
 * "Consultar otro día" asks for the target date here, before route/stop selection: a route's
 * stops and views can differ completely by day type (e.g. M1's circularA/B, M6's 7 variants),
 * so the date must be known before we can show a correct stop list for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtrasOpcionesScreen(
    onBack: () -> Unit,
    onShowReminders: () -> Unit,
    onCheckAnotherDay: (LocalDate) -> Unit,
    onPlanJourney: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Más opciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SquareLandingCard(
                    label = "Mis recordatorios",
                    onClick = onShowReminders,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp).padding(8.dp)
                    )
                }

                SquareLandingCard(
                    label = "Consultar otro día",
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp).padding(8.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SquareLandingCard(
                    label = "Planificar viaje",
                    onClick = onPlanJourney,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp).padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    if (showDatePicker) {
        val todayEpochMillis = LocalDate.now().toEpochDay() * MILLIS_PER_DAY
        val maxEpochMillis = LocalDate.now().plusDays(90).toEpochDay() * MILLIS_PER_DAY
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = todayEpochMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis in todayEpochMillis..maxEpochMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        showDatePicker = false
                        AnalyticsService.track("check_another_day")
                        onCheckAnotherDay(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY))
                    }
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
