/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bfollon.intersego.services.StopDirectoryService
import java.text.Normalizer

private fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/**
 * Searchable stop picker for the journey planner's origin/destination selection, grouped by
 * area, each row showing which routes serve it. Areas are rendered as their own rounded
 * [GroupedCard] (matching iOS's inset-grouped `List` sections) so the break between one area's
 * stops and the next reads clearly at a glance. Includes a "Mi ubicación" entry at the top when
 * [allowMyLocation] is true (degraded/hidden when location permission isn't available — the
 * caller decides via [allowMyLocation], this screen doesn't request permissions itself).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopPickerScreen(
    title: String,
    supportedRouteIds: List<String>,
    allowMyLocation: Boolean,
    onStopSelected: (physicalStopId: String, name: String) -> Unit,
    onMyLocationSelected: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<StopDirectoryService.Entry>>(emptyList()) }

    LaunchedEffect(Unit) {
        entries = StopDirectoryService(context).buildDirectory(supportedRouteIds)
    }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { normalize(it.stop.name).contains(normalize(query)) }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.stop.area ?: "Otros" }.toSortedMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Buscar parada") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (allowMyLocation && query.isBlank()) {
                    item {
                        GroupedCard {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onMyLocationSelected() }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.MyLocation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column {
                                    Text("Mi ubicación", fontWeight = FontWeight.Medium)
                                    Text(
                                        "Usar mi ubicación actual",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                grouped.forEach { (area, stopsInArea) ->
                    item {
                        Text(
                            text = area,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    item {
                        GroupedCard {
                            stopsInArea.forEachIndexed { index, entry ->
                                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onStopSelected(entry.physicalStopId, entry.stop.name) }
                                        .padding(16.dp)
                                ) {
                                    Text(entry.stop.name)
                                    Text(
                                        entry.routeIds.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(1.dp)) }
            }
        }
    }
}
