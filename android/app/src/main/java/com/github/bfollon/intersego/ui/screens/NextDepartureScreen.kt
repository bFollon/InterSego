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

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HolidayVillage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.bfollon.intersego.data.BoardingEvent
import com.github.bfollon.intersego.data.BoardingRequest
import com.github.bfollon.intersego.data.BusReminder
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.services.AnalyticsService
import com.github.bfollon.intersego.services.BoardingService
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.DeparturesService
import com.github.bfollon.intersego.services.PDFProcessingService
import com.github.bfollon.intersego.services.ReminderService
import com.github.bfollon.intersego.services.RouteLoadedData
import com.github.bfollon.intersego.services.StaticMapService
import com.github.bfollon.intersego.services.TaggedDeparture
import com.github.bfollon.intersego.services.TimetableService
import com.github.bfollon.intersego.ui.theme.WarningOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

// ============================================================================
// DAY TYPE HELPERS
// ============================================================================

/**
 * Map a Calendar day-of-week to the set of DayType values that might match.
 * M4 uses WEEKEND for Saturday; M6 uses SATURDAY and SUNDAY separately.
 */
internal fun dayTypesForCalendarDay(dayOfWeek: Int): Set<DayType> = when (dayOfWeek) {
    Calendar.SATURDAY -> setOf(DayType.SATURDAY, DayType.WEEKEND)
    Calendar.SUNDAY -> setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
    else -> setOf(DayType.WEEKDAY)
}

/** Expand a DayType value to the full set used for timetable filtering. */
internal fun dayTypesFor(dayType: DayType): Set<DayType> = when (dayType) {
    DayType.SATURDAY -> setOf(DayType.SATURDAY, DayType.WEEKEND)
    DayType.SUNDAY -> setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
    DayType.WEEKEND -> setOf(DayType.WEEKEND, DayType.SATURDAY, DayType.SUNDAY)
    DayType.HOLIDAY -> setOf(DayType.HOLIDAY, DayType.SUNDAY, DayType.WEEKEND)
    else -> setOf(DayType.WEEKDAY)
}

// ============================================================================
// MAIN SCREEN
// ============================================================================

