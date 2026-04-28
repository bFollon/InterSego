/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.content.Context
import android.location.Location
import com.github.bfollon.intersego.data.BusStop
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

/**
 * Progress information for closest bus stop search
 */
data class ClosestBusStopProgress(
    val current: Int,
    val total: Int,
    val stopName: String? = null
)

/**
 * Result of finding the closest bus stop
 * Adapted from FarmaciasDeGuardia ClosestPharmacyResult
 */
data class ClosestBusStopResult(
    val busStop: BusStop,
    val distance: Double, // in meters
    val estimatedTravelTime: Double?, // in seconds (TODO: Phase 8 - Routing integration)
    val estimatedWalkingTime: Double? // in seconds (TODO: Phase 8 - Routing integration)
) {
    val formattedDistance: String
        get() = if (distance < 1000) {
            "${distance.toInt()} m"
        } else {
            String.format("%.1f km", distance / 1000)
        }

    val formattedTravelTime: String
        get() {
            val travelTime = estimatedTravelTime ?: return ""
            val minutes = (travelTime / 60).toInt()
            return if (minutes < 1) {
                "< 1 min"
            } else {
                "$minutes min"
            }
        }

    val formattedWalkingTime: String
        get() {
            val walkingTime = estimatedWalkingTime ?: return ""
            val minutes = (walkingTime / 60).toInt()
            return if (minutes < 60) {
                "$minutes min"
            } else {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0) {
                    "${hours}h"
                } else {
                    "${hours}h ${remainingMinutes}m"
                }
            }
        }
}

/**
 * Service for finding the closest bus stop
 * Adapted from FarmaciasDeGuardia ClosestPharmacyService
 */
class ClosestBusStopService(private val context: Context) {

    // Location-based caching (same pattern as iOS)
    private var cachedResult: ClosestBusStopResult? = null
    private var cachedLocation: Location? = null
    private var cachedDate: Date? = null

    // Progress tracking for UI
    private val _progress = MutableStateFlow<ClosestBusStopProgress?>(null)
    val progress: StateFlow<ClosestBusStopProgress?> = _progress.asStateFlow()

    companion object {
        private const val CACHE_DISTANCE_THRESHOLD = 500.0 // 500 meters (same as iOS)
        private const val CACHE_TIME_THRESHOLD = 30 * 60 * 1000L // 30 minutes in milliseconds
    }

    sealed class ClosestBusStopError : Exception() {
        object NoBusStopsAvailable : ClosestBusStopError() {
            override val message: String = "No hay paradas de autobús disponibles"
        }

        object GeocodingFailed : ClosestBusStopError() {
            override val message: String = "No se pudo determinar la ubicación de las paradas"
        }

        object NoLocationPermission : ClosestBusStopError() {
            override val message: String = "Se necesita acceso a la ubicación para encontrar la parada más cercana"
        }
    }

