# Android CLAUDE.md

This file provides Android-specific guidance for InterSego development.

**For project overview and repository structure, see `../CLAUDE.md` at repository root.**

## Android Implementation

**InterSego** is an Android bus timetable app for Segovia, Spain. It loads bus timetable data from bundled JSON assets and helps users find the nearest bus stop using geolocation.

**Architecture Source:** Adapted from FarmaciasDeGuardia (pharmacy duty schedule app).

**Key Technologies:**
- Jetpack Compose + Material3 for UI
- Kotlin Coroutines for async operations
- OkHttp for HTTP networking
- JSON-based timetable loading via `TimetableLoader`
- Two-tier caching system (memory → bundle JSON)

## Build & Development Commands

### Build
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean build
```

### Installation
```bash
# Install debug build on connected device/emulator
./gradlew installDebug

# Install and run
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n intersegoo/.MainActivity
```

### Debugging

**Quick Commands (Pre-approved in `.claude/settings.local.json`):**
```bash
# Check connected devices
~/Library/Android/sdk/platform-tools/adb devices

# Launch the app
~/Library/Android/sdk/platform-tools/adb shell am start -n intersegoo/.MainActivity

# Stop the app
~/Library/Android/sdk/platform-tools/adb shell am force-stop intersegoo

# View filtered logs (InterSego only)
~/Library/Android/sdk/platform-tools/adb logcat InterSego:D *:S

# View all logcat output
~/Library/Android/sdk/platform-tools/adb logcat

# Clear app data (for testing cache)
adb shell pm clear intersegoo
```

**Common Debug Tags:**
- `InterSego` - General app logs
- `NetworkMonitor` - Network connectivity
- `TimetableService` - Timetable loading
- `RouteDataService` - Route metadata queries

**Quick Restart & Debug:**
```bash
~/Library/Android/sdk/platform-tools/adb shell am force-stop intersegoo && \
~/Library/Android/sdk/platform-tools/adb shell am start -n intersegoo/.MainActivity && \
~/Library/Android/sdk/platform-tools/adb logcat InterSego:D *:S
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run specific test
./gradlew test --tests TimetableServiceTest
```

## High-Level Architecture

### Data Loading

All timetable data is loaded from bundled JSON assets via `TimetableLoader`. There is no network fetch or PDF parsing at runtime.

**Single-Tier Caching** (in `TimetableService`):
- **Tier 1: Memory cache** — in-process map, cleared on app restart; cache miss goes directly to `RouteDataService.parseTimetables()` (JSON bundle read)

### Service Layer Structure

**Infrastructure Services** (domain-agnostic):
- `NetworkMonitor` - Connectivity checks
- `CoordinateCache` - Geocoding results cache
- `GeocodingService` - Address → coordinates
- `LocationManager` - User location services
- `DebugConfig` - Centralized debug logging

**Timetable Services**:
- `TimetableLoader` - Reads `assets/timetables/{routeId}.json`, produces `List<BusTimetable>`
- `TimetableCacheService` - Fetches per-route JSON from server, disk cache + ETag, hot-swap flag for `TimetableService`
- `HolidayService` - Fetches/caches the festivo calendar (`GET /api/holidays`, disk cache + ETag, bundled `assets/holidays/2026.json` fallback); exposes `isHoliday(Calendar)` — not yet consumed by day-type resolution
- `RouteDataService` - Coordinator: wraps `TimetableLoader`, maintains stop→route index, exposes route variants/views/entries
- `TimetableService` - Memory cache + departure helpers over `RouteDataService`
- `DeparturesService` - Departure queries combining `RouteDataService` + `TimetableService`

**Map Services**:
- `PolylineLoader` - Reads `assets/route_polylines/{routeId}-{viewId}.json` (disk cache first), produces `List<GeoPoint>`
- `PolylineCacheService` - Fetches each `{routeId}-{viewId}` polyline from server, disk cache + ETag

**Location Services**:
- `ClosestStopFinderService` - Finds nearest bus stop to user

### Data Models

**Core Domain:**
- `BusRoute` - Route metadata (id, number, name, origin, destination, type)
- `BusStop` - Stop metadata (id, name, address, coordinates, routes served)
- `BusTimetable` - Departure times for a route/stop/day
- `DepartureTime` - Single departure (hour, minute, notes)

**Enums:**
- `RouteType` - URBAN, INTERURBAN
- `DayType` - WEEKDAY, WEEKEND, HOLIDAY
- `RouteCacheStatus` - Cache state tracking

**Utilities:**
- `ScheduleDate` - Spanish date parsing with month names
- `UpdateProgressState` - Cache update progress

### Navigation & UI

**✅ Fully Functional Screens:**
- **LandingScreen** - Home hub with route list and closest-stop button
- **RouteSelectionScreen** - Route selection interface
- **RouteStopsScreen** - Visual route display with direction toggle
- **NextDepartureScreen** - Live departure countdown with timeline
- **DayScheduleScreen** - Full day's departures with "Ahora" marker
- **RouteMapScreen** - Interactive OSMDroid map with stop markers
- **AllRoutesScreen** - Dropdown selector across all routes
- **AboutScreen** - App info, links, legal notice
- **RemindersScreen** - Bell-tap / long-press reminders with tutorial
- **LiveUpdatesTutorial** - Boarding confirmation onboarding

**Navigation Flow:**
- Landing → RouteStops → NextDeparture (primary)
- Landing → AllRoutes → RouteStops / NextDeparture / DaySchedule

## Development Phases

**All routes (M1–M8) are fully operational via JSON-based loading.**

**✅ Completed:**
- Phase 1–4: Infrastructure, domain models, location services
- Phase 5: All 8 routes migrated to `assets/timetables/{routeId}.json` (TimetableLoader)
- Phase 6: Business logic services (TimetableService, RouteDataService, ClosestStopFinderService)
- Phase 7: Full UI (all screens listed above)

**Next milestone:** Load timetable JSON from a remote server instead of bundle assets (see `docs/JSON_REFACTOR_CLEANUP.md`).

## Important Implementation Patterns

### Debug Logging
Always use `DebugConfig` for logging:
```kotlin
DebugConfig.debugPrint("Message")
DebugConfig.debugWarn("Warning")
DebugConfig.debugError("Error message", exception)
```

### Service Initialization
Services are initialized in `MainActivity.onCreate()`:
```kotlin
NetworkMonitor.initialize(this)
CoordinateCache.initialize(this)
```

### Coroutine Patterns
Use `withContext(Dispatchers.IO)` for file operations:
```kotlin
suspend fun loadData() = withContext(Dispatchers.IO) {
    // I/O here
}
```

### Timetable JSON Format
When editing a timetable JSON, update **all three copies**:
- `resources/timetables/{routeId}.json` (source of truth)
- `android/app/src/main/assets/timetables/{routeId}.json`
- `iOS/InterSego/Timetables/{routeId}.json`

See `docs/TIMETABLE_JSON_REFACTOR.md` for the full JSON schema.

## Code Organization

```
app/src/main/java/com/github/bfollon/intersego/
├── MainActivity.kt                  # App entry point
├── data/                           # Data models (BusRoute, BusStop, etc.)
├── services/                      # Business logic & infrastructure
│   ├── NetworkMonitor.kt         # Connectivity checks
│   ├── DebugConfig.kt            # Debug logging
│   ├── LocationManager.kt        # User location
│   ├── GeocodingService.kt       # Address → coordinates
│   ├── CoordinateCache.kt        # Geocoding cache
│   ├── TimetableLoader.kt        # Bundle JSON reader → List<BusTimetable>
│   ├── TimetableCacheService.kt  # Server fetch + disk cache for timetables
│   ├── HolidayService.kt         # Server fetch + disk cache for festivo calendar
│   ├── RouteDataService.kt   # Route metadata coordinator
│   ├── TimetableService.kt       # Memory cache + departure helpers
│   ├── PolylineLoader.kt         # Bundle JSON reader → List<GeoPoint>
│   ├── PolylineCacheService.kt   # Server fetch + disk cache for polylines
│   ├── DeparturesService.kt      # Departure queries
│   └── ClosestStopFinderService.kt # Nearest stop finder
├── ui/
│   ├── theme/                    # Compose theme (Color, Theme, Type)
│   └── screens/                  # Screen composables
└── res/
    ├── drawable/                 # Vector drawables
    └── mipmap-*/                # App launcher icons
