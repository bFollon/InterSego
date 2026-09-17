/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.data.Leg
import androidx.compose.material.icons.filled.Warning
import com.github.bfollon.intersego.services.AnalyticsService
import com.github.bfollon.intersego.services.JourneyReminderHelper
import com.github.bfollon.intersego.services.ReminderService
import com.github.bfollon.intersego.services.RouteDataService
import com.github.bfollon.intersego.services.TripPlannerPrefs
import com.github.bfollon.intersego.ui.theme.warningColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun formatMin(minutesOfDay: Int): String {
    val h = (minutesOfDay / 60) % 24
    val m = minutesOfDay % 60
    return "%02d:%02d".format(h, m)
}

private fun formatDuration(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

private fun formatSearchDate(date: LocalDate): String =
    if (date == LocalDate.now()) "Hoy" else date.format(DateTimeFormatter.ofPattern("d MMM"))

/** Top 3 ranked journeys for the current query, plus (below them, in their own section) up to
 * [JourneyPlannerService.MAX_TIGHT_TRANSFER_RESULTS] additional options whose transfer margin
 * falls below the configured buffer — surfaced rather than silently dropped, so a user knows
 * they exist. See docs/JOURNEY_PLANNER.md. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyResultsScreen(
    originName: String,
    destinationName: String,
    date: LocalDate,
    departAfterMin: Int,
    arriveBeforeMin: Int?,
    journeys: List<Journey>,
    tightTransferJourneys: List<Journey> = emptyList(),
    isLoading: Boolean,
    routesById: Map<String, BusRoute>,
    stopsById: Map<String, BusStop>,
    routeDataService: RouteDataService,
    reminderService: ReminderService?,
    onJourneySelected: (Journey) -> Unit,
    onBack: () -> Unit,
) {
    var reminderKeys by remember { mutableStateOf(reminderService?.activeMatchKeys() ?: emptySet()) }

    @Composable
    fun journeyItem(journey: Journey) {
        val reminderContext = remember(journey, date, routesById, stopsById) {
            JourneyReminderHelper.build(
                journey = journey, date = date,
                originName = originName, destinationName = destinationName,
                routesById = routesById, stopsById = stopsById,
                routeDataService = routeDataService,
            )
        }
        val matchKey = reminderContext?.let { JourneyReminderHelper.matchKey(it) }
        JourneyCard(
            journey = journey,
            isReminderSet = matchKey != null && reminderKeys.contains(matchKey),
            reminderAvailable = reminderContext != null && reminderService != null,
            onClick = {
                AnalyticsService.track(
                    "journey_result_selected",
                    mapOf(
                        "duration_min" to (journey.arrivalMin - journey.departureMin),
                        "transfers" to journey.transferCount
                    )
                )
                onJourneySelected(journey)
            },
            onReminderToggle = {
                val context = reminderContext
                val service = reminderService
                if (context != null && service != null) {
                    if (matchKey != null && reminderKeys.contains(matchKey)) {
                        service.cancelReminder(
                            context.route.id, context.stop.id, context.direction,
                            context.departure.hour, context.departure.minute
                        )
                    } else {
                        val result = service.scheduleReminder(
                            context.departure, context.stop, context.route, context.direction,
                            dayType = context.dayType, journeyLabel = context.journeyLabel
                        )
                        if (result is ReminderService.ScheduleResult.Success) {
                            AnalyticsService.track("reminder_set", mapOf("type" to "journey"))
                        }
                    }
                    reminderKeys = service.activeMatchKeys()
                }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                // Static - "originName → destinationName" routinely overflowed the app bar.
                title = { Text("Resultados") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
        ) {
            item {
                SectionHeader("Resumen")
            }
            item {
                SearchSummaryCard(
                    originName = originName,
                    destinationName = destinationName,
                    date = date,
                    departAfterMin = departAfterMin,
                    arriveBeforeMin = arriveBeforeMin,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            item {
                SectionHeader("Opciones")
            }
            when {
                isLoading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                journeys.isEmpty() -> item {
                    Text(
                        "No hay viajes disponibles con margen suficiente para esta búsqueda. Prueba con otra hora.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                }
                else -> items(journeys) { journey ->
                    journeyItem(journey)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            if (!isLoading && tightTransferJourneys.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("Viajes con transbordos ajustados")
                }
                item {
                    TightTransferSectionBanner(modifier = Modifier.padding(bottom = 12.dp))
                }
                items(tightTransferJourneys) { journey ->
                    journeyItem(journey)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * Explains the "Viajes con transbordos ajustados" section as a whole — distinct from each card's
 * own [TightTransferPeekBanner], which names that specific journey's margin. This one sits once
 * at the top of the section so a user who'd otherwise see only the (possibly empty) main list
 * knows these extra, less comfortable options exist at all.
 */
@Composable
private fun TightTransferSectionBanner(modifier: Modifier = Modifier) {
    val warning = warningColor()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = warning.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = warning, modifier = Modifier.size(18.dp))
            Text(
                "Estos viajes requieren un transbordo con menos margen del configurado en los ajustes. Se muestran igualmente como alternativa, pero el cambio de autobús puede resultar más justo de lo habitual.",
                style = MaterialTheme.typography.bodySmall,
                color = warning,
            )
        }
    }
}

