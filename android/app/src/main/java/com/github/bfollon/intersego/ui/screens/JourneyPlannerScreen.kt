/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.Manifest
import com.github.bfollon.intersego.services.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class PickerTarget { ORIGIN, DESTINATION }

private data class Endpoint(val physicalStopId: String, val name: String)

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Entry UI for the journey planner: choose origin/destination (stop or "mi ubicación"),
 * departure date/time, and search. See `docs/JOURNEY_PLANNER.md` for the search itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyPlannerScreen(
    supportedRouteIds: List<String>,
    onBack: () -> Unit,
    onSearch: (originId: String, originName: String, destinationId: String, destinationName: String, date: LocalDate, departAfterMin: Int) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var origin by remember { mutableStateOf<Endpoint?>(null) }
    var destination by remember { mutableStateOf<Endpoint?>(null) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var departureTime by remember { mutableStateOf<LocalTime?>(null) } // null = "ahora"
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(RecentJourneysService.recent(context)) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var resolvingLocation by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    fun resolveMyLocation(target: PickerTarget) {
        coroutineScope.launch {
            resolvingLocation = true
            locationError = null
            try {
                val locationMgr = LocationManager(context)
                val closestStopFinder = ClosestStopFinderService(context)
                val routeDataService = RouteDataService(context)
                val allRoutes = supportedRouteIds.mapNotNull { runCatching { TimetableLoader(context).loadRoute(it) }.getOrNull() }
                val userLocation = locationMgr.requestLocationOnce()
                val result = closestStopFinder.findClosest(userLocation, allRoutes)
                val stopsById = routeDataService.getSupportedRoutes()
                    .firstNotNullOfOrNull { rid -> TimetableLoader(context).loadBusStopsById(rid)[result.stopId] }
                val physicalId = TimetableLoader(context).loadPhysicalStopIds(
                    routeDataService.getRoutesForStop(result.stopId).firstOrNull() ?: ""
                )[result.stopId] ?: result.stopId
                val stopLocation = android.location.Location("").apply {
                    latitude = stopsById?.resolvedLatitude ?: 0.0
                    longitude = stopsById?.resolvedLongitude ?: 0.0
                }
                val distanceMeters = userLocation.distanceTo(stopLocation).toInt()
                val name = stopsById?.name ?: result.stopId
                val endpoint = Endpoint(physicalId, name)
                if (target == PickerTarget.ORIGIN) origin = endpoint else destination = endpoint
                locationError = "Parada más cercana: $name ($distanceMeters m)"
            } catch (e: LocationManager.LocationError) {
                locationError = e.message
            } catch (e: ClosestStopFinderService.ClosestStopError) {
                locationError = e.message
            } catch (e: Exception) {
                locationError = "No se pudo determinar tu ubicación."
            } finally {
                resolvingLocation = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (!granted) {
            locationError = "Permiso de ubicación denegado. Puedes elegir una parada manualmente."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planificar viaje") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EndpointField(
                        label = "Origen",
                        endpoint = origin,
                        onClick = { pickerTarget = PickerTarget.ORIGIN }
                    )
                    EndpointField(
                        label = "Destino",
                        endpoint = destination,
                        onClick = { pickerTarget = PickerTarget.DESTINATION }
                    )
                }
                IconButton(
                    onClick = {
                        val tmp = origin
                        origin = destination
                        destination = tmp
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Intercambiar origen y destino")
                }
            }

            if (locationError != null) {
                Text(locationError!!, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(if (date == LocalDate.now()) "Hoy" else date.format(DateTimeFormatter.ofPattern("d MMM"))) },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) }
                )
                AssistChip(
                    onClick = { showTimePicker = true },
                    label = { Text(departureTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Ahora") }
                )
            }

            if (recents.isNotEmpty()) {
                Text("Recientes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(recents) { recent ->
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                            headlineContent = { Text("${recent.originName} → ${recent.destinationName}") },
                            modifier = Modifier.clickable {
                                origin = Endpoint(recent.originStopId, recent.originName)
                                destination = Endpoint(recent.destinationStopId, recent.destinationName)
                            }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Button(
                onClick = {
                    val o = origin
                    val d = destination
                    if (o != null && d != null) {
                        RecentJourneysService.record(context, RecentJourney(o.physicalStopId, o.name, d.physicalStopId, d.name))
                        val departAfterMin = departureTime?.let { it.hour * 60 + it.minute }
                            ?: (LocalTime.now().hour * 60 + LocalTime.now().minute)
                        onSearch(o.physicalStopId, o.name, d.physicalStopId, d.name, date, departAfterMin)
                    }
                },
                enabled = origin != null && destination != null && origin?.physicalStopId != destination?.physicalStopId,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buscar")
            }
        }
    }

    if (pickerTarget != null) {
        Dialog(onDismissRequest = { pickerTarget = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            StopPickerScreen(
                title = if (pickerTarget == PickerTarget.ORIGIN) "Origen" else "Destino",
                supportedRouteIds = supportedRouteIds,
                allowMyLocation = true,
                onStopSelected = { stopId, name ->
                    if (pickerTarget == PickerTarget.ORIGIN) origin = Endpoint(stopId, name) else destination = Endpoint(stopId, name)
                    pickerTarget = null
                },
                onMyLocationSelected = {
                    val target = pickerTarget!!
                    pickerTarget = null
                    if (hasLocationPermission) {
                        resolveMyLocation(target)
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                onBack = { pickerTarget = null }
            )
        }
    }

    if (showDatePicker) {
        val todayEpochMillis = LocalDate.now().toEpochDay() * MILLIS_PER_DAY
        val maxEpochMillis = LocalDate.now().plusDays(90).toEpochDay() * MILLIS_PER_DAY
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in todayEpochMillis..maxEpochMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis -> date = LocalDate.ofEpochDay(millis / MILLIS_PER_DAY) }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val initial = departureTime ?: LocalTime.now()
        val timePickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    departureTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { departureTime = null; showTimePicker = false }) { Text("Ahora") }
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
                }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

@Composable
private fun EndpointField(label: String, endpoint: Endpoint?, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(endpoint?.name ?: "Elegir parada", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
