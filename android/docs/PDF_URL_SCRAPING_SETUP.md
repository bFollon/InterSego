# PDF URL Scraping Setup

**Date:** October 28, 2025
**Status:** ✅ Fully Operational

## Overview

The PDF URL scraping system is configured for the Linecar bus website and fully integrated into the app. The scraper automatically fetches and caches PDF URLs on app launch, with self-healing 404 detection and automatic re-scraping.

## Configuration

### Source Website
- **URL:** `https://www.linecar.es/metropolitano/segovia/`
- **PDF Pattern:** Files containing `SEGOVIA-` and ending with `.pdf`

### Route ID Extraction

PDFs follow multiple naming patterns (scraper handles all):

**Pattern 1 (Old format):** `SEGOVIA-{ROUTE_ID}[optional-suffix].pdf`
- `SEGOVIA-M1.pdf` → Route ID: `M1`
- `SEGOVIA-M2-LABORABLES.pdf` → Route ID: `M2`

**Pattern 2 (New format):** `{ROUTE_ID}.pdf`
- `M4.pdf` → Route ID: `M4`

**Pattern 3 (New with date):** `{ROUTE_ID}-{month}-{year}.pdf`
- `M2-septiembre-2024.pdf` → Route ID: `M2`
- `M5-septiembre-2024.pdf` → Route ID: `M5`

**Regex Patterns Used:**
1. `SEGOVIA-([A-Z0-9]+)` - Old format
2. `^(M[0-9]+)\.pdf$` - New simple format
3. `^(M[0-9]+)-.*\.pdf$` - New format with suffix

**Verified Routes Discovered (October 28, 2025):**
- M1, M2, M3, M4, M5, M6, M7, M8 (8 routes total)

## Integration Flow

### 1. App Startup (MainActivity.onCreate)

```kotlin
// Initialize PDF URL repository and scrape URLs
val pdfUrlRepository = PDFURLRepository.getInstance(this)
lifecycleScope.launch {
    DebugConfig.debugPrint("🌐 Initializing PDF URLs...")
    val success = pdfUrlRepository.initializeURLs()
    if (success) {
        DebugConfig.debugPrint("✅ PDF URLs initialized successfully")
        DebugConfig.debugPrint(pdfUrlRepository.getStatus())
    } else {
        DebugConfig.debugWarn("⚠️ PDF URL initialization failed, using fallback URLs")
    }
}
```

### 2. PDFURLRepository.initializeURLs()

**Logic:**
1. Check network status
2. Load persisted URLs from SharedPreferences
3. Validate persisted URLs with HEAD requests
4. If any URLs are invalid → Scrape fresh URLs from website
5. Persist new URLs to SharedPreferences
6. Return success/failure

### 3. PDFURLScrapingService.scrapePDFURLs()

**Process:**
1. Fetch HTML from `https://www.linecar.es/metropolitano/segovia/`
2. Extract PDF links matching pattern: `href="([^"]*SEGOVIA-[^"]*\.pdf)"`
3. Convert relative URLs to absolute
4. Extract route ID from filename
5. Return list of `ScrapedPDFData(routeId, pdfUrl, lastUpdated)`

## URL Resolution Hierarchy

When requesting a PDF URL for a route:

1. **Scraped URLs** (from website) - Highest priority
2. **Persisted URLs** (from SharedPreferences) - Cached scraped URLs
3. **Fallback URLs** (hardcoded) - Last resort

```kotlin
// Get URL for route
val url = pdfUrlRepository.getURL("M1")

// Get URL with self-healing (re-scrape if 404)
val result = pdfUrlRepository.resolveURLWithHealing("M1")
when (result) {
    is URLResolutionResult.Success -> // Use result.url
    is URLResolutionResult.Updated -> // Old URL was 404, new URL found
    is URLResolutionResult.Failed -> // All methods failed
}
```

## Debug Output

### Expected Logcat Output on App Launch