/**
 * Screen displaying next departure times for a specific bus stop.
 *
 * Loads ALL lines serving the stop and shows merged departures with route badges.
 * When [primaryRouteId] is set (e.g. navigation from a specific line), the filter
 * chips pre-select that line. From closest-stop, all lines are shown by default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextDepartureScreen(
    stop: BusStop,
    allRoutes: List<BusRoute>,
    primaryRouteId: String? = null,
    primaryViewId: String? = null,
    reminderService: ReminderService? = null,
    onBack: () -> Unit,
    onDaySchedule: (routeId: String, direction: String, variantLabel: String?, overrideDayType: DayType?) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val pdfService = remember { PDFProcessingService(context) }
    val timetableService = remember { TimetableService(context) }

    var loadedRoutes by remember { mutableStateOf<List<RouteLoadedData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Line filter: null = all lines
    var selectedRouteId by remember { mutableStateOf(primaryRouteId) }

    // Unified direction string across all loaded routes
    var currentDirection by remember { mutableStateOf<String?>(null) }

    var reminderKeys by remember { mutableStateOf(reminderService?.activeMatchKeys() ?: emptySet()) }
    var dailyReminderKeys by remember { mutableStateOf(reminderService?.dailyMatchKeys() ?: emptySet()) }
    var reminderError by remember { mutableStateOf<String?>(null) }

    // Boarding state
    var boardingConfirmed by remember { mutableStateOf(false) }
    var boardingSubmitting by remember { mutableStateOf(false) }
    var boardingError by remember { mutableStateOf<String?>(null) }
    var activeBoardings by remember { mutableStateOf<List<BoardingEvent>>(emptyList()) }
    var showBoardingWindowTooltip by remember { mutableStateOf(false) }
    val boardingScope = rememberCoroutineScope()

    // Live update tutorial state
    val liveUpdatePrefs = remember { context.getSharedPreferences("live_update_tutorial", android.content.Context.MODE_PRIVATE) }
    var showLiveUpdateTutorial by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(60000)
        }
    }

    val currentDayOfWeek = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) }
    val currentDayTypes = remember { dayTypesForCalendarDay(currentDayOfWeek) }
    val currentDayType = remember {
        when (currentDayOfWeek) {
            Calendar.SATURDAY -> DayType.SATURDAY
            Calendar.SUNDAY -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }
    }

    // Load all routes serving this stop
    LaunchedEffect(stop.id) {
        isLoading = true
        errorMessage = null
        try {
            val departuresService = DeparturesService(context)
            val departuresData = departuresService.loadDepartures(stop, allRoutes, primaryRouteId)
            val routeIds = departuresData.routes.map { it.route.id }
            DebugConfig.debugPrint("NextDepartureScreen: ${stop.name} served by routes: $routeIds")
            loadedRoutes = departuresData.routes
            AnalyticsService.track("next_departure_viewed", mapOf("stop" to stop.id))

            // Init direction from primaryViewId if available, otherwise first available
            if (currentDirection == null) {
                currentDirection = if (primaryRouteId != null && primaryViewId != null) {
                    departuresData.routes.find { it.route.id == primaryRouteId }
                        ?.views?.find { it.id == primaryViewId }?.direction
                } else null
                    ?: departuresData.routes.firstOrNull()?.views?.firstOrNull()?.direction
                    ?: ""
            }
        } catch (e: Exception) {
            errorMessage = "Error al cargar horarios: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val showMultiRoute = loadedRoutes.size > 1

    // Available directions: only from timetables that actually serve this stop
    val availableDirections = remember(loadedRoutes, selectedRouteId, stop.id) {
        val routesToUse = if (selectedRouteId != null)
            loadedRoutes.filter { it.route.id == selectedRouteId }
        else loadedRoutes
        val seen = linkedSetOf<String>()
        routesToUse.flatMap { it.timetables }
            .filter { it.stopId == stop.id }
            .forEach { t -> t.direction?.let { seen.add(it) } }
        seen.toList()
    }

    val direction = currentDirection ?: availableDirections.firstOrNull() ?: ""
    val swapDirection = availableDirections.firstOrNull { it != direction }

    // Reset boarding confirmation when direction changes
    LaunchedEffect(direction) {
        boardingConfirmed = false
    }

    // Active view (for variant label) — from single-route context
    val activeView = remember(loadedRoutes, selectedRouteId, direction) {
        val routeId = selectedRouteId ?: if (loadedRoutes.size == 1) loadedRoutes.first().route.id else null
        if (routeId != null) {
            loadedRoutes.find { it.route.id == routeId }?.views?.find { it.direction == direction }
        } else null
    }
    val selectedVariantLabel = activeView?.departureLabel

    // Merged tagged departures for today
    val todayTaggedDepartures = remember(loadedRoutes, selectedRouteId, currentDayTypes, direction, stop.id) {
        val routesToUse = if (selectedRouteId != null)
            loadedRoutes.filter { it.route.id == selectedRouteId }
        else loadedRoutes
        routesToUse.flatMap { routeData ->
            routeData.timetables
                .filter { it.dayType in currentDayTypes && it.stopId == stop.id && it.direction == direction }
                .flatMap { timetable ->
                    timetable.seasonalDepartures(weekday = currentDayOfWeek)
                        .map { TaggedDeparture(it, routeData.route.id, routeData.route.number) }
                }
        }.sortedBy { it.departure.toMinutesSinceMidnight() }
    }

    val hasTodayDepartures = todayTaggedDepartures.isNotEmpty()

    // Future tagged departures (up to 7 days ahead)
    data class NextDayTaggedDepartures(val departures: List<TaggedDeparture>, val daysAhead: Int)

    val nextDayTaggedDepartures = remember(loadedRoutes, selectedRouteId, direction, stop.id) {
        var result: NextDayTaggedDepartures? = null
        for (daysAhead in 1..7) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
            val futureDayTypes = dayTypesForCalendarDay(calendar.get(Calendar.DAY_OF_WEEK))
            val routesToUse = if (selectedRouteId != null)
                loadedRoutes.filter { it.route.id == selectedRouteId }
            else loadedRoutes
            val departures = routesToUse.flatMap { routeData ->
                routeData.timetables
                    .filter { it.dayType in futureDayTypes && it.stopId == stop.id && it.direction == direction }
                    .flatMap { timetable ->
                        timetable.seasonalDepartures(weekday = calendar.get(Calendar.DAY_OF_WEEK))
                            .map { TaggedDeparture(it, routeData.route.id, routeData.route.number) }
                    }
            }.sortedBy { it.departure.toMinutesSinceMidnight() }
            if (departures.isNotEmpty()) {
                result = NextDayTaggedDepartures(departures, daysAhead)
                break
            }
        }
        result
    }

    // Departure info
    data class DepartureInfo(
        val departure: TaggedDeparture?,
        val following: List<TaggedDeparture>,
        val daysAhead: Int
    )

    val departureInfo = remember(todayTaggedDepartures, nextDayTaggedDepartures, currentTime) {
        DebugConfig.debugPrint("NextDepartureScreen: currentTime=$currentTime, todayDepartures=${todayTaggedDepartures.size}")
        if (hasTodayDepartures) {
            val currentMinutes = currentTime.hour * 60 + currentTime.minute
            val upcoming = todayTaggedDepartures.filter { td ->
                val dt = LocalTime.of(td.departure.hour, td.departure.minute)
                dt.isAfter(currentTime) || dt == currentTime
            }
            // If a bus departed within the last 20 minutes, show it as primary so
            // the boarding button always refers to the trip the user can see on screen.
            val justDeparted = todayTaggedDepartures
                .filter { td ->
                    val diff = currentMinutes - td.departure.toMinutesSinceMidnight()
                    diff in 1..20
                }
                .lastOrNull()
            if (justDeparted != null) {
                DepartureInfo(justDeparted, upcoming.take(5), 0)
            } else if (upcoming.isNotEmpty()) {
                DepartureInfo(upcoming.first(), upcoming.drop(1).take(5), 0)
            } else if (nextDayTaggedDepartures != null) {
                DepartureInfo(
                    nextDayTaggedDepartures.departures.firstOrNull(),
                    nextDayTaggedDepartures.departures.drop(1).take(5),
                    nextDayTaggedDepartures.daysAhead
                )
            } else {
                DepartureInfo(null, emptyList(), 0)
            }
        } else if (nextDayTaggedDepartures != null) {
            DepartureInfo(
                nextDayTaggedDepartures.departures.firstOrNull(),
                nextDayTaggedDepartures.departures.drop(1).take(5),
                nextDayTaggedDepartures.daysAhead
            )
        } else {
            DepartureInfo(null, emptyList(), 0)
        }
    }

    val nextTaggedDeparture = departureInfo.departure
    val followingTaggedDepartures = departureInfo.following
    val daysAhead = departureInfo.daysAhead

    // Show live update tutorial on first visit where the boarding button is visible (today only)
    LaunchedEffect(isLoading) {
        if (!isLoading && daysAhead == 0 && !liveUpdatePrefs.getBoolean("tutorial_shown", false)) {
            liveUpdatePrefs.edit { putBoolean("tutorial_shown", true) }
            showLiveUpdateTutorial = true
        }
    }

    // True when the displayed departure is within ±20 minutes of now.
    val isWithinBoardingWindow by remember(currentTime, nextTaggedDeparture, daysAhead) {
        derivedStateOf {
            if (daysAhead > 0 || nextTaggedDeparture == null) return@derivedStateOf false
            val currentMinutes = currentTime.hour * 60 + currentTime.minute
            kotlin.math.abs(nextTaggedDeparture.departure.toMinutesSinceMidnight() - currentMinutes) <= 20
        }
    }

    // Boarding derived state
    val tripKey = remember(nextTaggedDeparture, direction, currentDayType) {
        nextTaggedDeparture?.let { BoardingRequest.makeTripKey(it.routeId, direction, currentDayType, it.departure) }
    }
    val matchingBoardings = remember(activeBoardings, nextTaggedDeparture, direction, currentDayType) {
        val td = nextTaggedDeparture ?: return@remember emptyList()
        val myMinutes = td.departure.hour * 60 + td.departure.minute
        activeBoardings.filter { boarding ->
            val parts = boarding.tripKey.split("|", limit = 4)
            if (parts.size < 4) return@filter false
            val bTimeParts = parts[3].split(":")
            if (bTimeParts.size < 2) return@filter false
            val bHour = bTimeParts[0].toIntOrNull() ?: return@filter false
            val bMin = bTimeParts[1].toIntOrNull() ?: return@filter false
            val bMinutes = bHour * 60 + bMin
            parts[0] == td.routeId
                && parts[1] == direction
                && parts[2] == currentDayType.name
                && bMinutes <= myMinutes
                && myMinutes - bMinutes <= 90
        }
    }
    val adjustedETA: String? = remember(matchingBoardings, nextTaggedDeparture) {
        val latest = matchingBoardings.maxByOrNull { it.boardedAt } ?: return@remember null
        val scheduledStr = latest.scheduledDepartureTime ?: return@remember null
        val td = nextTaggedDeparture ?: return@remember null
        try {
            val boardedInstant = Instant.parse(latest.boardedAt)
            val scheduledInstant = Instant.parse(scheduledStr)
            val latenessMinutes = (boardedInstant.epochSecond - scheduledInstant.epochSecond) / 60
            val myMinutes = td.departure.hour * 60L + td.departure.minute
            val adjusted = ((myMinutes + latenessMinutes) % 1440 + 1440) % 1440
            "%02d:%02d".format(adjusted / 60, adjusted % 60)
        } catch (_: Exception) { null }
    }

    LaunchedEffect(tripKey) {
        boardingConfirmed = false
        if (tripKey == null) return@LaunchedEffect
        while (true) {
            activeBoardings = BoardingService.fetchBoardings().getOrDefault(emptyList())
            delay(60_000)
        }
    }

    // TopAppBar title: route name when single route context, stop name for multi-route
    val topBarTitle = if (loadedRoutes.size == 1) {
        "Línea ${loadedRoutes.first().route.number}"
    } else if (selectedRouteId != null) {
        "Línea $selectedRouteId"
    } else {
        stop.name
    }

    // The active single route (for "Ver horario completo" button and direction label)
    val activeSingleRoute = if (selectedRouteId != null) {
        loadedRoutes.find { it.route.id == selectedRouteId }?.route
    } else if (loadedRoutes.size == 1) {
        loadedRoutes.first().route
    } else null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (swapDirection != null) {
                        IconButton(onClick = { currentDirection = swapDirection }) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Cambiar dirección"
                            )
                        }
                    }
                    IconButton(onClick = { showLiveUpdateTutorial = true }) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = "Acerca de las actualizaciones en directo"
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
            nextTaggedDeparture == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay horarios disponibles para esta parada",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Hero header (no route badge — shown in filter chips or TopAppBar)
                    item {
                        StopHeroHeader(
                            stop = stop,
                            direction = direction,
                            isCircular = activeSingleRoute?.isCircular ?: false,
                            onNavigateClick = { openMapsForStop(context, stop) }
                        )
                    }

                    // Line filter chips (only when >1 route serves this stop)
                    if (showMultiRoute) {
                        item {
                            LineFilterChips(
                                routeIds = loadedRoutes.map { it.route.id },
                                selectedRouteId = selectedRouteId,
                                onSelect = { id ->
                                    selectedRouteId = id
                                    // When switching filter, reset direction to first available
                                    currentDirection = null
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Future day warning card
                    if (daysAhead > 0) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                FutureDayWarningCard(daysAhead = daysAhead)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // Next departure card
                    item {
                        val bellEnabled = daysAhead == 0 && reminderService != null
                        val td = nextTaggedDeparture
                        val matchKey = BusReminder.matchKey(
                            td.routeId, stop.id, direction, td.departure.hour, td.departure.minute
                        )
                        NextDepartureWithProgress(
                            departure = td.departure,
                            routeNumber = if (showMultiRoute && selectedRouteId == null) td.routeNumber else null,
                            currentTime = currentTime,
                            selectedVariantLabel = selectedVariantLabel,
                            daysAhead = daysAhead,
                            isBellSet = reminderKeys.contains(matchKey),
                            isDailyBell = dailyReminderKeys.contains(matchKey),
                            onBellTap = if (bellEnabled) {
                                {
                                    reminderError = null
                                    val route = allRoutes.find { it.id == td.routeId }
                                    if (reminderKeys.contains(matchKey)) {
                                        reminderService!!.cancelReminder(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute)
                                    } else if (route != null) {
                                        val result = reminderService!!.scheduleReminder(td.departure, stop, route, direction, dayType = currentDayType)
                                        if (result is ReminderService.ScheduleResult.Failure) reminderError = result.message
                                        else AnalyticsService.track("reminder_set", mapOf("type" to "one_off", "route" to td.routeId))
                                    }
                                    reminderKeys = reminderService!!.activeMatchKeys()
                                    dailyReminderKeys = reminderService.dailyMatchKeys()
                                }
                            } else null,
                            onBellLongPress = if (bellEnabled) {
                                {
                                    reminderError = null
                                    val route = allRoutes.find { it.id == td.routeId }
                                    if (dailyReminderKeys.contains(matchKey)) {
                                        reminderService!!.cancelReminder(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute)
                                    } else if (route != null) {
                                        if (reminderKeys.contains(matchKey)) {
                                            reminderService!!.cancelReminder(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute)
                                        }
                                        val result = reminderService!!.scheduleReminder(td.departure, stop, route, direction, isDaily = true, dayType = currentDayType)
                                        if (result is ReminderService.ScheduleResult.Failure) reminderError = result.message
                                        else AnalyticsService.track("reminder_set", mapOf("type" to "daily", "route" to td.routeId))
                                    }
                                    reminderKeys = reminderService!!.activeMatchKeys()
                                    dailyReminderKeys = reminderService.dailyMatchKeys()
                                }
                            } else null,
                            adjustedETA = adjustedETA,
                            boardingCount = matchingBoardings.size,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Disclaimer card
                    item {
                        TimesDisclaimerCard(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp)
                        )
                    }

                    // "Estoy en el autobús" button — disabled outside ±20 min window
                    if (daysAhead == 0) {
                        item {
                            val boardingWindowSheetText = run {
                                val openMin = (nextTaggedDeparture?.departure?.toMinutesSinceMidnight() ?: -1) - 20
                                if (openMin >= 0) {
                                    val nowMin = currentTime.hour * 60 + currentTime.minute
                                    "Disponible a partir de las %02d:%02d (en ${formatMinutes(openMin - nowMin)}), cuando el bus esté más cerca.".format(openMin / 60, openMin % 60)
                                } else "La ventana de confirmación ha pasado. Espera al siguiente bus."
                            }
                            if (showBoardingWindowTooltip) {
                                ModalBottomSheet(
                                    onDismissRequest = { showBoardingWindowTooltip = false },
                                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 32.dp)
                                            .padding(bottom = 48.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AccessTime,
                                            contentDescription = null,
                                            tint = Color(0xFFFF9800),
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Text(
                                            text = "Confirmación no disponible",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = boardingWindowSheetText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "El botón se activa en los 20 minutos antes y después de cada salida, para que tu confirmación sea útil para otros viajeros.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            BoardingButton(
                                confirmed = boardingConfirmed,
                                isLoading = boardingSubmitting,
                                isActive = isWithinBoardingWindow,
                                errorMessage = boardingError,
                                onClick = {
                                    boardingError = null
                                    boardingScope.launch {
                                        val dep = nextTaggedDeparture ?: return@launch
                                        val key = tripKey ?: return@launch
                                        boardingSubmitting = true
                                        try {
                                            val now = Instant.now()
                                            val scheduled = LocalDate.now()
                                                .atTime(LocalTime.of(dep.departure.hour, dep.departure.minute, 0))
                                                .atZone(ZoneId.systemDefault()).toInstant()
                                            val request = BoardingRequest(
                                                stopId = stop.id,
                                                routeId = dep.routeId,
                                                direction = direction,
                                                tripKey = key,
                                                boardedAt = now.toString(),
                                                scheduledDepartureTime = scheduled.toString()
                                            )
                                            val result = BoardingService.postBoarding(request)
                                            if (result.isSuccess) {
                                                boardingConfirmed = true
                                                AnalyticsService.track("boarding_confirmed", mapOf("route" to dep.routeId, "stop" to stop.id))
                                                activeBoardings = BoardingService.fetchBoardings().getOrDefault(emptyList())
                                            } else {
                                                boardingError = "No se pudo enviar. Inténtalo de nuevo."
                                            }
                                        } finally {
                                            boardingSubmitting = false
                                        }
                                    }
                                },
                                onInactiveClick = { showBoardingWindowTooltip = true },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 12.dp)
                            )
                        }
                    }

                    // "Ver horario completo" — show whenever there's a clear single route context,
                    // even when all of today's buses have passed or there's no service today.
                    if (activeSingleRoute != null) {
                        item {
                            // When showing a future day, link to that day's full schedule.
                            val scheduleOverrideDayType = if (daysAhead > 0) {
                                val cal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, daysAhead) }
                                when (cal.get(Calendar.DAY_OF_WEEK)) {
                                    Calendar.SATURDAY -> DayType.SATURDAY
                                    Calendar.SUNDAY -> DayType.SUNDAY
                                    else -> DayType.WEEKDAY
                                }
                            } else null
                            OutlinedButton(
                                onClick = {
                                    onDaySchedule(activeSingleRoute.id, direction, selectedVariantLabel, scheduleOverrideDayType)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 12.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ver horario completo")
                            }
                        }
                    }

                    // Following departures section
                    if (followingTaggedDepartures.isNotEmpty()) {
                        item { Spacer(modifier = Modifier.height(24.dp)) }

                        item {
                            Text(
                                text = "Siguientes salidas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }

                        item { Spacer(modifier = Modifier.height(12.dp)) }

                        item {
                            val timelineBellEnabled = daysAhead == 0 && reminderService != null
                            DepartureTimeline(
                                departures = followingTaggedDepartures,
                                selectedVariantLabel = selectedVariantLabel,
                                showRouteBadge = showMultiRoute && selectedRouteId == null,
                                isBellSetFor = if (timelineBellEnabled) { td ->
                                    reminderKeys.contains(BusReminder.matchKey(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute))
                                } else null,
                                isDailyBellFor = if (timelineBellEnabled) { td ->
                                    dailyReminderKeys.contains(BusReminder.matchKey(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute))
                                } else null,
                                onBellTap = if (timelineBellEnabled) { td ->
                                    reminderError = null
                                    val route = allRoutes.find { it.id == td.routeId }
                                    val key = BusReminder.matchKey(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute)
                                    if (reminderKeys.contains(key)) {
                                        reminderService!!.cancelReminder(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute)
                                    } else if (route != null) {
                                        val result = reminderService!!.scheduleReminder(td.departure, stop, route, direction, dayType = currentDayType)
                                        if (result is ReminderService.ScheduleResult.Failure) reminderError = result.message
                                    }
                                    reminderKeys = reminderService!!.activeMatchKeys()
                                    dailyReminderKeys = reminderService.dailyMatchKeys()
                                } else null,
                                onBellLongPress = if (timelineBellEnabled) { td ->
                                    reminderError = null
                                    val route = allRoutes.find { it.id == td.routeId }
                                    val key = BusReminder.matchKey(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute)
                                    if (dailyReminderKeys.contains(key)) {
                                        reminderService!!.cancelReminder(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute)
                                    } else if (route != null) {
                                        if (reminderKeys.contains(key)) reminderService!!.cancelReminder(td.routeId, stop.id, direction, td.departure.hour, td.departure.minute)
                                        val result = reminderService!!.scheduleReminder(td.departure, stop, route, direction, isDaily = true, dayType = currentDayType)
                                        if (result is ReminderService.ScheduleResult.Failure) reminderError = result.message
                                    }
                                    reminderKeys = reminderService!!.activeMatchKeys()
                                    dailyReminderKeys = reminderService.dailyMatchKeys()
                                } else null,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }

    if (showLiveUpdateTutorial) {
        ModalBottomSheet(
            onDismissRequest = { showLiveUpdateTutorial = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            LiveUpdateTutorialSheet(onDismiss = { showLiveUpdateTutorial = false })
        }
    }

}

// ============================================================================
// NAVIGATION HELPER
// ============================================================================

/**
 * Opens the system default maps app for navigation to a bus stop.
 */
