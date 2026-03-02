# InterSego Migration Plan

**Source:** FarmaciasDeGuardia Android App
**Target:** InterSego Bus Timetable App
**Date Created:** October 12, 2025

## Overview

This document outlines the complete migration strategy for adapting the FarmaciasDeGuardia Android app architecture to create InterSego, a bus timetable application for Segovia, Spain.

### App Comparison

#### FarmaciasDeGuardia
- Downloads and parses pharmacy duty schedule PDFs from Segovia health authorities
- Features: PDF caching, geolocation (nearest on-duty pharmacy), offline mode, URL scraping/validation
- Architecture: Jetpack Compose, Strategy Pattern for PDF parsing, 3-tier caching

#### InterSego
- Downloads and parses bus timetable PDFs from Segovia transportation authority
- Will need: PDF caching, geolocation (nearest bus stop), offline mode, URL scraping/validation
- Target architecture: Same foundation as FarmaciasDeGuardia

### Code Reuse Estimate
- **~30% Copy Directly**: Infrastructure, utilities, theme
- **~40% Adapt**: Services, data models, UI components
- **~30% New Code**: PDF parsing strategies, bus domain logic, specific UI screens

---

## Migration Phases

### ✅ Phase 1: Project Setup & Dependencies
**Status:** Complete
**Documentation:** [MIGRATION_PHASE_1.md](./MIGRATION_PHASE_1.md)

- Configure Gradle build system
- Add Jetpack Compose + Material3 dependencies
- Add PDF processing (iText7), networking (OkHttp), location services
- Configure AndroidManifest permissions
- Enable release build optimizations

---

### 📋 Phase 2: Infrastructure Layer (Copy with Minimal Changes)

Copy domain-agnostic services that work regardless of domain (pharmacy vs bus):

#### Services to Copy As-Is
| **File** | **Purpose** | **Changes Needed** |
|----------|-------------|-------------------|
| `services/NetworkMonitor.kt` | Network connectivity monitoring | Package name only |
| `services/DebugConfig.kt` | Debug logging configuration | Package name only |
| `services/CoordinateCache.kt` | Geocoding cache for addresses | Package name only |
| `services/GeocodingService.kt` | Address-to-coordinate conversion | Package name only |
| `services/LocationManager.kt` | User location services | Package name only |

**Note:** `RouteCache.kt` and `RoutingService.kt` are not needed for InterSego as we don't require route caching to bus stops.

#### Utilities to Copy
| **File** | **Purpose** | **Changes Needed** |
|----------|-------------|-------------------|
| `utils/MapUtils.kt` | Distance calculations, map intents | Package name only |

**Package Name Changes:**
- From: `com.github.bfollon.farmaciasdeguardiaensegovia`
- To: `intersego`

**Deliverables:**
- All infrastructure services copied and compiling
- Unit tests adapted (if any)

---

### 📋 Phase 3: PDF Infrastructure (Copy with Adaptations)

Adapt PDF downloading, caching, and URL management for bus timetables:

#### Files to Adapt
| **Original File** | **Target File** | **Key Changes** |
|-------------------|-----------------|-----------------|
| `services/PDFDownloadService.kt` | *(Same)* | Update comments/naming for bus context |
| `services/PDFCacheManager.kt` | *(Same)* | Rename cache directories (e.g., `TimetableCache`) |
| `services/PDFURLScrapingService.kt` | *(Same)* | Update scraping logic for bus website, new regex patterns |
| `repositories/PDFURLRepository.kt` | *(Same)* | Add hardcoded fallback URLs for bus routes |

**Specific Adaptations:**
- **URL Scraping:** Update to target Segovia bus transportation website
- **Regex Patterns:** Extract bus timetable PDF URLs instead of pharmacy PDFs
- **Cache Directories:**
  - `ScheduleCache` → `TimetableCache`
  - `PharmacyPDFs` → `BusPDFs`

**Deliverables:**
- PDF download service functional for bus PDFs
- URL scraping adapted for bus website
- PDF caching working with new directory structure

---

### 📋 Phase 4: Data Models (Transform Domain)

Transform pharmacy-specific models to bus transportation domain:

