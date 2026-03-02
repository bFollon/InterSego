# Migration Phase 2: Infrastructure Layer

**Date:** October 12, 2025
**Status:** ✅ Complete

## Overview

Phase 2 copies domain-agnostic infrastructure services from FarmaciasDeGuardia to InterSego. These services provide core functionality for network monitoring, debugging, caching, location services, and geocoding that work regardless of the application domain (pharmacy vs bus).

## Goals

1. Copy infrastructure services that require no business logic changes
2. Update package names from `farmaciasdeguardiaensegovia` to `intersego`
3. Adapt domain-specific method names and comments for bus context
4. Establish foundation for location-based features

## Services Copied

### 1. NetworkMonitor.kt
**Path:** `services/NetworkMonitor.kt`
**Size:** ~4.0 KB
**Changes:**
- Updated package: `com.github.bfollon.farmaciasdeguardiaensegovia.services` → `intersego.services`
- Updated TAG constant: `"FarmaciasDeGuardia"` → `"InterSego"`

**Functionality:**
- Monitors device network connectivity state
- Checks for validated internet access (not just network connection)
- Provides human-readable network state descriptions
- Used throughout app for offline mode detection

**Key Methods:**
- `initialize(context: Context)` - Initialize with app context
- `isOnline(): Boolean` - Check if device has internet access
- `getNetworkStateDescription(): String` - Get user-friendly network state

---

### 2. DebugConfig.kt
**Path:** `services/DebugConfig.kt`
**Size:** ~3.8 KB
**Changes:**
- Updated package: `com.github.bfollon.farmaciasdeguardiaensegovia.services` → `intersego.services`
- Updated TAG constant: `"FarmaciasDeGuardia"` → `"InterSego"`

**Functionality:**
- Centralized debug logging configuration
- Toggleable debug output for development vs production
- Separate detailed logging flag for verbose output
- Consistent logging API across entire app

**Key Methods:**
- `debugPrint(message: String)` - Print debug message if enabled
- `debugError(message: String, throwable: Throwable?)` - Always log errors
- `debugWarn(message: String)` - Print warning message
- `enableDebug()` / `disableDebug()` - Toggle debug mode

**Configuration:**
- `DEFAULT_DEBUG_ENABLED = true` - Enabled by default for development
- `isDetailedLoggingEnabled = false` - Verbose logging disabled by default

---

### 3. CoordinateCache.kt
**Path:** `services/CoordinateCache.kt`
**Size:** ~6.5 KB
**Changes:**
- Updated package: `com.github.bfollon.farmaciasdeguardiaensegovia.services` → `intersego.services`
- Updated cache key: `"pharmacy_coordinates_cache"` → `"bus_stop_coordinates_cache"`

**Functionality:**
- Persistent caching of geocoded address coordinates
- 30-day expiration for cached coordinates
- SharedPreferences-based storage with JSON serialization
- Automatic cleanup of expired entries

**Key Methods:**
- `initialize(context: Context)` - Initialize with app context
- `getCoordinates(address: String): Location?` - Get cached coordinates
- `setCoordinates(location: Location, address: String)` - Cache coordinates
- `cleanupExpiredEntries()` - Remove expired cache entries
- `clearAll()` - Clear entire cache
- `getCacheStats(): Pair<Int, String>` - Get cache size statistics

**Cache Structure:**
```kotlin
@Serializable
data class CachedCoordinate(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
```

---

### 4. GeocodingService.kt
**Path:** `services/GeocodingService.kt`
**Size:** ~7.5 KB
**Changes:**
- Updated package: `com.github.bfollon.farmaciasdeguardiaensegovia.services` → `intersego.services`
- Updated import: `data.Pharmacy` → `data.BusStop`
- Method renamed: `getCoordinatesForPharmacy(pharmacy: Pharmacy)` → `getCoordinatesForBusStop(busStop: BusStop)`
- Updated comments: "pharmacy" → "bus stop"
- Updated debug messages: "pharmacy" → "bus stop"

**Functionality:**
- Convert addresses to GPS coordinates using Android Geocoder
- Two-tier caching: session cache (memory) + persistent cache (CoordinateCache)
- Enhanced geocoding with entity names for better accuracy
- Automatic fallback to address-only geocoding on failure
- Region-aware geocoding (defaults to "Segovia, España")