fun openMapsForStop(
    context: android.content.Context,
    stop: BusStop
) {
    val lat = stop.resolvedLatitude ?: return
    val lon = stop.resolvedLongitude ?: return

    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(stop.name)})")
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

// ============================================================================
// WARNING CARDS
// ============================================================================

/**
 * Warning card shown when displaying a future day's first bus.
 */
@Composable
fun FutureDayWarningCard(daysAhead: Int) {
    val message = when (daysAhead) {
        1 -> "No hay más autobuses hoy. Mostrando horario de mañana."
        2 -> "No hay más autobuses hoy ni mañana. Mostrando horario de pasado mañana."
        else -> "No hay más autobuses en los próximos días. Mostrando próximo horario disponible."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = WarningOrange.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Advertencia",
                tint = WarningOrange,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = WarningOrange,
                lineHeight = 18.sp
            )
        }
    }
}

// ============================================================================
// LINE FILTER CHIPS
// ============================================================================

/**
 * Horizontal row of filter chips for selecting a specific line or "Todas".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineFilterChips(
    routeIds: List<String>,
    selectedRouteId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedRouteId == null,
            onClick = { onSelect(null) },
            label = { Text("Todas") }
        )
        routeIds.forEach { routeId ->
            FilterChip(
                selected = selectedRouteId == routeId,
                onClick = { onSelect(routeId) },
                label = { Text(routeId) }
            )
        }
    }
}

// ============================================================================
// ROUTE BADGE
// ============================================================================

/**
 * Small badge showing a line number (e.g. "M6", "M7").
 */