#### Model Transformations
| **FarmaciasDeGuardia Model** | **InterSego Model** | **Key Changes** |
|------------------------------|-------------------|-----------------|
| `data/Pharmacy.kt` | `data/BusStop.kt` | Replace: name, address, phone → name, address, routes serving stop |
| `data/Region.kt` | `data/BusRoute.kt` | Replace: region metadata → route number, name, PDF URL, origin, destination |
| `data/PharmacySchedule.kt` | `data/BusSchedule.kt` | Replace: duty assignments → departure times, days of week |
| `data/DutyTimeSpan.kt` | `data/TimeOfDay.kt` OR *(remove)* | Consider removing or adapting for morning/afternoon/evening |
| `data/DutyDate.kt` | `data/ScheduleDate.kt` | Keep date parsing logic, Spanish month handling |
| `data/ZBS.kt` | *(Remove or adapt)* | Only needed if multiple bus service zones exist |

#### New Models Needed
```kotlin
// Core bus models
data class BusRoute(
    val id: String,                    // "L1", "L2", "interurban-40"
    val number: String,                // "1", "2", "40"
    val name: String,                  // "Centro - San Lorenzo"
    val origin: String,
    val destination: String,
    val pdfURL: String,
    val routeType: RouteType           // URBAN, INTERURBAN
)

data class BusStop(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val routesServed: List<String>     // Route IDs
)

data class BusTimetable(
    val routeId: String,
    val stopId: String,
    val date: ScheduleDate,
    val departures: List<DepartureTime>,
    val dayType: DayType               // WEEKDAY, WEEKEND, HOLIDAY
)

data class DepartureTime(
    val hour: Int,
    val minute: Int,
    val notes: String? = null          // "Solo laborables", etc.
) {
    fun toDisplayString(): String = "%02d:%02d".format(hour, minute)
}

enum class DayType {
    WEEKDAY,
    WEEKEND,
    HOLIDAY
}

enum class RouteType {
    URBAN,        // City buses
    INTERURBAN    // Between cities/towns
}
```

**Deliverables:**
- All bus domain models defined
- Serialization annotations added
- Data model unit tests

---

### 📋 Phase 5: PDF Parsing Strategy (Custom Implementation)

This is the most domain-specific phase - requires new implementation for bus timetable layouts.

#### Strategy Pattern Structure
```
PDFProcessingService (coordinator)
├── BusTimetableParser (interface) - replaces PDFParsingStrategy
├── ColumnBasedPDFParser (base class - reuse from FarmaciasDeGuardia)
└── Route-specific parsers:
    ├── SegoviaUrbanBusParser (if city buses have common format)
    ├── SegoviaInterurbanParser (for interurban routes)
    └── Route{X}CustomParser (if specific routes need special handling)
```

#### Files to Adapt/Create
| **Action** | **File** | **Purpose** |
|------------|----------|-------------|
| Adapt | `services/PDFProcessingService.kt` | Update to use `BusTimetableParser` interface |
| Create | `services/pdfparsing/BusTimetableParser.kt` | New interface for bus parsers |
| Reuse | `services/pdfparsing/ColumnBasedPDFParser.kt` | Keep column-based extraction utilities |
| Create | `services/pdfparsing/strategies/SegoviaUrbanBusParser.kt` | Parser for urban bus timetables |
| Create | `services/pdfparsing/strategies/SegoviaInterurbanParser.kt` | Parser for interurban timetables |

#### Implementation Approach
1. **Analyze PDF Layouts:**
   - Download sample bus timetable PDFs
   - Identify column structures (time, stop names, notes)
   - Document any variations between routes

2. **Create Base Parser:**
   ```kotlin
   interface BusTimetableParser {
       fun parse(pdfPath: String): List<BusTimetable>
       fun canParse(routeId: String): Boolean
   }
   ```

3. **Implement Strategy Registration:**
   ```kotlin
   class PDFProcessingService {
       private val parsers = mutableMapOf<String, BusTimetableParser>()

       fun registerParser(routeId: String, parser: BusTimetableParser) {
           parsers[routeId] = parser
       }

       fun parseTimetable(routeId: String, pdfPath: String): List<BusTimetable> {
           val parser = parsers[routeId] ?: throw IllegalArgumentException(...)
           return parser.parse(pdfPath)
       }
   }
   ```

4. **Extract Key Information:**
   - Route number/name
   - Stop names (in order)
   - Departure times (hour:minute format)
   - Day type indicators (weekday/weekend)
   - Special notes (holidays, seasonal variations)

#### Techniques to Reuse from FarmaciasDeGuardia
- **Column-based extraction:** Define exact PDF coordinate boundaries
- **Row iteration:** Process line-by-line within columns
- **Text cleaning:** Remove whitespace, normalize formatting
- **Regex patterns:** Extract time strings (HH:MM), route numbers

