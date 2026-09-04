/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.Journey
import com.github.bfollon.intersego.data.Leg
import com.github.bfollon.intersego.services.PolylineLoader
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Cycled per ride leg (in leg order, ignoring walk legs) so consecutive rides are visually distinct. */
private val LEG_COLORS = listOf(
    "#1c74d3", // blue
    "#e67e22", // orange
    "#27ae60", // green
    "#8e44ad", // purple
    "#c0392b", // red
    "#16a085", // teal
)

private const val WALK_COLOR = android.graphics.Color.GRAY

/**
 * Overview map for a whole [Journey]: one colored polyline per ride leg (loaded via
 * [PolylineLoader], same road-following data as [RouteMapScreen]) plus a dashed straight line for
 * any walk legs, with markers at the origin, destination, and any transfer points in between.
 */
@Composable
fun ItineraryMapView(
    journey: Journey,
    stopLookup: (String) -> BusStop?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef.value?.onPause()
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
                mapView.outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                mapView.clipToOutline = true
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.setMultiTouchControls(true)
                mapView.isTilesScaledToDpi = true

                val allPoints = addItineraryOverlays(context, mapView, journey, stopLookup)

                if (allPoints.size >= 2) {
                    val boundingBox = BoundingBox.fromGeoPoints(allPoints)
                    mapView.post {
                        mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.3f), false)
                    }
                } else if (allPoints.size == 1) {
                    mapView.controller.setZoom(15.0)
                    mapView.controller.setCenter(allPoints[0])
                }
            }
        }
    )
}

private fun stopPoint(stopLookup: (String) -> BusStop?, stopId: String): GeoPoint? {
    val stop = stopLookup(stopId) ?: return null
    val lat = stop.resolvedLatitude ?: return null
    val lon = stop.resolvedLongitude ?: return null
    return GeoPoint(lat, lon)
}

/** Prefers [BusStop.routingLatitude]/[BusStop.routingLongitude] since those track the road the
 * polyline follows more closely than the physical stop location (see [BusStop.routingCoordinates]). */
private fun routingPoint(stopLookup: (String) -> BusStop?, stopId: String): GeoPoint? {
    val stop = stopLookup(stopId) ?: return null
    val lat = stop.routingLatitude ?: return null
    val lon = stop.routingLongitude ?: return null
    return GeoPoint(lat, lon)
}

private fun nearestIndex(polyline: List<GeoPoint>, point: GeoPoint): Int {
    var bestIndex = 0
    var bestDist = Double.MAX_VALUE
    polyline.forEachIndexed { index, p ->
        val dLat = p.latitude - point.latitude
        val dLon = p.longitude - point.longitude
        val dist = dLat * dLat + dLon * dLon
        if (dist < bestDist) {
            bestDist = dist
            bestIndex = index
        }
    }
    return bestIndex
}

/** A ride leg only covers part of its route's full polyline - trims it down to the stretch
 * between the boarding and alighting stops (by nearest-point matching), rather than drawing the
 * whole route. */
private fun trimPolylineToStops(polyline: List<GeoPoint>, from: GeoPoint, to: GeoPoint): List<GeoPoint> {
    val fromIndex = nearestIndex(polyline, from)
    val toIndex = nearestIndex(polyline, to)
    if (fromIndex == toIndex) return emptyList()
    val slice = if (fromIndex < toIndex) {
        polyline.subList(fromIndex, toIndex + 1)
    } else {
        polyline.subList(toIndex, fromIndex + 1).asReversed()
    }
    return slice
}

private fun addItineraryOverlays(
    context: Context,
    mapView: MapView,
    journey: Journey,
    stopLookup: (String) -> BusStop?,
): List<GeoPoint> {
    mapView.overlays.clear()
    val allPoints = mutableListOf<GeoPoint>()

    var rideIndex = 0
    journey.legs.forEach { leg ->
        when (leg) {
            is Leg.Ride -> {
                val loaded = PolylineLoader.load(context, leg.routeId, leg.variantId)
                val fromRouting = routingPoint(stopLookup, leg.fromStop)
                val toRouting = routingPoint(stopLookup, leg.toStop)
                val trimmed = if (loaded.size >= 2 && fromRouting != null && toRouting != null) {
                    trimPolylineToStops(loaded, fromRouting, toRouting)
                } else {
                    emptyList()
                }
                val points = trimmed.ifEmpty {
                    listOfNotNull(stopPoint(stopLookup, leg.fromStop), stopPoint(stopLookup, leg.toStop))
                }
                if (points.size >= 2) {
                    val color = LEG_COLORS[rideIndex % LEG_COLORS.size]
                    val polyline = Polyline(mapView).apply {
                        setPoints(points)
                        outlinePaint.color = android.graphics.Color.parseColor(color)
                        outlinePaint.strokeWidth = 6f
                    }
                    mapView.overlays.add(polyline)
                    allPoints.addAll(points)
                }
                rideIndex++
            }
            is Leg.Walk -> {
                val from = stopPoint(stopLookup, leg.fromStop)
                val to = stopPoint(stopLookup, leg.toStop)
                if (from != null && to != null) {
                    val polyline = Polyline(mapView).apply {
                        setPoints(listOf(from, to))
                        outlinePaint.color = WALK_COLOR
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
                    }
                    mapView.overlays.add(polyline)
                    allPoints.add(from)
                    allPoints.add(to)
                }
            }
        }
    }

    // Waypoints in journey order: origin, every intermediate transfer point, destination.
    val waypointIds = mutableListOf<String>()
    journey.legs.forEachIndexed { index, leg ->
        if (index == 0) waypointIds.add(leg.fromStop)
        waypointIds.add(leg.toStop)
    }
    val uniqueWaypoints = waypointIds.distinct()
    uniqueWaypoints.forEachIndexed { idx, stopId ->
        val stop = stopLookup(stopId) ?: return@forEachIndexed
        val lat = stop.resolvedLatitude ?: return@forEachIndexed
        val lon = stop.resolvedLongitude ?: return@forEachIndexed
        val marker = Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            title = stop.name
            snippet = when (idx) {
                0 -> "Origen"
                uniqueWaypoints.lastIndex -> "Destino"
                else -> "Transbordo"
            }
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)
    }

    mapView.invalidate()
    return allPoints
}
