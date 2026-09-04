/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bfollon.intersego.data.ServiceAlert
import com.github.bfollon.intersego.services.MainLandingAction

private val GreenTint = Color(0xFF34C759).copy(alpha = 0.1f)
private val GreenBorder = Color(0xFF34C759).copy(alpha = 0.3f)
private val GreenConfirmed = Color(0xFF34C759)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    onMainCardClick: () -> Unit,
    mainAction: MainLandingAction = MainLandingAction.ROUTES,
    onFindClosestStop: () -> Unit = {},
    onShowAbout: () -> Unit = {},
    onShowSettings: () -> Unit = {},
    onNavigateToOtrasOpciones: () -> Unit = {},
    onBoardBus: () -> Unit = {},
    isSearchingClosestStop: Boolean = false,
    closestStopError: String? = null,
    isBoardingBus: Boolean = false,
    boardingBusConfirmed: Boolean = false,
    boardingBusError: String? = null,
    activeAlerts: List<ServiceAlert> = emptyList(),
    onShowAlertDetail: () -> Unit = {},
) {
    val gradientColors = listOf(Color(0xFF34C759), Color(0xFF007AFF))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                val primary = alertsSortedBySeverity(activeAlerts).firstOrNull()
                if (primary != null) {
                    AlertPill(
                        alert = primary,
                        extraCount = activeAlerts.size - 1,
                        onClick = onShowAlertDetail,
                    )
                }
            },
            actions = {
                IconButton(onClick = onShowSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        Spacer(modifier = Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "InterSego",
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(gradientColors)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Metropolitanos de Segovia",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Full-width: Parada más cercana
            HorizontalLandingCard(
                label = "Parada más cercana",
                showChevron = true,
                isLoading = isSearchingClosestStop,
                onClick = onFindClosestStop,
                enabled = !isSearchingClosestStop
            ) {
                if (isSearchingClosestStop) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Full-width: Estoy en el autobús
            HorizontalLandingCard(
                label = if (boardingBusConfirmed) "¡Gracias por confirmar!" else "Estoy en el autobús",
                labelColor = if (boardingBusConfirmed) GreenConfirmed else null,
                showChevron = false,
                isLoading = isBoardingBus,
                onClick = onBoardBus,
                enabled = !isBoardingBus && !boardingBusConfirmed
            ) {
                if (isBoardingBus) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.DirectionsBus,
                        contentDescription = null,
                        tint = if (boardingBusConfirmed) GreenConfirmed else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 2-column square grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SquareLandingCard(
                    label = mainAction.label,
                    onClick = onMainCardClick,
                    modifier = Modifier.weight(1f)
                ) {
                    MainLandingActionIcon(mainAction)
                }

                SquareLandingCard(
                    label = "Más opciones",
                    onClick = onNavigateToOtrasOpciones,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp).padding(8.dp)
                    )
                }
            }

            if (closestStopError != null) {
                Text(
                    text = closestStopError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
            if (boardingBusError != null) {
                Text(
                    text = boardingBusError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onShowAbout,
            modifier = Modifier.navigationBarsPadding()
        ) {
            Text(
                text = "Acerca de",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun HorizontalLandingCard(
    label: String,
    showChevron: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    labelColor: Color? = null,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GreenTint)
            .border(1.dp, GreenBorder, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = labelColor ?: if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun SquareLandingCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun MainLandingActionIcon(action: MainLandingAction, size: Int = 40) {
    when (action) {
        MainLandingAction.ROUTES -> BusLineIcon(size = size)
        MainLandingAction.ROUTE_PLANNER -> Icon(
            imageVector = Icons.Filled.Route,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size.dp).padding(8.dp)
        )
        MainLandingAction.REMINDERS -> Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size.dp).padding(8.dp)
        )
        MainLandingAction.ANOTHER_DAY -> Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size.dp).padding(8.dp)
        )
    }
}

@Composable
internal fun BusLineIcon(size: Int) {
    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(size.dp)) {
        drawBusLineIcon(color)
    }
}

private fun alertsSortedBySeverity(alerts: List<ServiceAlert>): List<ServiceAlert> {
    val order = mapOf("critical" to 0, "warning" to 1, "info" to 2)
    return alerts.sortedBy { order[it.severity] ?: 3 }
}

@Composable
private fun AlertPill(
    alert: ServiceAlert,
    extraCount: Int,
    onClick: () -> Unit,
) {
    val color = alertColor(alert.severity)
    val icon = alertIcon(alert.severity)
    val label = if (extraCount > 0) "${alert.title} (+$extraCount)" else alert.title

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun alertColor(severity: String): Color = when (severity) {
    "critical" -> Color(0xFFB00020)
    "warning"  -> Color(0xFFE65100)
    else       -> Color(0xFF1565C0)
}

private fun alertIcon(severity: String): ImageVector = when (severity) {
    "critical", "warning" -> Icons.Default.Warning
    else                  -> Icons.Default.Info
}

private fun DrawScope.drawBusLineIcon(color: Color) {
    val midY = size.height / 2
    val lineStartX = size.width * 0.05f
    val lineEndX = size.width * 0.95f
    val lineWidth = size.width * 0.07f
    val stopRadius = size.width * 0.07f

    // Horizontal line
    drawLine(
        color = color,
        start = Offset(lineStartX, midY),
        end = Offset(lineEndX, midY),
        strokeWidth = lineWidth,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )

    // 4 evenly distributed stops
    val stopXRatios = listOf(0.05f, 0.368f, 0.632f, 0.95f)
    for (xRatio in stopXRatios) {
        drawCircle(
            color = color,
            radius = stopRadius,
            center = Offset(size.width * xRatio, midY)
        )
    }
}
