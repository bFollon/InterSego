# Migration Phase 3: PDF Infrastructure

**Date:** October 12, 2025
**Status:** ✅ Complete

## Overview

Phase 3 establishes the PDF infrastructure layer by copying and adapting services for downloading, caching, and URL management from FarmaciasDeGuardia to LineCapp. These services handle the retrieval and storage of bus timetable PDFs, which will later be parsed in Phase 5.

**Important Note:** PDF parsing strategies are intentionally **not** included in this phase. The actual parsing logic (Phase 5) will be implemented later once bus timetable PDF formats are analyzed and understood.

## Goals

1. Copy PDF download and caching infrastructure
2. Adapt URL scraping and fallback mechanisms for bus routes
3. Implement three-tier caching architecture for PDFs
4. Preserve offline support and self-healing URL resolution
5. Prepare infrastructure for future PDF parsing (Phase 5)

---

## Services Copied

### 1. PDFDownloadService.kt
**Path:** `services/PDFDownloadService.kt`
**Size:** 243 lines
**Purpose:** Downloads PDF files from URLs with robust error handling

**Key Features:**
- OkHttp-based HTTP client with custom configuration
- SSL certificate validation with hostname verification
- Configurable timeouts (connect: 30s, read: 60s, write: 60s)
- Automatic retry logic for transient failures
- Progress callbacks for UI updates
- NetworkMonitor integration for offline detection
- User-Agent header: `LineCapp-Android/1.0`

**Adaptations:**
- Package: `farmaciasdeguardiaensegovia` → `linecapp`
- User-Agent: `FarmaciasDeGuardia-Android` → `LineCapp-Android`
- SSL hostname verifier: `cofsegovia.com` → `avilabus.es` (example, marked with TODO)
- Comments: Updated from "pharmacy" to "bus timetable"

**Key Methods:**
```kotlin
suspend fun downloadPDF(url: String, outputFile: File): Boolean
fun isOnline(): Boolean  // Via NetworkMonitor
```

**SSL Configuration:**
- Custom SSL socket factory for certificate handling
- Hostname verification for specific domains
- Fallback to default verification for other domains

---

### 2. PDFCacheManager.kt
**Path:** `services/PDFCacheManager.kt`
**Size:** 597 lines
**Purpose:** Manages PDF file caching with three-tier architecture

**Three-Tier Caching:**
1. **Memory Cache** - In-memory `ByteArray` cache for fastest access
2. **Persistent Cache** - Disk storage in app-specific directory
3. **Download** - Network fetch as last resort

**Key Features:**
- PDF version tracking (Last-Modified, ETag, Content-Length)
- Automatic cache invalidation when PDF updates
- Self-healing: Re-scrapes URLs on 404 errors
- Daily update checks with configurable limits
- Cache size management and statistics
- Progress callbacks for long operations
- NetworkMonitor integration

**Adaptations:**
- Package: `farmaciasdeguardiaensegovia` → `linecapp`
- Cache directory: `PharmacyPDFs` → `BusTimetablePDFs`
- Data model: `Region` → `BusRoute`, accepts `routeId: String`
- Import: `RegionCacheStatus` → `RouteCacheStatus`
- Comments: "pharmacy" → "bus timetable"

**Key Methods:**
```kotlin
suspend fun getPDF(routeId: String): ByteArray?
suspend fun updatePDFIfNeeded(routeId: String): UpdateProgressState
suspend fun clearCache(routeId: String)
suspend fun getCacheStatus(routeId: String): RouteCacheStatus
fun getCacheStats(): Triple<Int, Int, String>
```

**Cache Structure:**
```
app_data/BusTimetablePDFs/
├── L1.pdf
├── L1.version.json
├── L2.pdf
├── L2.version.json
└── ...
```

**Version Tracking:**
```json
{
  "lastModified": "Wed, 21 Oct 2025 07:28:00 GMT",
  "etag": "\"3f80f-1b6-3e1cb03b\"",
  "contentLength": 12345,
  "lastChecked": 1729497600000
}
```

---