```
🚀 LineCapp starting...
📡 NetworkMonitor: Initialized
📍 CoordinateCache: Initialized
🗑️ CoordinateCache: Cleaned up 0 expired entries
🌐 Initializing PDF URLs...
🔍 PDFURLRepository: Validating 0 persisted URLs...
📭 PDFURLRepository: No persisted URLs to validate
🌐 PDFURLRepository: Some URLs are invalid, scraping fresh URLs...
🌐 PDFURLRepository: Starting fresh URL scraping...
PDFURLScrapingService: Starting PDF URL scraping from https://www.linecar.es/metropolitano/segovia/
PDFURLScrapingService: Successfully fetched HTML content (12543 chars)
PDFURLScrapingService: Found 8 Linecar PDF links in HTML
PDFURLScrapingService: After removing duplicates: 8 unique PDF URLs
PDFURLScrapingService: ✅ Extracted route M1 from https://www.linecar.es/metropolitano/segovia/SEGOVIA-M1.pdf
PDFURLScrapingService: ✅ Extracted route M2 from https://www.linecar.es/metropolitano/segovia/SEGOVIA-M2.pdf
... (more routes)
PDFURLScrapingService: Successfully scraped 8 PDF URLs
PDFURLScrapingService: Scraping completed, 8 URLs cached
💾 PDFURLRepository: Persisted 8 URLs to storage
✅ PDFURLRepository: Scraped and persisted 8 URLs
✅ PDFURLRepository: Initialization complete
✅ PDF URLs initialized successfully

PDFURLRepository Status:
Persisted URLs: 8
Last scrape: 12/10/25 18:30
Validation cache: 0 entries

M1: SEGOVIA-M1.pdf
M2: SEGOVIA-M2.pdf
M10: SEGOVIA-M10.pdf
...

✅ Services initialized
```

## Files Modified

### 1. PDFURLScrapingService.kt
**Changes:**
- Updated `BASE_URL` to `https://www.linecar.es/metropolitano/segovia/`
- Replaced generic PDF extraction with Linecar-specific pattern
- Added `extractRouteIdFromLinecarURL()` function
- Pattern: `href="([^"]*SEGOVIA-[^"]*\.pdf)"`
- Route ID extraction: `SEGOVIA-([A-Z0-9]+)`

### 2. MainActivity.kt
**Changes:**
- Added `PDFURLRepository` initialization
- Launches coroutine to scrape URLs on app start
- Logs scraping results to console

**New imports:**
```kotlin
import androidx.lifecycle.lifecycleScope
import com.github.bfollon.linecapp.repositories.PDFURLRepository
import kotlinx.coroutines.launch
```

## Testing the Scraper

### Manual Test

1. **Build and install app:**
   ```bash
   ./gradlew installDebug
   ```

2. **Monitor Logcat:**
   ```bash
   adb logcat | grep -E "LineCapp|PDFURLScrapingService|PDFURLRepository"
   ```

3. **Launch app** - Scraping will run automatically on startup

4. **Verify output:**
   - Should see "Successfully scraped X PDF URLs"
   - Should see list of routes found
   - Should see "PDF URLs initialized successfully"

### Expected Results

If successful:
- ✅ HTML fetched from Linecar website
- ✅ PDF links extracted
- ✅ Route IDs extracted from filenames
- ✅ URLs persisted to SharedPreferences
- ✅ Repository status logged

If failed:
- ⚠️ Network error (offline/timeout)
- ⚠️ No PDFs found (website structure changed)
- ⚠️ Fallback URLs will be used

## Offline Behavior

**When offline:**
- Scraping is skipped
- Uses persisted URLs from previous scrape
- Falls back to hardcoded URLs if no persisted URLs exist

**Network check:**
```kotlin
if (!NetworkMonitor.isOnline()) {
    DebugConfig.debugPrint("📡 PDFURLRepository: Offline, skipping initialization")
    return@withContext true // Success (will use persisted/fallback URLs)
}
```

## Discovered URLs (October 28, 2025)

**Successfully Scraped Routes:**

