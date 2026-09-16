/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.theme

import androidx.compose.ui.graphics.Color

// Bus-themed colors - Blue for urban transit
val BusBlue = Color(0xFF1976D2)       // Primary blue for buses
val BusLightBlue = Color(0xFF42A5F5)  // Lighter blue for accents

// Secondary colors - Orange for routes/schedules
val RouteOrange = Color(0xFFFF9800)   // Route highlighting
val RouteLightOrange = Color(0xFFFFB74D)

// Semantic UI colors
val SuccessGreen = Color(0xFF4CAF50)
val WarningOrange = Color(0xFFFFA726)

// Service alert severity (see DESIGN.md's documented alert-critical/warning/info palette;
// warning reuses RouteOrange below since it already matches that value; info uses
// MaterialTheme.colorScheme.primary directly at the call site for Dynamic Color awareness)
val AlertCritical = Color(0xFFD32F2F)

// DESIGN.md's "confirmation-green" (#34C759, iOS-system green) - distinct from SuccessGreen above
val ConfirmationGreen = Color(0xFF34C759)

// "InterSego" wordmark gradient (splash screen, Landing header, About screen). The second stop
// was previously a hard-coded #007AFF (Apple's system blue) duplicated across all 3 call sites -
// not a documented token. Now reuses the app's own transit-blue primary instead.
val WordmarkGradient = listOf(ConfirmationGreen, BusBlue)

// LaLiga IP-blocking banner + its detail sheet (see docs/LALIGA_BLOCKING_BANNER_RUNBOOK.md) -
// previously an identical literal duplicated in LandingScreen.kt and MainActivity.kt.
val LaLigaOrange = Color(0xFFE65100)