@Composable
fun RouteBadge(number: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ============================================================================
// COMPONENTS
// ============================================================================

/**
 * Determines the time of day period for a given hour.
 */
enum class TimeOfDay {
    MORNING,    // 6:00 - 12:59
    AFTERNOON,  // 13:00 - 19:59
    EVENING     // 20:00 - 5:59
}

fun getTimeOfDay(hour: Int): TimeOfDay {
    return when (hour) {
        in 6..12 -> TimeOfDay.MORNING
        in 13..19 -> TimeOfDay.AFTERNOON
        else -> TimeOfDay.EVENING
    }
}

/** Formats a duration in minutes as "Xh Ym", "Xh", or "Xm". */
fun formatMinutes(mins: Int): String {
    return if (mins >= 60) {
        val h = mins / 60; val m = mins % 60
        if (m == 0) "${h}h" else "${h}h ${m}m"
    } else "${mins}m"
}


/**
 * Small badge showing time of day with icon.
 */
@Composable
fun TimeOfDayIndicator(
    timeOfDay: TimeOfDay,
    modifier: Modifier = Modifier
) {
    val (icon, label, color) = when (timeOfDay) {
        TimeOfDay.MORNING -> Triple(
            Icons.Default.WbSunny,
            "Mañana",
            MaterialTheme.colorScheme.tertiary
        )
        TimeOfDay.AFTERNOON -> Triple(
            Icons.Default.Brightness4,
            "Tarde",
            MaterialTheme.colorScheme.primary
        )
        TimeOfDay.EVENING -> Triple(
            Icons.Default.NightsStay,
            "Noche",
            MaterialTheme.colorScheme.secondary
        )
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Badge displaying departure time in a styled chip format.
 */
@Composable
fun DepartureTimeBadge(
    time: String,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * Hero header section with gradient background, stop information, and optional static map tile.
 * Route badge is intentionally absent — line context is provided by filter chips or TopAppBar.
 */
@Composable
fun StopHeroHeader(
    stop: BusStop,
    direction: String,
    isCircular: Boolean = false,
    modifier: Modifier = Modifier,
    onNavigateClick: () -> Unit
) {
    val directionLabel = if (isCircular) {
        direction
    } else {
        val destination = direction.split("→").lastOrNull()?.trim() ?: direction
        "Dirección $destination"
    }
    val mapData = remember(stop) { StaticMapService.getStaticMapData(stop) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stop name
            Text(
                text = stop.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Direction pill
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = directionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Area pill (if available)
            stop.area?.let {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HolidayVillage,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Details (if available)
            stop.details?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // "Cómo llegar" navigation link
            Row(
                modifier = Modifier.clickable(onClick = onNavigateClick),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Cómo llegar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            }

            // Static map tile (hidden when coordinates unavailable)
            mapData?.let { data ->
                StopMapTile(mapData = data)
            }
        }
    }
}

/**
 * Displays a 3x3 grid of OSM tiles centered on the marker, with a 2:1 aspect ratio.
 */
@Composable
private fun StopMapTile(
    mapData: StaticMapService.StaticMapData,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pinColor = MaterialTheme.colorScheme.primary

    val tileUrls = remember(mapData.tileX, mapData.tileY, mapData.zoom) {
        (-1..1).flatMap { dy ->
            (-1..1).map { dx ->
                "https://tile.openstreetmap.org/${mapData.zoom}/${mapData.tileX + dx}/${mapData.tileY + dy}.png"
            }
        }
    }

    Layout(
        content = {
            tileUrls.forEach { url ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(true)
                        .addHeader("User-Agent", "InterSego/1.0 (Android; bus timetable app for Segovia)")
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds
                )
            }

            Canvas(modifier = Modifier) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val pinHeight = 32.dp.toPx()
                val pinRadius = 11.dp.toPx()
                val dotRadius = 4.dp.toPx()
                val strokeWidth = 2.5f.dp.toPx()

                val tipY = cy
                val bulbY = tipY - pinHeight + pinRadius

                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - pinRadius * 0.45f, bulbY + pinRadius * 0.7f)
                    lineTo(cx, tipY)
                    lineTo(cx + pinRadius * 0.45f, bulbY + pinRadius * 0.7f)
                    close()
                }
                drawPath(path, color = pinColor)
                drawPath(path, color = Color.White, style = Stroke(width = strokeWidth))

                drawCircle(color = pinColor, radius = pinRadius, center = Offset(cx, bulbY))
                drawCircle(color = Color.White, radius = pinRadius, center = Offset(cx, bulbY), style = Stroke(width = strokeWidth))
                drawCircle(color = Color.White, radius = dotRadius, center = Offset(cx, bulbY))
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(MaterialTheme.shapes.medium)
    ) { measurables, constraints ->
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight
        val tileSize = viewportW

        val markerGridX = tileSize + (mapData.markerX / 256f * tileSize).toInt()
        val markerGridY = tileSize + (mapData.markerY / 256f * tileSize).toInt()

        val offsetX = viewportW / 2 - markerGridX
        val offsetY = viewportH / 2 - markerGridY

        val tileConstraints = androidx.compose.ui.unit.Constraints.fixed(tileSize, tileSize)
        val tilePlaceables = measurables.take(9).map { it.measure(tileConstraints) }
        val pinPlaceable = measurables.last().measure(
            androidx.compose.ui.unit.Constraints.fixed(viewportW, viewportH)
        )

        layout(viewportW, viewportH) {
            tilePlaceables.forEachIndexed { i, placeable ->
                val col = i % 3
                val row = i / 3
                placeable.place(
                    x = col * tileSize + offsetX,
                    y = row * tileSize + offsetY
                )
            }
            pinPlaceable.place(0, 0)
        }
    }
}

// ============================================================================
// BELL ICON
// ============================================================================

/**
 * Bell icon with tap (one-off reminder) and long-press (daily reminder) support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BellIcon(
    isBellSet: Boolean,
    isDailyBell: Boolean = false,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = { onTap?.invoke() },
                onLongClick = { onLongPress?.invoke() }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isDailyBell) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Cancelar recordatorio diario",
                tint = primary,
                modifier = Modifier.size(32.dp)
            )
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(13.dp)
            )
        } else {
            Icon(
                imageVector = if (isBellSet) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                contentDescription = if (isBellSet) "Cancelar recordatorio" else "Programar recordatorio",
                tint = if (isBellSet) primary else onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ============================================================================
// DEPARTURE CARDS
// ============================================================================

/**
 * Compact next departure display with badge-styled time and countdown.
 * [routeNumber] shows a route badge (e.g. "M6") when displaying multi-line results.
 */
@Composable
fun NextDepartureWithProgress(
    departure: DepartureTime,
    routeNumber: String? = null,
    currentTime: LocalTime,
    selectedVariantLabel: String? = null,
    daysAhead: Int = 0,
    adjustedETA: String? = null,
    boardingCount: Int = 0,
    isBellSet: Boolean = false,
    isDailyBell: Boolean = false,
    onBellTap: (() -> Unit)? = null,
    onBellLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val departureTime = LocalTime.of(departure.hour, departure.minute)

    val minutesUntil = if (daysAhead > 0) {
        val minutesUntilMidnight = currentTime.until(LocalTime.MAX, ChronoUnit.MINUTES)
        val minutesFromMidnight = LocalTime.MIN.until(departureTime, ChronoUnit.MINUTES)
        val fullDaysMinutes = (daysAhead - 1) * 24 * 60
        minutesUntilMidnight + fullDaysMinutes + minutesFromMidnight + 1
    } else {
        currentTime.until(departureTime, ChronoUnit.MINUTES)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (minutesUntil < 0) "Última salida:" else "Próxima salida:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                DepartureTimeBadge(time = departure.toDisplayString())

                // Route badge for multi-line display
                routeNumber?.let {
                    RouteBadge(number = it)
                }

                // Variant label badge
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (onBellTap != null || onBellLongPress != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    BellIcon(
                        isBellSet = isBellSet,
                        isDailyBell = isDailyBell,
                        onTap = onBellTap,
                        onLongPress = onBellLongPress
                    )
                }
            }

            // Tiempo real (subtle, right under Próxima salida)
            if (adjustedETA != null && boardingCount > 0) {
                var showBoardingInfo by remember { mutableStateOf(false) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tiempo real: $adjustedETA",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { showBoardingInfo = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Información sobre tiempo real",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (showBoardingInfo) {
                    val who = if (boardingCount == 1) "1 usuario confirmó" else "$boardingCount usuarios confirmaron"
                    AlertDialog(
                        onDismissRequest = { showBoardingInfo = false },
                        title = { Text("Tiempo real") },
                        text = { Text("$who que están en este autobús, lo que nos permite ajustar el tiempo estimado de llegada a $adjustedETA.") },
                        confirmButton = {
                            TextButton(onClick = { showBoardingInfo = false }) { Text("Entendido") }
                        }
                    )
                }
            }

            // Countdown with live suffix when ETA available
            val liveMinutes = remember(adjustedETA, currentTime) {
                if (adjustedETA == null || boardingCount == 0) return@remember null
                val p = adjustedETA.split(":")
                if (p.size < 2) return@remember null
                val etaTotal = (p[0].toIntOrNull() ?: return@remember null) * 60 +
                               (p[1].toIntOrNull() ?: return@remember null)
                val nowTotal = currentTime.hour * 60 + currentTime.minute
                var diff = etaTotal - nowTotal
                if (diff < -720) diff += 1440
                diff
            }
            val liveSuffix = liveMinutes?.let { m ->
                val str = if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
                " ($str 🔴)"
            } ?: ""
            Text(
                text = when {
                    minutesUntil < 0 -> "Salió hace ${-minutesUntil} min"
                    minutesUntil < 1 -> "Saliendo ahora"
                    minutesUntil == 1L -> "Sale en 1 minuto"
                    minutesUntil < 60 -> "Sale en $minutesUntil minutos"
                    else -> {
                        val hours = minutesUntil / 60
                        val mins = minutesUntil % 60
                        if (mins == 0L) "Sale en $hours hora${if (hours > 1) "s" else ""}"
                        else "Sale en ${hours}h ${mins}m"
                    }
                } + liveSuffix,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            if (!departure.notes.isNullOrBlank()) {
                Text(
                    text = departure.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Timeline-style display for following departures.
 * [showRouteBadge] enables per-row route badges (for multi-line stops).
 */
@Composable
fun DepartureTimeline(
    departures: List<TaggedDeparture>,
    selectedVariantLabel: String? = null,
    showRouteBadge: Boolean = false,
    isBellSetFor: ((TaggedDeparture) -> Boolean)? = null,
    isDailyBellFor: ((TaggedDeparture) -> Boolean)? = null,
    onBellTap: ((TaggedDeparture) -> Unit)? = null,
    onBellLongPress: ((TaggedDeparture) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        departures.forEachIndexed { index, tagged ->
            val departure = tagged.departure
            val timeOfDay = getTimeOfDay(departure.hour)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timeline visual (dot + line)
                TimelineIndicator(isLast = index == departures.lastIndex)

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

                            // Route badge for multi-line stops
                            if (showRouteBadge) {
                                RouteBadge(number = tagged.routeNumber)
                            }

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

                        if (isBellSetFor != null || onBellTap != null) {
                            BellIcon(
                                isBellSet = isBellSetFor?.invoke(tagged) ?: false,
                                isDailyBell = isDailyBellFor?.invoke(tagged) ?: false,
                                onTap = onBellTap?.let { { it(tagged) } },
                                onLongPress = onBellLongPress?.let { { it(tagged) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Visual indicator for a timeline entry: a connecting line and a dot.
 * Spans the full height of the parent Row. The line fills from top to bottom,
 * and the dot is centered (drawn after so it covers the line's midpoint).
 * [isLast] controls whether the connecting line is shown (hidden for the last item).
 */
@Composable
private fun TimelineIndicator(isLast: Boolean) {
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

// ============================================================================
// DISCLAIMER CARD
// ============================================================================

/**
 * Expandable disclaimer card informing users that departure times are approximate.
 */
@Composable
fun TimesDisclaimerCard(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "Contraer aviso" else "Expandir aviso"
            ) { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = WarningOrange.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = WarningOrange,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Horarios orientativos",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningOrange,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = WarningOrange,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                Text(
                    text = "Los horarios mostrados son orientativos y pueden variar. " +
                        "Consulta siempre la información oficial de Linecar para confirmar los horarios actuales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningOrange,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ============================================================================
// BOARDING
// ============================================================================

/**
 * Full-width button that lets the user announce they are on this bus.
 * Disabled once confirmed; resets when the direction changes.
 */
@Composable
private fun BoardingButton(
    confirmed: Boolean,
    isLoading: Boolean,
    isActive: Boolean,
    errorMessage: String?,
    onClick: () -> Unit,
    onInactiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = { if (isActive) onClick() else onInactiveClick() },
            enabled = !confirmed && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!isActive && !confirmed) Modifier.alpha(0.5f) else Modifier),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (confirmed) "¡Gracias por confirmar!" else "Estoy en el autobús")
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ============================================================================
// PREVIEW
// ============================================================================

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 600)
@Composable
fun DepartureTimelinePreview() {
    val dummyDepartures = listOf(
        TaggedDeparture(
            departure = DepartureTime(7, 30),
            routeId = "M4",
            routeNumber = "M4"
        ),
        TaggedDeparture(
            departure = DepartureTime(8, 15),
            routeId = "M4",
            routeNumber = "M4"
        ),
        TaggedDeparture(
            departure = DepartureTime(9, 0),
            routeId = "M4",
            routeNumber = "M4"
        ),
        TaggedDeparture(
            departure = DepartureTime(9, 45),
            routeId = "M4",
            routeNumber = "M4"
        ),
        TaggedDeparture(
            departure = DepartureTime(10, 30),
            routeId = "M4",
            routeNumber = "M4"
        ),
    )

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            DepartureTimeline(
                departures = dummyDepartures,
                selectedVariantLabel = "Regular",
                showRouteBadge = false,
                isBellSetFor = { false },
                isDailyBellFor = { false },
                onBellTap = null,
                onBellLongPress = null
            )
        }
    }
}