```

## Key Configuration Files

- `app/build.gradle.kts` - App dependencies and build config
- `gradle/libs.versions.toml` - Version catalog for dependencies
- `app/src/main/AndroidManifest.xml` - Permissions, MainActivity registration
- `gradle.properties` - Gradle JVM settings

## Common Development Scenarios

### Adding a New Service
1. Create service class in `services/` package
2. Add source-available license header (see existing files for format)
3. Use `DebugConfig` for logging
4. Initialize in `MainActivity.onCreate()` if needed

### Adding a New Data Model
1. Create data class in `data/` package
2. Add `@Serializable` annotation (Kotlin Serialization)
3. Add source-available license header (see existing files for format)

### Development Best Practices

When completing major features or making significant changes:
1. **Update this file (`android/CLAUDE.md`)** with new components/screens
2. **Update `../CLAUDE.md` cross-platform feature tracker**
3. Only commit after user confirms the build works

## Known Issues & TODOs

- `TimetableScreen` exists but has no navigation entry point (dead code)

## References

**Documentation:**
- `docs/JSON_REFACTOR_CLEANUP.md` - Cleanup checklist for the JSON migration
- `docs/TIMETABLE_JSON_REFACTOR.md` - JSON timetable schema specification

**External:**
- Linecar website: https://www.linecar.es/metropolitano/segovia/
- Material3 Compose: https://developer.android.com/jetpack/compose/designsystems/material3
- Commit after completing every feature.
- Do not put any Claude co-authoring reference or link to claude.ai in commits.
- Before committing, allow the user to test the changes. Only commit after confirmation.
