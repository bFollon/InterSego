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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.github.bfollon.linecapp.data.BusRoute
import com.github.bfollon.linecapp.data.BusStop
import com.github.bfollon.linecapp.data.DayType
import com.github.bfollon.linecapp.data.DepartureTime
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

    // Calculate tomorrow's day type
    val tomorrowDayType = remember {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> DayType.WEEKEND
            Calendar.SUNDAY -> DayType.HOLIDAY
            else -> DayType.WEEKDAY
        }
    }

    // Filter timetable for current stop (by name), day type, AND direction
    val todayTimetable = remember(timetables, currentDayType, stop, direction) {
        timetables.find {
            it.dayType == currentDayType &&
            it.stopId == stop.name &&
            it.direction == direction
        }
    }

    val tomorrowTimetable = remember(timetables, tomorrowDayType, stop, direction) {
        timetables.find {
            it.dayType == tomorrowDayType &&
            it.stopId == stop.name &&
            it.direction == direction
        }
    }

    // Find next departures (including tomorrow if no more today)
    data class DepartureInfo(
        val departure: DepartureTime?,
        val following: List<DepartureTime>,
        val isNextDay: Boolean
    )

    val departureInfo = remember(todayTimetable, tomorrowTimetable, currentTime) {
        if (todayTimetable == null) {
            DepartureInfo(null, emptyList(), false)
        } else {
            val upcoming = todayTimetable.departures.filter { departure ->
                val departureTime = LocalTime.of(departure.hour, departure.minute)
                departureTime.isAfter(currentTime) || departureTime == currentTime
            }.sortedBy { LocalTime.of(it.hour, it.minute) }

            if (upcoming.isNotEmpty()) {
                // Found departures today
                val next = upcoming.firstOrNull()
                val following = upcoming.drop(1).take(5)
                DepartureInfo(next, following, false)
            } else if (tomorrowTimetable != null) {
                // No more today, get tomorrow's first departure
                val tomorrowFirst = tomorrowTimetable.departures
                    .sortedBy { LocalTime.of(it.hour, it.minute) }
                    .firstOrNull()
                DepartureInfo(tomorrowFirst, emptyList(), true)
            } else {
                DepartureInfo(null, emptyList(), false)
            }
        }
    }

    val nextDeparture = departureInfo.departure
    val followingDepartures = departureInfo.following
    val isNextDayDeparture = departureInfo.isNextDay

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
            todayTimetable == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay horarios disponibles para esta parada",
                        textAlign = TextAlign.Center
                    )
                }
            }
            nextDeparture == null -> {
                // No departures available at all (shouldn't happen normally)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay horarios disponibles",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Sticky stop info card
                    StopInfoCard(
                        route = route,
                        stop = stop,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    // Scrollable content
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Tomorrow warning card (if showing next day's first bus)
                        if (isNextDayDeparture) {
                            item {
                                HorizontalDivider()
                            }

                            item {
                                TomorrowWarningCard()
                            }
                        }

                        // Próxima salida section
                        if (!isNextDayDeparture) {
                            item {
                                HorizontalDivider()
                            }
                        }

                        item {
                            Text(
                                text = "Próxima salida",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        item {
                            NextDeparturePill(
                                departure = nextDeparture,
                                currentTime = currentTime,
                                isNextDay = isNextDayDeparture
                            )
                        }

                        // Following departures section
                        if (followingDepartures.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                            }

                            item {
                                Text(
                                    text = "Siguientes salidas",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            items(followingDepartures) { departure ->
                                FollowingDeparturePill(departure = departure)
                            }
                        }

                        // Bottom spacing
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
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
    isNextDay: Boolean = false
) {
    val departureTime = LocalTime.of(departure.hour, departure.minute)

    // Calculate minutes until departure (accounting for next day)
    val minutesUntil = if (isNextDay) {
        // Calculate time until midnight + time from midnight to departure
        val minutesUntilMidnight = currentTime.until(LocalTime.MAX, ChronoUnit.MINUTES)
        val minutesFromMidnight = LocalTime.MIN.until(departureTime, ChronoUnit.MINUTES)
        minutesUntilMidnight + minutesFromMidnight + 1 // +1 for the midnight minute
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
 * Warning card shown when displaying tomorrow's first bus.
 */
@Composable
fun TomorrowWarningCard() {
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
                text = "No hay más autobuses hoy. Mostrando horario de mañana.",
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
