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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
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
private const val ORIGIN_COLOR = "#27ae60" // green
private const val TRANSFER_COLOR = "#1c74d3" // blue
private const val WAYPOINT_ICON_SIZE_DP = 20f

/** Colored dot marker (matching iOS's `ItineraryWaypointMarker`) for origin/transfer waypoints. */
private fun dotMarkerIcon(context: Context, color: Int): BitmapDrawable {
    val sizePx = (WAYPOINT_ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawCircle(radius, radius, radius, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius * 0.4f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/** Checkered-flag marker for the destination waypoint - drawn in code (no bundled image asset)
 * as an NxN checkerboard clipped to a circle, keeping the same footprint as [dotMarkerIcon]. */
private fun checkeredFlagMarkerIcon(context: Context, gridSize: Int = 4): BitmapDrawable {
    val sizePx = (WAYPOINT_ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    val checkerboard = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val checkerCanvas = Canvas(checkerboard)
    val cell = sizePx.toFloat() / gridSize
    val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            cellPaint.color = if ((row + col) % 2 == 0) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            checkerCanvas.drawRect(col * cell, row * cell, (col + 1) * cell, (row + 1) * cell, cellPaint)
        }
    }

    val result = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val resultCanvas = Canvas(result)
    val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = sizePx / 2f
    resultCanvas.drawCircle(radius, radius, radius, clipPaint)
    clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    resultCanvas.drawBitmap(checkerboard, 0f, 0f, clipPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density
        color = android.graphics.Color.argb(120, 0, 0, 0)
    }
    resultCanvas.drawCircle(radius, radius, radius - borderPaint.strokeWidth / 2, borderPaint)
    return BitmapDrawable(context.resources, result)
}

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
            icon = when (idx) {
                0 -> dotMarkerIcon(context, android.graphics.Color.parseColor(ORIGIN_COLOR))
                uniqueWaypoints.lastIndex -> checkeredFlagMarkerIcon(context)
                else -> dotMarkerIcon(context, android.graphics.Color.parseColor(TRANSFER_COLOR))
            }
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        mapView.overlays.add(marker)
    }

    mapView.invalidate()
    return allPoints
}
