/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.services.*
import com.github.bfollon.intersego.services.RouteLoadedData
import com.github.bfollon.intersego.services.DeparturesService
import java.time.LocalTime
import java.util.*

data class DirectionOption(
    val routeId: String,
    val route: BusRoute,
    val direction: String,
    val viewId: String,
)

data class RouteDirectionGroup(
    val route: BusRoute,
    val directions: List<DirectionOption>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionPickerScreen(
    stopId: String,
    primaryRouteId: String?,
    primaryViewId: String?,
    stop: BusStop,
    allRoutes: List<BusRoute>,
    referenceDate: Calendar = Calendar.getInstance(),
    onDirectionSelected: (routeId: String, viewId: String) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var directionGroups by remember { mutableStateOf<List<RouteDirectionGroup>>(emptyList()) }
    var showTutorial by remember {
        val shown = GuidedModePrefs.isTutorialShown()
        mutableStateOf(!shown)
    }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val departuresService = DeparturesService(context)
            val departuresData = departuresService.loadDepartures(stop, allRoutes, primaryRouteId, referenceDate)

            // Build direction groups from the loaded routes
            val groups = mutableListOf<RouteDirectionGroup>()
            for (routeData in departuresData.routes) {
                // If this route uses mergedDirectionLabel (e.g. M4 circular), collapse to a
                // single option using the primary view — triggering auto-advance below.
                val primaryMergedView = routeData.views.firstOrNull { it.mergedDirectionLabel != null }
                if (primaryMergedView != null) {
                    val option = DirectionOption(
                        routeId = routeData.route.id,
                        route = routeData.route,
                        direction = primaryMergedView.direction ?: "",
                        viewId = primaryMergedView.id
                    )
                    groups.add(RouteDirectionGroup(route = routeData.route, directions = listOf(option)))
                    continue
                }

                // Normal flow: one option per distinct direction serving this stop
                val validDirections = routeData.timetables
                    .filter { it.stopId == stop.id }
                    .mapNotNull { it.direction }
                    .distinct()

                if (validDirections.isNotEmpty()) {
                    val dirOptions = validDirections.mapNotNull { direction ->
                        val view = routeData.views.find { it.direction == direction }
                        if (view != null) {
                            DirectionOption(
                                routeId = routeData.route.id,
                                route = routeData.route,
                                direction = direction,
                                viewId = view.id
                            )
                        } else {
                            null
                        }
                    }
                    if (dirOptions.isNotEmpty()) {
                        groups.add(RouteDirectionGroup(route = routeData.route, directions = dirOptions))
                    }
                }
            }

            directionGroups = groups

            if (groups.isEmpty()) {
                error = "No hay salidas disponibles para esta parada."
            }
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to load directions", e)
            error = "No se pudieron cargar las direcciones disponibles."
        } finally {
            loading = false
        }
    }

    if (showTutorial) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                GuidedModePrefs.setTutorialShown()
                showTutorial = false
            },
            sheetState = sheetState
        ) {
            GuidedModeTutorialSheet(onDismiss = {
                GuidedModePrefs.setTutorialShown()
                showTutorial = false
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stop.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Volver")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item {
                    Text(
                        text = "¿A dónde vas?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                directionGroups.forEach { group ->
                    item {
                        Surface(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Línea ${group.route.number}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items(group.directions) { option ->
                        val isLastSelected = GuidedModePrefs.getLastViewId(stopId, option.routeId) == option.viewId
                        DirectionPickerItem(
                            direction = destinationFromDirection(option.direction),
                            isLastSelected = isLastSelected,
                            onClick = {
                                AnalyticsService.track("direction_picker_used", mapOf("remembered" to isLastSelected))
                                GuidedModePrefs.saveLastViewId(stopId, option.routeId, option.viewId)
                                onDirectionSelected(option.routeId, option.viewId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionPickerItem(
    direction: String,
    isLastSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = direction,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        if (isLastSelected) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "Última",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
    Divider()
}

private fun destinationFromDirection(direction: String): String {
    return direction.split("→").lastOrNull()?.trim() ?: direction
}
