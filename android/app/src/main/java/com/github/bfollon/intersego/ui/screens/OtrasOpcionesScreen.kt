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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.github.bfollon.intersego.services.MainLandingAction
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Menu screen reached from Landing's "Más opciones" card — shows whichever pool actions
 * aren't currently pinned to one of Landing's 4 configurable slots, in the same square-card
 * style as Landing's own grid.
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
    onPlanJourney: () -> Unit,
    onNavigateToRouteList: () -> Unit,
    onShowFavorites: () -> Unit,
    onBoardBus: () -> Unit,
    pinnedActions: Set<MainLandingAction>,
    isBoardingBus: Boolean = false,
    boardingBusConfirmed: Boolean = false,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    // Whichever pool actions aren't pinned to one of Landing's 4 slots land here, so every
    // action stays reachable regardless of how the user configured their Landing screen.
    val visibleActions = MainLandingAction.entries.filter { it !in pinnedActions }
    val callbacks = LandingActionCallbacks(
        onNavigateToRouteList = onNavigateToRouteList,
        onPlanJourney = onPlanJourney,
        onShowReminders = onShowReminders,
        onOpenAnotherDay = { showDatePicker = true },
        onShowFavorites = onShowFavorites,
        onBoardBus = onBoardBus,
    )

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
            visibleActions.chunked(2).forEach { rowActions ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowActions.forEach { action ->
                        LandingActionSquare(
                            action = action,
                            modifier = Modifier.weight(1f),
                            callbacks = callbacks,
                            isBoardingBus = isBoardingBus,
                            boardingBusConfirmed = boardingBusConfirmed,
                        )
                    }
                    if (rowActions.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        AnotherDayDatePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                showDatePicker = false
                AnalyticsService.track("check_another_day")
                onCheckAnotherDay(date)
            },
        )
    }
}

/**
 * The "pick a date" step of the "Consultar otro día" flow — shared between [OtrasOpcionesScreen]'s
 * own card and Landing's pinned pill (see `LandingSlot.EXTRA_*`), which must open this directly
 * rather than routing through the "Más opciones" hub first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnotherDayDatePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
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
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onConfirm(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY))
                }
            }) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}