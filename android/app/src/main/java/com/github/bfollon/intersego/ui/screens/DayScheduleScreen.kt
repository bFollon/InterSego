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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.github.bfollon.intersego.services.HolidayService
import com.github.bfollon.intersego.services.ReminderService
import com.github.bfollon.intersego.services.TimetableService
import com.github.bfollon.intersego.services.TimetableQueryUtils
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
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
    mergedDirectionLabel: String? = null,
    allowDateSelection: Boolean = false,
    initialDate: LocalDate = LocalDate.now(),
    reminderService: ReminderService? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val timetableService = remember { TimetableService(context) }

    // "Consultar otro día" flow only — the pre-existing overrideDayType path (from
    // NextDepartureScreen's "Ver horario completo") keeps its existing today-anchored
    // weekday/seasonal resolution below, unchanged.
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val isToday = !allowDateSelection || selectedDate == LocalDate.now()
    val calendarForSelectedDate = remember(selectedDate) {
        Calendar.getInstance().apply {
            set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth, 12, 0, 0)
        }
    }

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
    val currentDayTypes = remember(overrideDayType, allowDateSelection, selectedDate) {
        when {
            allowDateSelection -> TimetableQueryUtils.dayTypesForDate(calendarForSelectedDate)
            overrideDayType != null -> dayTypesFor(overrideDayType)
            else -> TimetableQueryUtils.dayTypesForDate()
        }
    }
    // Effective day type used when scheduling reminders from this screen.
    val effectiveDayType = remember(overrideDayType, currentDayOfWeek, allowDateSelection, selectedDate) {
        if (allowDateSelection) TimetableQueryUtils.primaryDayType(calendarForSelectedDate)
        else overrideDayType ?: TimetableQueryUtils.primaryDayType()
    }
    // Weekday/month used for seasonal filtering — the picked date when selectable, else today's
    // (the pre-existing overrideDayType path only carries a DayType, not an actual date).
    val weekdayForSeasonal = remember(allowDateSelection, selectedDate) {
        if (allowDateSelection) calendarForSelectedDate.get(Calendar.DAY_OF_WEEK) else currentDayOfWeek
    }
    val monthForSeasonal = remember(allowDateSelection, selectedDate) {
        if (allowDateSelection) Month.of(selectedDate.monthValue) else LocalDate.now().month
    }
    val holidayName = remember(allowDateSelection, selectedDate) {
        if (allowDateSelection) HolidayService.holidayName(calendarForSelectedDate) else null
    }

    // Each item is (departure, effectiveDirection) — direction may vary per-departure in merged mode.
    val todayItems = remember(timetables, currentDayTypes, stop, direction, mergedDirectionLabel, weekdayForSeasonal, monthForSeasonal) {
        timetables.filter {
            it.dayType in currentDayTypes &&
            it.stopId == stop.id &&
            (mergedDirectionLabel != null || it.direction == direction)
        }.flatMap { timetable ->
            timetable.seasonalDepartures(month = monthForSeasonal, weekday = weekdayForSeasonal)
                .map { dep -> Pair(dep, timetable.direction ?: direction) }
        }.sortedBy { it.first.toMinutesSinceMidnight() }
    }

    val dayTypeLabel = remember(overrideDayType, allowDateSelection, selectedDate) {
        when (effectiveDayType) {
            DayType.SATURDAY, DayType.WEEKEND -> "Sábado"
            DayType.SUNDAY, DayType.HOLIDAY -> "Domingo"
            else -> "Lunes a Viernes"
        }
    }

    // Find marker index: first departure that is in the future. "Ahora" only makes sense
    // when the displayed schedule is actually today's.
    val markerIndex = remember(todayItems, currentTime, isToday) {
        if (!isToday) return@remember -1
        val idx = todayItems.indexOfFirst { (dep, _) ->
            val depTime = LocalTime.of(dep.hour, dep.minute)
            depTime.isAfter(currentTime) || depTime == currentTime
        }
        if (idx == -1) todayItems.size else idx
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
            todayItems.isEmpty() -> {
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    if (allowDateSelection) {
                        DateSelectionHeader(
                            selectedDate = selectedDate,
                            holidayName = holidayName,
                            onPickDate = { showDatePicker = true }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isToday) "No hay horarios disponibles para hoy"
                                   else "No hay horarios disponibles para este día",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                val listState = rememberLazyListState()

                // Auto-scroll to center marker in viewport (mirrors iOS scrollTo anchor: .center).
                // Only relevant for today's schedule — non-today dates have no "Ahora" marker.
                LaunchedEffect(markerIndex) {
                    if (markerIndex < 0) return@LaunchedEffect
                    val scrollTarget = markerIndex + 1 // +1 for header
                    listState.scrollToItem(scrollTarget)
                    val layoutInfo = listState.layoutInfo
                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == scrollTarget }
                    if (itemInfo != null) {
                        val itemCenter = itemInfo.offset + itemInfo.size / 2
                        listState.scrollBy((itemCenter - viewportHeight / 2).toFloat())
                    }
                }

                // Build flat list of items: header, departures with marker interleaved
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (allowDateSelection) {
                        item {
                            DateSelectionHeader(
                                selectedDate = selectedDate,
                                holidayName = holidayName,
                                onPickDate = { showDatePicker = true }
                            )
                        }
                    }

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
                    todayItems.forEachIndexed { index, (departure, depDir) ->
                        // Insert marker before the first future departure
                        if (index == markerIndex) {
                            item(key = "now_marker") {
                                NowMarkerRow(timeString = timeString, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }

                        item(key = "dep_$index") {
                            val matchKey = BusReminder.matchKey(
                                route.id, stop.id, depDir, departure.hour, departure.minute
                            )
                            DayScheduleTimelineRow(
                                departure = departure,
                                stop = stop,
                                selectedVariantLabel = selectedVariantLabel,
                                isLast = index == todayItems.size - 1 && markerIndex <= todayItems.size - 1,
                                isBellSet = reminderKeys.contains(matchKey),
                                isDailyBell = dailyReminderKeys.contains(matchKey),
                                showBell = reminderService != null,
                                onBellTap = {
                                    reminderError = null
                                    if (reminderKeys.contains(matchKey)) {
                                        reminderService?.cancelReminder(route.id, stop.id, depDir, departure.hour, departure.minute)
                                    } else {
                                        val result = reminderService?.scheduleReminder(departure, stop, route, depDir, dayType = effectiveDayType)
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
                                        isDaily -> reminderService?.cancelReminder(route.id, stop.id, depDir, departure.hour, departure.minute)
                                        isOneOff -> {
                                            reminderService?.cancelReminder(route.id, stop.id, depDir, departure.hour, departure.minute)
                                            val result = reminderService?.scheduleReminder(departure, stop, route, depDir, isDaily = true, dayType = effectiveDayType)
                                            if (result is ReminderService.ScheduleResult.Failure) reminderError = result.message
                                        }
                                        else -> {
                                            val result = reminderService?.scheduleReminder(departure, stop, route, depDir, isDaily = true, dayType = effectiveDayType)
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
                    if (markerIndex == todayItems.size) {
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

    if (showDatePicker) {
        val todayEpochMillis = LocalDate.now().toEpochDay() * MILLIS_PER_DAY
        val maxEpochMillis = LocalDate.now().plusDays(90).toEpochDay() * MILLIS_PER_DAY
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochDay() * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis in todayEpochMillis..maxEpochMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = LocalDate.ofEpochDay(millis / MILLIS_PER_DAY)
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Compact date affordance + festivo banner for the "Consultar otro día" flow.
 */
@Composable
private fun DateSelectionHeader(
    selectedDate: LocalDate,
    holidayName: String?,
    onPickDate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Surface(
            onClick = onPickDate,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = formatSelectedDate(selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                if (selectedDate != LocalDate.now()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Elegir otra fecha",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (holidayName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            FestivoBanner(holidayName)
        }
    }
}

/**
 * Purple banner explaining why a festivo is showing Sunday-shaped departures — shared between
 * DaySchedule's "Consultar otro día" date header and NextDeparture's "today" case.
 */
@Composable
internal fun FestivoBanner(holidayName: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Festivo: $holidayName · horario de domingo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

private fun formatSelectedDate(date: LocalDate): String {
    if (date == LocalDate.now()) return "Hoy"
    val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale("es", "ES"))
    return date.format(formatter).replaceFirstChar { it.uppercase() }
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
    stop: BusStop,
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

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
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

                        departure.alternateLocationName(stop)?.let { altName ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = altName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
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
