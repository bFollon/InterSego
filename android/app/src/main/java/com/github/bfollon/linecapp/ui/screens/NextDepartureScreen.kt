/*
 * LineCapp - Bus Timetable App for Segovia
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

package com.github.bfollon.linecapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.github.bfollon.linecapp.data.BusRoute
import com.github.bfollon.linecapp.data.BusStop
import com.github.bfollon.linecapp.data.DayType
import com.github.bfollon.linecapp.data.DepartureTime
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.GeocodingService
import com.github.bfollon.linecapp.services.TimetableService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * Screen displaying next departure times for a specific bus stop.
 *
 * Shows the next bus departure prominently with countdown timer,
 * followed by upcoming departures in a list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextDepartureScreen(
    route: BusRoute,
    stop: BusStop,
    direction: String,  // e.g., "Lastrilla → Sotillo" or "Sotillo → Lastrilla"
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val timetableService = remember { TimetableService(context) }
    val scope = rememberCoroutineScope()

    var timetables by remember { mutableStateOf<List<com.github.bfollon.linecapp.data.BusTimetable>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Current time updates every minute for countdown
    var currentTime by remember { mutableStateOf(LocalTime.now()) }

    // Update time every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(60000) // Update every minute
        }
    }

    // Load timetables on launch
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

    // Auto-detect current day type
    val currentDayType = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> DayType.WEEKEND
            Calendar.SUNDAY -> DayType.HOLIDAY
            else -> DayType.WEEKDAY
        }
    }

    // Filter timetable for current stop (by name), day type, AND direction
    val todayTimetable = remember(timetables, currentDayType, stop, direction) {
        DebugConfig.debugPrint("🔍 Filtering timetables: dayType=$currentDayType, stop=${stop.name}, direction=$direction")
        DebugConfig.debugPrint("📊 Total timetables: ${timetables.size}")
        DebugConfig.debugPrint("📋 Available timetables: ${timetables.joinToString("\n") { "  - ${it.stopId} / ${it.direction} / ${it.dayType} (${it.departures.size} departures)" }}")

        val result = timetables.find {
            it.dayType == currentDayType &&
            it.stopId == stop.name &&
            it.direction == direction
        }

        DebugConfig.debugPrint("✅ Found timetable: ${result != null} (${result?.departures?.size ?: 0} departures)")
        result
    }

    // Find next available timetable (checking up to 7 days ahead)
    data class NextTimetableInfo(
        val timetable: com.github.bfollon.linecapp.data.BusTimetable?,
        val daysAhead: Int
    )

    val nextTimetableInfo = remember(timetables, currentDayType, stop, direction) {
        var result: NextTimetableInfo? = null

        // Check up to 7 days ahead for the next available timetable
        for (daysAhead in 1..7) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
            val futureDayType = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> DayType.WEEKEND
                Calendar.SUNDAY -> DayType.HOLIDAY
                else -> DayType.WEEKDAY
            }

            val timetable = timetables.find {
                it.dayType == futureDayType &&
                it.stopId == stop.name &&
                it.direction == direction &&
                it.departures.isNotEmpty()
            }

            if (timetable != null) {
                DebugConfig.debugPrint("✅ Found next timetable in $daysAhead day(s): $futureDayType with ${timetable.departures.size} departures")
                result = NextTimetableInfo(timetable, daysAhead)
                break
            }
        }

        if (result == null) {
            DebugConfig.debugPrint("❌ No timetable found in next 7 days")
        }

        result
    }

    // Find next departures (including future days if no more today)
    data class DepartureInfo(
        val departure: DepartureTime?,
        val following: List<DepartureTime>,
        val daysAhead: Int  // 0 = today, 1 = tomorrow, 2+ = future
    )

    val departureInfo = remember(todayTimetable, nextTimetableInfo, currentTime) {
        DebugConfig.debugPrint("⏰ Current time: $currentTime")

        if (todayTimetable != null) {
            // We have a timetable for today, check for upcoming departures
            DebugConfig.debugPrint("📅 Today's departures: ${todayTimetable.departures.map { it.toDisplayString() }}")

            val upcoming = todayTimetable.departures.filter { departure ->
                val departureTime = LocalTime.of(departure.hour, departure.minute)
                departureTime.isAfter(currentTime) || departureTime == currentTime
            }.sortedBy { LocalTime.of(it.hour, it.minute) }

            DebugConfig.debugPrint("🚌 Upcoming departures today: ${upcoming.size} (${upcoming.map { it.toDisplayString() }})")

            if (upcoming.isNotEmpty()) {
                // Found departures today
                val next = upcoming.firstOrNull()
                val following = upcoming.drop(1).take(5)
                DebugConfig.debugPrint("✅ Next departure today: ${next?.toDisplayString()}")
                DepartureInfo(next, following, 0)
            } else if (nextTimetableInfo != null) {
                // No more today, get next available day's first departure
                DebugConfig.debugPrint("🌙 No more departures today, checking next available day...")
                DebugConfig.debugPrint("📅 Next day's departures (+${nextTimetableInfo.daysAhead} days): ${nextTimetableInfo.timetable?.departures?.map { it.toDisplayString() }}")
                val allDepartures = nextTimetableInfo.timetable?.departures
                    ?.sortedBy { LocalTime.of(it.hour, it.minute) } ?: emptyList()
                val nextFirst = allDepartures.firstOrNull()
                val following = allDepartures.drop(1).take(5)
                DebugConfig.debugPrint("✅ Next available departure: ${nextFirst?.toDisplayString()}")
                DebugConfig.debugPrint("📋 Following departures that day: ${following.map { it.toDisplayString() }}")
                DepartureInfo(nextFirst, following, nextTimetableInfo.daysAhead)
            } else {
                DebugConfig.debugPrint("❌ No timetable found in next 7 days")
                DepartureInfo(null, emptyList(), 0)
            }
        } else if (nextTimetableInfo != null) {
            // No timetable for today (e.g., Sunday/Holiday with no service), but we have future timetable
            DebugConfig.debugPrint("❌ No timetable for today, using next available day")
            DebugConfig.debugPrint("📅 Next day's departures (+${nextTimetableInfo.daysAhead} days): ${nextTimetableInfo.timetable?.departures?.map { it.toDisplayString() }}")
            val allDepartures = nextTimetableInfo.timetable?.departures
                ?.sortedBy { LocalTime.of(it.hour, it.minute) } ?: emptyList()
            val nextFirst = allDepartures.firstOrNull()
            val following = allDepartures.drop(1).take(5)
            DebugConfig.debugPrint("✅ Next available departure: ${nextFirst?.toDisplayString()}")
            DebugConfig.debugPrint("📋 Following departures that day: ${following.map { it.toDisplayString() }}")
            DepartureInfo(nextFirst, following, nextTimetableInfo.daysAhead)
        } else {
            // No timetable for today AND no future timetable found
            DebugConfig.debugPrint("❌ No timetable for today and no timetable found in next 7 days")
            DepartureInfo(null, emptyList(), 0)
        }
    }

    val nextDeparture = departureInfo.departure
    val followingDepartures = departureInfo.following
    val daysAhead = departureInfo.daysAhead

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Línea ${route.number}")
                },
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
            nextDeparture == null -> {
                // No departures available at all (not even in future days)
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
                val geocodingService = remember { GeocodingService(context) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Hero header (replaces StopInfoCard)
                    item {
                        StopHeroHeader(
                            route = route,
                            stop = stop,
                            onAddressClick = {
                                scope.launch {
                                    openMapsForStop(context, geocodingService, stop)
                                }
                            }
                        )
                    }

                    // Future day warning card (if showing future departure)
                    if (daysAhead > 0) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                FutureDayWarningCard(daysAhead = daysAhead)
                            }
                        }
                    }

                    // Spacer after header
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Próxima salida section header
                    item {
                        Text(
                            text = "Próxima salida",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Next departure with circular progress
                    item {
                        NextDepartureWithProgress(
                            departure = nextDeparture,
                            currentTime = currentTime,
                            daysAhead = daysAhead,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Following departures section
                    if (followingDepartures.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        item {
                            Text(
                                text = "Siguientes salidas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Timeline-style following departures
                        item {
                            DepartureTimeline(
                                departures = followingDepartures,
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
 * Prominent pill container showing the next bus departure with both time and countdown.
 */
