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
| Debug config / logging | ✅ | ✅ | |

### PDF Parsers

| Parser | Android | iOS | Notes |
|---|---|---|---|
| M4 (La Lastrilla - El Sotillo) | ✅ | ✅ | iOS handles PDFKit text differences |
| M6 (Segovia - Torrecaballeros) | ✅ | ✅ | Cluster-based stop estimation |
| M1 | ❌ | ❌ | |
| M2 | ❌ | ❌ | |
| M3 | ❌ | ❌ | |
| M5 | ❌ | ❌ | |
| M7 | ❌ | ❌ | |
| M8 | ❌ | ❌ | |

### UI Screens

| Screen | Android | iOS | Notes |
|---|---|---|---|
| Splash screen | ✅ | ✅ | App icon + "InterSego" + "Interurbanos de Segovia", fade transition |
| Landing screen (home hub) | ✅ | ✅ | Gradient title + "Líneas de bus" button card; permanent root, back from route list returns here |
| Route selection | ✅ | ✅ | |
| Route stops (visual line display) | ✅ | ✅ | Direction toggle on both |
| Next departure (live countdown) | ✅ | ✅ | iOS uses MapKit, Android uses OSM tiles |
| Next departure — direction indicator | ✅ | ✅ | Pill below stop name; circular routes show "A → B", others "Dirección X" |
| Next departure — direction swap button | ✅ | ✅ | Toolbar button toggles direction in-place, mirrors stop list behaviour |
| Route map screen | ✅ | ✅ | Interactive map with stop markers + polyline; accessible via map button in stop list toolbar; direction swap supported; tap marker → NextDeparture. Android uses OSMDroid, iOS uses MapKit |
| Day schedule (full day view) | ✅ | ✅ | All today's departures with "Ahora" marker, auto-scrolls |
| Times disclaimer card | ✅ | ✅ | Expandable card on NextDeparture screen, explains approximate times |
| Timetable (full schedule view) | ✅ (dead code) | ❌ | Android has it but no navigation to it |

### Location & Geolocation

| Feature | Android | iOS | Notes |
|---|---|---|---|
| Location manager | ✅ (backend only) | ❌ | Not used in any Android UI screen |
| Closest bus stop service | ✅ (backend only) | ❌ | Not used in any Android UI screen |
| Geocoding service | ✅ (backend only) | ❌ | Not used in any Android UI screen |
| Coordinate cache | ✅ (backend only) | ❌ | Not used in any Android UI screen |

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
    └── Route display/          # Route visualization assets
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
