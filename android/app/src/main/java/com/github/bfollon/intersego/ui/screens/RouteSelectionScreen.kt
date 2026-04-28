/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.bfollon.intersego.ui.theme.SuccessGreen
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.services.RouteDataService

/**
 * Screen for selecting a bus route from the list of available routes.
 *
 * Displays all metropolitan routes (M1-M8) with status indicators showing
 * which routes have parsers implemented.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSelectionScreen(
    routes: List<BusRoute>,
    routeDataService: RouteDataService,
    onRouteSelected: (BusRoute) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("InterSego - Segovia") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header text
            Text(
                text = "Selecciona una línea",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // Route list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val sortedRoutes = routes.sortedWith(
                    compareByDescending<BusRoute> { routeDataService.hasParserFor(it.id) }
                        .thenBy { it.number }
                )
                items(sortedRoutes) { route ->
                    val isAvailable = routeDataService.hasParserFor(route.id)
                    RouteCard(
                        route = route,
                        isAvailable = isAvailable,
                        onClick = {
                            if (isAvailable) {
                                onRouteSelected(route)
                            }
                        }
                    )
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Card displaying a single bus route with status indicator.
 *
 * @param route The bus route to display
 * @param isAvailable Whether the route has a parser implemented
 * @param onClick Callback when the route is tapped
 */
@Composable
fun RouteCard(
    route: BusRoute,
    isAvailable: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = isAvailable,
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isAvailable) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Route info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Route number
                Text(
                    text = route.number,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Route name
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isAvailable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }

            // Right side: Status indicator
            StatusIndicator(isAvailable = isAvailable)
        }
    }
}

/**
 * Status indicator showing whether a route is available or not.
 *
 * @param isAvailable Whether the route has a parser implemented
 */
@Composable
fun StatusIndicator(isAvailable: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isAvailable) {
                Icons.Default.CheckCircle
            } else {
                Icons.Default.Warning
            },
            contentDescription = if (isAvailable) "Disponible" else "No disponible",
            tint = if (isAvailable) {
                SuccessGreen
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )

        if (!isAvailable) {
            Text(
                text = "Próximamente",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
