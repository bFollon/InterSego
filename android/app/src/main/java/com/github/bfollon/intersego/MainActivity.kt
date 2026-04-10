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

package com.github.bfollon.intersego

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.github.bfollon.intersego.data.BoardingRequest
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusStopRegistry
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.services.BoardingService
import com.github.bfollon.intersego.services.ClosestStopFinderService
import com.github.bfollon.intersego.services.LocationManager as BusLocationManager
import com.github.bfollon.intersego.services.TimetableService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.RouteType
import com.github.bfollon.intersego.repositories.PDFURLRepository
import com.github.bfollon.intersego.services.CoordinateCache
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.NetworkMonitor
import com.github.bfollon.intersego.services.PDFCacheManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import com.github.bfollon.intersego.ui.screens.AboutScreen
import com.github.bfollon.intersego.ui.screens.AllRoutesScreen
import com.github.bfollon.intersego.ui.screens.DayScheduleScreen
import com.github.bfollon.intersego.ui.screens.LandingScreen
import com.github.bfollon.intersego.ui.screens.NextDepartureScreen
import com.github.bfollon.intersego.ui.screens.RouteMapScreen
import com.github.bfollon.intersego.ui.screens.RouteSelectionScreen
import com.github.bfollon.intersego.ui.screens.RouteStopsScreen
import com.github.bfollon.intersego.ui.screens.TimetableScreen
import com.github.bfollon.intersego.ui.theme.InterSegoTheme
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.github.bfollon.intersego.services.OsmTileFetcher
import com.github.bfollon.intersego.services.TileCacheService
import com.github.bfollon.intersego.services.GuidedModePrefs
import com.github.bfollon.intersego.ui.screens.RemindersScreen
import com.github.bfollon.intersego.ui.screens.DirectionPickerScreen
import com.github.bfollon.intersego.ui.screens.SettingsScreen
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Main activity for InterSego.
 *
 * Initializes services and sets up the Compose UI with navigation.
 * Implements ImageLoaderFactory to configure Coil for OSM tile compliance.
 */
class MainActivity : ComponentActivity(), ImageLoaderFactory {

    /**
     * Configure Coil ImageLoader with OSM-compliant settings.
     *
     * OSM Tile Usage Policy requirements:
     * - Custom User-Agent identifying the app and contact info
     * - HTTP caching for at least 7 days
     * - Respect server cache headers
     */
    override fun newImageLoader(): ImageLoader {
        // Custom OkHttpClient with OSM-compliant User-Agent
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "InterSego/1.0 (Android; +https://github.com/bfollon/intersego; contact:bruno.follon@gmail.com)")
                    .build()

                // Log tile requests for debugging
                DebugConfig.debugPrint("🗺️ Tile Request: ${request.url}")
                DebugConfig.debugPrint("🗺️ User-Agent: ${request.header("User-Agent")}")

                val response = chain.proceed(request)

                // Log response details
                DebugConfig.debugPrint("🗺️ Response Code: ${response.code}")
                if (!response.isSuccessful) {
                    DebugConfig.debugWarn("🗺️ Tile request failed: ${response.code} - ${response.message}")
                    // Log response body if there's an error
                    val errorBody = response.peekBody(1024).string()
                    if (errorBody.isNotEmpty()) {
                        DebugConfig.debugWarn("🗺️ Error Body: $errorBody")
                    }
                }

