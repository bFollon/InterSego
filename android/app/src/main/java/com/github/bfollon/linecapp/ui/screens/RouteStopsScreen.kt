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

package com.github.bfollon.linecapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bfollon.linecapp.data.BusRoute
import com.github.bfollon.linecapp.data.BusStop
import com.github.bfollon.linecapp.services.pdfparsing.strategies.M4Parser

/**
 * Screen displaying bus stops along a route with visual route line.
 *
 * Shows a linear representation of all stops on the route with a vertical line
 * connecting them. Users can toggle between regular and reverse directions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteStopsScreen(
    route: BusRoute,
    onBack: () -> Unit,
    onStopSelected: (BusStop) -> Unit
) {
    // State for direction toggle (true = regular, false = reverse)
    var isRegularDirection by remember { mutableStateOf(true) }

    // Get the appropriate stop list based on direction
    val stops = if (isRegularDirection) {
        M4Parser.m4RegularRoute
    } else {
        M4Parser.m4ReverseRoute
    }

    // Determine direction labels
    val directionLabel = if (isRegularDirection) {
        "La Lastrilla → El Sotillo"
    } else {
        "El Sotillo → La Lastrilla"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Línea ${route.number}")
                        Text(
                            text = directionLabel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    // Direction toggle button
                    IconButton(
                        onClick = { isRegularDirection = !isRegularDirection }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Cambiar dirección"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Add top spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            itemsIndexed(stops) { index, stop ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStopSelected(stop) },
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left side: Route line indicator
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(80.dp) // Fixed height for proper spacing
                    ) {
                        // Vertical line coming from above (except first item)
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(24.dp) // Line from top to circle
                                    .offset(x = 18.dp, y = 0.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        // Circle aligned with stop name
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .offset(x = 12.dp, y = 20.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )

                        // Vertical line going down (except last item)
                        if (index < stops.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(80.dp) // Line from circle to bottom and beyond
                                    .offset(x = 18.dp, y = 28.dp) // Start below circle
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    // Right side: Stop information
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = stop.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stop.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Add bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

