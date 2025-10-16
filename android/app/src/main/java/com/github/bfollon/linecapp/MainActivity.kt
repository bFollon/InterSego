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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.bfollon.linecapp.repositories.PDFURLRepository
import com.github.bfollon.linecapp.services.CoordinateCache
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.NetworkMonitor
import com.github.bfollon.linecapp.ui.screens.MainScreen
import com.github.bfollon.linecapp.ui.theme.LineCappTheme
import kotlinx.coroutines.launch

/**
 * Main activity for LineCapp.
 *
 * Initializes services and sets up the Compose UI with navigation.
 */
class MainActivity : ComponentActivity() {
    private var isInitialized by mutableStateOf(false)

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
 *
 * Currently minimal - will be expanded in future phases with:
 * - Splash screen
 * - Route selection
 * - Timetable display
 * - Settings/About modals
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen()
        }
    }
}
