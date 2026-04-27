# File-Based Asset Update Plan

Goal: ensure that updating a timetable or polyline JSON file is all that's needed to change app behaviour on both platforms — no code changes required. This is largely already true; the steps below close the remaining gaps and remove a confusing broken layer.

---

## Current state

| Asset | How loaded | File change enough? |
|---|---|---|
| Timetables | `TimetableLoader` reads JSON from bundle | ✅ Yes — but `TimetableCacheService` could theoretically serve stale parsed data if it ever worked |
| Polylines | Inline `loadBundledPolyline()` in `RouteMapView`/`RouteMapScreen` | ✅ Yes — bare `[[lat, lon]]` array decoded directly |
| Route/stop metadata | Embedded in timetable JSON (`stops`, `variants`, `routeDisplay`) | ✅ Yes |

The only real problems are:
1. `TimetableCacheService` is broken (always misses due to a stale PDF-timestamp check) and adds confusion — it could block file updates if it were ever fixed without being reworked.
2. Polyline loading logic is duplicated inline in both map views with no central home.
3. Polyline files have no version field, so there's no consistent way to track when they changed.

---

## Step 1 — Remove TimetableCacheService

The service caches parsed `[BusTimetable]` structs on disk. Its validity check has been broken since the JSON migration (it looks for a PDF file that no longer exists, so it always misses). JSON parsing is fast enough that the cache adds no meaningful performance benefit.

**iOS**
- Delete `TimetableCacheService.swift`.
- Remove all call sites (grep for `TimetableCacheService`).
- Remove `AppDirectories.pdfs` if it's only referenced from the cache service.

**Android**
- Delete `TimetableCacheService.kt`.
- Remove all call sites (grep for `TimetableCacheService`).

**Both:** update the CLAUDE.md feature tracker row for "Timetable cache service" to reflect it has been removed.

---

## Step 2 — Add a version field to polyline files

Mirrors the `"version"` field already present in timetable JSON. Lets you track what changed and when, and keeps the format consistent across asset types.

New format:
```json
{
  "version": "1.0",
  "coordinates": [
    [40.944672, -4.121719],
    [40.944722, -4.121639]
  ]
}
```

- Update all files under `resources/route_polylines/` (~40 files). Start versions at `"1.0"`.
- Mirror changes to `android/app/src/main/assets/route_polylines/` and `iOS/InterSego/RoutePolylines/`.
- Bump the version whenever coordinates are edited (same discipline as timetable `"version"`).

---

## Step 3 — Create PolylineLoader (both platforms)

Move the inline `loadBundledPolyline()` logic out of the map views into a dedicated loader. Mirrors the pattern of `TimetableLoader` and ensures any future format change (e.g. adding metadata fields) is made in one place.

**iOS — `PolylineLoader.swift`**

```swift
struct PolylineLoader {
    private struct PolylineFile: Decodable {
        let version: String
        let coordinates: [[Double]]
    }

    func load(routeId: String, viewId: String) -> [CLLocationCoordinate2D] {
        guard let url = Bundle.main.url(
            forResource: "\(routeId)-\(viewId)",
            withExtension: "json",
            subdirectory: "RoutePolylines"
        ),
        let data = try? Data(contentsOf: url),
        let file = try? JSONDecoder().decode(PolylineFile.self, from: data)
        else { return [] }

        return file.coordinates.compactMap { pair in
            guard pair.count >= 2 else { return nil }
            return CLLocationCoordinate2D(latitude: pair[0], longitude: pair[1])
        }
    }
}
```

Replace the inline `loadBundledPolyline(routeId:viewId:)` in `RouteMapView.swift` with a call to `PolylineLoader().load(routeId:viewId:)`.

**Android — `PolylineLoader.kt`**

```kotlin
object PolylineLoader {
    @Serializable
    private data class PolylineFile(
        val version: String,
        val coordinates: List<List<Double>>
    )

    fun load(context: Context, routeId: String, viewId: String): List<GeoPoint> {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val text = context.assets
                .open("route_polylines/$routeId-$viewId.json")
                .bufferedReader().readText()
            val file = json.decodeFromString<PolylineFile>(text)
            file.coordinates.mapNotNull { pair ->
                if (pair.size >= 2) GeoPoint(pair[0], pair[1]) else null
            }
        } catch (_: Exception) { emptyList() }
    }
}
```

Replace the inline `loadPolyline()` in `RouteMapScreen.kt` with `PolylineLoader.load(context, routeId, viewId)`.

---

## What stays the same

- Timetable loading via `TimetableLoader` — already clean.
- The 3-copy discipline (`resources/`, Android assets, iOS bundle) — unchanged; a script could automate the sync in future but is not needed now.
- Route and stop metadata inside timetable JSONs — already file-driven.

---

## Implementation order

1. Step 1 (remove cache service) — independent, do first to eliminate confusion.
2. Step 2 (version polylines) — data-only change, no code risk.
3. Step 3 (PolylineLoader) — depends on Step 2 (loader decodes new format).
