# Phase 6: Business Logic Services

**Status:** ✅ Complete
**Date:** October 12, 2025

## Overview

Phase 6 adapts the business logic layer from FarmaciasDeGuardia to InterSego, creating services that coordinate between infrastructure (networking, geocoding, caching) and the UI layer. These services implement the core application logic for timetable management and location-based features.

## Services Implemented

### 1. TimetableService

**Location:** `app/src/main/java/com/github/bfollon/intersego/services/TimetableService.kt`
**Source:** FarmaciasDeGuardia `ScheduleService.kt`
**Size:** 7.0 KB

#### Purpose

Central service for loading and managing bus timetables with three-tier caching architecture.

#### Key Features

- **Three-Tier Caching:**
  1. Memory cache (fastest) - In-memory map of route timetables
  2. Persistent cache (fast) - JSON files via TimetableCacheService
  3. PDF parsing (slowest) - Phase 7 placeholder

- **Day Type Detection:**
  - Automatically determines current DayType (WEEKDAY/WEEKEND)
  - Filters timetables by day type
  - Holiday detection placeholder (TODO)

- **Next Departure Calculation:**
  - `getNextDepartures()` finds upcoming buses from current time
  - Integrates with `BusTimetable.getNextDepartures()` from Phase 4
  - Configurable departure limit

- **Cache Management:**
  - Per-route cache clearing
  - Global cache clearing
  - Force refresh capability

#### Key Methods

```kotlin
// Load timetables for a route (with caching)
suspend fun loadTimetables(routeId: String, forceRefresh: Boolean = false): List<BusTimetable>

// Find timetable for specific stop and day type
fun findTimetableForDayType(
    timetables: List<BusTimetable>,
    stopId: String,
    dayType: DayType
): BusTimetable?

// Get next N departures from current time
fun getNextDepartures(
    timetable: BusTimetable,
    limit: Int = 5
): List<DepartureTime>

// Clear cache for specific route or all routes
suspend fun clearCacheForRoute(routeId: String)
suspend fun clearAllCache()

// Determine current day type (WEEKDAY/WEEKEND)
fun getCurrentDayType(): DayType
```

#### Adaptations from ScheduleService

