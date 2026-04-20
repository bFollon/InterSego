# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**InterSego** is a bus timetable app for Segovia, Spain. It downloads and parses bus timetable PDFs from Linecar (the local bus company), provides offline caching, and helps users find the nearest bus stop using geolocation.

**Current Status:** Android and iOS implementations at feature parity. Complete end-to-end user flow operational for M4 and M6 routes on both platforms.

## Cross-Platform Feature Tracker

**IMPORTANT: This tracker MUST be updated whenever a feature is added, modified, or removed on ANY platform.** When implementing changes, check this table and update the status for the affected platform. If a feature is added to one platform, add a row here even if the other platform doesn't have it yet — this is how we track drift.

### Core Infrastructure

| Feature | Android | iOS | Notes |
|---|---|---|---|
| Network monitoring | ✅ | ✅ | |
| PDF download service | ✅ | ✅ | |
| PDF URL scraping (self-healing) | ✅ | ✅ | |
| PDF cache manager (version checking) | ✅ | ✅ | |
| Three-tier caching (memory → JSON → PDF) | ✅ | ✅ | |
| Timetable cache service | ✅ | ✅ | |
| TimetableLoader (JSON-based) | ✅ | ✅ | Reads `assets/timetables/{routeId}.json` (Android) / `Timetables/{routeId}.json` (iOS) from bundle; produces `List<BusTimetable>`; **`resources/timetables/` is source-of-truth for humans only — neither app reads it at runtime; when editing a JSON timetable you MUST update all three copies**: `resources/timetables/`, `android/app/src/main/assets/timetables/`, and `iOS/InterSego/Timetables/`; replaces `buildStaticTimetables()` in migrated parsers; see `docs/TIMETABLE_JSON_REFACTOR.md` |
| Debug config / logging | ✅ | ✅ | |
| Boarding notification service | ✅ | ✅ | Node.js/TypeScript server at `server/`; stores boarding events; GET+POST /boardings; 4h TTL; Bearer auth; see `server/docs/API.md` |
| BoardingService client | ✅ | ✅ | Android: OkHttp object singleton + BuildConfig; iOS: URLSession actor + AppConfig |
| Analytics (Aptabase) | ✅ | ✅ (needs Xcode setup) | Self-hosted at analytics.bfollon.dev; app key A-SH-8450405077; consent-gated; tracks: app_launch, route_selected, stop_selected, next_departure_viewed, closest_stop_used, reminder_set, boarding_confirmed, pdf_parse_failed, pdf_download_failed |
| Error reporting (Bugsink/Sentry) | ✅ | ✅ (needs Xcode setup) | Self-hosted at errors.bfollon.dev; Sentry-compatible SDK; consent-gated; explicit captureError/captureMessage call sites only |
| Monitoring consent UI | ✅ | ✅ (needs Xcode setup) | Dual opt-in modal (error reporting + analytics independently); shown on first launch after splash; default off; re-accessible from Settings |
| MonitoringPreferencesService | ✅ | ✅ (needs Xcode setup) | Persists 4 UserDefaults/SharedPreferences keys; consent-gated SDK initialization |

### PDF Parsers

