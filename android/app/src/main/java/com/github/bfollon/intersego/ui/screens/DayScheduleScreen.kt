/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follón
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.TimetableService
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Calendar

/**
 * Screen displaying all departures for the current day with an "Ahora" marker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScheduleScreen(
    route: BusRoute,
    stop: BusStop,
    direction: String,
    selectedVariantLabel: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val timetableService = remember { TimetableService(context) }

    var timetables by remember { mutableStateOf<List<com.github.bfollon.intersego.data.BusTimetable>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }

    // Update time every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(60000)
        }
    }

    // Load timetables
    LaunchedEffect(route.id) {
        isLoading = true
        errorMessage = null
        try {
            val loaded = timetableService.loadTimetables(route.id, forceRefresh = false)
            timetables = loaded
        } catch (e: Exception) {
            errorMessage = "Error al cargar horarios: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val currentDayTypes = remember {
        dayTypesForCalendarDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    }

    val todayDepartures = remember(timetables, currentDayTypes, stop, direction) {
        timetables.filter {
            it.dayType in currentDayTypes &&
            it.stopId == stop.name &&
            it.direction == direction
        }.flatMap { it.seasonalDepartures() }.sortedBy { it.toMinutesSinceMidnight() }
    }

    val dayTypeLabel = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "Sábado"
            Calendar.SUNDAY -> "Domingo"
            else -> "Lunes a Viernes"
        }
    }

    // Find marker index: first departure that is in the future
    val markerIndex = remember(todayDepartures, currentTime) {
        val idx = todayDepartures.indexOfFirst { dep ->
            val depTime = LocalTime.of(dep.hour, dep.minute)
            depTime.isAfter(currentTime) || depTime == currentTime
        }
        if (idx == -1) todayDepartures.size else idx
    }

    val timeString = remember(currentTime) {
        String.format("%d:%02d", currentTime.hour, currentTime.minute)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Horario del día") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
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
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cargando horarios...")
                    }
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            todayDepartures.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay horarios disponibles para hoy",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()

                // Auto-scroll to marker
                LaunchedEffect(markerIndex) {
                    // Item index in LazyColumn: 1 (header) + items before marker + marker itself
                    // Each departure before marker contributes 1 item, then marker is at markerIndex + 1
                    val scrollTarget = markerIndex + 1 // +1 for header
                    listState.scrollToItem(scrollTarget)
                }

                // Build flat list of items: header, departures with marker interleaved
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Section header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stop.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = dayTypeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Departures with interleaved marker
                    todayDepartures.forEachIndexed { index, departure ->
                        // Insert marker before the first future departure
                        if (index == markerIndex) {
                            item(key = "now_marker") {
                                NowMarkerRow(timeString = timeString)
                            }
                        }

                        item(key = "dep_$index") {
                            DayScheduleTimelineRow(
                                departure = departure,
                                selectedVariantLabel = selectedVariantLabel,
                                isLast = index == todayDepartures.size - 1 && markerIndex <= todayDepartures.size - 1,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Marker at end if all departures are past
                    if (markerIndex == todayDepartures.size) {
                        item(key = "now_marker") {
                            NowMarkerRow(
                                timeString = timeString,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

/**
 * "Ahora" marker row with accent-colored pill.
 */
@Composable
private fun NowMarkerRow(
    timeString: String,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline connector line
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxHeight().width(2.dp)) {
                drawLine(
                    color = accentColor,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Pill
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accentColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ahora · $timeString",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
        }
    }
}

/**
 * Individual departure row in the day schedule timeline.
 */
@Composable
private fun DayScheduleTimelineRow(
    departure: DepartureTime,
    selectedVariantLabel: String?,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val timeOfDay = getTimeOfDay(departure.hour)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline visual (dot + line)
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isLast) {
                Canvas(
                    modifier = Modifier
                        .width(2.dp)
                        .height(56.dp)
                        .offset(y = 20.dp)
                ) {
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.LightGray,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            Surface(
                modifier = Modifier.size(12.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.primary
            ) {}
        }

        // Departure info
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = departure.toDisplayString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val showLabel = !departure.variantLabel.isNullOrBlank() && when {
                        selectedVariantLabel == null -> departure.variantLabel != "Regular"
                        else -> departure.variantLabel != selectedVariantLabel
                    }
                    if (showLabel) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = departure.variantLabel!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!departure.notes.isNullOrBlank()) {
                    Text(
                        text = departure.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            TimeOfDayIndicator(timeOfDay = timeOfDay)
        }
    }
}