                response
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .components {
                add(OsmTileFetcher.Factory())
            }
            .okHttpClient(okHttpClient)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB cache
                    .build()
            }
            .respectCacheHeaders(true) // Honor HTTP cache headers from non-tile images
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    /**
     * Get list of known bus routes for update checking
     * Based on actual routes available on Linecar website
     * Routes: M1-M8 (no M9, M10, M11, M12 exist)
     */
    fun getKnownRoutes(): List<BusRoute> {
        // Metropolitan routes M1-M8
        // URLs will be resolved dynamically via PDFURLRepository
        return listOf(
            BusRoute(
                id = "M1",
                number = "M1",
                name = "Garcillán - Valverde del Manjano",
                origin = "Segovia",
                destination = "Área Metropolitana",
                pdfURL = "", // Will be resolved by PDFURLRepository
                routeType = RouteType.URBAN,
                isCircular = true
            ),
            BusRoute(
                id = "M2",
                number = "M2",
                name = "Hontanares - Valseca",
                origin = "Segovia",
                destination = "Valseca",
                pdfURL = "",
                routeType = RouteType.URBAN,
                isCircular = true
            ),
            BusRoute(
                id = "M3",
                number = "M3",
                name = "La Granja - Navacerrada",
                origin = "Segovia",
                destination = "Navacerrada",
                pdfURL = "",
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M4",
                number = "M4",
                name = "La Lastrilla - El Sotillo",
                origin = "La Lastrilla",
                destination = "El Sotillo",
                pdfURL = "",
                routeType = RouteType.URBAN,
                isCircular = true
            ),
            BusRoute(
                id = "M5",
                number = "M5",
                name = "Espirdo - Sto. Domingo de Pirón",
                origin = "Segovia",
                destination = "Sto. Domingo de Pirón",
                pdfURL = "",
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M6",
                number = "M6",
                name = "San Cristóbal - Torrecaballeros",
                origin = "Segovia",
                destination = "Torrecaballeros",
                pdfURL = "",
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M7",
                number = "M7",
                name = "Tabanera - Palazuelos",
                origin = "Segovia",
                destination = "Torrecaballeros",
                pdfURL = "",
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M8",
                number = "M8",
                name = "La Granja - Valsaín",
                origin = "Segovia",
                destination = "Valsaín",
                pdfURL = "",
                routeType = RouteType.URBAN
            )
        ).map { route ->
            // Resolve PDF URLs from PDFURLRepository
            val pdfUrlRepository = PDFURLRepository.getInstance(this)
            route.copy(pdfURL = pdfUrlRepository.getURL(route.id))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DebugConfig.debugPrint("🚀 InterSego starting...")

        // Initialize network monitor
        NetworkMonitor.initialize(this)

        // Initialize caches and cleanup expired entries
        CoordinateCache.initialize(this)
        GuidedModePrefs.initialize(this)

        // Cleanup expired cache entries on app start
        CoordinateCache.cleanupExpiredEntries()

        // Initialize persistent tile cache for OSM map tiles
        TileCacheService.initialize(this)

        // Set Coil ImageLoader with OsmTileFetcher for persistent tile caching
        // (ImageLoaderFactory only works on Application, not Activity, so we set explicitly)
        Coil.setImageLoader(newImageLoader())

        // Initialize PDF Cache Manager
        val pdfCacheManager = PDFCacheManager.getInstance(this)
        pdfCacheManager.initialize()

        // Create notification channel for bus departure reminders
        com.github.bfollon.intersego.services.ReminderService.createNotificationChannel(this)

        setContent {
            // State for tracking initialization - created in composition context
            var isInitialized by remember { mutableStateOf(false) }

            // Perform async initialization in LaunchedEffect
            LaunchedEffect(Unit) {
                DebugConfig.debugPrint("🌐 Initializing PDF URLs...")
                val pdfUrlRepository = PDFURLRepository.getInstance(this@MainActivity)
                val success = pdfUrlRepository.initializeURLs()
                if (success) {
                    DebugConfig.debugPrint("✅ PDF URLs initialized successfully")
                    DebugConfig.debugPrint(pdfUrlRepository.getStatus())
                } else {
                    DebugConfig.debugWarn("⚠️ PDF URL initialization failed, using fallback URLs")
                }

                // Check for PDF updates (respects 24-hour limit)
                DebugConfig.debugPrint("🔍 Checking for PDF updates...")
                val allRoutes = getKnownRoutes()
                pdfCacheManager.checkForUpdatesIfNeeded(allRoutes)

                // Mark initialization as complete
                DebugConfig.debugPrint("🔧 Setting isInitialized = true...")
                isInitialized = true
                DebugConfig.debugPrint("✅ Services initialized - isInitialized = $isInitialized")
            }

            InterSegoTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = isInitialized,
                        enter = fadeIn()
                    ) {
                        AppNavigation()
                    }

                    AnimatedVisibility(
                        visible = !isInitialized,
                        exit = fadeOut()
                    ) {
                        SplashScreen()
                    }
                }
            }
        }
    }
}

