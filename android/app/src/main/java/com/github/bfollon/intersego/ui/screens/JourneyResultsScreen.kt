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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.data.Leg
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun formatMin(minutesOfDay: Int): String {
    val h = (minutesOfDay / 60) % 24
    val m = minutesOfDay % 60
    return "%02d:%02d".format(h, m)
}

private fun formatDuration(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

private fun formatSearchDate(date: LocalDate): String =
    if (date == LocalDate.now()) "Hoy" else date.format(DateTimeFormatter.ofPattern("d MMM"))

/** Top 3 ranked journeys for the current query. Below-buffer transfers are already filtered out
 * by the planner - what's shown here is always usable. See docs/JOURNEY_PLANNER.md. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyResultsScreen(
    originName: String,
    destinationName: String,
    date: LocalDate,
    departAfterMin: Int,
    arriveBeforeMin: Int?,
    journeys: List<Journey>,
    isLoading: Boolean,
    onJourneySelected: (Journey) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                // Static - "originName → destinationName" routinely overflowed the app bar.
                title = { Text("Resultados") },
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
        ) {
            item {
                SectionHeader("Resumen")
            }
            item {
                SearchSummaryCard(
                    originName = originName,
                    destinationName = destinationName,
                    date = date,
                    departAfterMin = departAfterMin,
                    arriveBeforeMin = arriveBeforeMin,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            item {
                SectionHeader("Opciones")
            }
            when {
                isLoading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                journeys.isEmpty() -> item {
                    Text(
                        "No hay viajes disponibles con margen suficiente para esta búsqueda. Prueba con otra hora.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                }
                else -> items(journeys) { journey ->
                    JourneyCard(journey = journey, onClick = { onJourneySelected(journey) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchSummaryCard(
    originName: String,
    destinationName: String,
    date: LocalDate,
    departAfterMin: Int,
    arriveBeforeMin: Int?,
    modifier: Modifier = Modifier,
) {
    val searchLabel = buildString {
        append(formatSearchDate(date))
        append(" · Desde las ")
        append(formatMin(departAfterMin))
        arriveBeforeMin?.let {
            append(" · Antes de las ")
            append(formatMin(it))
        }
    }
    GroupedCard(modifier = modifier) {
        SummaryRow(icon = Icons.Filled.FiberManualRecord, iconTint = MaterialTheme.colorScheme.primary, label = "Origen", value = originName)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SummaryRow(icon = Icons.Filled.Place, iconTint = MaterialTheme.colorScheme.error, label = "Destino", value = destinationName)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SummaryRow(icon = Icons.Filled.Schedule, iconTint = MaterialTheme.colorScheme.onSurfaceVariant, label = "Búsqueda", value = searchLabel)
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, iconTint: Color, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Medium)
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
                // Plain caption, matching iOS and the JourneyDetailScreen banner — a journey
                // built partly on cluster-estimated times shouldn't look more precise than the
                // app is elsewhere, but this is a secondary note, not an alert worth a card.
                Text(
                    "Horarios orientativos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
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