### 3. PDFURLScrapingService.kt
**Path:** `services/PDFURLScrapingService.kt`
**Size:** 380 lines
**Purpose:** Scrapes PDF URLs from bus company website

**Key Features:**
- HTML parsing with Regex-based extraction
- Route-specific URL detection
- Retry logic with exponential backoff
- Caches scraped URLs for 24 hours
- Fallback to PDFURLRepository on failure
- NetworkMonitor integration

**Adaptations:**
- Package: `farmaciasdeguardiaensegovia` → `linecapp`
- Base URL: `cofsegovia.com/farmacias-de-guardia/` → `avilabus.es/horarios/` (example with TODO)
- Data model: `ScrapedPDFData.regionName` → `ScrapedPDFData.routeId`
- Route detection: Adapted from region names (Segovia Capital, Cuéllar) to route IDs (L1, L2, L3, L4)
- Comments: "pharmacy" → "bus route"

**Route Detection Logic:**
```kotlin
// Example route detection (customizable)
when {
    url.contains("linea-1", ignoreCase = true) -> "L1"
    url.contains("linea-2", ignoreCase = true) -> "L2"
    url.contains("linea-3", ignoreCase = true) -> "L3"
    url.contains("linea-4", ignoreCase = true) -> "L4"
    else -> null
}
```

**Key Methods:**
```kotlin
suspend fun scrapePDFURLs(): List<ScrapedPDFData>
fun getCachedURLs(): List<ScrapedPDFData>
fun clearCache()
```

**Scraping Strategy:**
1. Fetch website HTML
2. Extract PDF links using regex patterns
3. Identify route ID from URL/text
4. Cache results for 24 hours
5. Return list of `ScrapedPDFData` objects

**TODO Items:**
- Update `BASE_URL` with actual bus company website
- Customize route detection patterns based on real HTML structure
- Update regex patterns for PDF link extraction

---

### 4. PDFURLRepository.kt
**Path:** `repositories/PDFURLRepository.kt`
**Size:** 420 lines
**Purpose:** Provides hardcoded fallback URLs when scraping fails

**Key Features:**
- Fallback URLs for all bus routes
- URL normalization and validation
- Integration with PDFURLScrapingService
- Automatic fallback on scraping failure
- Support for both scraped and hardcoded URLs

**Adaptations:**
- Package: `farmaciasdeguardiaensegovia` → `linecapp`
- Method signatures: Accept `routeId: String` instead of region names
- Route IDs: Changed from region names (Segovia Capital) to route IDs (L1, L2, L3, L4)
- Normalization: `normalizeRegionName()` → `normalizeRouteId()`
- Comments: "pharmacy" → "bus timetable"

**Fallback URL Structure:**
```kotlin
private val FALLBACK_URLS = mapOf(
    "L1" to "https://www.avilabus.es/pdf/linea-1-horarios.pdf",
    "L2" to "https://www.avilabus.es/pdf/linea-2-horarios.pdf",
    "L3" to "https://www.avilabus.es/pdf/linea-3-horarios.pdf",
    "L4" to "https://www.avilabus.es/pdf/linea-4-horarios.pdf"
)
```

**Key Methods:**
```kotlin
suspend fun getPDFURL(routeId: String): String?
fun getFallbackURL(routeId: String): String?
suspend fun refreshScrapedURLs()
```

**Resolution Strategy:**
1. Try scraped URL first (if available and < 24 hours old)
2. Fall back to hardcoded URL if scraping failed
3. Return `null` if no URL available

**TODO Items:**
- Update `FALLBACK_URLS` with real bus route PDF URLs
- Verify URL patterns match actual website structure

---

## Data Models

### 5. PDFVersion.kt
**Path:** `data/PDFVersion.kt`
**Size:** 33 lines

Tracks PDF metadata for cache invalidation:
```kotlin
@Serializable
data class PDFVersion(
    val lastModified: String? = null,    // Last-Modified header
    val etag: String? = null,            // ETag header
    val contentLength: Long? = null,     // Content-Length header
    val lastChecked: Long = System.currentTimeMillis()
)
```

