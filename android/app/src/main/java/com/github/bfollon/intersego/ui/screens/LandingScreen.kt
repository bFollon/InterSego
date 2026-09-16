/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bfollon.intersego.data.ServiceAlert
import com.github.bfollon.intersego.services.AnalyticsService
import com.github.bfollon.intersego.services.LandingSlot
import com.github.bfollon.intersego.services.MainLandingAction
import com.github.bfollon.intersego.ui.theme.AlertCritical
import com.github.bfollon.intersego.ui.theme.ConfirmationGreen
import com.github.bfollon.intersego.ui.theme.LaLigaOrange
import com.github.bfollon.intersego.ui.theme.RouteOrange
import com.github.bfollon.intersego.ui.theme.WordmarkGradient
import kotlin.math.min

private val GreenTint = ConfirmationGreen.copy(alpha = 0.1f)
private val GreenBorder = ConfirmationGreen.copy(alpha = 0.3f)
private val GreenConfirmed = ConfirmationGreen

/** Resolves each pool action to its navigation callback. Shared with the "Más opciones" hub. */
internal data class LandingActionCallbacks(
    val onNavigateToRouteList: () -> Unit,
    val onPlanJourney: () -> Unit,
    val onShowReminders: () -> Unit,
    val onOpenAnotherDay: () -> Unit,
    val onShowFavorites: () -> Unit,
    val onBoardBus: () -> Unit,
) {
    fun forAction(action: MainLandingAction): () -> Unit = when (action) {
        MainLandingAction.ROUTES -> onNavigateToRouteList
        MainLandingAction.ROUTE_PLANNER -> onPlanJourney
        MainLandingAction.REMINDERS -> onShowReminders
        MainLandingAction.ANOTHER_DAY -> onOpenAnotherDay
        MainLandingAction.FAVORITES -> onShowFavorites
        MainLandingAction.BOARD_BUS -> onBoardBus
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    landingSlots: Map<LandingSlot, MainLandingAction> = LandingSlot.entries.associateWith { it.default },
    onFindClosestStop: () -> Unit = {},
    onShowAbout: () -> Unit = {},
    onShowSettings: () -> Unit = {},
    onNavigateToOtrasOpciones: () -> Unit = {},
    onNavigateToRouteList: () -> Unit = {},
    onPlanJourney: () -> Unit = {},
    onShowReminders: () -> Unit = {},
    onOpenAnotherDay: () -> Unit = {},
    onShowFavorites: () -> Unit = {},
    onBoardBus: () -> Unit = {},
    isSearchingClosestStop: Boolean = false,
    closestStopError: String? = null,
    isBoardingBus: Boolean = false,
    boardingBusConfirmed: Boolean = false,
    boardingBusError: String? = null,
    activeAlerts: List<ServiceAlert> = emptyList(),
    onShowAlertDetail: () -> Unit = {},
    laLigaBlockingSuspected: Boolean = false,
    onShowLaLigaDetail: () -> Unit = {},
    isFetchingManifest: Boolean = false,
) {
    val callbacks = LandingActionCallbacks(
        onNavigateToRouteList = onNavigateToRouteList,
        onPlanJourney = onPlanJourney,
        onShowReminders = onShowReminders,
        onOpenAnotherDay = onOpenAnotherDay,
        onShowFavorites = onShowFavorites,
        onBoardBus = onBoardBus,
    )
    val slot1 = landingSlots.getValue(LandingSlot.MAIN_1)
    val slot2 = landingSlots.getValue(LandingSlot.MAIN_2)
    val extra1 = landingSlots.getValue(LandingSlot.EXTRA_1)
    val extra2 = landingSlots.getValue(LandingSlot.EXTRA_2)
    val boardingBusVisible = MainLandingAction.BOARD_BUS in landingSlots.values
    val gradientColors = WordmarkGradient

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isFetchingManifest) {
                        BouncingBallLoader()
                    }
                    val primary = alertsSortedBySeverity(activeAlerts).firstOrNull()
                    if (primary != null) {
                        AlertPill(
                            alert = primary,
                            extraCount = activeAlerts.size - 1,
                            onClick = {
                                AnalyticsService.track("alert_banner_tapped", mapOf("severity" to primary.severity))
                                onShowAlertDetail()
                            },
                        )
                    }
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

        if (laLigaBlockingSuspected) {
            LaLigaBlockingBanner(
                onClick = {
                    AnalyticsService.track("laliga_blocking_banner_tapped")
                    onShowLaLigaDetail()
                },
            )
        }

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

            // 2-column square grid: Acción principal 1 + Acción principal 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LandingActionSquare(
                    action = slot1,
                    modifier = Modifier.weight(1f),
                    callbacks = callbacks,
                    isBoardingBus = isBoardingBus,
                    boardingBusConfirmed = boardingBusConfirmed,
                )
                LandingActionSquare(
                    action = slot2,
                    modifier = Modifier.weight(1f),
                    callbacks = callbacks,
                    isBoardingBus = isBoardingBus,
                    boardingBusConfirmed = boardingBusConfirmed,
                )
            }

            // 3-column pill row: 2 configurable "acción adicional" + Más opciones (always last)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LandingActionPill(
                    action = extra1,
                    modifier = Modifier.weight(1f),
                    callbacks = callbacks,
                    isBoardingBus = isBoardingBus,
                    boardingBusConfirmed = boardingBusConfirmed,
                )
                LandingActionPill(
                    action = extra2,
                    modifier = Modifier.weight(1f),
                    callbacks = callbacks,
                    isBoardingBus = isBoardingBus,
                    boardingBusConfirmed = boardingBusConfirmed,
                )

                PillLandingCard(
                    label = "Más opciones",
                    onClick = {
                        AnalyticsService.track("otras_opciones_opened")
                        onNavigateToOtrasOpciones()
                    },
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.surface,
                    border = MaterialTheme.colorScheme.outlineVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Filled.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
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
            if (boardingBusVisible && boardingBusError != null) {
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
internal fun LandingActionSquare(
    action: MainLandingAction,
    modifier: Modifier,
    callbacks: LandingActionCallbacks,
    isBoardingBus: Boolean,
    boardingBusConfirmed: Boolean,
) {
    val isBoard = action == MainLandingAction.BOARD_BUS
    val confirmed = isBoard && boardingBusConfirmed
    val loading = isBoard && isBoardingBus
    val accent = if (isBoard) GreenConfirmed else MaterialTheme.colorScheme.primary
    val tint = if (isBoard) GreenTint else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val border = if (isBoard) GreenBorder else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    SquareLandingCard(
        label = if (confirmed) "¡Gracias por confirmar!" else action.label,
        subtitle = if (confirmed) "Viaje confirmado" else action.subtitle,
        onClick = {
            if (!isBoard) {
                AnalyticsService.track("main_card_tapped", mapOf("action" to action.name))
            }
            callbacks.forAction(action)()
        },
        modifier = modifier,
        tint = tint,
        border = border,
        accentColor = accent,
        enabled = !loading && !confirmed,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accent, strokeWidth = 2.5.dp)
        } else {
            MainLandingActionIcon(action, size = 36, confirmed = confirmed)
        }
    }
}

