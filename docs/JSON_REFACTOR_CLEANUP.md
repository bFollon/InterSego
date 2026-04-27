# JSON Refactor Cleanup

Pre-flight checklist before migrating to server-based timetable loading.
All routes are now fully JSON-based (bundle assets via `TimetableLoader`).
The PDF parser layer has been removed. These tasks clean up the residue.

---

## Tasks

### 1. Remove iText7 from Android build files
**Status:** Done

- [x] Remove `implementation(libs.itext.kernel)` from `android/app/build.gradle.kts`
- [x] Remove `itext = "8.0.5"` from `android/gradle/libs.versions.toml`
- [x] Remove `itext-kernel = { group = "com.itextpdf", name = "kernel", version.ref = "itext" }` from `libs.versions.toml`
- [ ] Verify the Android build still compiles cleanly

---

### 2. Delete PDFTextExtractor.swift and PDFParsing/ directory on iOS
**Status:** Done

- [x] Delete `iOS/InterSego/Services/PDFParsing/PDFTextExtractor.swift`
- [x] Delete the now-empty `iOS/InterSego/Services/PDFParsing/` directory
- [x] Remove the file reference from `iOS/InterSego.xcodeproj/project.pbxproj`
- [ ] Verify the iOS build still compiles cleanly

---

### 3. Verify parser test files were fully deleted on Android
**Status:** Done — all four test files confirmed absent.

---

### 4. Remove the PDF download infrastructure from both platforms
**Status:** Done

**Android — deleted:**
- [x] `android/.../services/PDFCacheManager.kt`
- [x] `android/.../services/PDFDownloadService.kt`
- [x] `android/.../services/PDFURLScrapingService.kt`
- [x] `android/.../repositories/PDFURLRepository.kt`
- [x] `android/.../data/PDFVersion.kt`
- [x] `android/app/src/test/.../services/PDFURLScrapingServiceTest.kt`

**Android — startup wiring removed from `MainActivity.kt`:**
- [x] `PDFURLRepository` import + `.map { route.copy(pdfURL = ...) }` block in `getKnownRoutes()`
- [x] `PDFCacheManager.getInstance(this)` + `pdfCacheManager.initialize()`
- [x] `LaunchedEffect` PDF URL init + `checkForUpdatesIfNeeded` block

**iOS — deleted:**
- [x] `iOS/InterSego/Services/PDFURLRepository.swift`
- [x] `iOS/InterSego/Services/PDFURLScrapingService.swift`
- [x] `iOS/InterSego/Services/PDFCacheManager.swift`
- [x] `iOS/InterSego/Services/PDFDownloadService.swift`

**iOS — startup wiring removed:**
- [x] `iOS/InterSego/InterSegoApp.swift`: `PDFURLRepository.shared.initializeURLs()` block
- [x] `project.pbxproj` references for all four files

**Both platforms:**
- [ ] Verify builds compile cleanly after removal
- `TimetableCacheService` left in place — still referenced by `TimetableService`; its disk cache always misses since no PDF files exist. Will be repurposed for JSON version tracking before server-loading work begins.

---

### 5. Rename PDFProcessingService → RouteDataService on both platforms
**Status:** Done

`PDFProcessingService` was renamed to `RouteDataService` (option A — reflects its actual role: route metadata queries backed by TimetableLoader). `TimetableService` remains as the caching/departure-helper layer.

- [x] `PDFProcessingService.kt` → `RouteDataService.kt` on Android
- [x] `PDFProcessingService.swift` → `RouteDataService.swift` on iOS
- [x] Class name updated in both files
- [x] All call sites updated (MainActivity, RouteSelectionScreen, NextDepartureScreen, TimetableService, DeparturesService, ClosestStop*Service on both platforms)
- [x] File references updated in `project.pbxproj`
- [x] `CLAUDE.md` and `android/CLAUDE.md` updated

---

### 6. Update android/CLAUDE.md to reflect the JSON-based architecture
**Status:** Done

- [x] Rewrote architecture description (memory → bundle JSON, no PDF tier)
- [x] Removed iText7, Strategy Pattern, PDF services from Key Technologies
- [x] Removed "Three-Tier Caching System", "PDF URL Management", "Strategy Pattern for PDF Parsing" sections
- [x] Updated Service Layer Structure to reflect current services
- [x] Removed PDFVersion data model entry
- [x] Updated Phase 5 to "complete JSON migration"
- [x] Removed "Working with PDF URLs" and "Implementing PDF Parsing" scenarios
- [x] Updated Known Issues & TODOs
- [x] Root CLAUDE.md also updated (project overview, tech stack, feature tracker)

---

## Context

- All 8 routes (M1–M8) now load from `assets/timetables/{routeId}.json` (Android) and `Timetables/{routeId}.json` (iOS) via `TimetableLoader`
- `RouteDataService` on both platforms is a facade with all methods delegating to `TimetableLoader`
- The PDF infrastructure (download, scrape, cache) still runs at startup but produces no output consumed by anything
- Next milestone after this cleanup: load timetable JSON from a server instead of bundle assets
