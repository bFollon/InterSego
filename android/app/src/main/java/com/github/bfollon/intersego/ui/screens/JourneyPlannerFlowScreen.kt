/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.services.JourneySearchCoordinator
import com.github.bfollon.intersego.services.StopDirectoryService
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed class FlowStep {
    object Picker : FlowStep()
    data class Results(
        val originId: String, val originName: String,
        val destinationId: String, val destinationName: String,
        val date: LocalDate, val departAfterMin: Int, val arriveBeforeMin: Int?,
    ) : FlowStep()
    data class Detail(val journey: Journey, val results: Results) : FlowStep()
}

/**
 * Orchestrates the journey planner: origin/destination picker → results list → leg detail.
 * Kept as one route with internal state (rather than three separate NavHost routes) since
 * `Journey` isn't a primitive nav-argument type; see `docs/JOURNEY_PLANNER.md` for the search.
 */
@Composable
fun JourneyPlannerFlowScreen(
    supportedRouteIds: List<String>,
    onExit: () -> Unit,
    onOpenNextDeparture: (routeId: String, stopId: String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var step by remember { mutableStateOf<FlowStep>(FlowStep.Picker) }
    var journeys by remember { mutableStateOf<List<Journey>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var stopNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        stopNames = StopDirectoryService(context).buildDirectory(supportedRouteIds)
            .associate { it.physicalStopId to it.stop.name }
    }

    when (val current = step) {
        is FlowStep.Picker -> {
            JourneyPlannerScreen(
                supportedRouteIds = supportedRouteIds,
                onBack = onExit,
                onSearch = { originId, originName, destinationId, destinationName, date, departAfterMin, arriveBeforeMin ->
                    val results = FlowStep.Results(originId, originName, destinationId, destinationName, date, departAfterMin, arriveBeforeMin)
                    step = results
                    isSearching = true
                    coroutineScope.launch {
                        journeys = JourneySearchCoordinator.search(context, originId, destinationId, date, departAfterMin, arriveBeforeMin)
                        isSearching = false
                    }
                }
            )
        }
        is FlowStep.Results -> {
            JourneyResultsScreen(
                originName = current.originName,
                destinationName = current.destinationName,
                journeys = journeys,
                isLoading = isSearching,
                onJourneySelected = { journey -> step = FlowStep.Detail(journey, current) },
                onBack = { step = FlowStep.Picker }
            )
        }
        is FlowStep.Detail -> {
            JourneyDetailScreen(
                journey = current.journey,
                stopName = { stopId -> stopNames[stopId] ?: stopId },
                onBack = { step = current.results },
                onLegSelected = onOpenNextDeparture
            )
        }
    }
}