@Composable
fun NextDeparturePill(
    departure: DepartureTime,
    currentTime: LocalTime,
    daysAhead: Int = 0
) {
    val departureTime = LocalTime.of(departure.hour, departure.minute)

    // Calculate minutes until departure (accounting for days ahead)
    val minutesUntil = if (daysAhead > 0) {
        // Calculate time until midnight + full days + time from midnight to departure
        val minutesUntilMidnight = currentTime.until(LocalTime.MAX, ChronoUnit.MINUTES)
        val minutesFromMidnight = LocalTime.MIN.until(departureTime, ChronoUnit.MINUTES)
        val fullDaysMinutes = (daysAhead - 1) * 24 * 60
        minutesUntilMidnight + fullDaysMinutes + minutesFromMidnight + 1 // +1 for the midnight minute
    } else {
        currentTime.until(departureTime, ChronoUnit.MINUTES)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Actual departure time
            Column {
                Text(
                    text = departure.toDisplayString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Notes if any
                if (!departure.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = departure.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Right side: Countdown
            Text(
                text = when {
                    minutesUntil < 1 -> "Saliendo\nahora"
                    minutesUntil == 1L -> "en 1\nminuto"
                    minutesUntil < 60 -> "en $minutesUntil\nminutos"
                    else -> {
                        val hours = minutesUntil / 60
                        val mins = minutesUntil % 60
                        if (mins == 0L) "en $hours\nhora${if (hours > 1) "s" else ""}"
                        else "en ${hours}h\n${mins}m"
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * Stop information card with route badge and tappable address.
 */
@Composable
fun StopInfoCard(
    route: BusRoute,
    stop: BusStop,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val geocodingService = remember { GeocodingService(context) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Stop name with route badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Route badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = route.number,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tappable address
            Text(
                text = stop.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    scope.launch {
                        openMapsForStop(context, geocodingService, stop)
                    }
                }
            )
        }
    }
}

/**
 * Opens the system default maps app for navigation to a bus stop.
 */
suspend fun openMapsForStop(
    context: android.content.Context,
    geocodingService: GeocodingService,
    stop: BusStop
) {
    // Try to get coordinates from stop first, then geocode if needed
    val location = if (stop.latitude != null && stop.longitude != null) {
        android.location.Location("").apply {
            latitude = stop.latitude
            longitude = stop.longitude
        }
    } else {
        geocodingService.getCoordinatesForBusStop(stop)
    }

    val intent = if (location != null) {
        // Use geo: URI with coordinates
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(${Uri.encode(stop.name)})")
        }
    } else {
        // Fallback to address-based search
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("geo:0,0?q=${Uri.encode("${stop.address}, Segovia, España")}")
        }
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

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
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * Smaller pill container for following departures.
 */
@Composable
fun FollowingDeparturePill(
    departure: DepartureTime
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = departure.toDisplayString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!departure.notes.isNullOrBlank()) {
                Text(
                    text = departure.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ============================================================================
// NEW REDESIGNED COMPONENTS
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
 * Circular progress indicator around a time display.
 */
@Composable
fun CircularProgressClock(
    time: String,
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        Canvas(modifier = Modifier.size(120.dp)) {
            val strokeWidth = 8.dp.toPx()
            drawCircle(
                color = androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.3f),
                style = Stroke(width = strokeWidth)
            )

            // Progress arc
            drawArc(
                color = androidx.compose.ui.graphics.Color(0xFF1C74D3), // Brand blue
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Time text in center
        Text(
            text = time,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Hero header section with gradient background and stop information.
 */
@Composable
fun StopHeroHeader(
    route: BusRoute,
    stop: BusStop,
    modifier: Modifier = Modifier,
    onAddressClick: () -> Unit
) {
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
            // Route badge
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Línea ${route.number}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Stop name
            Text(
                text = stop.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Address with location icon
            Row(
                modifier = Modifier.clickable(onClick = onAddressClick),
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
                    text = stop.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}

/**
 * Enhanced next departure display with circular progress and time-of-day indicator.
 */
@Composable
fun NextDepartureWithProgress(
    departure: DepartureTime,
    currentTime: LocalTime,
    daysAhead: Int = 0,
    modifier: Modifier = Modifier
) {
    val departureTime = LocalTime.of(departure.hour, departure.minute)

    // Calculate minutes until departure
    val minutesUntil = if (daysAhead > 0) {
        val minutesUntilMidnight = currentTime.until(LocalTime.MAX, ChronoUnit.MINUTES)
        val minutesFromMidnight = LocalTime.MIN.until(departureTime, ChronoUnit.MINUTES)
        val fullDaysMinutes = (daysAhead - 1) * 24 * 60
        minutesUntilMidnight + fullDaysMinutes + minutesFromMidnight + 1
    } else {
        currentTime.until(departureTime, ChronoUnit.MINUTES)
    }

    // Calculate progress (assuming max 60 minutes for full circle)
    val progress = if (minutesUntil >= 60) 1f else minutesUntil.toFloat() / 60f

    val timeOfDay = getTimeOfDay(departure.hour)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time of day indicator
            TimeOfDayIndicator(timeOfDay = timeOfDay)

            // Circular progress clock
            CircularProgressClock(
                time = departure.toDisplayString(),
                progress = progress
            )

            // Countdown text
            Text(
                text = when {
                    minutesUntil < 1 -> "Saliendo ahora"
                    minutesUntil == 1L -> "Sale en 1 minuto"
                    minutesUntil < 60 -> "Sale en $minutesUntil minutos"
                    else -> {
                        val hours = minutesUntil / 60
                        val mins = minutesUntil % 60
                        if (mins == 0L) "Sale en $hours hora${if (hours > 1) "s" else ""}"
                        else "Sale en ${hours}h ${mins}m"
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Notes if any
            if (!departure.notes.isNullOrBlank()) {
                Text(
                    text = departure.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Timeline-style display for following departures.
 */
@Composable
fun DepartureTimeline(
    departures: List<DepartureTime>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        departures.forEachIndexed { index, departure ->
            val timeOfDay = getTimeOfDay(departure.hour)

            Row(
                modifier = Modifier
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
                    // Vertical line
                    if (index < departures.size - 1) {
                        Canvas(modifier = Modifier
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

                    // Dot
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
                        Text(
                            text = departure.toDisplayString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

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
    }
}