**Removed:**
- Duty rotation logic (pharmacies rotate by date, buses don't)
- Shift time spans (day/night shifts)
- `DutyLocation` complexity (simplified to routeId String)
- Spanish date/month formatting methods
- `findCurrentSchedule()` (no duty dates for buses)

**Simplified:**
- `loadSchedules(location, forceRefresh)` → `loadTimetables(routeId, forceRefresh)`
- Single return type: `List<BusTimetable>` (no complex schedule maps)
- Removed ZBS (healthcare area) subdivision logic

**Added:**
- `findTimetableForDayType()` - Filter by DayType enum
- `getNextDepartures()` - Find upcoming departures
- `getCurrentDayType()` - WEEKDAY vs WEEKEND detection

#### Phase 7 Integration Points

```kotlin
// TODO: Phase 7 - PDF Parsing Integration
// When PDF parsing is implemented, add tier 3 here:
// 1. Download PDF for route (PDFDownloadService)
// 2. Parse PDF to extract timetables (PDFParsingService)
// 3. Cache the results (both memory and persistent)
// 4. Return parsed timetables
```

---

### 2. TimetableCacheService

**Location:** `app/src/main/java/com/github/bfollon/intersego/services/TimetableCacheService.kt`
**Source:** FarmaciasDeGuardia `ScheduleCacheService.kt`
**Size:** 8.5 KB

#### Purpose

Manages persistent storage of parsed timetables with PDF version validation.

#### Key Features

- **JSON Serialization:**
  - Uses kotlinx.serialization for efficient JSON encoding
  - Stores `List<BusTimetable>` per route
  - Automatic corruption detection and recovery

- **PDF Version Validation:**
  - Tracks PDF modification timestamps
  - Invalidates cache when PDF is updated
  - Prevents stale timetable data

- **Cache Metadata:**
  - Route ID
  - Timetable count
  - Cache timestamp
  - Last cache access time
  - PDF modification timestamp

- **Performance Monitoring:**
  - Load time tracking
  - Cache size metrics
  - Debug logging via DebugConfig

#### Storage Structure

```
Files/TimetableCache/
├── L1.json              # Timetables for Línea 1
├── L1.meta.json         # Metadata for Línea 1
├── L2.json              # Timetables for Línea 2
├── L2.meta.json         # Metadata for Línea 2
└── ...
```

#### Key Methods

```kotlin
// Save timetables to persistent cache
suspend fun saveTimetables(
    routeId: String,
    timetables: List<BusTimetable>,
    pdfLastModified: Long? = null
)

// Load timetables from persistent cache
suspend fun loadTimetables(routeId: String): List<BusTimetable>?

// Check if cache is valid (PDF hasn't changed)
suspend fun isCacheValid(routeId: String, pdfLastModified: Long?): Boolean

// Clear cache for specific route
suspend fun clearCache(routeId: String)

// Get cache metadata for debugging
suspend fun getCacheMetadata(routeId: String): CacheMetadata?

// Get cache statistics
suspend fun getCacheStats(): String
```

#### Data Classes

```kotlin
@Serializable
data class CachedTimetables(
    val routeId: String,
    val timetables: List<BusTimetable>,
    val cachedAt: Long
)

@Serializable
data class CacheMetadata(
    val routeId: String,
    val timetableCount: Int,
    val cachedAt: Long,
    val lastAccessedAt: Long,
    val pdfLastModified: Long?
)
```

#### Adaptations from ScheduleCacheService

**Removed:**
- ZBS (healthcare area) subdivision caching
- `Map<DutyLocation, List<PharmacySchedule>>` complexity
- Separate ZBS metadata files

**Simplified:**
- Cache file naming: `{location-id}.json` → `{route-id}.json`
- Single cache file per route (no subdivision)
- Direct `List<BusTimetable>` storage

**Changed:**
- Cache directory: `schedules` → `TimetableCache`
- Domain models: `PharmacySchedule` → `BusTimetable`
- Location identifier: `DutyLocation` → `String` (routeId)

#### Cache Validation Logic

```kotlin
// Cache is valid if:
// 1. Cache file exists
// 2. Metadata exists
// 3. PDF hasn't been modified since cache creation
suspend fun isCacheValid(routeId: String, pdfLastModified: Long?): Boolean {
    val metadata = getCacheMetadata(routeId) ?: return false

    // If no PDF timestamp available, cache is always valid
    if (pdfLastModified == null) return true

    // If PDF has no modification timestamp, cache is valid
    if (metadata.pdfLastModified == null) return true

    // Cache is valid if PDF hasn't been modified
    return metadata.pdfLastModified >= pdfLastModified
}
```

---

### 3. ClosestBusStopService

**Location:** `app/src/main/java/com/github/bfollon/intersego/services/ClosestBusStopService.kt`
**Source:** FarmaciasDeGuardia `ClosestPharmacyService.kt`
**Size:** 11 KB

#### Purpose

Finds the closest bus stop to the user's location with geocoding, distance calculations, and estimated travel times.

#### Key Features

- **Location-Based Search:**
  - Uses Android FusedLocationProviderClient
  - Finds closest stop from a list of BusStop objects
  - Handles location permissions gracefully

- **Geocoding Integration:**
  - Uses existing GeocodingService (Phase 2)
  - Parallel geocoding for performance
  - Session cache for geocoding results

- **Distance Calculations:**
  - Straight-line distance (Haversine formula)
  - Distance-based sorting
  - Estimated travel times (driving and walking)

- **Smart Caching:**
  - Location-based cache invalidation (500m threshold)
  - Time-based cache invalidation (30 min threshold)
  - Single cached result (memory efficient)

- **Progress Tracking:**
  - `StateFlow<ClosestBusStopProgress?>` for reactive UI
  - Geocoding progress updates
  - Calculating distance progress updates

#### Key Methods

```kotlin
// Find closest bus stop from list
suspend fun findClosestBusStop(
    busStops: List<BusStop>
): Result<ClosestBusStopResult>

// Calculate straight-line distance
private fun calculateDistance(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Float

// Estimate travel times
private fun estimateTravelTime(distanceMeters: Float): Int
private fun estimateWalkingTime(distanceMeters: Float): Int

// Cache management
private fun isCacheStillValid(userLocation: Location): Boolean
private fun clearCache()
```

#### Result Classes

```kotlin
data class ClosestBusStopResult(
    val busStop: BusStop,
    val distanceMeters: Float,
    val estimatedTravelTimeMinutes: Int,
    val estimatedWalkingTimeMinutes: Int,
    val userLatitude: Double,
    val userLongitude: Double
)

sealed class ClosestBusStopProgress {
    object GeocodingBusStops : ClosestBusStopProgress()
    object CalculatingDistances : ClosestBusStopProgress()
}

sealed class ClosestBusStopError : Exception() {
    object NoBusStopsAvailable : ClosestBusStopError()
    object GeocodingFailed : ClosestBusStopError()
    object NoLocationPermission : ClosestBusStopError()
}
```

#### Travel Time Estimation

**Current Implementation (Straight-Line Distance):**

```kotlin
// Walking: ~5 km/h = 1.39 m/s
private fun estimateWalkingTime(distanceMeters: Float): Int {
    val walkingSpeed = 1.39f // m/s
    val timeSeconds = distanceMeters / walkingSpeed
    return (timeSeconds / 60).toInt().coerceAtLeast(1)
}

// Driving: ~30 km/h = 8.33 m/s (urban speed)
private fun estimateTravelTime(distanceMeters: Float): Int {
    val drivingSpeed = 8.33f // m/s
    val timeSeconds = distanceMeters / drivingSpeed
    return (timeSeconds / 60).toInt().coerceAtLeast(1)
}
```

**Phase 8 Enhancement (Real Routing):**

```kotlin
// TODO: Phase 8 - Routing Integration
// When RoutingService is implemented:
// 1. Replace straight-line distance with actual driving/walking routes
// 2. Calculate estimated travel time (driving)
// 3. Calculate estimated walking time
// 4. Cache routes using RouteCacheService
```

#### Adaptations from ClosestPharmacyService

**Removed:**
- Duty schedule integration (bus stops don't go on/off duty)
- Region/ZBS subdivision logic
- Shift time span tracking
- RoutingService integration (deferred to Phase 8)
- RouteCache integration (deferred to Phase 8)

**Simplified:**
- Takes `List<BusStop>` directly (no schedule loading)
- Straight-line distance calculations (no real routing yet)
- Simple travel time estimates based on distance

**Changed:**
- Domain models: `Pharmacy` → `BusStop`
- Result types: `ClosestPharmacyResult` → `ClosestBusStopResult`
- Progress types: `ClosestPharmacyProgress` → `ClosestBusStopProgress`
- Error types: `ClosestPharmacyError` → `ClosestBusStopError`
- Debug messages: "pharmacy" → "bus stop"

#### Cache Invalidation Logic

```kotlin
private fun isCacheStillValid(userLocation: Location): Boolean {
    val cached = cachedClosestStop ?: return false

    // Distance threshold: 500m
    val distance = calculateDistance(
        userLocation.latitude, userLocation.longitude,
        cached.userLatitude, cached.userLongitude
    )

    if (distance > 500f) {
        DebugConfig.debugPrint("Cache invalid: User moved ${distance}m")
        return false
    }

    // Time threshold: 30 minutes
    val elapsedTime = System.currentTimeMillis() - cacheTimestamp
    if (elapsedTime > 30 * 60 * 1000) {
        DebugConfig.debugPrint("Cache invalid: ${elapsedTime / 60000}min elapsed")
        return false
    }

    return true
}
```

---

## Architecture Patterns

### Three-Tier Caching Pattern

```
UI Layer (ViewModels)
       ↓
TimetableService (Tier 1: Memory)
       ↓
TimetableCacheService (Tier 2: Persistent)
       ↓
[Phase 7] PDFParsingService (Tier 3: Parse)
```

**Benefits:**
- Dramatically reduced load times (memory cache is instant)
- Offline support (persistent cache works without network)
- Smart invalidation (PDF version tracking prevents stale data)

### Service Layer Design

All services follow consistent patterns:

1. **Context-based initialization:**
   ```kotlin
   class TimetableService private constructor(private val context: Context)
   companion object {
       fun getInstance(context: Context): TimetableService
   }
   ```

2. **Suspend functions for async operations:**
   ```kotlin
   suspend fun loadTimetables(routeId: String): List<BusTimetable>
   ```

3. **DebugConfig integration:**
   ```kotlin
   DebugConfig.debugPrint("🚌 TimetableService: Loading timetables...")
   ```

4. **Error handling with Result types:**
   ```kotlin
   Result<ClosestBusStopResult>
   sealed class ClosestBusStopError : Exception()
   ```

### Location-Based Caching

```
User Location → Check Cache Distance → Check Cache Age
                       ↓                        ↓
                  > 500m?                > 30 min?
                       ↓                        ↓
                   Clear Cache ← OR ← Clear Cache
                       ↓
              Recalculate Closest Stop
```

**Prevents:**
- Excessive geocoding API calls
- Unnecessary distance calculations
- Stale location data

---

## Integration Dependencies

### Existing Services (Already Implemented)

✅ **DebugConfig** (Phase 2)
- Used for consistent logging across all services
- `DebugConfig.debugPrint()`, `debugError()`, `debugWarn()`

✅ **GeocodingService** (Phase 2)
- Used by ClosestBusStopService for address → coordinates
- Session cache integration
- Parallel geocoding for performance

✅ **CoordinateCache** (Phase 2)
- Used by GeocodingService for persistent coordinate storage
- 30-day cache expiration

✅ **LocationManager** (Phase 2)
- Used by ClosestBusStopService for user location
- Permission handling

✅ **NetworkMonitor** (Phase 2)
- Used for offline detection
- Network state awareness

✅ **PDFCacheManager** (Phase 3)
- Used by TimetableService for PDF version tracking
- Cache validation

### Future Dependencies (Phase 7+)

⏳ **PDFParsingService** (Phase 7)
- Needed by TimetableService tier 3 caching
- Parse PDFs → extract timetables
- Multiple parsing strategies for different PDF formats

⏳ **RoutingService** (Phase 8)
- Needed by ClosestBusStopService for real routing
- Apple Maps / Google Maps integration
- Replace straight-line distance calculations

⏳ **RouteCache** (Phase 8)
- Needed by ClosestBusStopService for route caching
- Location-based cache invalidation
- Store: distance, travel time, walking time

---

## Data Flow Examples

### Loading Timetables (Three-Tier Cache)

```
User opens Route Screen
       ↓
ViewModel calls TimetableService.loadTimetables("L1")
       ↓
TimetableService checks memory cache (Tier 1)
       ↓ (cache miss)
TimetableService calls TimetableCacheService.loadTimetables("L1")
       ↓
TimetableCacheService checks persistent cache (Tier 2)
       ↓
TimetableCacheService validates PDF version
       ↓
- If valid: Load JSON → Return timetables
- If invalid: Return null
       ↓ (cache invalid)
[Phase 7] TimetableService calls PDFParsingService.parseTimetables("L1")
       ↓
PDFDownloadService downloads PDF
       ↓
PDFParsingService parses PDF → List<BusTimetable>
       ↓
TimetableCacheService.saveTimetables() (persist)
       ↓
TimetableService stores in memory cache
       ↓
Return timetables to ViewModel
       ↓
ViewModel updates UI
```

### Finding Closest Bus Stop

```
User opens "Nearest Stop" screen
       ↓
ViewModel calls ClosestBusStopService.findClosestBusStop(stops)
       ↓
ClosestBusStopService gets user location (LocationManager)
       ↓
ClosestBusStopService checks cache validity
       ↓ (cache invalid)
Progress: GeocodingBusStops
       ↓
ClosestBusStopService geocodes all bus stops (parallel)
       ↓
Progress: CalculatingDistances
       ↓
ClosestBusStopService calculates distances (Haversine)
       ↓
ClosestBusStopService sorts by distance
       ↓
ClosestBusStopService estimates travel times
       ↓
ClosestBusStopService caches result
       ↓
Return ClosestBusStopResult
       ↓
ViewModel updates UI
```

---

## Testing Recommendations

### TimetableService

```kotlin
// Test memory cache
@Test
fun testMemoryCacheHit() {
    // Load timetables twice
    // Second call should be instant (memory cache)
}

// Test persistent cache validation
@Test
fun testPersistentCacheValid() {
    // Save timetables with PDF timestamp
    // Load timetables with same PDF timestamp
    // Should use cache
}

// Test cache invalidation
@Test
fun testCacheInvalidation() {
    // Save timetables with old PDF timestamp
    // Load timetables with new PDF timestamp
    // Should invalidate cache
}

// Test day type detection
@Test
fun testGetCurrentDayType() {
    // Mock Calendar to different days
    // Verify WEEKDAY vs WEEKEND
}

// Test next departures
@Test
fun testGetNextDepartures() {
    // Create timetable with known departures
    // Mock current time
    // Verify correct upcoming departures returned
}
```

### TimetableCacheService

```kotlin
// Test save/load roundtrip
@Test
fun testSaveLoadRoundtrip() {
    // Save timetables
    // Load timetables
    // Verify data integrity
}

// Test PDF version validation
@Test
fun testPDFVersionValidation() {
    // Save with old PDF timestamp
    // Check validity with new PDF timestamp
    // Should return false
}

// Test cache corruption handling
@Test
fun testCacheCorruption() {
    // Write invalid JSON to cache file
    // Load timetables
    // Should return null gracefully
}

// Test cache statistics
@Test
fun testCacheStats() {
    // Save multiple route caches
    // Call getCacheStats()
    // Verify metrics
}
```

### ClosestBusStopService

```kotlin
// Test distance calculation
@Test
fun testDistanceCalculation() {
    // Known coordinates (e.g., Segovia Plaza Mayor to Bus Station)
    // Calculate distance
    // Verify against expected value
}

// Test geocoding failure handling
@Test
fun testGeocodingFailure() {
    // Mock GeocodingService to return null
    // Call findClosestBusStop()
    // Should return GeocodingFailed error
}

// Test location cache invalidation (distance)
@Test
fun testCacheInvalidationDistance() {
    // Cache result at location A
    // Request from location B (>500m away)
    // Should invalidate cache
}

// Test location cache invalidation (time)
@Test
fun testCacheInvalidationTime() {
    // Cache result
    // Wait 31 minutes (or mock time)
    // Should invalidate cache
}

// Test progress tracking
@Test
fun testProgressTracking() {
    // Collect progress flow
    // Call findClosestBusStop()
    // Verify GeocodingBusStops → CalculatingDistances
}
```

---

## Debug Output Examples

### TimetableService

```
🚌 TimetableService: Loading timetables for route L1 (forceRefresh=false)
✅ TimetableService: Found 3 timetables in memory cache for L1
🚌 TimetableService: No memory cache for L1, checking persistent cache...
✅ TimetableService: Loaded 3 timetables from persistent cache for L1
⚠️ TimetableService: Persistent cache invalid for L1, need to parse PDF
🗑️ TimetableService: Cleared cache for route L1
🗑️ TimetableService: Cleared all caches
```

### TimetableCacheService

```
💾 TimetableCacheService: Saving 3 timetables for route L1
📂 TimetableCacheService: Loaded 3 timetables for L1 (took 15ms)
✅ TimetableCacheService: Cache valid for L1 (PDF not modified)
❌ TimetableCacheService: Cache invalid for L1 (PDF modified)
🗑️ TimetableCacheService: Cleared cache for route L1

📊 TimetableCache Statistics:
Total routes cached: 4
Total timetables: 156
Total cache size: 127 KB
Routes:
  L1: 42 timetables (32 KB)
  L2: 38 timetables (28 KB)
  L3: 45 timetables (35 KB)
  L4: 31 timetables (32 KB)
```

### ClosestBusStopService

```
📍 ClosestBusStopService: Finding closest from 12 bus stops
✅ ClosestBusStopService: Using cached result (valid)
⚠️ ClosestBusStopService: Cache invalid: User moved 752m
⚠️ ClosestBusStopService: Cache invalid: 35min elapsed
🗑️ ClosestBusStopService: Cleared closest stop cache
🌐 ClosestBusStopService: Geocoding 12 bus stops...
✅ ClosestBusStopService: Geocoding complete (10/12 successful)
📏 ClosestBusStopService: Calculating distances...
✅ ClosestBusStopService: Closest stop: "Plaza Mayor" (234m, 3min walk)
```

---

## File Summary

| Service | Size | Lines | Status |
|---------|------|-------|--------|
| TimetableService.kt | 7.0 KB | ~200 | ✅ Complete |
| TimetableCacheService.kt | 8.5 KB | ~250 | ✅ Complete |
| ClosestBusStopService.kt | 11 KB | ~320 | ✅ Complete |
| **Total** | **26.5 KB** | **~770** | **✅ Complete** |

---

## Phase 6 Checklist

- [x] Copy ScheduleService → TimetableService
- [x] Adapt for bus domain (remove duty rotation logic)
- [x] Implement day type detection (WEEKDAY/WEEKEND)
- [x] Implement next departure calculation
- [x] Add Phase 7 integration placeholders (PDF parsing)
- [x] Copy ScheduleCacheService → TimetableCacheService
- [x] Adapt cache structure (routeId, timetables)
- [x] Preserve PDF version validation
- [x] Update cache directory and file naming
- [x] Copy ClosestPharmacyService → ClosestBusStopService
- [x] Adapt for bus stops (remove duty logic)
- [x] Implement straight-line distance calculations
- [x] Add travel time estimation
- [x] Add Phase 8 integration placeholders (routing)
- [x] Update all debug messages
- [x] Update all package names
- [x] Add GPL-v3 headers
- [x] Test compilation

---

## Next Steps

### Phase 7: PDF Parsing Integration
- Implement PDFParsingService
- Create parsing strategies for different PDF formats
- Integrate with TimetableService tier 3
- Parse tables → extract BusTimetable objects

### Phase 8: Routing Service
- Implement RoutingService (Apple Maps / Google Maps)
- Implement RouteCache for performance
- Update ClosestBusStopService with real routing
- Replace straight-line distance with actual routes

### Phase 9: ViewModels & UI
- Create TimetableViewModel
- Create ClosestStopViewModel
- Build route detail screens
- Build nearest stop screen
- Integrate with business logic services

---

## Notes

- All services follow FarmaciasDeGuardia architecture patterns
- Three-tier caching provides excellent performance
- Location-based caching reduces unnecessary API calls
- PDF parsing deferred to Phase 7 (requires actual PDFs)
- Real routing deferred to Phase 8 (straight-line distance sufficient for now)
- All services are testable with clear interfaces
- Debug logging integrated throughout

**Phase 6 successfully adapts the core business logic layer from FarmaciasDeGuardia to InterSego's bus timetable domain!**
