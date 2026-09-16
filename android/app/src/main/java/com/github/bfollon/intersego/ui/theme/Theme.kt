/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BusLightBlue,
    secondary = RouteLightOrange,
    tertiary = RouteLightOrange
)

private val LightColorScheme = lightColorScheme(
    primary = BusBlue,
    secondary = RouteOrange,
    tertiary = RouteOrange
)

@Composable
fun InterSegoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Warning/disclaimer accent color (times-disclaimer banners, seasonal notes). Material 3's
 * `ColorScheme` has no "warning" role, so - unlike `primary`/`secondary`, which come from
 * `MaterialTheme.colorScheme` and pick up Dynamic Color automatically - this needs its own
 * light/dark pairing, following the same lighter-in-dark-mode pattern already used for
 * `RouteOrange`/`RouteLightOrange`.
 */
@Composable
fun warningColor(): Color = if (isSystemInDarkTheme()) RouteLightOrange else WarningOrange