**Deliverables:**
- Bus timetable parser interface defined
- At least one working parser implementation
- Parsing tests with sample PDFs
- Documentation of PDF layouts

---

### 📋 Phase 6: Business Logic Services (Adapt)

Adapt core business logic from pharmacy domain to bus domain:

#### Service Adaptations
| **Original Service** | **Adapted Service** | **Key Changes** |
|---------------------|--------------------|--------------------|
| `services/ScheduleService.kt` | `services/TimetableService.kt` | Load bus schedules instead of pharmacy schedules |
| `services/ScheduleCacheService.kt` | `services/TimetableCacheService.kt` | Cache JSON timetables, validate with PDF timestamps |
| `services/ClosestPharmacyService.kt` | `services/ClosestStopService.kt` | Find nearest bus stop instead of pharmacy |

#### ScheduleService → TimetableService
**Original Logic:** Load pharmacy on-duty schedules, find current pharmacy
**New Logic:** Load bus timetables, find next departures

Key method adaptations:
```kotlin
// Original
fun findCurrentSchedule(schedules: List<PharmacySchedule>, date: Date): PharmacySchedule?

// Adapted
fun findNextDepartures(
    timetables: List<BusTimetable>,
    stopId: String,
    currentTime: LocalDateTime,
    limit: Int = 5
): List<DepartureTime>
```

#### ScheduleCacheService → TimetableCacheService
**Same core logic:** JSON serialization, metadata tracking, cache validation
**Changes:** Directory names, data model references

Cache structure:
```
Documents/TimetableCache/
├── route-L1.json          # Timetable data
├── route-L1.meta.json     # Metadata (PDF timestamp, cache date)
├── route-L2.json
└── route-L2.meta.json
```

#### ClosestPharmacyService → ClosestStopService
**Same core logic:** Geocoding, distance calculation, routing
**Changes:** Query bus stops instead of pharmacies

```kotlin
// Original
suspend fun findClosestPharmacy(
    pharmacies: List<Pharmacy>,
    userLocation: Location
): Pharmacy?

// Adapted
suspend fun findClosestStop(
    stops: List<BusStop>,
    userLocation: Location
): BusStop?
```

**Deliverables:**
- All business services adapted and compiling
- 3-tier caching working for timetables
- Nearest stop logic functional

---

### 📋 Phase 7: UI Layer (Adapt)

Transform UI from pharmacy schedules to bus timetables:

#### Theme (Copy Directly)
| **File** | **Changes** |
|----------|-------------|
| `ui/theme/Color.kt` | Update brand colors for InterSego |
| `ui/theme/Theme.kt` | Rename theme: `FarmaciasDeGuardiaEnSegoviaTheme` → `InterSegoTheme` |
| `ui/theme/Type.kt` | Copy as-is |

#### Reusable Components (Copy/Adapt)
| **Original Component** | **InterSego Component** | **Changes** |
|------------------------|------------------------|-------------|
| `ui/components/OfflineWarningCard.kt` | *(Copy)* | No changes needed |
| `ui/components/PharmacyCard.kt` | `ui/components/BusStopCard.kt` | Display stop name, routes, next departures |
| `ui/components/ShiftInfoCard.kt` | `ui/components/TimetableCard.kt` | Display departure times for a route |
| `ui/components/ClosestPharmacyButton.kt` | `ui/components/ClosestStopButton.kt` | Update text/icon |

#### Screens to Adapt
| **Original Screen** | **InterSego Screen** | **Purpose** |
|---------------------|---------------------|-------------|
| `MainActivity.kt` | *(Adapt)* | App initialization, navigation setup |
| `SplashScreen.kt` | *(Copy)* | Splash screen with cache loading |
| `ui/screens/MainScreen.kt` | *(Adapt)* | Route selection instead of region selection |
| `ui/screens/ScheduleScreen.kt` | `ui/screens/TimetableScreen.kt` | Display bus departure times |
| `ui/screens/SettingsScreen.kt` | *(Copy/Adapt)* | Settings (mostly reusable) |
| `ui/screens/AboutScreen.kt` | *(Adapt)* | Update app info, branding |
| `ui/screens/CacheStatusScreen.kt` | *(Copy)* | Cache management |
| `ui/screens/CacheRefreshScreen.kt` | *(Copy)* | Force refresh |

