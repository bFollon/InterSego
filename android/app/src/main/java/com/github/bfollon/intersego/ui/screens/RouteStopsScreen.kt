/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.R
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.RouteView
import com.github.bfollon.intersego.services.AnalyticsService

/**
 * Screen displaying bus stops along a route with visual route line.
 *
 * Renders route views with support for:
 * - "Ver todas las rutas" button for navigating to AllRoutesScreen
 * - Tab/chip selector for route types
 * - Direction swap button in the top bar
 * - Extended route section with outline and label
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteStopsScreen(
    route: BusRoute,
    views: List<RouteView>,
    initialViewId: String? = null,
    onAllRoutesSelected: (() -> Unit)? = null,
    onBack: () -> Unit,
    onStopSelected: (BusStop, String) -> Unit,  // stop, viewId
    onMapSelected: (String) -> Unit             // viewId
) {
    val noServiceToday = views.isEmpty()

    var currentViewId by rememberSaveable(views) {
        mutableStateOf(initialViewId ?: views.firstOrNull()?.id ?: "")
    }
    val viewById = remember(views) { views.associateBy { it.id } }
    val currentView = viewById[currentViewId] ?: views.firstOrNull()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Linea ${route.number}")
                        if (currentView != null) {
                            Text(
                                text = currentView.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
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
                    if (currentView != null) {
                        IconButton(onClick = {
                            AnalyticsService.track("map_opened", mapOf("route" to route.id, "mode" to "single"))
                            onMapSelected(currentViewId)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Map,
                                contentDescription = "Ver en mapa"
                            )
                        }
                        currentView.swapAction?.let { swap ->
                            IconButton(onClick = {
                                AnalyticsService.track("direction_swapped", mapOf("screen" to "route_stops"))
                                currentViewId = swap.targetViewId
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.SwapVert,
                                    contentDescription = "Cambiar dirección"
                                )
                            }
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
            if (!noServiceToday) {
                // Tab/chip row (with optional heading label above the chips)
                currentView?.tabs?.let { tabs ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        currentView.tabsLabel?.let { heading ->
                            Text(
                                text = heading,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tabs.forEach { tab ->
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
                }
            }

            // "Ver todas las rutas" button
            if (onAllRoutesSelected != null) {
                OutlinedButton(
                    onClick = onAllRoutesSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Ver todas las rutas")
                }
            }

            if (noServiceToday) {
                // No service today message
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Esta línea no ofrece servicio hoy",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // "Ruta de hoy" separator
                if (onAllRoutesSelected != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ruta de hoy",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // Stop list
            if (!noServiceToday && currentView != null) {
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
                                AnalyticsService.track("stop_selected", mapOf("stop" to viewStop.stop.id, "route" to route.id))
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

}

@Composable
internal fun StopRow(
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
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left side: Route line indicator
        StopIndicator(
            isFirst = isFirst,
            isLast = isLast,
            isExtended = isExtended,
            lineColor = lineColor
        )

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
internal fun DashedVerticalLine(
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

/**
 * Adaptive stop indicator: dots and lines whose positions are calculated from measured row height.
 * Replaces hardcoded offsets with dynamic calculations.
 */
@Composable
private fun StopIndicator(
    isFirst: Boolean,
    isLast: Boolean,
    isExtended: Boolean,
    lineColor: Color
) {
    val density = LocalDensity.current
    val containerWidth = 40.dp
    val dotSize = 16.dp
    val lineWidth = 4.dp
    val chevronSize = 24.dp

    var containerHeightPx by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .width(containerWidth)
            .fillMaxHeight()
            .onSizeChanged { containerHeightPx = it.height }
    ) {
        if (containerHeightPx > 0) {
            val h = with(density) { containerHeightPx.toDp() }

            // All positions derived from container height (no magic numbers)
            val dotX = (containerWidth - dotSize) / 2           // 12.dp
            val dotY = (h - dotSize) / 2                        // vertically centered
            val lineX = (containerWidth - lineWidth) / 2         // 18.dp
            val dotCenterY = h / 2
            val chevronX = (containerWidth - chevronSize) / 2   // 8.dp
            val chevronY = (h - chevronSize) / 2                // vertically centered

            when {
                isFirst -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_route_start_chevron),
                        contentDescription = "Inicio",
                        tint = lineColor,
                        modifier = Modifier
                            .size(chevronSize)
                            .offset(x = chevronX, y = chevronY)
                    )
                    if (isExtended) {
                        DashedVerticalLine(
                            color = lineColor,
                            modifier = Modifier
                                .width(lineWidth)
                                .fillMaxHeight()
                                .offset(x = lineX, y = dotCenterY)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(lineWidth)
                                .fillMaxHeight()
                                .offset(x = lineX, y = dotCenterY)
                                .background(lineColor)
                        )
                    }
                }
                isLast -> {
                    if (isExtended) {
                        DashedVerticalLine(
                            color = lineColor,
                            modifier = Modifier
                                .width(lineWidth)
                                .height(dotCenterY)
                                .offset(x = lineX)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(lineWidth)
                                .height(dotCenterY)
                                .offset(x = lineX)
                                .background(lineColor)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .offset(x = dotX, y = dotY)
                            .background(lineColor)
                    )
                }
                else -> {
                    // Line above (top to dot top)
                    if (isExtended) {
                        DashedVerticalLine(
                            color = lineColor,
                            modifier = Modifier
                                .width(lineWidth)
                                .height(dotY)
                                .offset(x = lineX)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(lineWidth)
                                .height(dotY)
                                .offset(x = lineX)
                                .background(lineColor)
                        )
                    }
                    // Line below (dot center to overflow)
                    if (isExtended) {
                        DashedVerticalLine(
                            color = lineColor,
                            modifier = Modifier
                                .width(lineWidth)
                                .fillMaxHeight()
                                .offset(x = lineX, y = dotCenterY)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(lineWidth)
                                .fillMaxHeight()
                                .offset(x = lineX, y = dotCenterY)
                                .background(lineColor)
                        )
                    }
                    // Dot (drawn last to render on top)
                    if (isExtended) {
                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .offset(x = dotX, y = dotY)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = CircleShape
                                )
                                .border(3.dp, lineColor, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .offset(x = dotX, y = dotY)
                                .background(lineColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExtendedSectionSeparator(label: String) {
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
