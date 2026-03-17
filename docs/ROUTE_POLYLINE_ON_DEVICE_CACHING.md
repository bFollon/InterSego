# On-Device Route Polyline Fetching & Caching

## Status: Not implemented — design notes preserved for future reference

The current approach bundles pre-computed road-following polylines as static JSON assets
(see `resources/route_polylines/` and the generation script at
`resources/scripts/generate_route_polylines.sh`). This is sufficient because **stops are
hardcoded in the parser files** and only change with a code update + app release. When stops
change, the script is re-run and new polylines are bundled with the new version.

This document captures the design for an on-device fetch-and-cache approach, which becomes
worthwhile if either of these conditions is ever met:

- Stops become dynamic (e.g. parsed from a remote source or user-editable).
- Live bus stop detection is added (GPS-based "closest stop" logic that could surface stops
  the user hasn't seen before, making bundled polylines feel stale within a single session).

---

## Design

### Core idea

On first map open for a route+direction, load the bundled JSON as a seed. In the background,
fetch a fresh polyline from OSRM and cache it to disk. On subsequent opens, serve the disk
cache if it's still valid; otherwise re-fetch silently.

### Cache key & invalidation

Key the disk cache entry by a **hash of the stop routing coordinate sequence** for a given
`routeId + viewId` pair. Any stop addition, removal, or coordinate change produces a different
hash → cache miss → silent background re-fetch.

```
cacheKey = sha256(routeId + ":" + viewId + ":" + stops.map { "${it.routingLatitude},${it.routingLongitude}" }.joinToString(";"))
```

Stored as `{cacheKey}.json` in the platform cache directory. No TTL needed; the hash acts as
the version identifier.

### Fetch endpoint

OSRM public demo (no API key required, acceptable for low-traffic apps with proper caching):

```
https://router.project-osrm.org/route/v1/driving/{lon1,lat1};{lon2,lat2};...?overview=full&geometries=geojson
```

Extract `routes[0].geometry.coordinates` → array of `[lon, lat]` → swap to `[[lat, lon], ...]`
(same format as the bundled JSON files).

### Fallback chain (in priority order)

1. Valid disk cache entry (hash matches current stops).
2. Bundled JSON asset (ships with the app; always present for M4 and M6).
3. Straight lines between stop markers (zero-config last resort).

While a background fetch is in progress, the map displays whichever fallback is available.
The polyline updates in-place once the fetch completes.

---

## Platform implementation sketch

### Android — `RoutePolylineService`

```kotlin
class RoutePolylineService(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "route_polylines")

    suspend fun getPolyline(routeId: String, viewId: String, stops: List<BusStop>): List<GeoPoint> {
        val key = cacheKey(routeId, viewId, stops)
        val cached = readCache(key)
        if (cached != null) return cached

        // Return bundled seed immediately; fetch in background
        val seed = loadBundled(routeId, viewId)
        fetchAndCache(routeId, viewId, stops, key) // fire-and-forget coroutine
        return seed
    }

    private fun cacheKey(routeId: String, viewId: String, stops: List<BusStop>): String {
        val input = stops.joinToString(";") { "${it.routingLatitude},${it.routingLongitude}" }
        return MessageDigest.getInstance("SHA-256")
            .digest("$routeId:$viewId:$input".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun fetchAndCache(routeId: String, viewId: String, stops: List<BusStop>, key: String) {
        val coords = stops.mapNotNull { stop ->
            val lat = stop.routingLatitude ?: return@mapNotNull null
            val lon = stop.routingLongitude ?: return@mapNotNull null
            "$lon,$lat"
        }.joinToString(";")

        val url = "https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson"
        // … OkHttp call, parse geometry.coordinates, swap lon/lat, write to cache file …
    }
}
```

Replace the `loadPolylineFromAssets()` call in `RouteMapScreen.kt` with a call to this service
(wrapped in a `LaunchedEffect` or `ViewModel`).

### iOS — `RoutePolylineService`

```swift
actor RoutePolylineService {

    private let cacheDir: URL

    init() {
        cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("route_polylines")
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
    }

    func getPolyline(routeId: String, viewId: String, stops: [BusStop]) async -> [CLLocationCoordinate2D] {
        let key = cacheKey(routeId: routeId, viewId: viewId, stops: stops)
        if let cached = readCache(key: key) { return cached }

        let seed = loadBundled(routeId: routeId, viewId: viewId)
        Task { await fetchAndCache(routeId: routeId, viewId: viewId, stops: stops, key: key) }
        return seed
    }

    private func cacheKey(routeId: String, viewId: String, stops: [BusStop]) -> String {
        let input = stops.map { "\($0.routingLatitude ?? 0),\($0.routingLongitude ?? 0)" }.joined(separator: ";")
        let data = Data("\(routeId):\(viewId):\(input)".utf8)
        // SHA-256 via CryptoKit: SHA256.hash(data:).map { String(format: "%02x", $0) }.joined()
        return "" // placeholder
    }
}
```

Replace the `loadBundledPolyline()` call in `RouteMapView.swift` with an `await` call to this
service inside the existing `.task(id: currentViewId)` modifier.

---

## Notes

- The OSRM public demo has no SLA. If it's unavailable, the fallback chain handles it gracefully.
- Consider rate-limiting: add a short delay between waypoint requests if fetching multiple
  route views in one session (the generation script already does 1 s between requests as a
  courtesy).
- Cache files are in the OS cache directory and may be evicted under storage pressure — the
  bundled JSON ensures the app always has something to show.