**Used by:** PDFCacheManager for version comparison

---

### 6. UpdateProgressState.kt
**Path:** `data/UpdateProgressState.kt`
**Size:** 30 lines

Sealed class for update progress tracking:
```kotlin
sealed class UpdateProgressState {
    object Checking : UpdateProgressState()
    data class Downloading(val progress: Int) : UpdateProgressState()
    object Downloaded : UpdateProgressState()
    object UpToDate : UpdateProgressState()
    data class Error(val message: String) : UpdateProgressState()
}
```

**Used by:** PDFCacheManager for UI progress callbacks

---

### 7. RouteCacheStatus.kt
**Path:** `data/RouteCacheStatus.kt`
**Size:** 34 lines
**Adapted from:** `RegionCacheStatus`

Cache status information for routes:
```kotlin
@Serializable
data class RouteCacheStatus(
    val routeId: String,
    val isCached: Boolean,
    val lastUpdated: Long?,
    val fileSize: Long?
)
```

**Used by:** PDFCacheManager for cache statistics

---

## Architecture

### Three-Tier Caching Flow

```
┌─────────────────────────────────────┐
│      Request PDF for Route L1       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│    1. Check Memory Cache            │
│    ✓ Fast (0ms)                     │
└──────────────┬──────────────────────┘
               │ Cache miss
               ▼
┌─────────────────────────────────────┐
│    2. Check Persistent Cache        │
│    ✓ Fast (~10ms)                   │
│    ✓ Validates version              │
└──────────────┬──────────────────────┘
               │ Cache miss or outdated
               ▼
┌─────────────────────────────────────┐
│    3. Download from Network         │
│    ✗ Slow (~1-5s)                   │
│    ✓ Requires internet              │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│    Cache in Memory & Disk           │
│    Ready for next request           │
└─────────────────────────────────────┘
```

### URL Resolution Flow

```
┌─────────────────────────────────────┐
│   Need PDF URL for Route L1        │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   Check PDFURLScrapingService       │
│   Scraped < 24h ago?                │
└──────────────┬──────────────────────┘
               │
         ┌─────┴──────┐
         │            │
        YES           NO
         │            │
         ▼            ▼
┌───────────┐  ┌──────────────┐
│  Use      │  │  Scrape      │
│  Scraped  │  │  Website     │
│  URL      │  │              │
└─────┬─────┘  └──────┬───────┘
      │               │
      │        ┌──────┴────────┐
      │        │               │
      │    Success          Failure
      │        │               │
      │        ▼               ▼
      │  ┌──────────┐   ┌──────────────┐
      └─>│   Use    │   │  Use Fallback│
         │ Scraped  │   │  from        │
         │   URL    │   │  Repository  │
         └──────────┘   └──────────────┘
```

---

## Key Features Preserved

### ✅ SSL Certificate Validation
- Custom SSL socket factory
- Hostname verification for specific domains
- Secure HTTPS connections

### ✅ Three-Tier Caching Architecture
- Memory cache for instant access
- Persistent disk cache for offline use
- Network download as fallback

### ✅ Offline Support
- NetworkMonitor integration throughout
- Graceful degradation when offline
- Cache-first strategy

### ✅ PDF Version Tracking
- Last-Modified header tracking
- ETag support for cache validation
- Content-Length verification

### ✅ Automatic Update Checking
- Daily update check limit
- Configurable check intervals
- Background update support

### ✅ Self-Healing URL Resolution
- Detects 404 errors
- Automatically re-scrapes on failure
- Falls back to hardcoded URLs

### ✅ Progress Callbacks
- Real-time download progress
- UI-friendly state updates
- UpdateProgressState sealed class

### ✅ Debug Logging
- DebugConfig integration
- Detailed operation logging
- Error tracking

---

## Customization Required

### High Priority (Before Production)

1. **PDFURLScrapingService - Update BASE_URL**
   ```kotlin
   // TODO: Update with actual bus company website
   private const val BASE_URL = "https://www.avilabus.es/horarios/"
   ```

