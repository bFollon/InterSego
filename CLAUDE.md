# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**LineCapp** is a bus timetable app for Segovia, Spain. It downloads and parses bus timetable PDFs from Linecar (the local bus company), provides offline caching, and helps users find the nearest bus stop using geolocation.

**Current Status:** Android implementation in active development. Complete end-to-end user flow operational for M4 route.

## Repository Structure

```
LineCapp/
├── CLAUDE.md                    # This file - project overview
├── .gitignore                   # Repository-wide ignore patterns
├── android/                     # Android implementation
│   ├── CLAUDE.md               # Android-specific development guide
│   ├── app/                    # Android app source code
│   ├── docs/                   # Implementation documentation
│   └── build.gradle.kts        # Android build configuration
└── resources/                   # Shared resources across platforms
    ├── Icons/                  # App launcher icons
    │   └── ic_launcher-6905343ae8c3c/  # Current icon (bus timetable design)
    │       ├── android/        # Android adaptive icons (PNG)
    │       └── ios/            # iOS app icons (future use)
    └── Route display/          # Route visualization assets
```

## Platform Implementations

### Android (`android/`)
**Status:** Active development - Phase 7 (UI Implementation)

**Key Features Implemented:**
- ✅ PDF download and caching infrastructure
- ✅ PDF URL scraping with self-healing
- ✅ M4 route PDF parsing (complete)
- ✅ Three-tier caching system (memory → persistent → PDF)
- ✅ Route selection UI
- ✅ Visual route display with direction toggle
- ✅ Next departure screen with live countdown
- ✅ Complete timetable view

**In Progress:**
- PDF parsers for routes M1-M3, M5-M8

**Technology Stack:**
- Jetpack Compose + Material3 for UI
- Kotlin Coroutines for async operations
- iText7 for PDF parsing
- OkHttp for HTTP networking
- Strategy Pattern for PDF parsing

See `android/CLAUDE.md` for detailed Android development guide.

### iOS (Future)
Not yet implemented. Icons prepared in `resources/Icons/`.

### Web (Future)
Not yet implemented.

## Shared Resources

### App Icons (`resources/Icons/`)
**Current Design:** Minimalistic bus timetable icon
- Color scheme: Blue (#1c74d3) primary, Orange accent
- Style: Material Design 3 compatible
- Format: PNG (Android), prepared for iOS

**Location:** `resources/Icons/ic_launcher-6905343ae8c3c/`
- `android/` - Adaptive icons for API 26+, all density buckets
- `ios/` - App icon sets for iOS (prepared for future use)

### Route Display Assets (`resources/Route display/`)
Visual assets for displaying bus routes and stops.

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

## Development Workflow

### Working on Android
```bash
cd android
# See android/CLAUDE.md for build commands, debugging, etc.
```

### Working on Resources
- Icons: Update source files in `resources/Icons/`
- Route assets: Add to `resources/Route display/`
- After updating icons, copy to platform directories (see platform-specific docs)

## Project Background

**Architecture Source:** Adapted from FarmaciasDeGuardia (pharmacy duty schedule app).
- Similar PDF parsing and caching patterns
- Offline-first architecture
- Geolocation-based nearest location finder

**Reference Project:** `/Users/bruno.follon/Personal/dev/apps/FarmaciasDeGuardia/android/`

## Documentation Index

**Root Level:**
- This file - Project overview and repository structure

**Android Implementation:**
- `android/CLAUDE.md` - Android development guide (build, debug, architecture)
- `android/docs/CURRENT_STATUS.md` - Current phase status and next steps
- `android/docs/MIGRATION_PLAN.md` - Complete migration strategy
- `android/docs/MIGRATION_PHASE_*.md` - Detailed phase documentation
- `android/docs/PDF_URL_SCRAPING_SETUP.md` - URL scraping configuration

## Quick Start

**For Android Development:**
1. Navigate to `android/` directory
2. Read `android/CLAUDE.md` for build and development commands
3. Use `./gradlew assembleDebug` to build
4. Use `./gradlew installDebug` to install on device

**For Resource Updates:**
1. Update files in `resources/` directory
2. Copy to platform directories as needed
3. Update platform documentation

## License

GPL-v3 (see source file headers)
