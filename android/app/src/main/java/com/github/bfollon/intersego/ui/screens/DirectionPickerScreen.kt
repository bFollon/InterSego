/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
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

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextAlign
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
            val departuresData = departuresService.loadDepartures(stop, allRoutes, primaryRouteId)

            // Build direction groups from the loaded routes
            val groups = mutableListOf<RouteDirectionGroup>()
            for (routeData in departuresData.routes) {
                // Collect all distinct directions for this route that serve this stop
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

            // Auto-advance if only one direction
            if (groups.size == 1 && groups[0].directions.size == 1) {
                val option = groups[0].directions[0]
                GuidedModePrefs.saveLastViewId(stopId, option.routeId, option.viewId)
                onDirectionSelected(option.routeId, option.viewId)
                return@LaunchedEffect
            }

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
                title = { Text("Selecciona dirección") },
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
                        text = stop.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }

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
                        Text(
                            text = "Línea ${group.route.number}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(group.directions) { option ->
                        DirectionPickerItem(
                            direction = option.direction,
                            isLastSelected = GuidedModePrefs.getLastViewId(stopId, option.routeId) == option.viewId,
                            onClick = {
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
