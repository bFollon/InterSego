# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**LineCapp** is an Android bus timetable app for Segovia, Spain. It downloads and parses bus timetable PDFs from Linecar (the local bus company), provides offline caching, and helps users find the nearest bus stop using geolocation.

**Architecture Source:** Adapted from FarmaciasDeGuardia (pharmacy duty schedule app). The migration strategy and detailed phase documentation are in `docs/MIGRATION_PLAN.md`.

**Key Technologies:**
- Jetpack Compose + Material3 for UI
- Kotlin Coroutines for async operations
- iText7 for PDF parsing
- OkHttp for HTTP networking
- Strategy Pattern for PDF parsing
- Three-tier caching system (memory → persistent → PDF)

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
adb shell am start -n com.github.bfollon.linecapp/.MainActivity
```

### Debugging
```bash
# View app logs (filtered)
adb logcat | grep -E "LineCapp|PDFURLScrapingService|PDFURLRepository|TimetableService"

# View all app logs
adb logcat -s "LineCappDebug"

# Clear app data (for testing cache)
adb shell pm clear com.github.bfollon.linecapp
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

### Three-Tier Caching System

**Tier 1: Memory Cache** (fastest)
- In-memory dictionaries in service classes
- Cleared on app restart
- Example: `TimetableService.cachedTimetables`

**Tier 2: Persistent Cache** (fast)
- JSON files in app's Documents directory
- Validated against PDF modification timestamps
- Managed by `TimetableCacheService` and `PDFCacheManager`
- Structure: `Documents/TimetableCache/route-{routeId}.json`

**Tier 3: PDF Parsing** (slowest, source of truth)
- Downloads PDF from URL
- Parses using strategy pattern
- Caches results in Tier 1 & 2
- Falls back to offline if no network

### PDF URL Management

**Self-Healing URL System** (`PDFURLRepository`):
1. **Scraping:** Fetches PDF URLs from https://www.linecar.es/metropolitano/segovia/
2. **Persistence:** Stores scraped URLs in SharedPreferences
3. **Validation:** HEAD requests to verify URLs are still valid
4. **Healing:** If URL returns 404, automatically re-scrapes website for fresh URLs

**URL Resolution Priority:**
1. Scraped URLs (from website)
2. Persisted URLs (cached scrapes)
3. Fallback URLs (hardcoded in PDFURLRepository)

### Strategy Pattern for PDF Parsing

**Phase 5 (Not Yet Implemented):**
```
PDFProcessingService (coordinator)
    ↓
BusTimetableParser (interface)
    ↓
Route-specific parser strategies:
├── SegoviaUrbanBusParser
├── SegoviaInterurbanParser
└── RouteXCustomParser
```

PDF parsing will use column-based extraction techniques (similar to FarmaciasDeGuardia).

### Service Layer Structure

**Infrastructure Services** (domain-agnostic):
- `NetworkMonitor` - Connectivity checks
- `CoordinateCache` - Geocoding results cache
- `GeocodingService` - Address → coordinates (mock for now, uses coordinate cache)
- `LocationManager` - User location services
- `DebugConfig` - Centralized debug logging

**PDF Services** (bus-specific):
- `PDFDownloadService` - Downloads PDFs from URLs
- `PDFCacheManager` - Manages PDF file cache
- `PDFURLScrapingService` - Scrapes URLs from Linecar website
- `PDFURLRepository` - URL management with self-healing

**Business Logic Services**:
- `TimetableService` - Loads and caches bus timetables (three-tier cache)
- `TimetableCacheService` - Persistent JSON cache for timetables
- `ClosestBusStopService` - Finds nearest bus stop to user

### Data Models

**Core Domain:**
- `BusRoute` - Route metadata (id, number, name, origin, destination, PDF URL, type)
- `BusStop` - Stop metadata (id, name, address, coordinates, routes served)
- `BusTimetable` - Departure times for a route/stop/day
- `DepartureTime` - Single departure (hour, minute, notes)

**Enums:**
- `RouteType` - URBAN, INTERURBAN
- `DayType` - WEEKDAY, WEEKEND, HOLIDAY
- `RouteCacheStatus` - Cache state tracking

**Utilities:**
- `ScheduleDate` - Spanish date parsing with month names
- `PDFVersion` - PDF metadata tracking
- `UpdateProgressState` - Cache update progress

### Navigation & UI

**Current State (Phase 7 - Minimal):**
- Single screen: `MainScreen` (placeholder)
- Theme: `LineCappTheme` (bus-themed blue/orange colors)
- Navigation: Single route via `NavHost`

**Future Screens (Full Phase 7):**
- `RouteSelectionScreen` - Choose bus route
- `StopSelectionScreen` - Choose stop for route
- `TimetableScreen` - Display departure times
- `SettingsScreen`, `AboutScreen`, `CacheStatusScreen`

## Development Phases & Current Status

**Project Status:** Phase 7 (Minimal) complete - app compiles and launches

**✅ Completed Phases:**
- Phase 1: Project setup, dependencies, build config
- Phase 2: Infrastructure services (NetworkMonitor, location, geocoding, caching)
- Phase 3: PDF infrastructure (download, cache, URL scraping)
- Phase 4: Bus domain data models
- Phase 6: Business logic services (TimetableService, ClosestBusStopService)
- Phase 7 (Minimal): Basic UI shell (theme + placeholder screen)

**❌ Not Yet Implemented:**
- Phase 5: PDF parsing strategies (requires working app to test)
- Phase 7 (Full): Route selection, timetable display, settings screens
- Phase 8: ViewModels and state management
- Phase 9: Repository layer (optional, may integrate into services)
- Phase 10: Testing, configuration, polish

**Key Blockers Resolved:**
- MainActivity.kt created (resolves manifest error)
- Theme conflicts resolved (minimal XML theme)
- App now compiles and launches

