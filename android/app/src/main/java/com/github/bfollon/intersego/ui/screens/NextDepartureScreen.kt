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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HolidayVillage
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.github.bfollon.intersego.ui.theme.WarningOrange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.StaticMapService
import com.github.bfollon.intersego.services.TimetableService
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * Map a Calendar day-of-week to the set of DayType values that might match.
 * M4 uses WEEKEND for Saturday; M6 uses SATURDAY and SUNDAY separately.
 */
internal fun dayTypesForCalendarDay(dayOfWeek: Int): Set<DayType> = when (dayOfWeek) {
    Calendar.SATURDAY -> setOf(DayType.SATURDAY, DayType.WEEKEND)
    Calendar.SUNDAY -> setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
    else -> setOf(DayType.WEEKDAY)
}

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
    selectedVariantLabel: String? = null, // Label of the variant the user selected (labels from other variants are shown)
    onBack: () -> Unit,
    onDaySchedule: () -> Unit = {}
) {
    val context = LocalContext.current
    val timetableService = remember { TimetableService(context) }

    var timetables by remember { mutableStateOf<List<com.github.bfollon.intersego.data.BusTimetable>>(emptyList()) }
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

    // Auto-detect current day types (supports both M4's WEEKEND and M6's SATURDAY/SUNDAY)
    val currentDayTypes = remember {
        dayTypesForCalendarDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    }

    // Filter and merge all timetables for current stop, day type, AND direction
    // Multiple timetables may exist for the same stop (e.g., Regular + Extended variants)
    val todayDepartures = remember(timetables, currentDayTypes, stop, direction) {
        DebugConfig.debugPrint("Filtering timetables: dayTypes=$currentDayTypes, stop=${stop.name}, direction=$direction")
        DebugConfig.debugPrint("Total timetables: ${timetables.size}")

        val matching = timetables.filter {
            it.dayType in currentDayTypes &&
            it.stopId == stop.name &&
            it.direction == direction
        }

        val merged = matching.flatMap { it.seasonalDepartures() }.sortedBy { it.toMinutesSinceMidnight() }
        DebugConfig.debugPrint("Found ${matching.size} matching timetables with ${merged.size} total departures")
        merged
    }

    val hasTodayDepartures = todayDepartures.isNotEmpty()

    // Find next available departures (checking up to 7 days ahead)
    data class NextDayDepartures(
        val departures: List<DepartureTime>,
        val daysAhead: Int
    )

    val nextDayDepartures = remember(timetables, currentDayTypes, stop, direction) {
        var result: NextDayDepartures? = null

        for (daysAhead in 1..7) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
            val futureDayTypes = dayTypesForCalendarDay(calendar.get(Calendar.DAY_OF_WEEK))

            val departures = timetables
                .filter {
                    it.dayType in futureDayTypes &&
                    it.stopId == stop.name &&
                    it.direction == direction
                }
                .flatMap { it.seasonalDepartures() }
                .sortedBy { it.toMinutesSinceMidnight() }

            if (departures.isNotEmpty()) {
                DebugConfig.debugPrint("Found next departures in $daysAhead day(s): ${departures.size} departures")
                result = NextDayDepartures(departures, daysAhead)
                break
            }
        }

        if (result == null) {
            DebugConfig.debugPrint("No departures found in next 7 days")
        }

        result
    }

    // Find next departures (including future days if no more today)
    data class DepartureInfo(
        val departure: DepartureTime?,
        val following: List<DepartureTime>,
        val daysAhead: Int  // 0 = today, 1 = tomorrow, 2+ = future
    )

    val departureInfo = remember(todayDepartures, nextDayDepartures, currentTime) {
        DebugConfig.debugPrint("Current time: $currentTime")

        if (hasTodayDepartures) {
            val upcoming = todayDepartures.filter { departure ->
                val departureTime = LocalTime.of(departure.hour, departure.minute)
                departureTime.isAfter(currentTime) || departureTime == currentTime
            }

            DebugConfig.debugPrint("Upcoming departures today: ${upcoming.size}")

            if (upcoming.isNotEmpty()) {
                val next = upcoming.first()
                val following = upcoming.drop(1).take(5)
                DepartureInfo(next, following, 0)
            } else if (nextDayDepartures != null) {
                val nextFirst = nextDayDepartures.departures.firstOrNull()
                val following = nextDayDepartures.departures.drop(1).take(5)
                DepartureInfo(nextFirst, following, nextDayDepartures.daysAhead)
            } else {
                DepartureInfo(null, emptyList(), 0)
            }
        } else if (nextDayDepartures != null) {
            val nextFirst = nextDayDepartures.departures.firstOrNull()
            val following = nextDayDepartures.departures.drop(1).take(5)
            DepartureInfo(nextFirst, following, nextDayDepartures.daysAhead)
        } else {
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Hero header with map tile
                    item {
                        StopHeroHeader(
                            route = route,
                            stop = stop,
                            onNavigateClick = {
                                openMapsForStop(context, stop)
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

                    // Next departure with circular progress
                    item {
                        NextDepartureWithProgress(
                            departure = nextDeparture,
                            currentTime = currentTime,
                            selectedVariantLabel = selectedVariantLabel,
                            daysAhead = daysAhead,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // "Ver horario completo" button — only for today's schedule
                    if (hasTodayDepartures) {
                        item {
                            OutlinedButton(
                                onClick = onDaySchedule,
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
                                selectedVariantLabel = selectedVariantLabel,
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

/**
 * Warning card shown when displaying a future day's first bus.
 * Styled to match FarmaciasDeGuardia warning cards.
 */
@Composable
fun FutureDayWarningCard(daysAhead: Int) {
    val message = when (daysAhead) {
        1 -> "No hay más autobuses hoy. Mostrando horario de mañana."
        2 -> "No hay más autobuses hoy ni mañana. Mostrando horario de pasado mañana."
        else -> "No hay más autobuses en los próximos días. Mostrando próximo horario disponible."
    }

    val warningColor = WarningOrange

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = warningColor.copy(alpha = 0.15f)
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
                tint = warningColor,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = warningColor,
                lineHeight = 18.sp
            )
        }
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
 * Reuses the visual style of TimeOfDayIndicator for consistency.
 */
@Composable
fun DepartureTimeBadge(
    time: String,  // e.g., "14:30"
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
 */
@Composable
fun StopHeroHeader(
    route: BusRoute,
    stop: BusStop,
    modifier: Modifier = Modifier,
    onNavigateClick: () -> Unit
) {
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
 * The grid is offset so the pin marker always appears at the center of the viewport.
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

                // Tip of the pin sits at the center (the actual location)
                val tipY = cy
                val bulbY = tipY - pinHeight + pinRadius

                // Tapered tail from bulb to tip
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - pinRadius * 0.45f, bulbY + pinRadius * 0.7f)
                    lineTo(cx, tipY)
                    lineTo(cx + pinRadius * 0.45f, bulbY + pinRadius * 0.7f)
                    close()
                }
                drawPath(path, color = pinColor)
                drawPath(path, color = Color.White, style = Stroke(width = strokeWidth))

                // Filled circle (bulb)
                drawCircle(color = pinColor, radius = pinRadius, center = Offset(cx, bulbY))
                drawCircle(color = Color.White, radius = pinRadius, center = Offset(cx, bulbY), style = Stroke(width = strokeWidth))

                // Inner dot
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

        // Marker position within the 3x3 grid (center tile is at row=1, col=1)
        val markerGridX = tileSize + (mapData.markerX / 256f * tileSize).toInt()
        val markerGridY = tileSize + (mapData.markerY / 256f * tileSize).toInt()

        // Offset the grid so the marker lands at the viewport center
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

/**
 * Compact next departure display with badge-styled time and countdown.
 */
@Composable
fun NextDepartureWithProgress(
    departure: DepartureTime,
    currentTime: LocalTime,
    selectedVariantLabel: String? = null,
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
            // Top row: "Próxima salida:" + time badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Próxima salida:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                DepartureTimeBadge(time = departure.toDisplayString())

                // Variant label badge: show for non-default variants
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
            }

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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            // Notes if any
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
 */
@Composable
fun DepartureTimeline(
    departures: List<DepartureTime>,
    selectedVariantLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
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
                                color = Color.LightGray,
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
    }
}