/**
 * Splash screen shown during app initialization.
 */
@Composable
fun SplashScreen() {
    val iconGreen = Color(0xFF3CA27A)
    val gradientColors = listOf(Color(0xFF34C759), Color(0xFF007AFF))

    var logoVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }
    var spinnerVisible by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.8f,
        animationSpec = tween(800, easing = EaseOut),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(800, easing = EaseOut),
        label = "logoAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(800, easing = EaseOut),
        label = "textAlpha"
    )
    val textScale by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0.9f,
        animationSpec = tween(800, easing = EaseOut),
        label = "textScale"
    )
    val spinnerAlpha by animateFloatAsState(
        targetValue = if (spinnerVisible) 1f else 0f,
        animationSpec = tween(800, easing = EaseOut),
        label = "spinnerAlpha"
    )

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(500)
        textVisible = true
        delay(100)
        spinnerVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(iconGreen)
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "InterSego",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textAlpha)
                    .scale(textScale)
            ) {
                Text(
                    text = "InterSego",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        brush = Brush.linearGradient(gradientColors)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Interurbanos de Segovia",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .alpha(spinnerAlpha),
                strokeWidth = 2.dp
            )

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

/**
 * App navigation structure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    com.github.bfollon.intersego.services.DebugConfig.debugPrint("🎯 AppNavigation composing...")

    val navController = rememberNavController()

    // Get list of routes once
    val activity = LocalActivity.current as? MainActivity
        ?: error("AppNavigation must be hosted in MainActivity")
    val routes = remember {
        activity.getKnownRoutes()
    }

    // Create PDFProcessingService for dynamic parser queries
    com.github.bfollon.intersego.services.DebugConfig.debugPrint("🔧 About to create PDFProcessingService...")
    val pdfProcessingService = remember {
        com.github.bfollon.intersego.services.DebugConfig.debugPrint("🔧 Inside remember block - creating PDFProcessingService...")
        try {
            val service = com.github.bfollon.intersego.services.PDFProcessingService(activity)
            com.github.bfollon.intersego.services.DebugConfig.debugPrint("✅ PDFProcessingService created successfully")
            service
        } catch (e: Exception) {
            com.github.bfollon.intersego.services.DebugConfig.debugError("❌ Failed to create PDFProcessingService", e)
            throw e
        }
    }
    com.github.bfollon.intersego.services.DebugConfig.debugPrint("🔧 PDFProcessingService variable assigned")

    // Force service initialization and log available routes
    com.github.bfollon.intersego.services.DebugConfig.debugPrint("🔧 Checking supported routes: ${pdfProcessingService.getSupportedRoutes()}")

    // --- Reminder service ---
    val reminderService = remember {
        com.github.bfollon.intersego.services.ReminderService(activity).also {
            it.initialize()
            it.pruneExpired()
        }
    }
    // --- End reminder service ---

    // --- About modal state ---
    var showAboutModal by remember { mutableStateOf(false) }
    // --- End about modal state ---

    // --- Closest stop state ---
    val coroutineScope = rememberCoroutineScope()
    var isSearchingClosestStop by remember { mutableStateOf(false) }
    var closestStopError by remember { mutableStateOf<String?>(null) }
    val locationMgr = remember { BusLocationManager(activity) }
    val closestStopFinder = remember { ClosestStopFinderService(activity) }

    // Stable lambda stored in remember so the permission-result callback always holds a
    // non-stale reference. coroutineScope and navController are stable across recompositions;
    // the MutableState objects backing isSearchingClosestStop/closestStopError are also stable.
    val launchClosestStopSearch: () -> Unit = remember(coroutineScope, navController) {
        {
            coroutineScope.launch {
                isSearchingClosestStop = true
                closestStopError = null
                try {
                    val location = locationMgr.requestLocationOnce()
                    val result = closestStopFinder.findClosest(location, routes)
                    val route = if (GuidedModePrefs.isGuidedModeEnabled()) {
                        "direction_picker/${result.stopId}/none/none"
                    } else {
                        "next_departure/${result.stopId}/none/none"
                    }
                    navController.navigate(route)
                } catch (e: BusLocationManager.LocationError) {
                    closestStopError = e.message
                } catch (e: ClosestStopFinderService.ClosestStopError) {
                    closestStopError = e.message
                } catch (e: Exception) {
                    closestStopError = "No se pudo encontrar la parada más cercana."
                } finally {
                    isSearchingClosestStop = false
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchClosestStopSearch()
        } else {
            closestStopError = "Permiso de ubicación denegado. Actívalo en Ajustes para usar esta función."
        }
    }
    // --- End closest stop state ---

    // --- Landing boarding state ---
    val timetableService = remember { TimetableService(activity) }
    var isSearchingBoardingStop by remember { mutableStateOf(false) }
    var boardingStop by remember { mutableStateOf<BusStop?>(null) }
    var boardingRouteDirections by remember { mutableStateOf<List<Pair<BusRoute, List<String>>>>(emptyList()) }
    var showBoardingRoutePicker by remember { mutableStateOf(false) }
    var selectedBoardingRoute by remember { mutableStateOf<BusRoute?>(null) }
    var showBoardingDirectionPicker by remember { mutableStateOf(false) }
    var landingBoardingConfirmed by remember { mutableStateOf(false) }
    var landingBoardingSubmitting by remember { mutableStateOf(false) }
    var landingBoardingError by remember { mutableStateOf<String?>(null) }
    var showNoServiceSheet by remember { mutableStateOf(false) }
    var noServiceStop by remember { mutableStateOf<BusStop?>(null) }

    val launchBoardingSearch: () -> Unit = remember(coroutineScope) {
        {
            coroutineScope.launch {
                isSearchingBoardingStop = true
                landingBoardingError = null
                try {
                    val location = locationMgr.requestLocationOnce()
                    val result = closestStopFinder.findClosest(location, routes)
                    val stop = BusStopRegistry.findById(result.stopId) ?: run {
                        landingBoardingError = "No se pudo encontrar la parada."
                        return@launch
                    }
                    val routeIds = pdfProcessingService.getRoutesForStop(stop.id)
                    val cal = java.util.Calendar.getInstance()
                    val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                    val todayDayTypes = when (dayOfWeek) {
                        java.util.Calendar.SATURDAY -> setOf(DayType.SATURDAY, DayType.WEEKEND)
                        java.util.Calendar.SUNDAY -> setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
                        else -> setOf(DayType.WEEKDAY)
                    }
                    val currentMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
                    val options = mutableListOf<Pair<BusRoute, List<String>>>()
                    for (routeId in routeIds.sorted()) {
                        val route = routes.find { it.id == routeId } ?: continue
                        val timetables = timetableService.loadTimetables(routeId)
                        val dirs = timetables
                            .filter { it.stopId == stop.id && it.dayType in todayDayTypes }
                            .filter { t ->
                                t.seasonalDepartures(weekday = dayOfWeek)
                                    .any { dep -> kotlin.math.abs(dep.toMinutesSinceMidnight() - currentMinutes) <= 20 }
                            }
                            .mapNotNull { it.direction }
                            .distinct()
                        if (dirs.isNotEmpty()) options.add(Pair(route, dirs))
                    }
                    if (options.isEmpty()) {
                        noServiceStop = stop
                        showNoServiceSheet = true
                    } else {
                        boardingStop = stop
                        boardingRouteDirections = options
                        if (options.size == 1) {
                            selectedBoardingRoute = options[0].first
                            showBoardingDirectionPicker = true
                        } else {
                            showBoardingRoutePicker = true
                        }
                    }
                } catch (e: BusLocationManager.LocationError) {
                    landingBoardingError = e.message
                } catch (e: ClosestStopFinderService.ClosestStopError) {
                    landingBoardingError = e.message
                } catch (e: Exception) {
                    landingBoardingError = "No se pudo encontrar la parada más cercana."
                } finally {
                    isSearchingBoardingStop = false
                }
            }
        }
    }

    val boardingPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchBoardingSearch()
        } else {
            landingBoardingError = "Permiso de ubicación denegado. Actívalo en Ajustes para usar esta función."
        }
    }
    // --- End landing boarding state ---

    // Route picker dialog for landing boarding
    if (showBoardingRoutePicker) {
        AlertDialog(
            onDismissRequest = { showBoardingRoutePicker = false },
            title = { Text("¿En qué línea estás?") },
            text = {
                Column {
                    boardingStop?.name?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        boardingRouteDirections.forEach { (route, _) ->
                            Surface(
                                onClick = {
                                    selectedBoardingRoute = route
                                    showBoardingRoutePicker = false
                                    showBoardingDirectionPicker = true
                                },
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = route.number,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBoardingRoutePicker = false }) { Text("Cancelar") }
            }
        )
    }

    // Direction picker dialog for landing boarding
    if (showBoardingDirectionPicker) {
        val dirs = boardingRouteDirections.find { it.first.id == selectedBoardingRoute?.id }?.second ?: emptyList()
        AlertDialog(
            onDismissRequest = { showBoardingDirectionPicker = false },
            title = { Text("¿En qué dirección vas?") },
            text = {
                Column {
                    dirs.forEach { dir ->
                        TextButton(
                            onClick = {
                                showBoardingDirectionPicker = false
                                val stop = boardingStop ?: return@TextButton
                                val route = selectedBoardingRoute ?: return@TextButton
                                coroutineScope.launch {
                                    landingBoardingSubmitting = true
                                    landingBoardingError = null
                                    try {
                                        val timetables = timetableService.loadTimetables(route.id)
                                        val cal = java.util.Calendar.getInstance()
                                        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                                        val currentDayType = when (dayOfWeek) {
                                            java.util.Calendar.SATURDAY -> DayType.SATURDAY
                                            java.util.Calendar.SUNDAY -> DayType.SUNDAY
                                            else -> DayType.WEEKDAY
                                        }
                                        val currentDayTypes = when (dayOfWeek) {
                                            java.util.Calendar.SATURDAY -> setOf(DayType.SATURDAY, DayType.WEEKEND)
                                            java.util.Calendar.SUNDAY -> setOf(DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
                                            else -> setOf(DayType.WEEKDAY)
                                        }
                                        val currentTime = LocalTime.now()
                                        val departure = timetables
                                            .filter { it.dayType in currentDayTypes && it.stopId == stop.id && it.direction == dir }
                                            .flatMap { it.seasonalDepartures(weekday = dayOfWeek) }
                                            .sortedBy { it.toMinutesSinceMidnight() }
                                            .firstOrNull { LocalTime.of(it.hour, it.minute).isAfter(currentTime) }
                                            ?: DepartureTime(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                                        val key = BoardingRequest.makeTripKey(route.id, dir, currentDayType, departure)
                                        val now = Instant.now()
                                        val scheduled = LocalDate.now()
                                            .atTime(LocalTime.of(departure.hour, departure.minute, 0))
                                            .atZone(ZoneId.systemDefault()).toInstant()
                                        val request = BoardingRequest(
                                            stopId = stop.id,
                                            routeId = route.id,
                                            direction = dir,
                                            tripKey = key,
                                            boardedAt = now.toString(),
                                            scheduledDepartureTime = scheduled.toString()
                                        )
                                        val result = BoardingService.postBoarding(request)
                                        if (result.isSuccess) {
                                            landingBoardingConfirmed = true
                                        } else {
                                            landingBoardingError = "No se pudo enviar. Inténtalo de nuevo."
                                        }
                                    } catch (e: Exception) {
                                        landingBoardingError = "No se pudo enviar. Inténtalo de nuevo."
                                    } finally {
                                        landingBoardingSubmitting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = dir.split("→").lastOrNull()?.trim() ?: dir
                            Text(label, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBoardingDirectionPicker = false }) { Text("Cancelar") }
            }
        )
    }

    // No service nearby sheet
    if (showNoServiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNoServiceSheet = false },
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
                    imageVector = Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "No hay buses próximos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                noServiceStop?.name?.let { stopName ->
                    Text(
                        text = "Hemos detectado que estás en la parada $stopName.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = "Sin embargo, ninguna de sus líneas tiene salidas en los próximos 20 minutos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Vuelve a pulsar el botón cuando estés a punto de subir al autobús.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "landing"
    ) {
        composable("landing") {
            LandingScreen(
                onNavigateToRouteList = {
                    navController.navigate("route_selection")
                },
                onShowAbout = { showAboutModal = true },
                onShowReminders = { navController.navigate("reminders") },
                onShowSettings = { navController.navigate("settings") },
                onFindClosestStop = {
                    closestStopError = null
                    if (locationMgr.hasLocationPermission()) {
                        launchClosestStopSearch()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                onBoardBus = {
                    landingBoardingError = null
                    if (locationMgr.hasLocationPermission()) {
                        launchBoardingSearch()
                    } else {
                        boardingPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                isSearchingClosestStop = isSearchingClosestStop,
                closestStopError = closestStopError,
                isBoardingBus = isSearchingBoardingStop || landingBoardingSubmitting,
                boardingBusConfirmed = landingBoardingConfirmed,
                boardingBusError = landingBoardingError
            )
        }

        composable("route_selection") {
            RouteSelectionScreen(
                routes = routes,
                pdfProcessingService = pdfProcessingService,
                onRouteSelected = { route ->
                    navController.navigate("route_stops/${route.id}")
                }
            )
        }

        composable("route_stops/{routeId}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val route = routes.find { it.id == routeId } ?: return@composable

            val todayDayType = remember {
                when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
                    java.util.Calendar.SATURDAY -> DayType.SATURDAY
                    java.util.Calendar.SUNDAY -> DayType.SUNDAY
                    else -> DayType.WEEKDAY
                }
            }

            val views = remember(routeId, todayDayType) {
                pdfProcessingService.getRouteViews(routeId, todayDayType)
            }

            val entries = remember(routeId) {
                pdfProcessingService.getRouteEntries(routeId)
            }
            val showAllRoutes = entries.size > 1 || views.isEmpty()

            RouteStopsScreen(
                route = route,
                views = views,
                onAllRoutesSelected = if (showAllRoutes) {{
                    navController.navigate("all_routes/${route.id}")
                }} else null,
                onBack = { navController.popBackStack() },
                onStopSelected = { stop, viewId ->
                    navController.navigate("next_departure/${stop.id}/${route.id}/$viewId")
                },
                onMapSelected = { viewId ->
                    navController.navigate("route_map/${route.id}/$viewId/none")
                }
            )
        }

        composable("direction_picker/{stopId}/{primaryRouteId}/{primaryViewId}") { backStackEntry ->
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val primaryRouteId = backStackEntry.arguments?.getString("primaryRouteId")
                ?.takeIf { it != "none" }
            val primaryViewId = backStackEntry.arguments?.getString("primaryViewId")
                ?.takeIf { it != "none" }

            // Resolve stop from BusStopRegistry
            val stop = remember(stopId) {
                com.github.bfollon.intersego.data.BusStopRegistry.findById(stopId)
                    ?: run {
                        // Fallback: search across known route views
                        val dayType = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
                            java.util.Calendar.SATURDAY -> DayType.SATURDAY
                            java.util.Calendar.SUNDAY -> DayType.SUNDAY
                            else -> DayType.WEEKDAY
                        }
                        pdfProcessingService.getSupportedRoutes()
                            .flatMap { pdfProcessingService.getRouteViews(it, dayType) }
                            .flatMap { it.stops }
                            .find { it.stop.id == stopId }
                            ?.stop
                    }
            } ?: return@composable

            DirectionPickerScreen(
                stopId = stopId,
                primaryRouteId = primaryRouteId,
                primaryViewId = primaryViewId,
                stop = stop,
                allRoutes = routes,
                pdfProcessingService = pdfProcessingService,
                onDirectionSelected = { routeId, viewId ->
                    navController.navigate("next_departure/$stopId/$routeId/$viewId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("next_departure/{stopId}/{primaryRouteId}/{primaryViewId}") { backStackEntry ->
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val primaryRouteId = backStackEntry.arguments?.getString("primaryRouteId")
                ?.takeIf { it != "none" }
            val primaryViewId = backStackEntry.arguments?.getString("primaryViewId")
                ?.takeIf { it != "none" }

            // Resolve stop from BusStopRegistry, falling back to a search across all views
            val stop = remember(stopId) {
                com.github.bfollon.intersego.data.BusStopRegistry.findById(stopId)
                    ?: run {
                        // Fallback: search across known route views
                        val dayType = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
                            java.util.Calendar.SATURDAY -> DayType.SATURDAY
                            java.util.Calendar.SUNDAY -> DayType.SUNDAY
                            else -> DayType.WEEKDAY
                        }
                        pdfProcessingService.getSupportedRoutes()
                            .flatMap { pdfProcessingService.getRouteViews(it, dayType) }
                            .flatMap { it.stops }
                            .find { it.stop.id == stopId }
                            ?.stop
                    }
            } ?: return@composable

            NextDepartureScreen(
                stop = stop,
                allRoutes = routes,
                primaryRouteId = primaryRouteId,
                primaryViewId = primaryViewId,
                reminderService = reminderService,
                onBack = { navController.popBackStack() },
                onDaySchedule = { routeId, direction, variantLabel, overrideDayType ->
                    val label = variantLabel ?: "all"
                    val dayTypeParam = overrideDayType?.name ?: "none"
                    navController.navigate("day_schedule/$routeId/${stop.id}/$direction/$label/$dayTypeParam")
                }
            )
        }

        composable("route_map/{routeId}/{viewId}/{groupId}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val initialViewId = backStackEntry.arguments?.getString("viewId") ?: return@composable
            val route = routes.find { it.id == routeId } ?: return@composable

            val todayDayType = remember {
                when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
                    java.util.Calendar.SATURDAY -> DayType.SATURDAY
                    java.util.Calendar.SUNDAY -> DayType.SUNDAY
                    else -> DayType.WEEKDAY
                }
            }

            val views = remember(routeId) {
                pdfProcessingService.getRouteViews(routeId, todayDayType)
            }

            RouteMapScreen(
                route = route,
                views = views,
                initialViewId = initialViewId,
                onBack = { navController.popBackStack() },
                onStopSelected = { stop, viewId ->
                    navController.navigate("next_departure/${stop.id}/${route.id}/$viewId")
                }
            )
        }

        composable("all_routes/{routeId}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val route = routes.find { it.id == routeId } ?: return@composable

            val routeEntries = remember(routeId) {
                pdfProcessingService.getRouteEntries(routeId)
            }
            var selectedEntryId by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(routeEntries.firstOrNull { it.isActiveToday }?.id ?: routeEntries.firstOrNull()?.id ?: "")
            }
            val selectedEntry = routeEntries.firstOrNull { it.id == selectedEntryId }

            val views = selectedEntry?.views ?: emptyList()

            AllRoutesScreen(
                route = route,
                routeEntries = routeEntries,
                selectedEntryId = selectedEntryId,
                onEntrySelected = { entry -> selectedEntryId = entry.id },
                views = views,
                initialViewId = selectedEntry?.initialViewId,
                onBack = { navController.popBackStack() },
                onStopSelected = { stop, viewId ->
                    if (selectedEntry == null || selectedEntry.isActiveToday) {
                        navController.navigate("next_departure/${stop.id}/${route.id}/$viewId")
                    } else {
                        val direction = views.find { it.id == viewId }?.direction ?: ""
                        val dayTypeOverride = selectedEntry.timetableDayType.name
                        val label = views.find { it.id == viewId }?.departureLabel ?: "all"
                        navController.navigate("day_schedule/${route.id}/${stop.id}/$direction/$label/$dayTypeOverride")
                    }
                },
                onMapSelected = { viewId ->
                    navController.navigate("all_routes_map/${route.id}/$viewId/$selectedEntryId")
                }
            )
        }

        composable("all_routes_map/{routeId}/{viewId}/{entryId}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val initialViewId = backStackEntry.arguments?.getString("viewId") ?: return@composable
            val initialEntryId = backStackEntry.arguments?.getString("entryId")
            val route = routes.find { it.id == routeId } ?: return@composable

            val routeEntries = remember(routeId) {
                pdfProcessingService.getRouteEntries(routeId)
            }
            var selectedEntryId by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(initialEntryId ?: routeEntries.firstOrNull { it.isActiveToday }?.id ?: routeEntries.firstOrNull()?.id ?: "")
            }
            val selectedEntry = routeEntries.firstOrNull { it.id == selectedEntryId }
            val views = selectedEntry?.views ?: emptyList()

            RouteMapScreen(
                route = route,
                views = views,
                initialViewId = selectedEntry?.initialViewId ?: initialViewId,
                routeEntries = routeEntries,
                selectedEntryId = selectedEntryId,
                onEntrySelected = { entry -> selectedEntryId = entry.id },
                allRoutesMode = true,
                onBack = { navController.popBackStack() },
                onStopSelected = { stop, viewId ->
                    if (selectedEntry == null || selectedEntry.isActiveToday) {
                        navController.navigate("next_departure/${stop.id}/${route.id}/$viewId")
                    } else {
                        val direction = views.find { it.id == viewId }?.direction ?: ""
                        val dayTypeOverride = selectedEntry.timetableDayType.name
                        val label = views.find { it.id == viewId }?.departureLabel ?: "all"
                        navController.navigate("day_schedule/${route.id}/${stop.id}/$direction/$label/$dayTypeOverride")
                    }
                }
            )
        }

        composable("reminders") {
            RemindersScreen(
                reminderService = reminderService,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("timetable/{routeId}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val route = routes.find { it.id == routeId } ?: return@composable

            TimetableScreen(
                route = route,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("day_schedule/{routeId}/{stopId}/{direction}/{variantLabel}/{dayTypeOverride}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val direction = backStackEntry.arguments?.getString("direction") ?: return@composable
            val variantLabel = backStackEntry.arguments?.getString("variantLabel")
            val dayTypeOverrideName = backStackEntry.arguments?.getString("dayTypeOverride")?.takeIf { it != "none" }
            val route = routes.find { it.id == routeId } ?: return@composable

            val stop = listOf(DayType.WEEKDAY, DayType.SATURDAY, DayType.SUNDAY)
                .firstNotNullOfOrNull { dayType ->
                    pdfProcessingService.getRouteViews(routeId, dayType)
                        .flatMap { it.stops }
                        .find { it.stop.id == stopId }
                        ?.stop
                } ?: return@composable

            val effectiveVariantLabel = if (variantLabel == "all") null else variantLabel
            val overrideDayType = dayTypeOverrideName?.let {
                runCatching { DayType.valueOf(it) }.getOrNull()
            }

            DayScheduleScreen(
                route = route,
                stop = stop,
                direction = direction,
                selectedVariantLabel = effectiveVariantLabel,
                overrideDayType = overrideDayType,
                reminderService = reminderService,
                onBack = { navController.popBackStack() }
            )
        }
    }

    if (showAboutModal) {
        ModalBottomSheet(
            onDismissRequest = { showAboutModal = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AboutScreen(onDismiss = { showAboutModal = false })
        }
    }
}
