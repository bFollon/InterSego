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

package com.github.bfollon.linecapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.bfollon.linecapp.data.BusRoute
import com.github.bfollon.linecapp.data.RouteType
import com.github.bfollon.linecapp.repositories.PDFURLRepository
import com.github.bfollon.linecapp.services.CoordinateCache
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.NetworkMonitor
import com.github.bfollon.linecapp.services.PDFCacheManager
import com.github.bfollon.linecapp.ui.screens.MainScreen
import com.github.bfollon.linecapp.ui.screens.RouteSelectionScreen
import com.github.bfollon.linecapp.ui.screens.RouteStopsScreen
import com.github.bfollon.linecapp.ui.screens.TimetableScreen
import com.github.bfollon.linecapp.ui.theme.LineCappTheme
import kotlinx.coroutines.launch

/**
 * Main activity for LineCapp.
 *
 * Initializes services and sets up the Compose UI with navigation.
 */
class MainActivity : ComponentActivity() {
    private var isInitialized by mutableStateOf(false)

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

        DebugConfig.debugPrint("🚀 LineCapp starting...")

        // Initialize network monitor
        NetworkMonitor.initialize(this)

        // Initialize caches and cleanup expired entries
        CoordinateCache.initialize(this)

        // Cleanup expired cache entries on app start
        CoordinateCache.cleanupExpiredEntries()

        // Initialize PDF Cache Manager
        val pdfCacheManager = PDFCacheManager.getInstance(this)
        pdfCacheManager.initialize()

        // Initialize PDF URL repository and scrape URLs
        val pdfUrlRepository = PDFURLRepository.getInstance(this)
        lifecycleScope.launch {
            DebugConfig.debugPrint("🌐 Initializing PDF URLs...")
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
            isInitialized = true
            DebugConfig.debugPrint("✅ Services initialized")
        }

        setContent {
            LineCappTheme {
                if (isInitialized) {
                    AppNavigation()
                } else {
                    LoadingScreen()
                }
            }
        }
    }
}

/**
 * Loading screen shown during app initialization (URL scraping, etc.)
 */
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Inicializando LineCapp...")
        }
    }
}

/**
 * App navigation structure.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Get list of routes once
    val context = androidx.compose.ui.platform.LocalContext.current
    val routes = remember {
        (context as MainActivity).getKnownRoutes()
    }

    NavHost(
        navController = navController,
        startDestination = "route_selection"
    ) {
        composable("route_selection") {
            RouteSelectionScreen(
                routes = routes,
                onRouteSelected = { route ->
                    navController.navigate("route_stops/${route.id}")
                }
            )
        }

        composable("route_stops/{routeId}") { backStackEntry ->
            val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
            val route = routes.find { it.id == routeId } ?: return@composable

            RouteStopsScreen(
                route = route,
                onBack = {
                    navController.popBackStack()
                },
                onStopSelected = { stop ->
                    // TODO: Navigate to stop-specific timetable screen
                    // For now, navigate to the old timetable screen
                    navController.navigate("timetable/${route.id}")
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
    }
}