2. **PDFURLScrapingService - Customize Route Detection**
   ```kotlin
   // TODO: Update patterns based on actual HTML structure
   private fun detectRouteId(url: String, linkText: String): String? {
       return when {
           // Customize these patterns
           url.contains("linea-1") -> "L1"
           linkText.contains("Línea 1") -> "L1"
           // ...
       }
   }
   ```

3. **PDFURLRepository - Update Fallback URLs**
   ```kotlin
   // TODO: Update with real PDF URLs
   private val FALLBACK_URLS = mapOf(
       "L1" to "https://actual-bus-website.com/l1.pdf",
       "L2" to "https://actual-bus-website.com/l2.pdf",
       // ...
   )
   ```

### Medium Priority (Optional Enhancements)

4. **PDFDownloadService - Update SSL Hostname Verifier**
   ```kotlin
   // TODO: Update with actual domains if needed
   override fun verify(hostname: String, session: SSLSession): Boolean {
       return hostname == "avilabus.es" ||
              defaultVerifier.verify(hostname, session)
   }
   ```

5. **PDFURLScrapingService - Update Regex Patterns**
   ```kotlin
   // TODO: Customize based on actual HTML
   private val pdfLinkRegex = """<a[^>]+href=["']([^"']*\.pdf)["'][^>]*>""".toRegex()
   ```

---

## Phase 5 Placeholder

**PDF Parsing Strategies are intentionally skipped in this phase.**

**Why?**
- Actual bus timetable PDF formats are not yet known
- PDF layouts vary significantly between routes and companies
- Parsing logic requires manual analysis of sample PDFs
- Infrastructure (download/cache) works independently of parsing

**When Phase 5 will be implemented:**
- After obtaining sample bus timetable PDFs
- After analyzing PDF layouts (column structures, fonts, spacing)
- After identifying common patterns vs route-specific variations

**What Phase 5 will include:**
- `BusTimetableParser` interface
- Route-specific parser implementations
- Column-based or text-based extraction strategies
- Time string parsing (HH:MM format)
- Day type detection (weekday/weekend/holiday)
- Stop name extraction
- Departure time list building

---

## Integration Notes

### Dependencies on Other Phases

**Phase 2 (Infrastructure):**
- Uses `NetworkMonitor` for offline detection
- Uses `DebugConfig` for logging
- Uses `CoordinateCache` indirectly via GeocodingService

**Phase 4 (Data Models):**
- Uses `BusRoute` model for route representation
- Uses `RouteCacheStatus` for cache metadata
- Uses `PDFVersion` for version tracking
- Uses `UpdateProgressState` for progress updates

### Integration with Future Phases

**Phase 5 (PDF Parsing - Future):**
- PDF parsing strategies will consume PDFs from PDFCacheManager
- Parsers will work on cached `ByteArray` data
- No network dependencies for parsing

**Phase 6 (Business Logic):**
- TimetableService will use PDFCacheManager to get PDFs
- ScheduleCacheService will cache parsed timetable data
- Services can trigger PDF updates when needed

---

## Testing Strategy

### Unit Tests (Future)

**PDFDownloadService:**
- Test successful download
- Test timeout handling
- Test SSL verification
- Test offline behavior

**PDFCacheManager:**
- Test three-tier cache lookup
- Test version comparison
- Test cache invalidation
- Test update checking logic

**PDFURLScrapingService:**
- Test HTML parsing
- Test route detection
- Test cache expiration
- Test fallback behavior

**PDFURLRepository:**
- Test fallback URL retrieval
- Test route ID normalization
- Test scraped URL priority

### Integration Tests (Future)

- Test complete download → cache → retrieval flow
- Test offline mode with cached PDFs
- Test automatic URL re-scraping on 404
- Test concurrent access to cache

### Manual Testing (Current)

1. **Download Test:**
   ```kotlin
   val service = PDFDownloadService()
   val file = File(context.cacheDir, "test.pdf")
   val success = service.downloadPDF("http://example.com/test.pdf", file)
   ```

2. **Cache Test:**
   ```kotlin
   val manager = PDFCacheManager(context)
   val pdf = manager.getPDF("L1")
   val status = manager.getCacheStatus("L1")
   ```