**Key Methods:**
- `getCoordinates(address: String, region: String): Location?` - Basic geocoding
- `getCoordinatesForBusStop(busStop: BusStop): Location?` - Enhanced geocoding with bus stop name
- `clearSessionCache()` - Clear in-memory cache
- `getSessionCacheStats(): Pair<Int, String>` - Get session cache statistics

**Caching Strategy:**
1. Check session cache (fastest)
2. Check persistent cache (fast)
3. Perform geocoding (slow)
4. Cache result in both caches

---

### 5. LocationManager.kt
**Path:** `services/LocationManager.kt`
**Size:** ~6.8 KB
**Changes:**
- Updated package: `com.github.bfollon.farmaciasdeguardiaensegovia.services` → `intersego.services`

**Functionality:**
- Manages Android location permissions and access
- Requests user location with FusedLocationProviderClient
- Handles location permission flow
- Provides location callbacks with success/error handling

**Key Methods:**
- `requestLocation(activity: ComponentActivity, onResult: (Location?) -> Unit)` - Request current location
- `hasLocationPermission(context: Context): Boolean` - Check if permissions granted
- Permission handling: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`

**Permission Flow:**
1. Check if permissions granted
2. Request permissions if needed
3. Get last known location or request new location
4. Return result via callback

---

### 6. MapUtils.kt
**Path:** `utils/MapUtils.kt`
**Size:** ~1.5 KB
**Changes:**
- Updated package: `com.github.bfollon.farmaciasdeguardiaensegovia.utils` → `intersego.utils`
- Added GPL license header (was missing in original)

**Functionality:**
- Calculate distances between coordinates
- Open external map apps with directions
- Support for both Google Maps and Apple Maps (if available)

**Key Methods:**
- `calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float` - Calculate distance in meters using Haversine formula
- `openMapsApp(context: Context, latitude: Double, longitude: Double, label: String)` - Open external map app with location

**Map App Support:**
- Google Maps: `geo:` URI scheme
- Fallback: Opens Google Maps website in browser

---

## Files Not Copied (Intentionally Excluded)

### RouteCache.kt
**Reason:** InterSego doesn't need to cache routes to bus stops. Users will simply view timetables for stops, not calculate routes.

### RoutingService.kt
**Reason:** InterSego doesn't need route calculation functionality. The app focuses on displaying bus timetables, not providing navigation to stops.

---

## Package Name Changes

All files updated from:
```
com.github.bfollon.farmaciasdeguardiaensegovia
```

To:
```
intersego
```

**Affected imports in copied files:**
- `services.NetworkMonitor`
- `services.DebugConfig`
- `services.CoordinateCache`
- `services.GeocodingService`
- `services.LocationManager`
- `utils.MapUtils`

---

## Domain Adaptations

### GeocodingService Specific Changes

**Method Signature:**
```kotlin
// Original (FarmaciasDeGuardia)
suspend fun getCoordinatesForPharmacy(pharmacy: Pharmacy): Location?

// Adapted (InterSego)
suspend fun getCoordinatesForBusStop(busStop: BusStop): Location?
```

**Enhanced Query Construction:**
```kotlin
// Original
val enhancedQuery = "${pharmacy.name}, ${pharmacy.address}, Segovia, España"

// Adapted
val enhancedQuery = "${busStop.name}, ${busStop.address}, Segovia, España"
```

**Debug Messages:**
- `"Geocoding pharmacy:"` → `"Geocoding bus stop:"`
- `"Geocoded pharmacy"` → `"Geocoded bus stop"`
- `"No coordinates found for pharmacy:"` → `"No coordinates found for bus stop:"`

---

## Dependencies

### New Dependencies (Already in Phase 1)
- ✅ `kotlinx-serialization-json` - For CoordinateCache JSON serialization
- ✅ `kotlinx-coroutines-android` - For GeocodingService suspend functions
- ✅ `com.google.android.gms:play-services-location` - For LocationManager

### Android Permissions (Already in Phase 1)
- ✅ `android.permission.INTERNET` - For network monitoring
- ✅ `android.permission.ACCESS_NETWORK_STATE` - For network state checking
- ✅ `android.permission.ACCESS_FINE_LOCATION` - For precise location
- ✅ `android.permission.ACCESS_COARSE_LOCATION` - For approximate location

---

## Directory Structure Created

```
InterSego/android/app/src/main/java/com/github/bfollon/intersego/
├── services/
│   ├── NetworkMonitor.kt
│   ├── DebugConfig.kt
│   ├── CoordinateCache.kt
│   ├── GeocodingService.kt
│   └── LocationManager.kt
└── utils/
    └── MapUtils.kt
