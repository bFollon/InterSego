/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.R
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.RouteView

/**
 * Screen displaying bus stops along a route with visual route line.
 *
 * Renders route views with support for:
 * - Tab/chip selector for route types (e.g., Regular vs Circular)
 * - Direction swap button in the top bar
 * - Extended route section with outline and label
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteStopsScreen(
    route: BusRoute,
    views: List<RouteView>,
    onBack: () -> Unit,
    onStopSelected: (BusStop, String) -> Unit  // stop, viewId
) {
    var currentViewId by remember { mutableStateOf(views.first().id) }
    val viewById = remember(views) { views.associateBy { it.id } }
    val currentView = viewById[currentViewId] ?: views.first()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Linea ${route.number}")
                        Text(
                            text = currentView.label,
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
                    currentView.swapAction?.let { swap ->
                        IconButton(onClick = {
                            currentViewId = swap.targetViewId
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Cambiar dirección"
                            )
                        }
                    }
                },
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
            // Tab/chip row
            currentView.tabs?.let { tabs ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { tab ->
                        // A tab is selected if the current view matches it,
                        // or if the current view's swap target matches it
                        // (e.g., reversed direction still highlights the "Regular" tab)
                        val isSelected = currentViewId == tab.viewId ||
                                currentView.swapAction?.targetViewId == tab.viewId
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) {
                                    currentViewId = tab.viewId
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }

            // Stop list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Build items with extended section separator
                val stops = currentView.stops
                val extendedLabel = currentView.extendedSectionLabel
                val hasExtendedStops = extendedLabel != null && stops.any { it.isExtendedOnly }

                itemsIndexed(stops) { index, viewStop ->
                    // Check if we need the extended section separator at the boundary
                    if (hasExtendedStops && index > 0) {
                        val prevIsExtended = stops[index - 1].isExtendedOnly
                        val currIsExtended = viewStop.isExtendedOnly
                        if (prevIsExtended != currIsExtended) {
                            ExtendedSectionSeparator(label = extendedLabel!!)
                        }
                    }

                    StopRow(
                        stop = viewStop.stop,
                        isExtended = viewStop.isExtendedOnly,
                        isFirst = index == 0,
                        isLast = index == stops.lastIndex,
                        onClick = {
                            onStopSelected(viewStop.stop, currentView.id)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    stop: BusStop,
    isExtended: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val lineColor = if (isExtended) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left side: Route line indicator
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(80.dp)
        ) {
            when {
                isFirst -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_route_start_chevron),
                        contentDescription = "Inicio",
                        tint = lineColor,
                        modifier = Modifier
                            .size(24.dp)
                            .offset(x = 8.dp, y = 12.dp)
                    )
                    if (isExtended) {
                        DashedVerticalLine(
                            color = lineColor,
                            modifier = Modifier
                                .width(4.dp)
                                .height(80.dp)
                                .offset(x = 18.dp, y = 28.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(80.dp)
                                .offset(x = 18.dp, y = 28.dp)
                                .background(lineColor)
                        )
                    }
                }
                isLast -> {
                    if (isExtended) {
                        DashedVerticalLine(
                            color = lineColor,
                            modifier = Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .offset(x = 18.dp, y = 0.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .offset(x = 18.dp, y = 0.dp)
                                .background(lineColor)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .offset(x = 12.dp, y = 20.dp)
                            .background(lineColor)
                    )
                }
                else -> {
                    // Line above
                    if (isExtended) {
                        DashedVerticalLine(
                            color = lineColor,
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .offset(x = 18.dp, y = 0.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .offset(x = 18.dp, y = 0.dp)
                                .background(lineColor)
                        )
                    }
                    // Line below (drawn before dot so dot renders on top)
                    if (isExtended) {
                        DashedVerticalLine(
                            color = lineColor,
                            modifier = Modifier
                                .width(4.dp)
                                .height(80.dp)
                                .offset(x = 18.dp, y = 28.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(80.dp)
                                .offset(x = 18.dp, y = 28.dp)
                                .background(lineColor)
                        )
                    }
                    // Stop dot (drawn last to cover dashed lines)
                    if (isExtended) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .offset(x = 12.dp, y = 20.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = CircleShape
                                )
                                .border(3.dp, lineColor, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .offset(x = 12.dp, y = 20.dp)
                                .background(
                                    color = lineColor,
                                    shape = CircleShape
                                )
                        )
                    }
                }
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
            if (stop.area != null) {
                Text(
                    text = stop.area,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashedVerticalLine(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawBehind {
            val dashLength = 6.dp.toPx()
            val gapLength = 4.dp.toPx()
            drawLine(
                color = color,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = size.width,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(dashLength, gapLength),
                    0f
                )
            )
        }
    )
}

@Composable
private fun ExtendedSectionSeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
