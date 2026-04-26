# JSON Refactor Cleanup

Pre-flight checklist before migrating to server-based timetable loading.
All routes are now fully JSON-based (bundle assets via `TimetableLoader`).
The PDF parser layer has been removed. These tasks clean up the residue.

---

## Tasks

### 1. Remove iText7 from Android build files
**Status:** Pending

No source file imports iText7 after the parser removal. It's dead weight that increases APK size and build time.

- [ ] Remove `implementation(libs.itext.kernel)` from `android/app/build.gradle.kts` (line 86)
- [ ] Remove `itext = "8.0.5"` from `android/gradle/libs.versions.toml` (line 16)
- [ ] Remove `itext-kernel = { group = "com.itextpdf", name = "kernel", version.ref = "itext" }` from `libs.versions.toml` (line 42)
- [ ] Verify the Android build still compiles cleanly

---

### 2. Delete PDFTextExtractor.swift and PDFParsing/ directory on iOS
**Status:** Pending

`PDFTextExtractor.swift` is the only file left in `iOS/InterSego/Services/PDFParsing/`. Nothing calls it now that all parsers are gone.

- [ ] Delete `iOS/InterSego/Services/PDFParsing/PDFTextExtractor.swift`
- [ ] Delete the now-empty `iOS/InterSego/Services/PDFParsing/` directory
- [ ] Remove the file reference from `iOS/InterSego.xcodeproj/project.pbxproj`
- [ ] Verify the iOS build still compiles cleanly

---

### 3. Verify parser test files were fully deleted on Android
**Status:** Pending

The diff tool flagged these as changed files. Confirm they no longer exist — they should be gone, but check for empty stubs.

- [ ] Confirm `android/app/src/test/.../pdfparsing/PDFTextDecoderTest.kt` is deleted
- [ ] Confirm `android/app/src/test/.../pdfparsing/TimetableParserUtilsTest.kt` is deleted
- [ ] Confirm `android/app/src/test/.../pdfparsing/strategies/M4ParserTest.kt` is deleted
- [ ] Confirm `android/app/src/test/.../pdfparsing/strategies/M6ParserTest.kt` is deleted

---

### 4. Remove the PDF download infrastructure from both platforms
**Status:** Pending  
**Priority:** Must be done before starting server-loading work.

`PDFCacheManager`, `PDFDownloadService`, `PDFURLRepository`, and `PDFURLScrapingService` are still wired up at app startup but their results are never consumed — `TimetableLoader` reads from bundle assets directly. If left in place, adding server-based loading will create two competing data-source systems running at startup with confusing naming.

**Android — files to delete:**
- [ ] `android/.../services/PDFCacheManager.kt`
- [ ] `android/.../services/PDFDownloadService.kt`
- [ ] `android/.../services/PDFURLScrapingService.kt`
- [ ] `android/.../repositories/PDFURLRepository.kt`
- [ ] `android/.../data/PDFVersion.kt` (only if unused after above removals)

**Android — startup wiring to remove:**
- [ ] `MainActivity.kt` line 286: `PDFURLRepository.getInstance(this)`
- [ ] `MainActivity.kt` line 328: `PDFCacheManager.getInstance(this)`
- [ ] `MainActivity.kt` lines 342+: URL resolution block
- [ ] `android/app/src/test/.../services/PDFURLScrapingServiceTest.kt`

**iOS — files to delete:**
- [ ] `iOS/InterSego/Services/PDFURLRepository.swift`
- [ ] `iOS/InterSego/Services/PDFURLScrapingService.swift`
- [ ] `iOS/InterSego/Services/PDFCacheManager.swift` (if it exists)
- [ ] `iOS/InterSego/Services/PDFDownloadService.swift` (if it exists)

**iOS — startup wiring to remove:**
- [ ] `iOS/InterSego/InterSegoApp.swift` line 585: `PDFURLRepository.shared.initializeURLs()`
- [ ] Remove file references from `project.pbxproj`

**Both platforms:**
- [ ] Verify builds compile cleanly after removal
- [ ] Check `TimetableCacheService` on both platforms — its `.meta.json` version-checking was tied to the PDF pipeline; decide if it stays (repurposed for JSON version tracking) or goes

---

### 5. Rename PDFProcessingService → TimetableService on both platforms
**Status:** Pending  
**Depends on:** Task 4

`PDFProcessingService` is now a thin facade over `TimetableLoader` — no PDF processing occurs. The name is actively misleading. Do this after Task 4 so the rename isn't tangled with infrastructure removal.

- [ ] Rename `PDFProcessingService.kt` → `TimetableService.kt` on Android
- [ ] Rename `PDFProcessingService.swift` → `TimetableService.swift` on iOS
- [ ] Update class name and all call sites on both platforms
- [ ] Update file references in `project.pbxproj`
- [ ] Update any references in `CLAUDE.md` and `android/CLAUDE.md`

---

### 6. Update android/CLAUDE.md to reflect the JSON-based architecture
**Status:** Pending

`android/CLAUDE.md` still describes the old three-tier PDF caching architecture, the Strategy Pattern for PDF parsing, iText7, and PDF services that no longer exist.

- [ ] Remove or rewrite the "Three-Tier Caching System" section (now: memory → bundle JSON)
- [ ] Remove "Strategy Pattern for PDF Parsing" section
- [ ] Remove PDF service descriptions from "Service Layer Structure"
- [ ] Remove iText7 from "Key Technologies"
- [ ] Update "Phase 5" references (PDF parsers → JSON-based, complete)
- [ ] Remove "Implementing PDF Parsing" from "Common Development Scenarios"
- [ ] Update "Known Issues & TODOs" to remove PDF parser todos

---

## Context

- All 8 routes (M1–M8) now load from `assets/timetables/{routeId}.json` (Android) and `Timetables/{routeId}.json` (iOS) via `TimetableLoader`
- `PDFProcessingService` on both platforms is a facade with all methods delegating to `TimetableLoader`
- The PDF infrastructure (download, scrape, cache) still runs at startup but produces no output consumed by anything
- Next milestone after this cleanup: load timetable JSON from a server instead of bundle assets