#### New Screens Needed
```kotlin
// Route selection screen
@Composable
fun RouteSelectionScreen(
    routes: List<BusRoute>,
    onRouteSelected: (BusRoute) -> Unit
)

// Stop selection for a route
@Composable
fun StopSelectionScreen(
    routeId: String,
    stops: List<BusStop>,
    onStopSelected: (BusStop) -> Unit
)

// Timetable display (by stop and route)
@Composable
fun TimetableScreen(
    routeId: String,
    stopId: String,
    timetable: BusTimetable,
    onBack: () -> Unit
)
```

#### Navigation Structure
```
SplashScreen
    ↓
MainScreen (Route Selection)
    ↓
StopSelectionScreen
    ↓
TimetableScreen
    ├→ Settings Modal
    └→ About Modal
```

**Deliverables:**
- Theme customized for InterSego
- Core UI components adapted
- All screens implemented
- Navigation flow functional

---

### 📋 Phase 8: ViewModels (Adapt)

Adapt ViewModels to work with bus domain:

#### ViewModel Adaptations
| **Original ViewModel** | **InterSego ViewModel** | **Key Changes** |
|------------------------|------------------------|-----------------|
| `viewmodels/ScheduleViewModel.kt` | `viewmodels/TimetableViewModel.kt` | Load/display timetables, manage route/stop selection |
| `viewmodels/ClosestPharmacyViewModel.kt` | `viewmodels/ClosestStopViewModel.kt` | Find nearest stop, handle location permissions |
| `viewmodels/SplashViewModel.kt` | *(Copy)* | Cache initialization |
| `viewmodels/CacheRefreshViewModel.kt` | *(Copy)* | Cache refresh logic |
| `viewmodels/CacheStatusViewModel.kt` | *(Copy)* | Cache status display |

#### Key ViewModel Methods

**TimetableViewModel:**
```kotlin
class TimetableViewModel : ViewModel() {
    private val _selectedRoute = MutableStateFlow<BusRoute?>(null)
    val selectedRoute: StateFlow<BusRoute?> = _selectedRoute

    private val _selectedStop = MutableStateFlow<BusStop?>(null)
    val selectedStop: StateFlow<BusStop?> = _selectedStop

    private val _nextDepartures = MutableStateFlow<List<DepartureTime>>(emptyList())
    val nextDepartures: StateFlow<List<DepartureTime>> = _nextDepartures

    fun loadTimetable(routeId: String, stopId: String) {
        viewModelScope.launch {
            val timetable = timetableService.loadTimetable(routeId, stopId)
            val departures = timetableService.findNextDepartures(
                timetable,
                stopId,
                LocalDateTime.now()
            )
            _nextDepartures.value = departures
        }
    }
}
```

**ClosestStopViewModel:**
```kotlin
class ClosestStopViewModel : ViewModel() {
    fun findNearestStop(stops: List<BusStop>) {
        viewModelScope.launch {
            val location = locationManager.getCurrentLocation()
            val closest = closestStopService.findClosestStop(stops, location)
            _closestStop.value = closest
        }
    }
}
```

**Deliverables:**
- All ViewModels adapted
- State management working
- Proper lifecycle handling

---

### 📋 Phase 9: Repositories (Adapt)

Adapt data access layer for bus domain:

#### Repository Adaptations
| **Original Repository** | **InterSego Repository** | **Purpose** |
|-------------------------|-------------------------|-------------|
| `repositories/PDFURLRepository.kt` | *(Adapt)* | Hardcoded fallback URLs for bus route PDFs |
| `repositories/PharmacyScheduleRepository.kt` | `repositories/BusScheduleRepository.kt` | Data access layer for timetables |

**PDFURLRepository:**
```kotlin
object PDFURLRepository {
    fun getFallbackURL(routeId: String): String {
        return when (routeId) {
            "L1" -> "https://www.example.com/bus-l1-timetable.pdf"
            "L2" -> "https://www.example.com/bus-l2-timetable.pdf"
            // ... more routes
            else -> throw IllegalArgumentException("Unknown route: $routeId")
        }
    }
}
```

**BusScheduleRepository:**
```kotlin
class BusScheduleRepository(
    private val timetableService: TimetableService,
    private val pdfProcessingService: PDFProcessingService
) {
    suspend fun getTimetable(routeId: String): List<BusTimetable> {
        // Try cache first
        val cached = timetableService.loadFromCache(routeId)
        if (cached != null) return cached

        // Parse PDF
        val pdfPath = downloadPDF(routeId)
        val timetables = pdfProcessingService.parseTimetable(routeId, pdfPath)

        // Cache result
        timetableService.saveToCache(routeId, timetables)

        return timetables
    }
}
```