| Parser | Android | iOS | Notes |
|---|---|---|---|
| M4 (La Lastrilla - El Sotillo) | ✅ (static data) | ✅ (static data) | Hardcoded from PDF (2026-04-15); v2.2; isCircular=true; weekday + Saturday (Jul/Aug only); YEAR_ROUND = JULIO Y AGOSTO buses (run all year, only buses in summer); SCHOOL_ONLY = non-summer buses; SCHOOL_ONLY trips (by Azoguejo departure): regular 14:00 + reverse 07:40*, 08:20*, 14:40*; all other trips are YEAR_ROUND; 07:00 trip PARROQ=07:20, RAFAEL=07:21 (corrected from v2.1); 4 reverse (*) trips per weekday (14:40* and 21:40* skip PARROQ2); DayType changed from WEEKEND to SATURDAY; merged-directions display: RouteView uses tabsLabel="Pasa primero por" + tabs chips (La Lastrilla/El Sotillo) in stop list, mergedDirectionLabel="La Lastrilla · El Sotillo" for NextDeparture (all departures merged, "Sotillo" variantLabel badge on El Sotillo-first trips); AllRoutes/Map dropdowns split L-V into two entries |
| M6 (Segovia - Torrecaballeros) | ✅ | ✅ | Cluster-based stop estimation; v0.9 (fully static, both platforms); Sat inbound: proper reversal via PlazaToros→LaPista→AndresLaguna→Jardinillos; Sáb has 2 separate entries (Ida/Vuelta) |
| M1 | ✅ (static data) | ✅ (static data) | Hardcoded from PDF screenshot (2026-03-18); v1.6; isCircular=true; two circular directions (circularA: full outbound via villages + direct return; circularB: direct outbound + return via villages); ★=JUNE_TO_SEPT_ONLY, (*)=YEAR_ROUND, LYV=MON_FRI_ONLY (circularB: Martín Miguel 9:40, Valverde 9:40), #=FRI_ONLY |
| M2 | ✅ (JSON) | ✅ (JSON) | v1.2; isCircular=true; weekday only; two circular directions (circularA: Segovia→Valseca outbound view; circularB: Valseca→Segovia return via Los Huertos+Hontanares); 7:25 Valseca bus originates from Valseca (chronologically out of stop order in circularB, accepted as Option A); timetable data migrated to `resources/timetables/m2.json`; loaded via TimetableLoader on both platforms |
| M3 | ✅ (JSON) | ✅ (JSON) | Saturday only; linear (Segovia→Navacerrada); Segovia is a 4-stop cluster (Estación de Autobuses, Iglesia Santo Tomás, Frente Bar Norte, Plaza de Toros) with +2 min estimated times; v1.2; timetable data migrated to `resources/timetables/m3.json`; loaded via TimetableLoader on both platforms |
| M5 | ✅ (JSON) | ✅ (JSON) | linear (Azoguejo→Sto. Domingo de Pirón); weekday + Saturday; partial trips (some weekday services only Azoguejo–La Higuera); Azoguejo is primary stop; Saturday trips use Estación de Autobuses alternate (alternateLocationId="estacion-autobuses"); v1.3; timetable in `resources/timetables/m5.json`; TimetableLoader on both platforms; AlternateLocation badge in DaySchedule + NextDeparture timeline |
| M7 | ✅ (static data) | ✅ (static data) | Hardcoded from PDF screenshot (2026-03-25); v1.4; weekday: 5-stop circular (Segovia→Tabanera 2-stop→Palazuelos→Segovia); Sat/Sun: extended route (Segovia cluster→...→Torrecaballeros); Segovia cluster outbound: 5 sub-stops; inbound: 4 sub-stops; Sunday has SCHOOL_ONLY/SUMMER_ONLY seasonal trips; all shared stops reuse M6 coordinates and clusters: Tabanera (2-stop: Tabanera+Tabanera 2), San Cristóbal (3-stop), Sonsoto (2-stop: Potro+Sonsoto 2), Trescasas (2-stop: Plaza de la constitución+Trescasas 2), Torrecaballeros (3-stop); Valsaín–La Granja feeder deferred to M7-AVE |
| M8 | ✅ (static data) | ✅ (static data) | Hardcoded from PDF screenshot (2026-03-25); v1.0; linear (Segovia→Valsaín via La Granja); weekday + Saturday + Sunday/Festivos; Segovia cluster all day types: 4 stops (Estación Bus→Iglesia Santo Tomás→Enfrente Bar Norte→Plaza de Toros); C. La Fuencisla is optional (only subset of trips); Saturday has 2 partial trips (outbound 14:30 ends at Ptas. Segovia; inbound 14:50 starts at F. Cristal) |

### UI Screens

