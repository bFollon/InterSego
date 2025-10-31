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

**Quick Commands (Pre-approved in `.claude/settings.local.json`):**
```bash
# Check connected devices
~/Library/Android/sdk/platform-tools/adb devices

# Launch the app
~/Library/Android/sdk/platform-tools/adb shell am start -n com.github.bfollon.linecapp/.MainActivity

# Stop the app
~/Library/Android/sdk/platform-tools/adb shell am force-stop com.github.bfollon.linecapp

# View filtered logs (LineCapp only)
~/Library/Android/sdk/platform-tools/adb logcat LineCapp:D *:S

# View all logcat output
~/Library/Android/sdk/platform-tools/adb logcat

# Clear app data (for testing cache)
adb shell pm clear com.github.bfollon.linecapp
```

**Common Debug Tags:**
- `LineCapp` - General app logs
- `PDFURLScrapingService` - PDF URL scraping
- `PDFDownloadService` - PDF downloads
- `PDFURLRepository` - URL repository operations
- `M4Parser` - M4 route PDF parsing
- `NetworkMonitor` - Network connectivity
- `TimetableService` - Timetable loading

**Quick Restart & Debug:**
```bash
~/Library/Android/sdk/platform-tools/adb shell am force-stop com.github.bfollon.linecapp && \
~/Library/Android/sdk/platform-tools/adb shell am start -n com.github.bfollon.linecapp/.MainActivity && \
~/Library/Android/sdk/platform-tools/adb logcat LineCapp:D *:S
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

**Current State (Phase 7 - Active Development):**

**✅ Fully Functional Screens:**
- **RouteSelectionScreen** - Route selection interface
  - List of M1-M8 routes with cache status indicators
  - Material3 Card-based design
  - Navigates to RouteStopsScreen

- **RouteStopsScreen** - Visual route display (NEW - Oct 31)
  - Continuous vertical line connecting all stops
  - Chevron indicators for start/end stops
  - Circles for intermediate stops
  - Direction toggle button (Regular ↔ Reverse)
  - Tap to select stop and see departures
  - Status: ✅ Complete and polished

- **NextDepartureScreen** - Live departure information (NEW - Oct 31)
  - Prominent next departure with large time display
  - Real-time countdown timer (updates every 60 seconds)
  - Following 5 departures shown in compact pills
  - Auto day-type detection (Weekday/Weekend/Holiday)
  - Direction-aware timetable filtering
  - Handles edge cases (no more buses today)
  - Spanish localization
  - Status: ✅ Complete and functional

- **TimetableScreen** - Complete timetable view
  - Full departure times grouped by day type
  - Alternative to NextDepartureScreen for viewing all times

- **Navigation Flow:**
  - RouteSelection → RouteStops → NextDeparture (primary flow)
  - RouteSelection → Timetable (alternative view)
  - Direction passed through navigation chain
  - Status: ✅ Working correctly

**Theme & Resources:**
- `LineCappTheme` - Bus-themed blue/orange Material3 design
- `ic_route_start_chevron.xml` - Downward chevron SVG (NEW)
- `ic_route_end_chevron.xml` - Upward chevron SVG (NEW)
- `LoadingScreen` - Shown during app initialization

**🎉 M4 Route Status:** Complete end-to-end user flow operational

**📋 Future Enhancements (Optional):**
- `SettingsScreen` - App configuration
- `AboutScreen` - App information
- `CacheStatusScreen` - Cache management UI

## Development Phases & Current Status

**Project Status:** Backend complete. Active UI development (Phase 7).

**✅ Completed Phases:**
- Phase 1: Project setup, dependencies, build config
- Phase 2: Infrastructure services (NetworkMonitor, location, geocoding, caching)
- Phase 3: PDF infrastructure (download, cache, URL scraping)
- Phase 4: Bus domain data models
- Phase 5: PDF parsing for M4 (complete with full UI workflow)
- Phase 6: Business logic services (TimetableService, ClosestBusStopService)
- Phase 7 (Minimal): Basic UI shell (theme + placeholder screen)
- Phase 7 (M4 Route): Complete UI workflow for M4 ✅ (Oct 31, 2025)
  - RouteStopsScreen with visual route display
  - NextDepartureScreen with live countdown
  - Direction-aware filtering
  - Complete end-to-end user experience

**⚠️ Partially Implemented:**
- Phase 5: PDF parsing strategies (M4 ✅ complete, M1-M3, M5-M8 pending)

**❌ Not Yet Implemented:**
- Phase 7 (Other Routes): UI for M1-M3, M5-M8 (parsers needed first)
- Phase 8: ViewModels and state management (optional - direct service calls work)
- Phase 9: Repository layer (optional, may integrate into services)
- Phase 10: Testing, configuration, polish

**Current Priority:**
- Implement PDF parsers for M1-M3, M5-M8 (same UI screens can be reused)

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

**Supported PDF Filename Formats:**
- Old format: `SEGOVIA-M4.pdf` → Route ID: "M4"
- New format: `M4.pdf` → Route ID: "M4"
- New with date: `M5-septiembre-2024.pdf` → Route ID: "M5"

The scraper (`PDFURLScrapingService`) finds ALL .pdf files and tries multiple regex patterns to extract route IDs.

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
│   │   ├── RouteSelectionScreen.kt  # Route selection list
│   │   ├── RouteStopsScreen.kt      # Visual route display ✅ NEW
│   │   ├── NextDepartureScreen.kt   # Live countdown timer ✅ NEW
│   │   ├── TimetableScreen.kt       # Complete timetable view
│   │   └── MainScreen.kt            # Legacy demo screen (unused)
│   └── components/               # (Future: reusable UI components)
├── utils/
│   └── MapUtils.kt               # Distance calculations, map intents
└── res/
    ├── drawable/                 # Vector drawables
    │   ├── ic_route_start_chevron.xml   # Start indicator ✅ NEW
    │   └── ic_route_end_chevron.xml     # End indicator ✅ NEW
    └── mipmap-*/                # App launcher icons
        ├── ic_launcher.png           # Main app icon
        ├── ic_launcher_round.png     # Rounded app icon
        └── ic_launcher_foreground.png # Adaptive icon foreground
```

**App Icon:**
- Minimalistic bus timetable design with blue (#1c74d3) and orange theme colors
- Adaptive icon support for Android 8.0+ (API 26+)
- Source files: `../resources/icons/ic_launcher-6905343ae8c3c/`

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

**Phase 5 TODOs (HIGH PRIORITY):**
- Implement PDF parsing strategies for M1-M3, M5-M8
- Use M4Parser as template/reference
- Test each parser with real PDF files

**Phase 7 TODOs (HIGH PRIORITY - ACTIVE DEVELOPMENT):**
- Design and implement proper M4 route screen
- Enhance route selection UI
- Redesign timetable display
- Add proper loading/error states
- Add Settings screen (backend APIs ready)
- Add About screen
- Add Cache Status screen
- Add offline warning banner

**Phase 8 TODOs (OPTIONAL):**
- Create ViewModels for state management (currently using direct service calls)
- Add proper state hoisting patterns

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
- Commit after completing every feature.
- Do not put any claude co-authorig reference of link to claude.ai in the commits. No promotion.
- Before committing, allow the user to test the changes via launching the app. Only commit after confirmation.