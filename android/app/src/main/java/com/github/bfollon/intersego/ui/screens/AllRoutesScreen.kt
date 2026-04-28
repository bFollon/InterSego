/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.RouteSelectorEntry
import com.github.bfollon.intersego.data.RouteView

/**
 * Screen displaying all route variants with a dropdown selector.
 *
 * Allows the user to browse stops for any operating schedule (weekday, Saturday,
 * Sunday) and direction, not just today's active one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRoutesScreen(
    route: BusRoute,
    routeEntries: List<RouteSelectorEntry>,
    selectedEntryId: String,
    onEntrySelected: (RouteSelectorEntry) -> Unit,
    views: List<RouteView>,
    initialViewId: String?,
    onBack: () -> Unit,
    onStopSelected: (BusStop, String) -> Unit,
    onMapSelected: (String) -> Unit
) {
    var currentViewId by rememberSaveable(views, selectedEntryId) {
        mutableStateOf(initialViewId ?: views.firstOrNull()?.id ?: "")
    }
    val viewById = remember(views) { views.associateBy { it.id } }
    val currentView = viewById[currentViewId] ?: views.firstOrNull() ?: return

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Linea ${route.number}")
                        Text(
                            text = "Todas las rutas",
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
                    IconButton(onClick = { onMapSelected(currentViewId) }) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = "Ver en mapa"
                        )
                    }
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
            // Entry dropdown selector
            RouteEntryDropdown(
                entries = routeEntries,
                selectedEntryId = selectedEntryId,
                onEntrySelected = onEntrySelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Stop list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val stops = currentView.stops
                val extendedLabel = currentView.extendedSectionLabel
                val hasExtendedStops = extendedLabel != null && stops.any { it.isExtendedOnly }

                itemsIndexed(stops) { index, viewStop ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteEntryDropdown(
    entries: List<RouteSelectorEntry>,
    selectedEntryId: String,
    onEntrySelected: (RouteSelectorEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedEntry = entries.firstOrNull { it.id == selectedEntryId } ?: entries.firstOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedEntry?.label ?: "",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        onEntrySelected(entry)
                        expanded = false
                    }
                )
            }
        }
    }
}
