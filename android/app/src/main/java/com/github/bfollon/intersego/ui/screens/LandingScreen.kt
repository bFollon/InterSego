/*
 * InterSego - Bus Timetable App for Segovia
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

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GreenTint = Color(0xFF34C759).copy(alpha = 0.12f)
private val GreenConfirmed = Color(0xFF34C759)

@Composable
fun LandingScreen(
    onNavigateToRouteList: () -> Unit,
    onFindClosestStop: () -> Unit = {},
    onShowAbout: () -> Unit = {},
    onShowReminders: () -> Unit = {},
    onBoardBus: () -> Unit = {},
    isSearchingClosestStop: Boolean = false,
    closestStopError: String? = null,
    isBoardingBus: Boolean = false,
    boardingBusConfirmed: Boolean = false,
    boardingBusError: String? = null
) {
    val gradientColors = listOf(Color(0xFF34C759), Color(0xFF007AFF))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                text = "Interurbanos de Segovia",
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
                    label = "Líneas de bus",
                    onClick = onNavigateToRouteList,
                    modifier = Modifier.weight(1f)
                ) {
                    BusLineIcon(size = 40)
                }

                SquareLandingCard(
                    label = "Mis recordatorios",
                    onClick = onShowReminders,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
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
private fun HorizontalLandingCard(
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
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(GreenTint)
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
private fun SquareLandingCard(
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
private fun BusLineIcon(size: Int) {
    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(size.dp)) {
        drawBusLineIcon(color)
    }
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
