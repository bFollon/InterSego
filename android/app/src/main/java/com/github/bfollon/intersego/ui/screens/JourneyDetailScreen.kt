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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.data.JourneyStep
import com.github.bfollon.intersego.data.Leg

private fun formatMin(minutesOfDay: Int): String {
    val h = (minutesOfDay / 60) % 24
    val m = minutesOfDay % 60
    return "%02d:%02d".format(h, m)
}

/** Leg-by-leg breakdown of one journey. See docs/JOURNEY_PLANNER.md. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    journey: Journey,
    stopName: (String) -> String,
    onBack: () -> Unit,
    onLegSelected: (routeId: String, stopId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${formatMin(journey.departureMin)} — ${formatMin(journey.arrivalMin)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(journey.stepsWithWaits()) { step ->
                when (step) {
                    is JourneyStep.LegStep -> {
                        val onClick: (() -> Unit)? = (step.leg as? Leg.Ride)?.let { ride -> { onLegSelected(ride.routeId, ride.fromStop) } }
                        LegRow(leg = step.leg, stopName = stopName, onClick = onClick)
                    }
                    is JourneyStep.Wait -> WaitRow(minutes = step.minutes)
                }
            }
        }
    }
}

@Composable
private fun LegRow(leg: Leg, stopName: (String) -> String, onClick: (() -> Unit)?) {
    // Tapping a Ride leg reaches the existing NextDeparture screen for that route+stop, so the
    // planner feeds into what's already there rather than being a dead end (Walk legs aren't
    // clickable - there's no route/stop screen for a walking segment).
    val cardModifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    if (onClick != null) {
        ElevatedCard(onClick = onClick, modifier = cardModifier) { LegRowContent(leg, stopName) }
    } else {
        ElevatedCard(modifier = cardModifier) { LegRowContent(leg, stopName) }
    }
}

@Composable
private fun WaitRow(minutes: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Espera", fontWeight = FontWeight.Medium)
                Text("$minutes min", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LegRowContent(leg: Leg, stopName: (String) -> String) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        when (leg) {
            is Leg.Ride -> {
                Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${leg.routeId} · ${stopName(leg.fromStop)} → ${stopName(leg.toStop)}", fontWeight = FontWeight.Medium)
                    Text("${formatMin(leg.depMin)} — ${formatMin(leg.arrMin)}", style = MaterialTheme.typography.bodySmall)
                    if (leg.isEstimated) {
                        // Same copy as NextDepartureScreen's TimesDisclaimerCard - see
                        // JourneyResultsScreen for the full expandable version.
                        Text(
                            "Horarios orientativos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            is Leg.Walk -> {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Caminar · ${stopName(leg.fromStop)} → ${stopName(leg.toStop)}", fontWeight = FontWeight.Medium)
                    Text("${leg.minutes} min (${leg.meters} m)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
