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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.BusReminder
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.ReminderService
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
    overrideDayType: DayType? = null,
    reminderService: ReminderService? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val timetableService = remember { TimetableService(context) }

    var timetables by remember { mutableStateOf<List<com.github.bfollon.intersego.data.BusTimetable>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var reminderKeys by remember { mutableStateOf(reminderService?.activeMatchKeys() ?: emptySet()) }
    var dailyReminderKeys by remember { mutableStateOf(reminderService?.dailyMatchKeys() ?: emptySet()) }
    var reminderError by remember { mutableStateOf<String?>(null) }

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

    val currentDayOfWeek = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) }
    // When an override is set, use the matching DayType set; otherwise derive from today's calendar day.
    val currentDayTypes = remember(overrideDayType) {
        if (overrideDayType != null) dayTypesFor(overrideDayType) else dayTypesForCalendarDay(currentDayOfWeek)
    }
    // Effective day type used when scheduling reminders from this screen.
    val effectiveDayType = remember(overrideDayType, currentDayOfWeek) {
        overrideDayType ?: when (currentDayOfWeek) {
            Calendar.SATURDAY -> DayType.SATURDAY
            Calendar.SUNDAY -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }
    }
    // Weekday used for seasonal filtering — use today's for "now" context even when overriding
    val weekdayForSeasonal = remember { currentDayOfWeek }

    val todayDepartures = remember(timetables, currentDayTypes, stop, direction) {
        timetables.filter {
            it.dayType in currentDayTypes &&
            it.stopId == stop.id &&
            it.direction == direction
        }.flatMap { it.seasonalDepartures(weekday = weekdayForSeasonal) }.sortedBy { it.toMinutesSinceMidnight() }
    }

    val dayTypeLabel = remember(overrideDayType) {
        when (overrideDayType ?: run {
            when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> DayType.SATURDAY
                Calendar.SUNDAY -> DayType.SUNDAY
                else -> DayType.WEEKDAY
            }
        }) {
            DayType.SATURDAY, DayType.WEEKEND -> "Sábado"
            DayType.SUNDAY, DayType.HOLIDAY -> "Domingo"
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
                            val matchKey = BusReminder.matchKey(
                                route.id, stop.id, direction, departure.hour, departure.minute
                            )
                            DayScheduleTimelineRow(
                                departure = departure,
                                selectedVariantLabel = selectedVariantLabel,
                                isLast = index == todayDepartures.size - 1 && markerIndex <= todayDepartures.size - 1,
                                isBellSet = reminderKeys.contains(matchKey),
                                isDailyBell = dailyReminderKeys.contains(matchKey),
                                showBell = reminderService != null,
                                onBellTap = {
                                    reminderError = null
                                    if (reminderKeys.contains(matchKey)) {
                                        reminderService?.cancelReminder(route.id, stop.id, direction, departure.hour, departure.minute)
                                    } else {
                                        val result = reminderService?.scheduleReminder(departure, stop, route, direction, dayType = effectiveDayType)
                                        if (result is ReminderService.ScheduleResult.Failure) {
                                            reminderError = result.message
                                        }
                                    }
                                    reminderKeys = reminderService?.activeMatchKeys() ?: emptySet()
                                    dailyReminderKeys = reminderService?.dailyMatchKeys() ?: emptySet()
                                },
                                onBellLongPress = {
                                    reminderError = null
                                    val isDaily = dailyReminderKeys.contains(matchKey)
                                    val isOneOff = reminderKeys.contains(matchKey)
                                    when {
                                        isDaily -> reminderService?.cancelReminder(route.id, stop.id, direction, departure.hour, departure.minute)
                                        isOneOff -> {
                                            reminderService?.cancelReminder(route.id, stop.id, direction, departure.hour, departure.minute)
                                            val result = reminderService?.scheduleReminder(departure, stop, route, direction, isDaily = true, dayType = effectiveDayType)
                                            if (result is ReminderService.ScheduleResult.Failure) reminderError = result.message
                                        }
                                        else -> {
                                            val result = reminderService?.scheduleReminder(departure, stop, route, direction, isDaily = true, dayType = effectiveDayType)
                                            if (result is ReminderService.ScheduleResult.Failure) reminderError = result.message
                                        }
                                    }
                                    reminderKeys = reminderService?.activeMatchKeys() ?: emptySet()
                                    dailyReminderKeys = reminderService?.dailyMatchKeys() ?: emptySet()
                                },
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
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
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
    isBellSet: Boolean = false,
    isDailyBell: Boolean = false,
    showBell: Boolean = false,
    onBellTap: () -> Unit = {},
    onBellLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val timeOfDay = getTimeOfDay(departure.hour)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline visual (dot + line)
        DayScheduleTimelineIndicator(isLast = isLast)

        // Departure info
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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

                    // Seasonal label badge
                    departure.seasonalAvailability.displayLabel?.let { label ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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

            // Right-side controls grouped together
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeOfDayIndicator(timeOfDay = timeOfDay)

                if (showBell) {
                    BellIcon(
                        isBellSet = isBellSet,
                        isDailyBell = isDailyBell,
                        onTap = onBellTap,
                        onLongPress = onBellLongPress
                    )
                }
            }
        }
    }
}

/**
 * Visual indicator for a day schedule timeline entry: a connecting line and a dot.
 * Spans the full height of the parent Row. The line fills from top to bottom,
 * and the dot is centered (drawn after so it covers the line's midpoint).
 * [isLast] controls whether the connecting line is shown (hidden for the last item).
 */
@Composable
private fun DayScheduleTimelineIndicator(isLast: Boolean) {
    val dotColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val dotSize = 12.dp
    val density = LocalDensity.current

    var containerHeight by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .width(32.dp)
            .fillMaxHeight()
            .onSizeChanged { size ->
                containerHeight = size.height
            },
        contentAlignment = Alignment.Center
    ) {
        // Tall connecting line that overflows Row bounds to bridge gaps, drawn first so dot renders on top
        // Line starts from the bottom of the centered dot: containerHeight/2 + dotSize/2
        if (!isLast && containerHeight > 0) {
            // Convert container height from pixels to dp
            val containerHeightDp = with(density) { containerHeight.toDp() }
            val dotRadiusDp = dotSize / 2
            val lineOffsetDp = containerHeightDp / 2 + dotRadiusDp
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .offset(y = lineOffsetDp)
                    .background(lineColor)
            )
        }
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(dotColor, CircleShape)
        )
    }
}