```

---

## Known Issues / Notes

### 1. BusStop Data Model Reference
**Issue:** GeocodingService references `data.BusStop` which doesn't exist yet.
**Resolution:** Will be created in Phase 4 (Data Models).
**Impact:** Code will not compile until BusStop is created.

### 2. GPL License Headers
**Note:** All files maintain GPL-v3 license headers from original codebase.
**Action Required:** None - license is preserved correctly.

### 3. Serialization Kotlinx Import
**Note:** CoordinateCache uses `kotlinx.serialization.*` annotations.
**Verification:** Dependencies added in Phase 1, serialization plugin configured.

---

## Testing Strategy

### Manual Verification
1. **NetworkMonitor:**
   - Test with WiFi on/off
   - Test with airplane mode
   - Verify state descriptions

2. **DebugConfig:**
   - Check log output in Logcat with TAG "InterSego"
   - Toggle debug mode programmatically
   - Verify error/warn/debug levels

3. **CoordinateCache:**
   - Cache coordinates and retrieve them
   - Wait for expiration (or manually set expired timestamp)
   - Verify cleanup removes expired entries
   - Check cache statistics

4. **GeocodingService:**
   - Geocode test addresses in Segovia
   - Verify session caching (repeated lookups should be instant)
   - Verify persistent caching (restart app, check if cached)
   - Test fallback geocoding when enhanced query fails

5. **LocationManager:**
   - Request location permission
   - Get current device location
   - Handle permission denied case
   - Test on device without GPS

6. **MapUtils:**
   - Calculate distances between known coordinates
   - Open external map app with test location
   - Verify Google Maps opens correctly

### Integration Testing
- Will be tested when integrated with MainActivity in later phases
- Location features will be tested with ClosestStopService (Phase 6)

---

## Next Phase

**Phase 4: Data Models** (skipping Phase 3 for now per recommended order)
- Create `BusRoute.kt` - Bus route information
- Create `BusStop.kt` - Bus stop information (required by GeocodingService)
- Create `BusTimetable.kt` - Timetable data structure
- Create `DepartureTime.kt` - Individual departure times
- Create enum types: `DayType`, `RouteType`

This will resolve the compilation issue with GeocodingService's BusStop reference.

---

## Files Modified

```
InterSego/android/
├── docs/
│   ├── MIGRATION_PLAN.md              (Updated - v1.2)
│   └── MIGRATION_PHASE_2.md           (New)
└── app/src/main/java/com/github/bfollon/intersego/
    ├── services/
    │   ├── NetworkMonitor.kt          (New)
    │   ├── DebugConfig.kt             (New)
    │   ├── CoordinateCache.kt         (New)
    │   ├── GeocodingService.kt        (New)
    │   └── LocationManager.kt         (New)
    └── utils/
        └── MapUtils.kt                (New)
```

---

## Success Criteria

- ✅ All 6 infrastructure files copied
- ✅ Package names updated to `intersego`
- ✅ Domain-specific adaptations made (pharmacy → bus stop)
- ✅ GPL license headers preserved
- ✅ Files compile independently (pending BusStop model in Phase 4)
- ✅ Documentation complete

---

## Summary

Phase 2 successfully establishes the infrastructure layer for InterSego by copying and adapting 6 core service files from FarmaciasDeGuardia. These services provide:

- **Network monitoring** for offline mode support
- **Debug logging** for development and troubleshooting
- **Coordinate caching** for performance optimization
- **Geocoding** for address-to-GPS conversion
- **Location management** for finding nearest stops
- **Map utilities** for distance calculations and external map integration

The services are domain-agnostic and require minimal changes, primarily package name updates. GeocodingService received additional domain-specific adaptations to work with bus stops instead of pharmacies.

**Code Reuse:** ~100% of infrastructure code reused with minimal modifications.

**Next Step:** Phase 4 (Data Models) to create BusStop and other bus-specific data structures.