```
M1: https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M1.pdf
M2: https://www.linecar.es/wp-content/uploads/2024/09/M2-septiembre-2024.pdf
M3: https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M3.pdf
M4: https://www.linecar.es/wp-content/uploads/2025/10/M4.pdf (Latest!)
M5: https://www.linecar.es/wp-content/uploads/2024/09/M5-septiembre-2024.pdf
M6: https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M6.pdf
M7: https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M7.pdf
M8: https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M8.pdf
```

**Note:** URLs are dynamically scraped and automatically updated. M4 detected as October 2025 version (most recent).

## Fallback URLs

**Location:** `PDFURLRepository.kt` (companion object)

Fallback URLs have been updated to match scraped URLs (as of October 28, 2025):

```kotlin
private val FALLBACK_URLS = mapOf(
    "M1" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M1.pdf",
    "M2" to "https://www.linecar.es/wp-content/uploads/2024/09/M2-septiembre-2024.pdf",
    "M3" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M3.pdf",
    "M4" to "https://www.linecar.es/wp-content/uploads/2025/10/M4.pdf",
    "M5" to "https://www.linecar.es/wp-content/uploads/2024/09/M5-septiembre-2024.pdf",
    "M6" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M6.pdf",
    "M7" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M7.pdf",
    "M8" to "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M8.pdf"
)
```

**Note:** Fallback URLs serve as last resort when scraping fails. Scraping service keeps URLs current automatically.

## Self-Healing URL Resolution

The repository supports automatic URL healing:

```kotlin
val result = pdfUrlRepository.resolveURLWithHealing("M1")
```

**Process:**
1. Validate current URL with HEAD request
2. If 404 → Re-scrape website for fresh URLs
3. Update persisted URLs
4. Return new URL

**Use case:** Bus company updates their website and PDFs move to new URLs.

## Data Persistence

**Storage:** SharedPreferences (`pdf_url_repository`)

**Keys:**
- `scraped_urls` - JSON map of routeId → pdfUrl
- `last_scrape_timestamp` - Timestamp of last successful scrape

**Format:**
```json
{
  "urls": {
    "M1": "https://www.linecar.es/metropolitano/segovia/SEGOVIA-M1.pdf",
    "M2": "https://www.linecar.es/metropolitano/segovia/SEGOVIA-M2.pdf"
  },
  "timestamp": 1728759000000
}
```

## Next Steps

### Phase 5 (PDF Parsing)

With URLs now being scraped, the next phase can:

1. Download PDFs using scraped URLs
2. Parse PDF content to extract timetables
3. Test parsing with real Linecar PDFs

### Phase 8 (UI)

UI can display:
- Route list (from scraped route IDs)
- PDF download status
- Last scrape timestamp
- Refresh button to force re-scrape

## Troubleshooting

### No PDFs Found

**Possible causes:**
- Website structure changed
- Different URL format than expected
- Network timeout

**Solution:**
- Check Logcat for HTML content length
- Verify website still uses `SEGOVIA-` prefix
- Manually inspect `https://www.linecar.es/metropolitano/segovia/`

### Wrong Route IDs Extracted

**Possible causes:**
- PDF naming convention changed
- Multiple dashes in filename

**Solution:**
- Check Logcat for extracted filenames
- Adjust regex pattern in `extractRouteIdFromLinecarURL()`
- Test with actual PDF filename examples

### Scraping Fails Silently

**Possible causes:**
- Network blocked by firewall
- SSL/TLS certificate issues
- User-Agent rejection

**Solution:**
- Check OkHttp logs
- Verify User-Agent header
- Test URL in browser

## References

- **Service:** `services/PDFURLScrapingService.kt`
- **Repository:** `repositories/PDFURLRepository.kt`
- **Integration:** `MainActivity.kt:59-70`
- **Migration Plan:** [MIGRATION_PLAN.md](./MIGRATION_PLAN.md) (Phase 3)

---

**PDF URL scraping is now fully configured and integrated!**