**Deliverables:**
- Repositories implemented
- Data access layer working
- Proper error handling

---

### 📋 Phase 10: Testing & Configuration

Final phase: testing, configuration, and polish.

#### Test Structure
Copy test templates and create bus-specific tests:

**Unit Tests:**
- `test/services/TimetableServiceTest.kt`
- `test/services/PDFProcessingServiceTest.kt`
- `test/pdfparsing/BusTimetableParserTest.kt`
- `test/data/BusRouteTest.kt`

**Instrumented Tests:**
- `androidTest/ui/TimetableScreenTest.kt`
- `androidTest/services/LocationManagerTest.kt`

#### Configuration Files

**AppConfig.kt:**
```kotlin
object AppConfig {
    const val APP_NAME = "InterSego"
    const val APP_VERSION = "1.0.0"
    const val CACHE_DIR = "TimetableCache"
    const val PDF_CACHE_DIR = "BusPDFs"
    const val MAX_CACHE_AGE_DAYS = 7

    // Bus service URLs
    const val BUS_WEBSITE_URL = "https://www.segovia.es/transportes"
    const val PDF_BASE_URL = "https://www.segovia.es/pdf/bus/"
}
```

#### Resources to Update
- `res/values/strings.xml` - App name, UI strings
- `res/values/colors.xml` - Brand colors
- `res/mipmap-*/` - App launcher icons
- `res/drawable/` - UI icons, graphics

#### ProGuard Rules
Update `proguard-rules.pro` for release builds:
```proguard
# iText PDF library
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep data models
-keep class intersego.data.** { *; }
```

**Deliverables:**
- All tests passing
- Release build working
- App configuration complete
- Documentation updated

---

## Detailed File-by-File Checklist

### ✅ Copy Directly (Minimal Changes)
- [ ] `services/NetworkMonitor.kt`
- [ ] `services/DebugConfig.kt`
- [ ] `services/CoordinateCache.kt`
- [ ] `services/GeocodingService.kt`
- [ ] `services/LocationManager.kt`
- [ ] `utils/MapUtils.kt`
- [ ] `ui/theme/Color.kt` (update colors)
- [ ] `ui/theme/Theme.kt` (update theme name)
- [ ] `ui/theme/Type.kt`
- [ ] `ui/components/OfflineWarningCard.kt`

**Skipped (Not needed for InterSego):**
- ~~`services/RouteCache.kt`~~ - Route caching not required
- ~~`services/RoutingService.kt`~~ - Route calculation not required

### 🔄 Adapt/Rename
- [ ] `services/PDFDownloadService.kt`
- [ ] `services/PDFCacheManager.kt`
- [ ] `services/PDFProcessingService.kt`
- [ ] `services/PDFURLScrapingService.kt`
- [ ] `services/ScheduleService.kt` → `TimetableService.kt`
- [ ] `services/ScheduleCacheService.kt` → `TimetableCacheService.kt`
- [ ] `services/ClosestPharmacyService.kt` → `ClosestStopService.kt`
- [ ] `repositories/PDFURLRepository.kt`
- [ ] `repositories/PharmacyScheduleRepository.kt` → `BusScheduleRepository.kt`
- [ ] `data/Pharmacy.kt` → `BusStop.kt`
- [ ] `data/Region.kt` → `BusRoute.kt`
- [ ] `data/PharmacySchedule.kt` → `BusSchedule.kt`
- [ ] `data/DutyDate.kt` → `ScheduleDate.kt`
- [ ] All ViewModels
- [ ] All UI Screens
- [ ] All UI Components

### ✏️ Write from Scratch
- [ ] `services/pdfparsing/BusTimetableParser.kt` (interface)
- [ ] `services/pdfparsing/strategies/SegoviaUrbanBusParser.kt`
- [ ] `services/pdfparsing/strategies/SegoviaInterurbanParser.kt`
- [ ] `data/BusRoute.kt`
- [ ] `data/BusStop.kt`
- [ ] `data/BusTimetable.kt`
- [ ] `data/DepartureTime.kt`
- [ ] `data/DayType.kt` (enum)
- [ ] `data/RouteType.kt` (enum)
- [ ] `data/AppConfig.kt` (bus-specific config)
- [ ] Bus-specific UI screens
- [ ] Tests for new components

---

## Recommended Implementation Order

