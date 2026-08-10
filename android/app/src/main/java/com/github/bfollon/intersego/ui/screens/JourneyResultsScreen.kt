/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.data.Leg

private fun formatMin(minutesOfDay: Int): String {
    val h = (minutesOfDay / 60) % 24
    val m = minutesOfDay % 60
    return "%02d:%02d".format(h, m)
}

private fun formatDuration(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

/** Top 3 ranked journeys for the current query. Below-buffer transfers are already filtered out
 * by the planner - what's shown here is always usable. See docs/JOURNEY_PLANNER.md. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyResultsScreen(
    originName: String,
    destinationName: String,
    journeys: List<Journey>,
    isLoading: Boolean,
    onJourneySelected: (Journey) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$originName → $destinationName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                journeys.isEmpty() -> Text(
                    "No hay viajes disponibles con margen suficiente para esta búsqueda. Prueba con otra hora.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(journeys) { journey ->
                        JourneyCard(journey = journey, onClick = { onJourneySelected(journey) })
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyCard(journey: Journey, onClick: () -> Unit) {
    val hasEstimatedLeg = journey.legs.any { it is Leg.Ride && it.isEstimated }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${formatMin(journey.departureMin)} — ${formatMin(journey.arrivalMin)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(formatDuration(journey.arrivalMin - journey.departureMin), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (journey.transferCount == 0) "Directo" else "${journey.transferCount} transbordo(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                journey.legs.forEach { leg ->
                    when (leg) {
                        is Leg.Ride -> LegChip(text = leg.routeId, icon = Icons.Filled.DirectionsBus)
                        is Leg.Walk -> LegChip(text = "${leg.minutes} min", icon = Icons.AutoMirrored.Filled.DirectionsWalk)
                    }
                }
            }
            if (hasEstimatedLeg) {
                // Reuses the same "Horarios orientativos" disclaimer language as NextDepartureScreen
                // (TimesDisclaimerCard) rather than inventing new copy — a journey built partly on
                // cluster-estimated times shouldn't look more precise than the app is elsewhere.
                TimesDisclaimerCard()
            }
        }
    }
}

/**
 * Looks like an [AssistChip] but isn't clickable, so a tap anywhere on the card — including on
 * one of these — reaches the card's own `onClick` instead of being swallowed by the chip's own
 * ripple and doing nothing (matches iOS, where the whole row is one tap target).
 */
@Composable
private fun LegChip(text: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