| Screen | Android | iOS | Notes |
|---|---|---|---|
| Splash screen | ✅ | ✅ | App icon + "InterSego" + "Interurbanos de Segovia", fade transition |
| Landing screen (home hub) | ✅ | ✅ | Gradient title + "Líneas de bus" + "Parada más cercana" button cards; permanent root, back from route list returns here |
| Closest stop button | ✅ | ✅ | Geolocates user, finds nearest stop across all supported routes, navigates to NextDeparture; ties broken by soonest departure; inline error on failure |
| Route selection | ✅ | ✅ | |
| Route stops (visual line display) | ✅ | ✅ | Direction toggle on both |
| All routes screen | ✅ | ✅ | "Ver todas las rutas" button on stop list → new screen with dropdown selector + stop list; header shows current direction label; map button leads to map with same dropdown; tapping stop → NextDeparture (today) or DaySchedule (non-today). M6: 5 entries (L-V Regular/Circular, Sáb Ida/Vuelta, Dom); M1: 4 entries (L-V/Sáb × circularA/B) |
| Next departure (live countdown) | ✅ | ✅ | iOS uses MapKit, Android uses OSM tiles |
| Next departure — direction indicator | ✅ | ✅ | Pill below stop name; circular routes show "A → B", others "Dirección X" |
| Next departure — direction swap button | ✅ | ✅ | Toolbar button toggles direction in-place, mirrors stop list behaviour; hidden when route uses mergedDirectionLabel (e.g. M4) |
| Route map screen | ✅ | ✅ | Interactive map with stop markers + polyline; accessible via map button in stop list toolbar; direction swap supported; tap marker → NextDeparture. Android uses OSMDroid, iOS uses MapKit. All-routes mode adds dropdown above map |
| Day schedule (full day view) | ✅ | ✅ | All today's departures with "Ahora" marker, auto-scrolls; accepts overrideDayType for non-today route groups |
| Times disclaimer card | ✅ | ✅ | Expandable card on NextDeparture screen, explains approximate times |
| Timetable (full schedule view) | ✅ (dead code) | ❌ | Android has it but no navigation to it |
| About screen | ✅ | ✅ | Sheet presented from "Acerca de" footer button on landing screen; shows app info, Ko-fi, GitHub, Linecar data source, contact links, legal notice |
| Reminders screen | ✅ | ✅ | "Mis recordatorios" card on Landing; bell tap = one-off, long-press = daily (recurring); daily reminders show repeat badge (circular arrows + bell); smart-skip: day type + seasonal check at fire time silently skips if bus doesn't run; one-off uses "next occurrence" logic (finds next future date the bus runs, up to 30 days ahead); bells shown on ALL departures in DaySchedule + NextDeparture (not just today's future ones); separate lead times for one-off (default 10 min) and daily (default 15 min); cancel individual reminders; auto-pruned one-offs on app launch. Android: AlarmManager one-shot re-scheduling + BootReceiver for reboot resilience; iOS: 7-day rolling batch of UNNotificationRequest with deterministic IDs + replenishDailyReminders() at launch |
| Reminders tutorial | ✅ | ✅ | 4-slide onboarding shown on first open of RemindersScreen; re-accessible via "?" button in toolbar; slides: La campana → Aviso puntual → Aviso diario → Tus recordatorios; platform-specific screenshots; Android: ModalBottomSheet + SharedPreferences flag; iOS: .sheet + @AppStorage flag |
| Live updates tutorial | ✅ | ✅ | 3-slide onboarding shown on first visit to NextDepartureScreen where boarding button is visible (daysAhead==0); re-accessible via "?" button in toolbar (topBarTrailing, alongside swap button); slides: icon intro → boarding button screenshot → boarded/ETA screenshot; Android: ModalBottomSheet + SharedPreferences ("live_update_tutorial"); iOS: .sheet + @AppStorage("liveUpdateTutorialShown") |
| Live boarding confirmations | ✅ | ✅ | "Estoy en el autobús" button on NextDeparture (uses current direction directly, no picker) + card on Landing screen (geolocate → route picker if multiple → direction picker → POST); one-tap-per-session; badge + adjusted ETA when others confirmed same trip; 60s polling via BoardingService; server URL + API key in BuildConfig/AppConfig |

### Location & Geolocation

| Feature | Android | iOS | Notes |
|---|---|---|---|
| Location manager | ✅ | ✅ | Android: FusedLocationProviderClient; iOS: CLLocationManager (in ClosestStopService) |
| Closest stop finder | ✅ | ✅ | Android: ClosestStopFinderService; iOS: ClosestStopService |
| Geocoding service | ✅ (backend only) | ❌ | Not used in any UI screen (all stops have embedded coordinates) |
| Coordinate cache | ✅ (backend only) | ❌ | Not used in any UI screen |

### Data Models

| Model | Android | iOS | Notes |
|---|---|---|---|
| BusRoute | ✅ | ✅ | |
| BusStop | ✅ | ✅ | |
| BusTimetable | ✅ | ✅ | |
| DepartureTime | ✅ | ✅ | |
| DayType | ✅ | ✅ | |
| RouteVariant | ✅ | ✅ | |
| RouteView | ✅ | ✅ | |
| SeasonalAvailability | ✅ | ✅ | |
| PDFVersion | ✅ | ✅ | |
| RouteType | ✅ | ✅ | |
| RouteCacheStatus | ✅ | ❌ | Used in PDFCacheManager, not surfaced in UI |
| UpdateProgressState | ✅ | ❌ | Used in PDFCacheManager, not surfaced in UI |
| ScheduleDate | ✅ | ❌ | Not used in any UI screen |

## Repository Structure