3. **Scraping Test:**
   ```kotlin
   val scraper = PDFURLScrapingService()
   val urls = scraper.scrapePDFURLs()
   ```

---

## Known Issues / Notes

### 1. Example URLs
**Issue:** All URLs are currently placeholder examples.
**Resolution:** Update with real bus company URLs before production.
**Files affected:** PDFURLScrapingService, PDFURLRepository

### 2. Route Detection Logic
**Issue:** Route ID detection patterns are generic examples.
**Resolution:** Customize based on actual website HTML structure.
**File affected:** PDFURLScrapingService

### 3. SSL Hostname Verification
**Issue:** Configured for `avilabus.es` (example domain).
**Resolution:** Update with actual bus company domains.
**File affected:** PDFDownloadService

### 4. Cache Directory Naming
**Note:** Cache directory changed from `PharmacyPDFs` to `BusTimetablePDFs`.
**Impact:** No migration needed (fresh install).

### 5. No PDF Parsing Yet
**Note:** Phase 5 (PDF parsing) intentionally skipped.
**Reason:** Actual PDF formats not yet known.
**Timeline:** Implement after obtaining sample PDFs.

---

## Success Criteria

- ✅ All 4 services copied and adapted
- ✅ All 3 data models created
- ✅ Package names updated to `linecapp`
- ✅ Domain terminology adapted (pharmacy → bus)
- ✅ GPL-v3 license headers preserved
- ✅ Three-tier caching architecture preserved
- ✅ Offline support maintained
- ✅ Self-healing URL resolution functional
- ✅ TODO comments added for customization points
- ✅ Documentation complete

---

## Files Created

```
LineCapp/android/app/src/main/java/com/github/bfollon/linecapp/
├── services/
│   ├── PDFDownloadService.kt          (243 lines)
│   ├── PDFCacheManager.kt             (597 lines)
│   └── PDFURLScrapingService.kt       (380 lines)
├── repositories/
│   └── PDFURLRepository.kt            (420 lines)
└── data/
    ├── PDFVersion.kt                  (33 lines)
    ├── UpdateProgressState.kt         (30 lines)
    └── RouteCacheStatus.kt            (34 lines)

Total: 1,737 lines of code
```

---

## Next Phases

### Phase 5: PDF Parsing Strategies (Future - To Be Done Later)
**Prerequisites:**
- Obtain sample bus timetable PDFs
- Analyze PDF layouts and structures
- Identify parsing patterns

**Will Include:**
- `BusTimetableParser` interface
- Route-specific parser implementations
- Text extraction utilities
- Time parsing logic

**Timeline:** After PDF format analysis

### Phase 6: Business Logic Services (Can Proceed Now)
**No PDF Parsing Required:**
- TimetableService (business logic layer)
- TimetableCacheService (parsed data caching)
- ClosestStopService (location-based queries)

**Dependencies:**
- Phase 2 (Infrastructure) ✅
- Phase 3 (PDF Infrastructure) ✅
- Phase 4 (Data Models) ✅

---

## Summary

Phase 3 successfully establishes the PDF infrastructure layer for LineCapp by copying and adapting 4 core services and 3 data models from FarmaciasDeGuardia. The infrastructure provides:

- **Robust PDF downloading** with SSL support and retry logic
- **Three-tier caching** for optimal performance and offline support
- **URL scraping and fallback** for resilient PDF URL resolution
- **Version tracking** for intelligent cache invalidation
- **Progress callbacks** for responsive UI updates

The services are adapted for the bus timetable domain with placeholder URLs and detection patterns marked with TODO comments for future customization. PDF parsing strategies are intentionally deferred to Phase 5 to allow for proper analysis of actual bus timetable PDF formats.

**Code Reuse:** 100% of PDF infrastructure reused with domain adaptations.

**Customization Required:** Update URLs, detection patterns, and SSL domains before production.

**Next Step:** Phase 6 (Business Logic Services) can proceed without PDF parsing, or Phase 5 can be implemented once sample PDFs are available.
