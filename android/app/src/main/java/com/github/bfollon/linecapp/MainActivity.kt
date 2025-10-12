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
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.bfollon.linecapp.services.CoordinateCache
import com.github.bfollon.linecapp.services.DebugConfig
import com.github.bfollon.linecapp.services.NetworkMonitor
import com.github.bfollon.linecapp.ui.screens.MainScreen
import com.github.bfollon.linecapp.ui.theme.LineCappTheme

/**
 * Main activity for LineCapp.
 *
 * Initializes services and sets up the Compose UI with navigation.
 */
class MainActivity : ComponentActivity() {
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

        DebugConfig.debugPrint("✅ Services initialized")

        setContent {
            LineCappTheme {
                AppNavigation()
            }
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
