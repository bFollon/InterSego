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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.data.JourneyStep
import com.github.bfollon.intersego.data.Leg
import com.github.bfollon.intersego.services.AnalyticsService
import com.github.bfollon.intersego.services.JourneyReminderHelper
import com.github.bfollon.intersego.services.ReminderService
import com.github.bfollon.intersego.services.RouteDataService
import com.github.bfollon.intersego.services.TripPlannerPrefs
import java.time.LocalDate

private fun formatMin(minutesOfDay: Int): String {
    val h = (minutesOfDay / 60) % 24
    val m = minutesOfDay % 60
    return "%02d:%02d".format(h, m)
}

private fun formatDuration(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

/**
 * Leg-by-leg breakdown of one journey, rendered as a single grouped card with dividers between
 * steps (matching iOS's single-List-with-dividers detail view), rather than a separate card per
 * step. See docs/JOURNEY_PLANNER.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    journey: Journey,
    date: LocalDate,
    originName: String,
    destinationName: String,
    stopName: (String) -> String,
    stopLookup: (String) -> BusStop?,
    routesById: Map<String, BusRoute>,
    stopsById: Map<String, BusStop>,
    routeDataService: RouteDataService,
    reminderService: ReminderService?,
    onBack: () -> Unit,
    onLegSelected: (routeId: String, stopId: String) -> Unit,
) {
    val reminderContext = remember(journey, date, routesById, stopsById) {
        JourneyReminderHelper.build(
            journey = journey, date = date,
            originName = originName, destinationName = destinationName,
            routesById = routesById, stopsById = stopsById,
            routeDataService = routeDataService,
        )
    }
    val matchKey = reminderContext?.let { JourneyReminderHelper.matchKey(it) }
    var reminderKeys by remember { mutableStateOf(reminderService?.activeMatchKeys() ?: emptySet()) }
    val isReminderSet = matchKey != null && reminderKeys.contains(matchKey)
    // At most one: the planner never returns more than one mid-journey transfer (see
    // docs/JOURNEY_PLANNER.md).
    val tightMargin = remember(journey) {
        journey.transferMargins().firstOrNull { it.marginMin < TripPlannerPrefs.RECOMMENDED_MIN_BUFFER }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val transferLabel = if (journey.transferCount == 0) "Directo" else "${journey.transferCount} transbordo(s)"
                    Text("${formatDuration(journey.arrivalMin - journey.departureMin)} · $transferLabel")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (reminderContext != null && reminderService != null) {
                        IconButton(onClick = {
                            if (isReminderSet) {
                                reminderService.cancelReminder(
                                    reminderContext.route.id, reminderContext.stop.id, reminderContext.direction,
                                    reminderContext.departure.hour, reminderContext.departure.minute
                                )
                            } else {
                                val result = reminderService.scheduleReminder(
                                    reminderContext.departure, reminderContext.stop, reminderContext.route, reminderContext.direction,
                                    dayType = reminderContext.dayType, journeyLabel = reminderContext.journeyLabel
                                )
                                if (result is ReminderService.ScheduleResult.Success) {
                                    AnalyticsService.track("reminder_set", mapOf("type" to "journey"))
                                }
                            }
                            reminderKeys = reminderService.activeMatchKeys()
                        }) {
                            Icon(
                                imageVector = if (isReminderSet) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = if (isReminderSet) "Cancelar recordatorio" else "Recuérdame salir",
                                tint = if (isReminderSet) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
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
                SectionHeader("Itinerario")
            }
            item {
                // One grouped card for the whole itinerary summary (departure/arrival rows, map)
                // rather than separate floating cards - matches iOS, where these all live in one
                // List Section and read as a single unified block.
                GroupedCard(modifier = Modifier.padding(bottom = 16.dp)) {
                    TripSummaryRow(
                        icon = Icons.Filled.FiberManualRecord,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = "Salida",
                        stopName = stopName(journey.legs.first().fromStop),
                        time = formatMin(journey.departureMin)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    TripSummaryRow(
                        icon = Icons.Filled.Place,
                        iconTint = MaterialTheme.colorScheme.error,
                        label = "Llegada",
                        stopName = stopName(journey.legs.last().toStop),
                        time = formatMin(journey.arrivalMin)
                    )
                    // Full-bleed, no horizontal inset - matches iOS, where the map stretches to
                    // the card's full width rather than sitting inset like the rows above it. The
                    // map is always this card's last element, flush against its bottom edge (no
                    // padding), so its bottom corners use GroupedCard's own radius (20dp) instead
                    // of the map's usual 12dp, matching the outer card's curve exactly rather than
                    // leaving a gap or clipping unevenly against it.
                    ItineraryMapView(
                        journey = journey,
                        stopLookup = stopLookup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(220.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    )
                }
            }
            item {
                SectionHeader("Pasos")
            }
            item {
                // PeekingBannerCard draws TightTransferPeekBanner full width behind the card, with
                // the card pushed down (top padding) so only a strip of the banner peeks out above
                // the card's rounded top corners, like a tab tucked behind it. Same pattern as
                // JourneyResultsScreen's JourneyCard - and same banner, styled the same as the
                // results-list warning rather than the light tint this screen used to show under
                // the map. GroupedCardCornerRadius (20dp) is shared with GroupedCard's own shape
                // below so the banner's curve/hidden-height can't drift out of sync with the card.
                PeekingBannerCard(
                    bannerTitle = tightMargin?.let { "Transbordo ajustado en ${stopName(it.stopId)}" },
                    bannerSubtitle = tightMargin?.let { "${it.marginMin} min de margen" },
                    cornerRadius = GroupedCardCornerRadius,
                ) { topPadding ->
                    GroupedCard(
                        modifier = Modifier.padding(top = topPadding)
                    ) {
                        val steps = journey.stepsWithWaits()
                        steps.forEachIndexed { index, step ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            when (step) {
                                is JourneyStep.LegStep -> {
                                    val onClick: (() -> Unit)? = (step.leg as? Leg.Ride)?.let { ride -> {
                                        AnalyticsService.track("journey_leg_tapped", mapOf("route" to ride.routeId))
                                        onLegSelected(ride.routeId, ride.fromStop)
                                    } }
                                    LegRow(leg = step.leg, stopName = stopName, onClick = onClick)
                                }
                                is JourneyStep.Wait -> WaitRow(minutes = step.minutes)
                            }
                        }
                    }
                }
            }
            if (journey.legs.any { it is Leg.Ride && it.isEstimated }) {
                item {
                    // Shown once for the whole journey, not per-leg — riding on just the one leg
                    // that happens to be estimated read like a note about that specific bus.
                    Text(
                        "Horarios orientativos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun TripSummaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color, label: String, stopName: String, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stopName, fontWeight = FontWeight.Medium)
        }
        Text(time, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LegRow(leg: Leg, stopName: (String) -> String, onClick: (() -> Unit)?) {
    // Tapping a Ride leg reaches the existing NextDeparture screen for that route+stop, so the
    // planner feeds into what's already there rather than being a dead end (Walk legs aren't
    // clickable - there's no route/stop screen for a walking segment).
    val rowModifier = Modifier.fillMaxWidth().let { base ->
        if (onClick != null) base.clickable(onClick = onClick) else base
    }
    Row(modifier = rowModifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        when (leg) {
            is Leg.Ride -> {
                Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${leg.routeId} · ${stopName(leg.fromStop)} → ${stopName(leg.toStop)}", fontWeight = FontWeight.Medium)
                    Text("${formatMin(leg.depMin)} — ${formatMin(leg.arrMin)}", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun WaitRow(minutes: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.Gray)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("Espera", fontWeight = FontWeight.Medium)
            Text("$minutes min", style = MaterialTheme.typography.bodySmall)
        }
    }
}