@Composable
internal fun LandingActionPill(
    action: MainLandingAction,
    modifier: Modifier,
    callbacks: LandingActionCallbacks,
    isBoardingBus: Boolean,
    boardingBusConfirmed: Boolean,
) {
    val isBoard = action == MainLandingAction.BOARD_BUS
    val confirmed = isBoard && boardingBusConfirmed
    val loading = isBoard && isBoardingBus
    val accent = if (isBoard) GreenConfirmed else MaterialTheme.colorScheme.primary
    val tint = if (isBoard) GreenTint else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val border = if (isBoard) GreenBorder else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    PillLandingCard(
        label = if (confirmed) "¡Confirmado!" else action.label,
        onClick = callbacks.forAction(action),
        modifier = modifier,
        tint = tint,
        border = border,
        contentColor = accent,
        enabled = !loading && !confirmed,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accent, strokeWidth = 2.5.dp)
        } else {
            MainLandingActionIcon(action, size = 26, confirmed = confirmed, glyphScale = 1f)
        }
    }
}

@Composable
internal fun SquareLandingCard(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color,
    border: Color,
    accentColor: Color,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(tint)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(contentAlignment = Alignment.TopStart) {
            icon()
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold,
                color = if (enabled) accentColor else accentColor.copy(alpha = 0.5f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = accentColor.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PillLandingCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color,
    contentColor: Color,
    border: Color? = null,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    val twoLineHeight = with(LocalDensity.current) {
        (MaterialTheme.typography.labelMedium.lineHeight.toDp()) * 2
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(18.dp)) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Box(modifier = Modifier.height(twoLineHeight), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
internal fun MainLandingActionIcon(
    action: MainLandingAction,
    size: Int = 40,
    confirmed: Boolean = false,
    glyphScale: Float = 0.72f
) {
    val glyphSize = (size * glyphScale).dp
    when (action) {
        MainLandingAction.ROUTES -> BusLineIcon(size = size)
        MainLandingAction.ROUTE_PLANNER -> Icon(
            imageVector = Icons.Filled.Route,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(glyphSize)
        )
        MainLandingAction.REMINDERS -> Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(glyphSize)
        )
        MainLandingAction.ANOTHER_DAY -> Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(glyphSize)
        )
        MainLandingAction.BOARD_BUS -> Icon(
            imageVector = if (confirmed) Icons.Filled.CheckCircle else Icons.Filled.DirectionsBus,
            contentDescription = null,
            tint = GreenConfirmed,
            modifier = Modifier.size(glyphSize)
        )
        MainLandingAction.FAVORITES -> Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(glyphSize)
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

/** Small indeterminate indicator shown while the app is contacting the server: a dot bouncing back and forth along a track. */
@Composable
private fun BouncingBallLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "bouncingBall")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bouncingBallProgress",
    )

    // Squash-and-stretch pulse as the ball touches each end of the track — driven by the same
    // progress value as the bounce itself, so it's tied to the turnaround, not an independent clock.
    val edgeProximity = min(progress, 1f - progress)
    val squashWindow = 0.12f
    val squash = 1f - (edgeProximity / squashWindow).coerceIn(0f, 1f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )

        Canvas(modifier = Modifier.size(width = 64.dp, height = 16.dp)) {
            val ballRadius = size.height / 2f - 2.dp.toPx()
            val trackInset = ballRadius + 2.dp.toPx()
            val trackY = size.height / 2f

            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(trackInset, trackY),
                end = Offset(size.width - trackInset, trackY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            val ballX = trackInset + (size.width - 2 * trackInset) * progress
            // Flatten along the direction of travel (horizontal) and bulge perpendicular (vertical) —
            // matches a ball bouncing off a wall it's moving into, not one dropping onto a floor.
            scale(scaleX = 1f - squash * 0.12f, scaleY = 1f + squash * 0.12f, pivot = Offset(ballX, trackY)) {
                drawCircle(color = color, radius = ballRadius, center = Offset(ballX, trackY))
            }
        }

        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
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

@Composable
private fun LaLigaBlockingBanner(onClick: () -> Unit) {
    val color = LaLigaOrange

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.SportsSoccer,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Sin conexión al servidor: posible bloqueo de LaLiga en curso",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
    }
}

// internal, not private - also used by MainActivity.kt's AlertDetailCard (was previously its own
// separate, out-of-sync literal-color copy of this exact mapping). @Composable so "info" can
// resolve through MaterialTheme.colorScheme.primary (Dynamic Color-aware) rather than a static
// blue - matching what AlertDetailCard's own copy already did before being deduplicated here.
@Composable
internal fun alertColor(severity: String): Color = when (severity) {
    "critical" -> AlertCritical
    "warning"  -> RouteOrange
    else       -> MaterialTheme.colorScheme.primary
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