```
InterSego/
├── CLAUDE.md                    # This file - project overview & feature tracker
├── .gitignore                   # Repository-wide ignore patterns
├── android/                     # Android implementation
│   ├── CLAUDE.md               # Android-specific development guide
│   ├── app/                    # Android app source code
│   ├── docs/                   # Implementation documentation
│   └── build.gradle.kts        # Android build configuration
├── iOS/                         # iOS implementation
│   ├── InterSego/              # App source code
│   │   ├── Models/             # Data models
│   │   ├── Services/           # Business logic & infrastructure
│   │   └── Views/              # SwiftUI views
│   └── InterSego.xcodeproj/    # Xcode project
└── resources/                   # Shared resources across platforms
    ├── Icons/                  # App launcher icons
    ├── Route display/          # Route visualization assets
    └── timetables/             # Shared JSON timetable files (source of truth for migrated routes)
```

## Platform Implementations

### Android (`android/`)

**Technology Stack:**
- Jetpack Compose + Material3 for UI
- Kotlin Coroutines for async operations
- iText7 for PDF parsing
- OkHttp for HTTP networking
- Strategy Pattern for PDF parsing

See `android/CLAUDE.md` for detailed Android development guide.

### iOS (`iOS/`)

**Technology Stack:**
- SwiftUI with default iOS styling
- Swift actors for thread safety
- PDFKit for PDF parsing
- URLSession for HTTP networking
- Strategy Pattern for PDF parsing

**Build:** Open `iOS/InterSego.xcodeproj` in Xcode. Deployment target: iOS 17.0.

#### PDFKit vs iText7 parsing differences

PDFKit (iOS) and iText7 (Android) extract PDF text differently. Known artifacts in M6:

- **Line swapping:** PDFKit sometimes returns two adjacent time rows in reversed order. Fixed by `reorderSwappedLines()` in `M6Parser`.
- **Cell splitting:** Differently-formatted cells (e.g. highlighted departure cells like `**21:20`) are read as separate text blocks and may be attached to a later line (e.g. `**21:20 SÁBADOS`). Fixed by `preprocessLines()` in `M6Parser`, which splits mixed time+keyword lines and backward-merges orphaned leading times into their correct row.

When porting parsers from Android or debugging parsing issues on iOS, always check for these two artifacts first.

### Web (Future)
Not yet implemented.

## External Data Source

**Linecar Bus Company Website:**
- URL: https://www.linecar.es/metropolitano/segovia/
- Contains: Bus route PDFs (schedules), route information
- Format: PDF files (e.g., `M4.pdf`, `M5-septiembre-2024.pdf`)
- Update frequency: Irregular (seasonal changes, service updates)

**Data Strategy:**
- PDFs are scraped and cached locally
- Self-healing URL system re-scrapes when PDFs return 404
- Offline-first architecture with persistent caching

## Parser Cache Versioning

Both platforms implement parser-version–aware cache invalidation. Each parser declares `capabilities.version` (e.g. `"1.4"`). This version is written to `TimetableCache/<routeId>.meta.json` alongside the PDF timestamp. On next load, if the stored version doesn't match the current parser version the JSON cache is discarded and the route is re-parsed from scratch.

**When to bump the parser version:** any time a code change affects the parsed output — bug fixes, new stops, corrected coordinates, timetable corrections, etc. Increment the version string in the parser's `capabilities` to force all existing devices to re-parse on next launch. Always bump on **both** platforms together.

**Android:** implemented in `TimetableCacheService` (stores/checks `parserVersion` in `.meta.json`) via `PDFProcessingService.getParserVersion()`.
**iOS:** implemented in `TimetableCacheService` (same `.meta.json` contract).

## Development Rules

### Feature Tracker Maintenance
- **ALWAYS update the feature tracker table above** when adding, modifying, or removing features on any platform
- Mark new features with ✅ on the platform where implemented and ❌ on others
- Add notes explaining platform-specific differences (e.g., "iOS uses MapKit, Android uses OSM tiles")
- If a feature exists on one platform but not the other, leave the row so drift is visible

### Commit Guidelines
- Do not put any Claude co-authoring reference or link to claude.ai in commits
- Before committing, allow the user to test the changes. Only commit after confirmation
- NEVER push anything to the remote repository unless explicitly asked

### Code Style
- GPL-v3 license headers on all source files
- Use each platform's default styling (Material 3 on Android, system defaults on iOS)
- Prefer platform-native solutions over cross-platform libraries

## Quick Start

**For Android Development:**
```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```
See `android/CLAUDE.md` for full guide.

**For iOS Development:**
Open `iOS/InterSego.xcodeproj` in Xcode, build and run.

## License

GPL-v3 (see source file headers)
