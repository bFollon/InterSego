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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.github.bfollon.intersego.ui.screens.DayScheduleScreen
import com.github.bfollon.intersego.ui.screens.NextDepartureScreen
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
                name = "Línea Metropolitana 1",
                origin = "Segovia",
                destination = "Área Metropolitana",
                pdfURL = "", // Will be resolved by PDFURLRepository
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M2",
                number = "M2",
                name = "Línea Metropolitana 2",
                origin = "Segovia",
                destination = "Área Metropolitana",
                pdfURL = "",
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M3",
                number = "M3",
                name = "Línea Metropolitana 3",
                origin = "Segovia",
                destination = "Área Metropolitana",
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
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M5",
                number = "M5",
                name = "Línea Metropolitana 5",
                origin = "Segovia",
                destination = "Área Metropolitana",
                pdfURL = "",
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M6",
                number = "M6",
                name = "Línea Metropolitana 6",
                origin = "Segovia",
                destination = "Área Metropolitana",
                pdfURL = "",
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M7",
                number = "M7",
                name = "Línea Metropolitana 7",
                origin = "Segovia",
                destination = "Área Metropolitana",
                pdfURL = "",
                routeType = RouteType.URBAN
            ),
            BusRoute(
                id = "M8",
                number = "M8",
                name = "Línea Metropolitana 8",
                origin = "Segovia",
                destination = "Área Metropolitana",
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
    val iconGreen = Color(0xFF60A37F)
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

    NavHost(
        navController = navController,
        startDestination = "route_selection"
    ) {
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

            // Determine current day type for variant filtering
            val currentDayType = remember {
                when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
                    java.util.Calendar.SATURDAY -> DayType.SATURDAY
                    java.util.Calendar.SUNDAY -> DayType.SUNDAY
                    else -> DayType.WEEKDAY
                }
            }

            // Get route views from parser dynamically
            val views = pdfProcessingService.getRouteViews(routeId, currentDayType)

            RouteStopsScreen(
                route = route,
                views = views,
                onBack = {
                    navController.popBackStack()
                },
                onStopSelected = { stop, direction, departureLabel ->
                    val label = departureLabel ?: "all"
                    navController.navigate("next_departure/${route.id}/${stop.id}/$direction/$label")
                }
            )
        }

        composable("next_departure/{routeId}/{stopId}/{direction}/{variantLabel}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val direction = backStackEntry.arguments?.getString("direction") ?: return@composable
            val variantLabel = backStackEntry.arguments?.getString("variantLabel")
            val route = routes.find { it.id == routeId } ?: return@composable

            // Find the stop from all views by ID (dynamic lookup across day types)
            val stop = listOf(DayType.WEEKDAY, DayType.SATURDAY, DayType.SUNDAY)
                .firstNotNullOfOrNull { dayType ->
                    pdfProcessingService.getRouteViews(routeId, dayType)
                        .flatMap { it.stops }
                        .find { it.stop.id == stopId }
                        ?.stop
                } ?: return@composable

            // "all" means show all departures without variant badges
            val effectiveVariantLabel = if (variantLabel == "all") null else variantLabel

            NextDepartureScreen(
                route = route,
                stop = stop,
                direction = direction,
                selectedVariantLabel = effectiveVariantLabel,
                onBack = {
                    navController.popBackStack()
                },
                onDaySchedule = {
                    val label = effectiveVariantLabel ?: "all"
                    navController.navigate("day_schedule/${route.id}/${stop.id}/$direction/$label")
                }
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

        composable("day_schedule/{routeId}/{stopId}/{direction}/{variantLabel}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val stopId = backStackEntry.arguments?.getString("stopId") ?: return@composable
            val direction = backStackEntry.arguments?.getString("direction") ?: return@composable
            val variantLabel = backStackEntry.arguments?.getString("variantLabel")
            val route = routes.find { it.id == routeId } ?: return@composable

            val stop = listOf(DayType.WEEKDAY, DayType.SATURDAY, DayType.SUNDAY)
                .firstNotNullOfOrNull { dayType ->
                    pdfProcessingService.getRouteViews(routeId, dayType)
                        .flatMap { it.stops }
                        .find { it.stop.id == stopId }
                        ?.stop
                } ?: return@composable

            val effectiveVariantLabel = if (variantLabel == "all") null else variantLabel

            DayScheduleScreen(
                route = route,
                stop = stop,
                direction = direction,
                selectedVariantLabel = effectiveVariantLabel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
