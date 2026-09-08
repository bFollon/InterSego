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
import androidx.compose.material.icons.filled.Star
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

private data class MenuCard(val label: String, val onClick: () -> Unit, val icon: @Composable () -> Unit)

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
    onPlanJourney: () -> Unit,
    onNavigateToRouteList: () -> Unit,
    onShowFavorites: () -> Unit,
    mainAction: MainLandingAction = MainLandingAction.ROUTES,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    // The action bound to Landing's main card is dropped here so every action stays reachable
    // regardless of which one the user picked as their main card.
    val visibleActions = MainLandingAction.entries.filter { it != mainAction }
    fun onClickFor(action: MainLandingAction): () -> Unit = when (action) {
        MainLandingAction.ROUTES -> onNavigateToRouteList
        MainLandingAction.ROUTE_PLANNER -> onPlanJourney
        MainLandingAction.REMINDERS -> onShowReminders
        MainLandingAction.ANOTHER_DAY -> { { showDatePicker = true } }
    }

    // "Favoritos" isn't one of the 4 pinnable MainLandingAction cards — it's an always-present
    // extra entry, appended after the rotating actions.
    val menuCards: List<MenuCard> = visibleActions.map { action ->
        MenuCard(label = action.label, onClick = onClickFor(action)) { MainLandingActionIcon(action) }
    } + MenuCard(label = "Favoritos", onClick = onShowFavorites) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
    }
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
            menuCards.chunked(2).forEach { rowCards ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowCards.forEach { card ->
                        SquareLandingCard(
                            label = card.label,
                            onClick = card.onClick,
                            modifier = Modifier.weight(1f),
                            icon = card.icon
                        )
                    }
                    if (rowCards.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
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
