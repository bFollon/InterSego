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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.Manifest
import com.github.bfollon.intersego.services.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class PickerTarget { ORIGIN, DESTINATION }

enum class TimeMode { DEPART_AFTER, ARRIVE_BEFORE }

data class Endpoint(val physicalStopId: String, val name: String)

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Holds the planner form's input state (everything the user has typed/picked) outside
 * [JourneyPlannerScreen] itself, so it survives the screen being disposed and recomposed when
 * [JourneyPlannerFlowScreen] switches between its picker/results/detail steps — without this,
 * navigating to results and back would silently reset origin/destination/date/time.
 */
class JourneyPlannerFormState {
    var origin by mutableStateOf<Endpoint?>(null)
    var destination by mutableStateOf<Endpoint?>(null)
    var date by mutableStateOf(LocalDate.now())
    var timeMode by mutableStateOf(TimeMode.DEPART_AFTER)
    var departureTime by mutableStateOf<LocalTime?>(null) // null = "ahora"
    var arriveBeforeTime by mutableStateOf(LocalTime.now().plusHours(1))
}

@Composable
fun rememberJourneyPlannerFormState(): JourneyPlannerFormState = remember { JourneyPlannerFormState() }

/**
 * Entry UI for the journey planner: choose origin/destination (stop or "mi ubicación"), a
 * departure date and either a departure time ("Salir a las") or an arrival deadline ("Llegar
 * antes de"), and search. See `docs/JOURNEY_PLANNER.md` for the search itself, in particular
 * "Arrive-before mode" for how the two time modes differ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyPlannerScreen(
    supportedRouteIds: List<String>,
    formState: JourneyPlannerFormState,
    onBack: () -> Unit,
    onSearch: (originId: String, originName: String, destinationId: String, destinationName: String, date: LocalDate, departAfterMin: Int, arriveBeforeMin: Int?) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var origin by formState::origin
    var destination by formState::destination
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var date by formState::date
    var timeMode by formState::timeMode
    var departureTime by formState::departureTime
    var arriveBeforeTime by formState::arriveBeforeTime
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showArriveBeforePicker by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(RecentJourneysService.recent(context)) }
    var locationError by remember { mutableStateOf<String?>(null) }
    // Which field's "mi ubicación" lookup is in flight, if any - lets the field itself show a
    // spinner and reject taps while resolving, rather than sitting inert with no feedback.
    var resolvingTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    fun resolveMyLocation(target: PickerTarget) {
        coroutineScope.launch {
            resolvingTarget = target
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
                resolvingTarget = null
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
        LazyColumn(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GroupedCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EndpointRow(
                                label = "Origen",
                                endpoint = origin,
                                isLoading = resolvingTarget == PickerTarget.ORIGIN,
                                onClick = { pickerTarget = PickerTarget.ORIGIN }
                            )
                            HorizontalDivider()
                            EndpointRow(
                                label = "Destino",
                                endpoint = destination,
                                isLoading = resolvingTarget == PickerTarget.DESTINATION,
                                onClick = { pickerTarget = PickerTarget.DESTINATION }
                            )
                        }
                        IconButton(
                            onClick = {
                                val tmp = origin
                                origin = destination
                                destination = tmp
                            }
                        ) {
                            Icon(Icons.Filled.SwapVert, contentDescription = "Intercambiar origen y destino")
                        }
                    }
                }
            }

            if (locationError != null) {
                item {
                    GroupedCard {
                        Text(
                            locationError!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                GroupedCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fecha", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        PillValue(
                            text = if (date == LocalDate.now()) "Hoy" else date.format(DateTimeFormatter.ofPattern("d MMM")),
                            onClick = { showDatePicker = true }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = timeMode == TimeMode.DEPART_AFTER,
                            onClick = { timeMode = TimeMode.DEPART_AFTER },
                            label = { Text("Salir a las") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = timeMode == TimeMode.ARRIVE_BEFORE,
                            onClick = { timeMode = TimeMode.ARRIVE_BEFORE },
                            label = { Text("Llegar antes de") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    if (timeMode == TimeMode.DEPART_AFTER) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hora de salida", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            PillValue(
                                text = departureTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Ahora",
                                onClick = { showTimePicker = true }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Llegar antes de", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            PillValue(
                                text = arriveBeforeTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                onClick = { showArriveBeforePicker = true }
                            )
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                item {
                    Text("Recientes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
                item {
                    GroupedCard {
                        recents.forEachIndexed { index, recent ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        origin = Endpoint(recent.originStopId, recent.originName)
                                        destination = Endpoint(recent.destinationStopId, recent.destinationName)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
                                Text("${recent.originName} → ${recent.destinationName}")
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val o = origin
                        val d = destination
                        if (o != null && d != null) {
                            RecentJourneysService.record(context, RecentJourney(o.physicalStopId, o.name, d.physicalStopId, d.name))
                            if (timeMode == TimeMode.ARRIVE_BEFORE) {
                                val arriveBeforeMin = arriveBeforeTime.hour * 60 + arriveBeforeTime.minute
                                onSearch(o.physicalStopId, o.name, d.physicalStopId, d.name, date, 0, arriveBeforeMin)
                            } else {
                                val departAfterMin = departureTime?.let { it.hour * 60 + it.minute }
                                    ?: (LocalTime.now().hour * 60 + LocalTime.now().minute)
                                onSearch(o.physicalStopId, o.name, d.physicalStopId, d.name, date, departAfterMin, null)
                            }
                        }
                    },
                    enabled = origin != null && destination != null && origin?.physicalStopId != destination?.physicalStopId,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Buscar")
                }
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
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = LocalDate.ofEpochDay(millis / MILLIS_PER_DAY)
                        // "Ahora" only makes sense for today; a future date needs an explicit time.
                        if (date != LocalDate.now() && departureTime == null) {
                            departureTime = LocalTime.now()
                        }
                    }
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
                    if (date == LocalDate.now()) {
                        TextButton(onClick = { departureTime = null; showTimePicker = false }) { Text("Ahora") }
                    }
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
                }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    if (showArriveBeforePicker) {
        val timePickerState = rememberTimePickerState(initialHour = arriveBeforeTime.hour, initialMinute = arriveBeforeTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showArriveBeforePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    arriveBeforeTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showArriveBeforePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showArriveBeforePicker = false }) { Text("Cancelar") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

@Composable
private fun EndpointRow(label: String, endpoint: Endpoint?, isLoading: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isLoading) "Buscando ubicación…" else (endpoint?.name ?: "Elegir parada"),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun PillValue(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