    /**
     * Find the closest bus stop to the user's location
     * Adapted from iOS findClosestOnDutyPharmacy
     */
    suspend fun findClosestBusStop(
        userLocation: Location,
        busStops: List<BusStop>,
        date: Date = Date()
    ): ClosestBusStopResult {

        // Check if we can use cached result first
        getCachedResultIfValid(userLocation, date)?.let { cachedResult ->
            DebugConfig.debugPrint("ClosestBusStopService: Using cached result - user hasn't moved significantly")
            return cachedResult
        }

        // Only log when we're actually doing calculations
        DebugConfig.debugPrint("ClosestBusStopService: Finding closest bus stop at $date")
        DebugConfig.debugPrint("ClosestBusStopService: User location: ${userLocation.latitude}, ${userLocation.longitude}")

        if (busStops.isEmpty()) {
            DebugConfig.debugPrint("ClosestBusStopService: No bus stops provided")
            throw ClosestBusStopError.NoBusStopsAvailable
        }

        DebugConfig.debugPrint("ClosestBusStopService: Total bus stops to evaluate: ${busStops.size}")

        // Geocode all bus stop addresses concurrently
        val geocodingService = GeocodingService(context)
        val stopsWithCoordinates = mutableListOf<BusStopWithCoordinates>()

        coroutineScope {
            val geocodingJobs = busStops.map { busStop ->
                async {
                    // Use existing coordinates if available
                    if (busStop.hasCoordinates) {
                        val location = Location("existing").apply {
                            latitude = busStop.resolvedLatitude!!
                            longitude = busStop.resolvedLongitude!!
                        }
                        BusStopWithCoordinates(busStop, location)
                    } else {
                        // Geocode the address
                        val coordinates = geocodingService.getCoordinatesForBusStop(busStop)
                        if (coordinates != null) {
                            BusStopWithCoordinates(busStop, coordinates)
                        } else {
                            null
                        }
                    }
                }
            }

            stopsWithCoordinates.addAll(geocodingJobs.awaitAll().filterNotNull())
        }

        if (stopsWithCoordinates.isEmpty()) {
            DebugConfig.debugPrint("ClosestBusStopService: Could not geocode any bus stop addresses")
            throw ClosestBusStopError.GeocodingFailed
        }

        DebugConfig.debugPrint("ClosestBusStopService: Successfully geocoded ${stopsWithCoordinates.size} bus stops")

        // Calculate distances to all bus stops
        val distanceResults = mutableListOf<ClosestBusStopResult>()
        val totalStops = stopsWithCoordinates.size

        for ((index, stopWithCoords) in stopsWithCoordinates.withIndex()) {
            // Update progress for UI
            _progress.value = ClosestBusStopProgress(
                current = index + 1,
                total = totalStops,
                stopName = stopWithCoords.busStop.name
            )

            DebugConfig.debugPrint("ClosestBusStopService: Calculating distance to: ${stopWithCoords.busStop.name} (${index + 1}/$totalStops)")

            // Calculate straight-line distance
            val distance = userLocation.distanceTo(stopWithCoords.coordinates).toDouble()

            // TODO: Phase 8 - Routing Integration
            // When RoutingService is implemented:
            // 1. Replace straight-line distance with actual driving/walking routes
            // 2. Calculate estimated travel time (driving)
            // 3. Calculate estimated walking time
            // 4. Cache routes using RouteCacheService
            // For now, we use straight-line distance and estimate times

            // Rough estimate: walking speed ~5 km/h = 1.39 m/s
            val estimatedWalkingTime = distance / 1.39

            // Rough estimate: driving speed ~30 km/h in urban = 8.33 m/s
            val estimatedTravelTime = distance / 8.33

            val result = ClosestBusStopResult(
                busStop = stopWithCoords.busStop,
                distance = distance,
                estimatedTravelTime = estimatedTravelTime,
                estimatedWalkingTime = estimatedWalkingTime
            )

            distanceResults.add(result)
            DebugConfig.debugPrint("   ${stopWithCoords.busStop.name}: ${result.formattedDistance}, ~${result.formattedTravelTime} driving, ~${result.formattedWalkingTime} walking")
        }

        // Clear progress when done
        _progress.value = null

        // Sort by distance and log top candidates
        distanceResults.sortBy { it.distance }

        DebugConfig.debugPrint("ClosestBusStopService: Top 5 closest bus stops:")
        distanceResults.take(5).forEachIndexed { index, result ->
            DebugConfig.debugPrint("   ${index + 1}. ${result.busStop.name}: ${result.formattedDistance}")
        }

        val closest = distanceResults.firstOrNull()
            ?: throw ClosestBusStopError.GeocodingFailed

        DebugConfig.debugPrint("ClosestBusStopService: CLOSEST: ${closest.busStop.name} at ${closest.formattedDistance}")

        // Cache the result
        cacheResult(closest, userLocation, date)

        return closest
    }

    /**
     * Get cached result if still valid
     */
    private fun getCachedResultIfValid(userLocation: Location, date: Date): ClosestBusStopResult? {
        val cached = cachedResult ?: return null
        val cachedLoc = cachedLocation ?: return null
        val cachedDt = cachedDate ?: return null

        // Check if location has changed significantly
        val distance = userLocation.distanceTo(cachedLoc)
        if (distance > CACHE_DISTANCE_THRESHOLD) {
            DebugConfig.debugPrint("ClosestBusStopService: User moved ${distance.toInt()}m, cache invalid")

            // TODO: Phase 8 - Clear route cache when user moves significantly
            // RouteCache.clearAllRoutes()

            return null
        }

        // Check if time has passed significantly
        val timeDiff = date.time - cachedDt.time
        if (timeDiff > CACHE_TIME_THRESHOLD) {
            DebugConfig.debugPrint("ClosestBusStopService: Cache expired (${timeDiff / 1000}s old)")
            return null
        }

        return cached
    }

    /**
     * Cache the result for future use
     */
    private fun cacheResult(result: ClosestBusStopResult, location: Location, date: Date) {
        cachedResult = result
        cachedLocation = location
        cachedDate = date
        DebugConfig.debugPrint("ClosestBusStopService: Cached result for ${result.busStop.name}")
    }

    /**
     * Clear cached result (useful when bus stop data changes)
     */
    fun clearCache() {
        cachedResult = null
        cachedLocation = null
        cachedDate = null
        DebugConfig.debugPrint("ClosestBusStopService: Cleared cached result")
    }
}

/**
 * Helper data class
 */
private data class BusStopWithCoordinates(
    val busStop: BusStop,
    val coordinates: Location
)