See `docs/CURRENT_STATUS.md` for detailed status and `docs/MIGRATION_PLAN.md` for complete roadmap.

## Important Implementation Patterns

### Debug Logging
Always use `DebugConfig` for logging (controlled by `DebugConfig.DEBUG_ENABLED`):
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
Use `withContext(Dispatchers.IO)` for network/file operations:
```kotlin
suspend fun downloadPDF() = withContext(Dispatchers.IO) {
    // Network or file I/O here
}
```

### Offline-First Loading
Always check cache before network:
```kotlin
// 1. Check memory cache
if (memoryCache.contains(key)) return memoryCache[key]

// 2. Check persistent cache
val cached = loadFromDisk(key)
if (cached != null) return cached

// 3. Fetch from network (only if online)
if (NetworkMonitor.isOnline()) {
    return fetchFromNetwork(key)
}
```

### PDF URL Scraping
URLs are scraped on app startup (MainActivity) and validated periodically:
```kotlin
val pdfUrlRepository = PDFURLRepository.getInstance(this)
val success = pdfUrlRepository.initializeURLs()
```

Extract route ID from Linecar PDFs: `SEGOVIA-{ROUTE_ID}.pdf` → Route ID extracted with regex `SEGOVIA-([A-Z0-9]+)`

## Code Organization

```
app/src/main/java/com/github/bfollon/linecapp/
├── MainActivity.kt                  # App entry point
├── data/                           # Data models (BusRoute, BusStop, etc.)
├── repositories/                   # Data access layer
│   └── PDFURLRepository.kt        # URL management with self-healing
├── services/                      # Business logic & infrastructure
│   ├── NetworkMonitor.kt         # Connectivity checks
│   ├── DebugConfig.kt            # Debug logging
│   ├── LocationManager.kt        # User location
│   ├── GeocodingService.kt       # Address → coordinates
│   ├── CoordinateCache.kt        # Geocoding cache
│   ├── PDFDownloadService.kt     # PDF downloading
│   ├── PDFCacheManager.kt        # PDF file cache
│   ├── PDFURLScrapingService.kt  # URL scraping from website
│   ├── TimetableService.kt       # Timetable loading (three-tier cache)
│   ├── TimetableCacheService.kt  # Persistent JSON cache
│   └── ClosestBusStopService.kt  # Nearest stop finder
├── ui/
│   ├── theme/                    # Compose theme (Color, Theme, Type)
│   ├── screens/                  # Screen composables
│   │   └── MainScreen.kt        # Placeholder main screen
│   └── components/               # (Future: reusable UI components)
└── utils/
    └── MapUtils.kt               # Distance calculations, map intents
```

## Key Configuration Files

- `app/build.gradle.kts` - App dependencies and build config
- `gradle/libs.versions.toml` - Version catalog for dependencies
- `app/src/main/AndroidManifest.xml` - Permissions, MainActivity registration
- `gradle.properties` - Gradle JVM settings

## Common Development Scenarios

### Adding a New Service
1. Create service class in `services/` package
2. Add GPL-v3 license header
3. Use `DebugConfig` for logging
4. Initialize in `MainActivity.onCreate()` if needed
5. Document with KDoc comments

### Adding a New Data Model
1. Create data class in `data/` package
2. Add `@Serializable` annotation (Kotlin Serialization)
3. Include validation logic if needed
4. Add GPL-v3 license header

### Working with PDF URLs
```kotlin
// Get repository instance
val repository = PDFURLRepository.getInstance(context)

// Simple URL lookup (persisted or fallback)
val url = repository.getURL("M1")

// Self-healing URL resolution (validates + re-scrapes if needed)
when (val result = repository.resolveURLWithHealing("M1")) {
    is URLResolutionResult.Success -> useURL(result.url)
    is URLResolutionResult.Updated -> useURL(result.newUrl)
    is URLResolutionResult.Failed -> showError(result.message)
}
```

### Implementing PDF Parsing (Phase 5)
1. Define `BusTimetableParser` interface
2. Create parser strategies for different route types
3. Integrate with `TimetableService.loadTimetables()` (see TODO comments)
4. Use column-based extraction (similar to FarmaciasDeGuardia)
5. Cache parsed results

## Known Issues & TODOs

**Phase 5 TODOs:**
- Implement PDF parsing strategies
- Integrate parsing with TimetableService (see `TimetableService.kt:66-71`)

**Phase 7 TODOs:**
- Implement actual UI screens (route selection, timetable display)
- Add reusable UI components (cards, buttons)
- Add offline warning banner
- Implement navigation between screens

**Phase 8 TODOs:**
- Create ViewModels for state management
- Connect UI to business logic services

**Build Warnings:**
- 39 lint warnings (non-blocking, can be addressed in Phase 10)
- `jvmTarget` deprecation warning (non-breaking, style preference)

## References

**Documentation:**
- `docs/CURRENT_STATUS.md` - Current phase status and immediate next steps
- `docs/MIGRATION_PLAN.md` - Complete migration strategy from FarmaciasDeGuardia
- `docs/MIGRATION_PHASE_*.md` - Detailed documentation for each completed phase
- `docs/PDF_URL_SCRAPING_SETUP.md` - URL scraping configuration and testing

**Source Reference:**
- FarmaciasDeGuardia: `/Users/bruno.follon/Personal/dev/apps/FarmaciasDeGuardia/android/`
- Architecture patterns and services adapted from FarmaciasDeGuardia

**External:**
- Linecar website: https://www.linecar.es/metropolitano/segovia/
- Material3 Compose: https://developer.android.com/jetpack/compose/designsystems/material3
- iText7 PDF: https://itextpdf.com/en/resources/api-documentation