@Composable
private fun SearchSummaryCard(
    originName: String,
    destinationName: String,
    date: LocalDate,
    departAfterMin: Int,
    arriveBeforeMin: Int?,
    modifier: Modifier = Modifier,
) {
    val searchLabel = buildString {
        append(formatSearchDate(date))
        append(" · Desde las ")
        append(formatMin(departAfterMin))
        arriveBeforeMin?.let {
            append(" · Antes de las ")
            append(formatMin(it))
        }
    }
    GroupedCard(modifier = modifier) {
        SummaryRow(icon = Icons.Filled.FiberManualRecord, iconTint = MaterialTheme.colorScheme.primary, label = "Origen", value = originName)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SummaryRow(icon = Icons.Filled.Place, iconTint = MaterialTheme.colorScheme.error, label = "Destino", value = destinationName)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SummaryRow(icon = Icons.Filled.Schedule, iconTint = MaterialTheme.colorScheme.onSurfaceVariant, label = "Búsqueda", value = searchLabel)
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, iconTint: Color, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}

/** Corner radius of [JourneyCard]'s [ElevatedCard] - shared with its [TightTransferPeekBanner]
 * so the peeking banner's curve and hidden-height calculation can never drift out of sync with
 * the card it backs (the two used to be two independently hardcoded 12dp values). */
val JourneyCardCornerRadius = 12.dp

@Composable
private fun JourneyCard(
    journey: Journey,
    isReminderSet: Boolean,
    reminderAvailable: Boolean,
    onClick: () -> Unit,
    onReminderToggle: () -> Unit,
) {
    val hasEstimatedLeg = journey.legs.any { it is Leg.Ride && it.isEstimated }
    val tightestMargin = journey.transferMargins()
        .map { it.marginMin }
        .filter { it < TripPlannerPrefs.RECOMMENDED_MIN_BUFFER }
        .minOrNull()

    PeekingBannerCard(
        bannerTitle = tightestMargin?.let { "Transbordo ajustado · $it min de margen" },
        cornerRadius = JourneyCardCornerRadius,
    ) { topPadding ->
        ElevatedCard(
            onClick = onClick,
            shape = RoundedCornerShape(JourneyCardCornerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
        ) {
            Column {
                Column(
                    modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = if (reminderAvailable) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${formatMin(journey.departureMin)} — ${formatMin(journey.arrivalMin)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(formatDuration(journey.arrivalMin - journey.departureMin), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        if (journey.transferCount == 0) "Directo" else "${journey.transferCount} transbordo(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        journey.legs.forEach { leg ->
                            when (leg) {
                                is Leg.Ride -> LegChip(text = leg.routeId, icon = Icons.Filled.DirectionsBus)
                                is Leg.Walk -> LegChip(text = "${leg.minutes} min", icon = Icons.AutoMirrored.Filled.DirectionsWalk)
                            }
                        }
                    }
                    if (hasEstimatedLeg) {
                        // Plain caption, matching iOS and the JourneyDetailScreen banner — a journey
                        // built partly on cluster-estimated times shouldn't look more precise than the
                        // app is elsewhere, but this is a secondary note, not an alert worth a card.
                        Text(
                            "Horarios orientativos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                if (reminderAvailable) {
                    // Inset divider, matching SearchSummaryCard's row dividers above rather than
                    // running edge-to-edge against the pill's own border.
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onReminderToggle)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isReminderSet) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (isReminderSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (isReminderSet) "Te avisaremos para salir" else "Recuérdame salir",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isReminderSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Breathing room between the banner's own text and wherever the card in front of it starts -
 * purely cosmetic (the [cornerRadius]-sized padding below already guarantees full coverage on
 * its own), so a small fixed constant is fine here. Rides along on top of that minimum - see the
 * `bottom` padding below. */
private val TightTransferPeekVisibleGap = 6.dp

/**
 * Warning banner tucked behind a card, peeking out above its top edge - see [PeekingBannerCard],
 * which wraps this together with the card it backs, both here in [JourneyCard] and for the
 * "Pasos" card in JourneyDetailScreen. Light tint matching the app's other warning surfaces (the
 * disclaimer cards in NextDepartureScreen: [warningColor] at 15% alpha background + full-color
 * icon/text) rather than a solid fill - the surface it sits behind (the screen background,
 * revealed through this row's `listRowBackground`-equivalent transparency) is itself opaque, so
 * the tint still fully backs the card's rounded corner with no visible gap. Bottom corners
 * square, not rounded - this banner is the same width as the card sitting on top of it, so a
 * rounded bottom corner here would clash with the card's own rounded top corner right where they
 * overlap. Only the top needs rounding (it's the only part that's ever actually visible, peeking
 * above the card). Not `private` - shared with JourneyDetailScreen.
 */
@Composable
fun TightTransferPeekBanner(
    title: String,
    subtitle: String? = null,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val warning = warningColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomStart = 0.dp, bottomEnd = 0.dp))
            .background(warning.copy(alpha = 0.15f))
            // Bottom padding is the part that ends up hidden behind the card on top; only the
            // top strip (icon + text + TightTransferPeekVisibleGap) shows above the card's edge.
            // `cornerRadius` is the hard minimum - the card's rounded top corner cuts away a
            // quarter-circle of that radius, so the banner must stay opaque at least that far
            // past the card's top edge or the tip of the curve has a background-colored gap
            // behind it. [PeekingBannerCard] measures this banner's actual rendered height (which
            // grows with the title/subtitle text and this gap) and derives the visible peek from
            // that minus `cornerRadius` - so a longer stop name or a larger system font scale
            // grows the peek automatically instead of relying on a hand-tuned guess per caller.
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = cornerRadius + TightTransferPeekVisibleGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = warning, modifier = Modifier.size(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = warning)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = warning)
            }
        }
    }
}

/**
 * Wraps [content] (a card) with an optional [TightTransferPeekBanner] tucked behind it, peeking
 * out above the card's rounded top corners. `content` receives the top padding it must apply to
 * itself (in addition to its own corner clipping/radius) - this composable only computes that
 * offset, it doesn't draw the card.
 *
 * The peek height (how far the card is pushed down) is derived at layout time from the banner's
 * own measured height ([onGloballyPositioned]) minus [cornerRadius], rather than a value
 * hand-tuned per caller. That measured height already reflects however tall the banner's
 * title/subtitle text actually renders - so it self-adjusts for a longer stop name, a wrapped
 * line, or a larger system font scale instead of silently under- or over-shooting a guess made
 * for one specific string at one specific text size. Not `private` - shared with
 * JourneyDetailScreen.
 */
@Composable
fun PeekingBannerCard(
    bannerTitle: String?,
    bannerSubtitle: String? = null,
    cornerRadius: Dp,
    content: @Composable (topPadding: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var bannerHeight by remember(bannerTitle, bannerSubtitle) { mutableStateOf(0.dp) }
    val topPadding = if (bannerTitle != null) (bannerHeight - cornerRadius).coerceAtLeast(0.dp) else 0.dp

    Box(modifier = Modifier.fillMaxWidth()) {
        if (bannerTitle != null) {
            TightTransferPeekBanner(
                title = bannerTitle,
                subtitle = bannerSubtitle,
                cornerRadius = cornerRadius,
                modifier = Modifier.onGloballyPositioned {
                    bannerHeight = with(density) { it.size.height.toDp() }
                }
            )
        }
        content(topPadding)
    }
}

/**
 * Looks like an [AssistChip] but isn't clickable, so a tap anywhere on the card — including on
 * one of these — reaches the card's own `onClick` instead of being swallowed by the chip's own
 * ripple and doing nothing (matches iOS, where the whole row is one tap target).
 */
@Composable
private fun LegChip(text: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
