/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.RouteView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Screen displaying all stops for a route direction on an interactive map.
 *
 * Shows stops as markers connected by a polyline. The direction swap button
 * mirrors the behaviour in RouteStopsScreen. Tapping a marker navigates to
 * NextDepartureScreen for that stop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteMapScreen(
    route: BusRoute,
    views: List<RouteView>,
    initialViewId: String,
    onBack: () -> Unit,
    onStopSelected: (BusStop, String) -> Unit
) {
    var currentViewId by remember { mutableStateOf(initialViewId) }
    val currentView = views.find { it.id == currentViewId } ?: views.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("Línea ${route.number}")
                        Text(
                            text = currentView.label,
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
                    currentView.swapAction?.let { swap ->
                        IconButton(onClick = { currentViewId = swap.targetViewId }) {
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
        val stopsWithCoords = remember(currentViewId) {
            currentView.stops.map { it.stop }.filter { it.hasCoordinates }
        }

        // key() forces a fresh MapView when the direction changes
        key(currentViewId) {
            RouteOsmMapView(
                stops = stopsWithCoords,
                currentViewId = currentViewId,
                onStopSelected = onStopSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@Composable
private fun RouteOsmMapView(
    stops: List<BusStop>,
    currentViewId: String,
    onStopSelected: (BusStop, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapViewRef.value?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = "InterSego/1.0 (Android; contact:bruno.follon@gmail.com)"
            }

            MapView(ctx).also { mapView ->
                mapViewRef.value = mapView
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setMultiTouchControls(true)
                mapView.isTilesScaledToDpi = true

                addStopOverlays(mapView, stops, currentViewId, onStopSelected)

                // Zoom to fit all stops once the view is laid out
                if (stops.size >= 2) {
                    val points = stops.mapNotNull { stop ->
                        val lat = stop.resolvedLatitude ?: return@mapNotNull null
                        val lon = stop.resolvedLongitude ?: return@mapNotNull null
                        GeoPoint(lat, lon)
                    }
                    if (points.size >= 2) {
                        val boundingBox = BoundingBox.fromGeoPoints(points)
                        mapView.post {
                            mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.3f), false)
                        }
                    }
                } else if (stops.size == 1) {
                    val lat = stops[0].resolvedLatitude ?: return@also
                    val lon = stops[0].resolvedLongitude ?: return@also
                    mapView.controller.setZoom(15.0)
                    mapView.controller.setCenter(GeoPoint(lat, lon))
                }
            }
        }
    )
}

private fun addStopOverlays(
    mapView: MapView,
    stops: List<BusStop>,
    currentViewId: String,
    onStopSelected: (BusStop, String) -> Unit
) {
    mapView.overlays.clear()

    val points = stops.mapNotNull { stop ->
        val lat = stop.resolvedLatitude ?: return@mapNotNull null
        val lon = stop.resolvedLongitude ?: return@mapNotNull null
        GeoPoint(lat, lon)
    }

    // Draw polyline connecting stops in order
    if (points.size >= 2) {
        val polyline = Polyline(mapView).apply {
            setPoints(points)
            outlinePaint.color = android.graphics.Color.parseColor("#1c74d3")
            outlinePaint.strokeWidth = 6f
        }
        mapView.overlays.add(polyline)
    }

    // Add a marker for each stop
    stops.forEachIndexed { index, stop ->
        val lat = stop.resolvedLatitude ?: return@forEachIndexed
        val lon = stop.resolvedLongitude ?: return@forEachIndexed

        val marker = Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            title = stop.name
            snippet = stop.area
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ ->
                onStopSelected(stop, currentViewId)
                true
            }
        }
        mapView.overlays.add(marker)
    }

    mapView.invalidate()
}