1. ✅ **Phase 1** - Project Setup (COMPLETE)
2. **Phase 2** - Infrastructure Layer (copy utilities)
3. **Phase 4** - Data Models (define bus domain)
4. **Phase 3** - PDF Infrastructure (download/cache)
5. **Phase 5** - PDF Parsing (bus timetable parsers)
6. **Phase 6** - Business Logic Services
7. **Phase 7** - UI Theme + Reusable Components
8. **Phase 8** - ViewModels
9. **Phase 7** - UI Screens (continued)
10. **Phase 9** - Repositories
11. **Phase 10** - Testing & Polish

---

## Architecture Patterns to Preserve

### Strategy Pattern for PDF Parsing
- Central coordinator: `PDFProcessingService`
- Parser interface: `BusTimetableParser`
- Strategy registration: Map route IDs to parser instances
- Keep column-based extraction techniques

### Three-Tier Caching
1. **Memory Cache** - In-memory dictionary in service layer
2. **Persistent Cache** - JSON files in app Documents directory
3. **Source of Truth** - PDF parsing (fallback)

Cache validation: Compare PDF modification timestamps

### Offline-First Architecture
- **NetworkMonitor** - Check connectivity before network calls
- **Cache-first loading** - Always try cache before network
- **UI Indicators** - OfflineWarningCard, cache timestamps
- **Graceful degradation** - App functional without network

### Clean Architecture Layers
```
UI Layer (Compose)
    ↓
ViewModel Layer (State Management)
    ↓
Repository Layer (Data Access)
    ↓
Service Layer (Business Logic)
    ↓
Data Layer (Models, Cache, Network)
```

---

## Success Criteria

### Phase Completion
- [ ] All phases 1-10 completed
- [ ] All checklist items marked complete
- [ ] Build succeeds without errors
- [ ] All tests passing

### Functional Requirements
- [ ] App launches and shows route selection
- [ ] Timetables load from PDFs or cache
- [ ] Offline mode works (cached data)
- [ ] Nearest stop finding works
- [ ] Map integration opens correctly
- [ ] Settings and cache management functional

### Code Quality
- [ ] No compiler warnings (except deprecations)
- [ ] All TODOs resolved
- [ ] Code documented with KDoc comments
- [ ] GPL-v3 license headers on all files
- [ ] No hardcoded strings (use strings.xml)

### Performance
- [ ] App starts in < 3 seconds
- [ ] Timetable loads in < 2 seconds (cached)
- [ ] PDF parsing completes in < 10 seconds
- [ ] Smooth 60fps UI scrolling

---

## Known Challenges

### PDF Layout Variations
**Challenge:** Bus timetable PDFs may have inconsistent layouts across routes.
**Mitigation:**
- Implement multiple parser strategies
- Add fallback generic parser
- Document PDF format for each route

### Location Permissions
**Challenge:** Android location permissions require runtime handling.
**Solution:** Copy permission handling from FarmaciasDeGuardia's `LocationManager.kt`

### Route Complexity
**Challenge:** Different route types (urban, interurban) may need different data models.
**Solution:** Use `RouteType` enum to handle variations, create base models that work for both

### PDF URL Changes
**Challenge:** Bus authority may change PDF URLs without notice.
**Solution:**
- Implement URL scraping service
- Maintain fallback hardcoded URLs
- Add URL validation before download

---

## Future Enhancements (Post-MVP)

- Real-time bus tracking integration
- Push notifications for route delays
- Favorite routes/stops
- Trip planner (multi-leg journeys)
- Widget for home screen (next departures)
- Share timetable via WhatsApp/email
- Accessibility improvements (TalkBack support)
- Multiple language support (Spanish/English)

---

## References

- **Source App:** FarmaciasDeGuardia Android (`FarmaciasDeGuardia/android/`)
- **Architecture Documentation:** `FarmaciasDeGuardia/CLAUDE.md`
- **Phase 1 Documentation:** [MIGRATION_PHASE_1.md](./MIGRATION_PHASE_1.md)
- **Android Compose Docs:** https://developer.android.com/jetpack/compose
- **iText7 PDF Docs:** https://itextpdf.com/en/resources/api-documentation

---

## Revision History

| **Date** | **Version** | **Changes** |
|----------|-------------|-------------|
| 2025-10-12 | 1.0 | Initial migration plan created |
| 2025-10-12 | 1.1 | Phase 1 completed, documented |
| 2025-10-12 | 1.2 | Removed RouteCache and RoutingService from Phase 2 (not needed for InterSego) |
