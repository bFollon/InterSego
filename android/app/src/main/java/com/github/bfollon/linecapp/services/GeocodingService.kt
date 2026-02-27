/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
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

package com.github.bfollon.linecapp.services

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.github.bfollon.linecapp.data.BusStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * GeocodingService handles address to coordinate conversion
 * Equivalent to iOS GeocodingService with CLGeocoder
 */
class GeocodingService(private val context: Context) {

    private val geocoder = Geocoder(context, Locale.getDefault())

    // Session cache for faster repeated lookups (same as iOS)
    private val sessionCache = mutableMapOf<String, Location>()

    /**
     * Get coordinates for an address with region context
     * Equivalent to iOS getCoordinates(for:region:)
     */
    suspend fun getCoordinates(address: String, region: String = "Segovia, España"): Location? {
        val cacheKey = "$address, $region"

        // Check session cache first (fastest)
        sessionCache[cacheKey]?.let { cachedLocation ->
            DebugConfig.debugPrint("📍 Using session cache for: $address")
            return cachedLocation
        }

        // Check persistent cache
        CoordinateCache.getCoordinates(cacheKey)?.let { persistentLocation ->
            DebugConfig.debugPrint("📍 Using persistent cache for: $address")
            sessionCache[cacheKey] = persistentLocation // Also cache in session
            return persistentLocation
        }

        val fullAddress = "$address, $region"

        return withContext(Dispatchers.IO) {
            try {
                DebugConfig.debugPrint("🔍 Geocoding address: $fullAddress")

                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(fullAddress, 1)

                val address = addresses?.firstOrNull()
                if (address != null) {
                    val location = Location("geocoder").apply {
                        latitude = address.latitude
                        longitude = address.longitude
                    }

                    // Cache in both session and persistent storage
                    sessionCache[cacheKey] = location
                    CoordinateCache.setCoordinates(location, cacheKey)

                    DebugConfig.debugPrint("✅ Geocoded $address -> ${location.latitude}, ${location.longitude}")
                    location
                } else {
                    DebugConfig.debugPrint("❌ No coordinates found for: $fullAddress")
                    null
                }
            } catch (exception: Exception) {
                DebugConfig.debugPrint("❌ Geocoding failed for $fullAddress: ${exception.message}")
                null
            }
        }
    }

    /**
     * Enhanced geocoding for bus stops that includes the bus stop name for better accuracy
     * Equivalent to iOS getCoordinatesForBusStop
     */
    suspend fun getCoordinatesForBusStop(busStop: BusStop): Location? {
        val enhancedQuery = "${busStop.name}, ${busStop.address}, Segovia, España"
        val cacheKey = enhancedQuery

        // Check session cache first (fastest)
        sessionCache[cacheKey]?.let { cachedLocation ->
            DebugConfig.debugPrint("📍 Using session cache for bus stop: ${busStop.name}")
            return cachedLocation
        }

        // Check persistent cache
        CoordinateCache.getCoordinates(cacheKey)?.let { persistentLocation ->
            DebugConfig.debugPrint("📍 Using persistent cache for bus stop: ${busStop.name}")
            sessionCache[cacheKey] = persistentLocation // Also cache in session
            return persistentLocation
        }

        return withContext(Dispatchers.IO) {
            try {
                DebugConfig.debugPrint("🔍 Geocoding bus stop: $enhancedQuery")

                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(enhancedQuery, 1)

                val address = addresses?.firstOrNull()
                if (address != null) {
                    val location = Location("geocoder").apply {
                        latitude = address.latitude
                        longitude = address.longitude
                    }

                    // Cache in both session and persistent storage
                    sessionCache[cacheKey] = location
                    CoordinateCache.setCoordinates(location, cacheKey)

                    DebugConfig.debugPrint("✅ Geocoded bus stop ${busStop.name} -> ${location.latitude}, ${location.longitude}")
                    location
                } else {
                    DebugConfig.debugPrint("❌ No coordinates found for bus stop: $enhancedQuery")

                    // Fallback to address-only geocoding
                    DebugConfig.debugPrint("🔄 Trying fallback geocoding with address only for: ${busStop.name}")
                    val fallbackResult = getCoordinates(busStop.address)
                    if (fallbackResult != null) {
                        DebugConfig.debugPrint("✅ Fallback geocoding succeeded for: ${busStop.name}")
                    } else {
                        DebugConfig.debugPrint("❌ Fallback geocoding also failed for: ${busStop.name}")
                    }
                    fallbackResult
                }
            } catch (exception: Exception) {
                DebugConfig.debugPrint("❌ Bus stop geocoding failed for $enhancedQuery: ${exception.message}")

                // Fallback to address-only geocoding
                DebugConfig.debugPrint("🔄 Trying fallback geocoding with address only for: ${busStop.name}")
                getCoordinates(busStop.address)
            }
        }
    }

    /**
     * Clear session cache (useful for memory management)
     */
    fun clearSessionCache() {
        sessionCache.clear()
        DebugConfig.debugPrint("🧹 Cleared geocoding session cache")
    }

    /**
     * Get session cache statistics
     */
    fun getSessionCacheStats(): Pair<Int, String> {
        val count = sessionCache.size
        val sizeEstimate = count * 100 // Rough estimate: 100 bytes per entry
        val sizeString = when {
            sizeEstimate < 1024 -> "${sizeEstimate}B"
            sizeEstimate < 1024 * 1024 -> "${sizeEstimate / 1024}KB"
            else -> "${sizeEstimate / (1024 * 1024)}MB"
        }
        return count to sizeString
    }
}
