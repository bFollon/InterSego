/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.bfollon.intersego.data.ServiceAlert
import com.github.bfollon.intersego.services.AlertService
import com.github.bfollon.intersego.services.ReminderService
import com.github.bfollon.intersego.services.RouteDataService
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.github.bfollon.intersego.data.BoardingRequest
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.BusStopRegistry
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.services.BoardingService
import com.github.bfollon.intersego.services.TimetableCacheService
import com.github.bfollon.intersego.services.PolylineCacheService
import com.github.bfollon.intersego.services.ClosestStopFinderService
import com.github.bfollon.intersego.services.LocationManager as BusLocationManager
import com.github.bfollon.intersego.services.TimetableService
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.services.CoordinateCache
import com.github.bfollon.intersego.services.DebugConfig
import com.github.bfollon.intersego.services.NetworkMonitor
import com.github.bfollon.intersego.services.TimetableLoader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import com.github.bfollon.intersego.ui.screens.AboutScreen
import com.github.bfollon.intersego.ui.screens.AllRoutesScreen
import com.github.bfollon.intersego.ui.screens.DayScheduleScreen
import com.github.bfollon.intersego.ui.screens.LandingScreen
import com.github.bfollon.intersego.services.DeviceTokenService
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
import com.github.bfollon.intersego.ui.screens.MonitoringConsentScreen
import com.github.bfollon.intersego.ui.screens.NotificationConsentScreen
import com.github.bfollon.intersego.services.AnalyticsService
import com.github.bfollon.intersego.services.DeparturesService
import com.github.bfollon.intersego.services.ErrorReportingService
import com.github.bfollon.intersego.services.MonitoringPreferencesService
import com.github.bfollon.intersego.services.NotificationPreferencesService
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

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

    fun getKnownRoutes(): List<BusRoute> = TimetableLoader(this).loadAllRoutes()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DebugConfig.debugPrint("🚀 InterSego starting...")

        // Initialize network monitor
        NetworkMonitor.initialize(this)

        // Initialize monitoring preferences (must be before error reporting and analytics)
        MonitoringPreferencesService.initialize(this)

        // Initialize error reporting and analytics if user has opted in
        ErrorReportingService.initialize(this)
        AnalyticsService.initialize(this)

        // Analytics: app launch event
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }
        AnalyticsService.track("app_launch", mapOf("version" to appVersion, "platform" to "android"))

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

        // Create notification channel for bus departure reminders
        com.github.bfollon.intersego.services.ReminderService.createNotificationChannel(this)

        // Ensure FCM token is stored and registered on first launch (onNewToken only fires
        // on rotation, not when a token already exists from a previous install).
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                getSharedPreferences("fcm_prefs", MODE_PRIVATE)
                    .edit { putString("fcm_token", token) }
                CoroutineScope(Dispatchers.IO).launch { DeviceTokenService.register(token, this@MainActivity) }
            }

        setContent {
            var isInitialized by remember { mutableStateOf(false) }
            var showMonitoringConsent by remember { mutableStateOf(false) }
            var showNotificationConsent by remember { mutableStateOf(false) }

            // Hoist services so the splash covers their initialization
            val reminderService = remember { ReminderService(this@MainActivity) }
            val routeDataService = remember { RouteDataService(this@MainActivity) }
            var routes by remember { mutableStateOf<List<BusRoute>>(emptyList()) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    // Mirror iOS initialize(): prune reminders and load route data
                    reminderService.initialize()
                    reminderService.pruneExpired()
                    routes = getKnownRoutes()
                    routeDataService.getSupportedRoutes()
                }

                isInitialized = true
                DebugConfig.debugPrint("✅ Services initialized")

                if (!MonitoringPreferencesService.hasUserMadeAnalyticsChoice()) {
                    showMonitoringConsent = true
                }
                if (!NotificationPreferencesService.hasUserMadeNotificationChoice(this@MainActivity)) {
                    showNotificationConsent = true
                }

                // Background timetable + polyline refresh — non-blocking, uses disk cache + ETags
                if (NetworkMonitor.isOnline()) {
                    launch { TimetableCacheService.fetchAllRoutes(this@MainActivity) }
                    launch { PolylineCacheService.fetchAllPolylines(this@MainActivity) }
                }
            }

            InterSegoTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = isInitialized,
                        enter = fadeIn()
                    ) {
                        AppNavigation(
                            routes = routes,
                            routeDataService = routeDataService,
                            reminderService = reminderService
                        )
                    }

                    AnimatedVisibility(
                        visible = !isInitialized,
                        exit = fadeOut()
                    ) {
                        SplashScreen()
                    }

                    if (showMonitoringConsent) {
                        MonitoringConsentScreen(
                            onDismiss = { showMonitoringConsent = false }
                        )
                    } else if (showNotificationConsent) {
                        NotificationConsentScreen(
                            onDismiss = { showNotificationConsent = false }
                        )
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
                    text = "Metropolitanos de Segovia",
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
fun AppNavigation(
    routes: List<BusRoute>,
    routeDataService: RouteDataService,
    reminderService: ReminderService,
) {
    val navController = rememberNavController()
    val activity = LocalActivity.current as? MainActivity
        ?: error("AppNavigation must be hosted in MainActivity")

    // --- About modal state ---
    var showAboutModal by remember { mutableStateOf(false) }
    // --- End about modal state ---

    // --- Service alerts state ---
    var activeAlerts by remember { mutableStateOf<List<ServiceAlert>>(emptyList()) }
    var showAlertDetail by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        activeAlerts = AlertService.fetchActiveAlerts()
    }
    // --- End service alerts state ---

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
                    AnalyticsService.track("closest_stop_used", mapOf("stop" to result.stopId))
                    val route = if (GuidedModePrefs.isGuidedModeEnabled()) {
                        val stop = com.github.bfollon.intersego.data.BusStopRegistry.findById(result.stopId)
                        val resolved = if (stop != null) {
                            DeparturesService(activity).resolveDirectionIfUnambiguous(stop, routes, null)
                        } else null
                        if (resolved != null) {
                            "next_departure/${result.stopId}/${resolved.first}/${resolved.second}"
                        } else {
                            "direction_picker/${result.stopId}/none/none"
                        }
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
                    val routeIds = routeDataService.getRoutesForStop(stop.id)
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
                                            AnalyticsService.track("boarding_confirmed", mapOf("route" to route.id, "stop" to stop.id))
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
                boardingBusError = landingBoardingError,
                activeAlerts = activeAlerts,
                onShowAlertDetail = { showAlertDetail = true },
            )
        }

        composable("route_selection") {
            RouteSelectionScreen(
                routes = routes,
                routeDataService = routeDataService,
                onRouteSelected = { route ->
                    AnalyticsService.track("route_selected", mapOf("route" to route.id))
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
                val todayViews = routeDataService.getRouteViews(routeId, todayDayType)
                if (todayViews.isNotEmpty()) todayViews
                else {
                    // No service today — fall back to any available day type so stops are still shown.
                    // NextDepartureScreen handles looking up to 7 days ahead for the actual departure.
                    listOf(DayType.WEEKDAY, DayType.SATURDAY, DayType.SUNDAY, DayType.WEEKEND, DayType.HOLIDAY)
                        .firstNotNullOfOrNull { dt -> routeDataService.getRouteViews(routeId, dt).takeIf { it.isNotEmpty() } }
                        ?: emptyList()
                }
            }

            val entries = remember(routeId) {
                routeDataService.getRouteEntries(routeId)
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
                    if (GuidedModePrefs.isGuidedModeEnabled()) {
                        coroutineScope.launch {
                            val resolved = DeparturesService(activity).resolveDirectionIfUnambiguous(stop, routes, route.id)
                            if (resolved != null) {
                                navController.navigate("next_departure/${stop.id}/${resolved.first}/${resolved.second}")
                            } else {
                                navController.navigate("direction_picker/${stop.id}/${route.id}/$viewId")
                            }
                        }
                    } else {
                        navController.navigate("next_departure/${stop.id}/${route.id}/$viewId")
                    }
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
                        routeDataService.getSupportedRoutes()
                            .flatMap { routeDataService.getRouteViews(it, dayType) }
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
                onDirectionSelected = { routeId, viewId ->
                    // Pop the direction_picker off the back stack so pressing Back from
                    // NextDeparture returns to wherever the user came from (stop list, etc.)
                    // rather than re-triggering the picker.
                    navController.navigate("next_departure/$stopId/$routeId/$viewId") {
                        popUpTo("direction_picker/{stopId}/{primaryRouteId}/{primaryViewId}") {
                            inclusive = true
                        }
                    }
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
                        routeDataService.getSupportedRoutes()
                            .flatMap { routeDataService.getRouteViews(it, dayType) }
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
                onDaySchedule = { routeId, direction, variantLabel, overrideDayType, mergedLabel ->
                    val label = variantLabel ?: "all"
                    val dayTypeParam = overrideDayType?.name ?: "none"
                    val base = "day_schedule/$routeId/${stop.id}/$direction/$label/$dayTypeParam"
                    if (mergedLabel != null) {
                        navController.navigate("$base?mergedLabel=${android.net.Uri.encode(mergedLabel)}")
                    } else {
                        navController.navigate(base)
                    }
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
                routeDataService.getRouteViews(routeId, todayDayType).ifEmpty {
                    sequenceOf(DayType.WEEKDAY, DayType.SATURDAY, DayType.SUNDAY)
                        .map { routeDataService.getRouteViews(routeId, it) }
                        .firstOrNull { it.isNotEmpty() }
                        ?: emptyList()
                }
            }

            RouteMapScreen(
                route = route,
                views = views,
                initialViewId = initialViewId,
                onBack = { navController.popBackStack() },
                onStopSelected = { stop, viewId ->
                    if (GuidedModePrefs.isGuidedModeEnabled()) {
                        coroutineScope.launch {
                            val resolved = DeparturesService(activity).resolveDirectionIfUnambiguous(stop, routes, route.id)
                            if (resolved != null) {
                                navController.navigate("next_departure/${stop.id}/${resolved.first}/${resolved.second}")
                            } else {
                                navController.navigate("direction_picker/${stop.id}/${route.id}/$viewId")
                            }
                        }
                    } else {
                        navController.navigate("next_departure/${stop.id}/${route.id}/$viewId")
                    }
                }
            )
        }

        composable("all_routes/{routeId}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val route = routes.find { it.id == routeId } ?: return@composable

            val routeEntries = remember(routeId) {
                routeDataService.getRouteEntries(routeId)
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
                        if (GuidedModePrefs.isGuidedModeEnabled()) {
                            coroutineScope.launch {
                                val resolved = DeparturesService(activity).resolveDirectionIfUnambiguous(stop, routes, route.id)
                                if (resolved != null) {
                                    navController.navigate("next_departure/${stop.id}/${resolved.first}/${resolved.second}")
                                } else {
                                    navController.navigate("direction_picker/${stop.id}/${route.id}/$viewId")
                                }
                            }
                        } else {
                            navController.navigate("next_departure/${stop.id}/${route.id}/$viewId")
                        }
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
                routeDataService.getRouteEntries(routeId)
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
                        if (GuidedModePrefs.isGuidedModeEnabled()) {
                            coroutineScope.launch {
                                val resolved = DeparturesService(activity).resolveDirectionIfUnambiguous(stop, routes, route.id)
                                if (resolved != null) {
                                    navController.navigate("next_departure/${stop.id}/${resolved.first}/${resolved.second}")
                                } else {
                                    navController.navigate("direction_picker/${stop.id}/${route.id}/$viewId")
                                }
                            }
                        } else {
                            navController.navigate("next_departure/${stop.id}/${route.id}/$viewId")
                        }
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

        composable(
            "day_schedule/{routeId}/{stopId}/{direction}/{variantLabel}/{dayTypeOverride}?mergedLabel={mergedLabel}",
            arguments = listOf(navArgument("mergedLabel") { nullable = true; defaultValue = null; type = NavType.StringType })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val direction = backStackEntry.arguments?.getString("direction") ?: return@composable
            val variantLabel = backStackEntry.arguments?.getString("variantLabel")
            val dayTypeOverrideName = backStackEntry.arguments?.getString("dayTypeOverride")?.takeIf { it != "none" }
            val mergedLabel = backStackEntry.arguments?.getString("mergedLabel")
            val route = routes.find { it.id == routeId } ?: return@composable

            val stop = listOf(DayType.WEEKDAY, DayType.SATURDAY, DayType.SUNDAY)
                .firstNotNullOfOrNull { dayType ->
                    routeDataService.getRouteViews(routeId, dayType)
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
                mergedDirectionLabel = mergedLabel,
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

    if (showAlertDetail) {
        ModalBottomSheet(
            onDismissRequest = { showAlertDetail = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AlertDetailSheet(alerts = activeAlerts)
        }
    }
}

@Composable
private fun AlertDetailSheet(alerts: List<ServiceAlert>) {
    val sorted = alerts.sortedBy { mapOf("critical" to 0, "warning" to 1, "info" to 2)[it.severity] ?: 3 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Avisos de servicio",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )

        sorted.forEachIndexed { index, alert ->
            AlertDetailCard(alert)
            if (index < sorted.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun AlertDetailCard(alert: ServiceAlert) {
    val color = when (alert.severity) {
        "critical" -> androidx.compose.ui.graphics.Color(0xFFB00020)
        "warning"  -> androidx.compose.ui.graphics.Color(0xFFE65100)
        else       -> MaterialTheme.colorScheme.primary
    }
    val severityLabel = when (alert.severity) {
        "critical" -> "URGENTE"
        "warning"  -> "AVISO"
        else       -> "INFORMACIÓN"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (alert.severity == "info") Icons.Default.Info else Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Text(severityLabel, style = MaterialTheme.typography.labelSmall, color = color)
        }

        Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Text(
            text = alert.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val dateRange = formatAlertDateRange(alert.startsAt, alert.endsAt)
        if (dateRange.isNotEmpty()) {
            Text(
                text = dateRange,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val routes = alert.affectedRoutes
        if (!routes.isNullOrEmpty()) {
            Text(
                text = "Líneas: ${routes.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatAlertDateRange(startsAt: String, endsAt: String): String {
    return try {
        val zone = java.time.ZoneId.of("Europe/Madrid")
        val dateFmt = java.time.format.DateTimeFormatter.ofPattern(
            "d MMM yyyy", java.util.Locale("es", "ES")
        )
        val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val start = java.time.Instant.parse(startsAt).atZone(zone)
        val end = java.time.Instant.parse(endsAt).atZone(zone)
        if (start.toLocalDate() == end.toLocalDate()) {
            "${start.format(dateFmt)}, ${start.format(timeFmt)} – ${end.format(timeFmt)}"
        } else {
            "${start.format(dateFmt)} ${start.format(timeFmt)} – ${end.format(dateFmt)} ${end.format(timeFmt)}"
        }
    } catch (_: Exception) {
        ""
    }
}
