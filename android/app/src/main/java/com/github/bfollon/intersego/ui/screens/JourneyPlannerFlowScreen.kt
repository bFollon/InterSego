/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.services.JourneySearchCoordinator
import com.github.bfollon.intersego.services.ReminderService
import com.github.bfollon.intersego.services.RouteDataService
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
    routes: List<BusRoute>,
    routeDataService: RouteDataService,
    reminderService: ReminderService,
    onExit: () -> Unit,
    onOpenNextDeparture: (routeId: String, stopId: String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val routesById = remember(routes) { routes.associateBy { it.id } }

    var step by remember { mutableStateOf<FlowStep>(FlowStep.Picker) }
    var journeys by remember { mutableStateOf<List<Journey>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var stopNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var stopsById by remember { mutableStateOf<Map<String, BusStop>>(emptyMap()) }
    // Hoisted above JourneyPlannerScreen (rather than remembered inside it) so it survives that
    // screen being disposed/recomposed when step switches away and back - see its doc comment.
    val formState = rememberJourneyPlannerFormState()

    LaunchedEffect(Unit) {
        val directory = StopDirectoryService(context).buildDirectory(supportedRouteIds)
        stopNames = directory.associate { it.physicalStopId to it.stop.name }
        stopsById = directory.associate { it.physicalStopId to it.stop }
    }

    // The system back gesture/button only pops JourneyPlannerFlowScreen itself off the app's
    // real navigation stack (step is just local state, not part of it) - without this, swiping
    // back from results or detail would skip straight past the picker to wherever this flow was
    // launched from. Disabled on the picker step so a swipe/press there falls through to onExit.
    BackHandler(enabled = step !is FlowStep.Picker) {
        step = when (val current = step) {
            is FlowStep.Detail -> current.results
            is FlowStep.Results -> FlowStep.Picker
            FlowStep.Picker -> FlowStep.Picker
        }
    }

    when (val current = step) {
        is FlowStep.Picker -> {
            JourneyPlannerScreen(
                supportedRouteIds = supportedRouteIds,
                formState = formState,
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
                date = current.date,
                departAfterMin = current.departAfterMin,
                arriveBeforeMin = current.arriveBeforeMin,
                journeys = journeys,
                isLoading = isSearching,
                routesById = routesById,
                stopsById = stopsById,
                routeDataService = routeDataService,
                reminderService = reminderService,
                onJourneySelected = { journey -> step = FlowStep.Detail(journey, current) },
                onBack = { step = FlowStep.Picker }
            )
        }
        is FlowStep.Detail -> {
            JourneyDetailScreen(
                journey = current.journey,
                date = current.results.date,
                originName = current.results.originName,
                destinationName = current.results.destinationName,
                stopName = { stopId -> stopNames[stopId] ?: stopId },
                stopLookup = { stopId -> stopsById[stopId] },
                routesById = routesById,
                stopsById = stopsById,
                routeDataService = routeDataService,
                reminderService = reminderService,
                onBack = { step = current.results },
                onLegSelected = onOpenNextDeparture
            )
        }
    }
}
