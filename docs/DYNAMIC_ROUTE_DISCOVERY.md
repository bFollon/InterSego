# Dynamic Route Discovery

## Status: Proposed

## Motivation

Adding a new route (e.g. M9, M10, M11) currently requires bundling its timetable JSON
and polyline JSON into the next app release on **both** platforms, even though the
server already serves the same files via `/api/timetables/:routeId` and
`/api/polylines/:id`.

**Goal:** once this feature ships, adding route M12 becomes a server-only operation:

1. Drop `resources/timetables/m12.json` (+ `resources/route_polylines/M12-*.json`) into
   the server's data directories.
2. `POST /api/timetables/reload` and `POST /api/polylines/reload`.
3. The app picks up M12 automatically next time it's online — no new app build, no
   bundled assets.

This is the "Next milestone" referenced in the root `CLAUDE.md` and Android `CLAUDE.md`.

---

## Current Architecture (as of `feature/remote-polylines`)

Route discovery is **bundle-driven**, not hardcoded, but the bundle is still the only
source the app consults for "which routes exist":

- **`TimetableLoader.loadAllRoutes()`** (Android & iOS): scans the app bundle
  (`context.assets.list("timetables")` / `Bundle.main.urls(forResourcesWithExtension:subdirectory:)`),
  loads each JSON, and returns `[BusRoute]` sorted by `displayOrder`. This list backs
  `getKnownRoutes()` (Android) / `BusRouteRegistry.knownRoutes()` (iOS) — i.e. everything
  the UI shows.
- **`TimetableCacheService.fetchAllRoutes()`** (Android & iOS): derives the route ID list
  from the same bundle scan (`bundleRouteIds()`), then fetches each one from
  `GET /api/timetables/:routeId` with ETag/304. Updated files are written to the disk
  cache, which `TimetableLoader.loadFile()` already prefers over the bundle.
- **`PolylineCacheService.fetchAllPolylines()`** (Android & iOS): same pattern, deriving
  polyline IDs from the bundled `route_polylines/` / `RoutePolylines/` assets and
  fetching `GET /api/polylines/:id`.
- **Server** (`services/InterSegoService/src/routes/`): already exposes
  `GET /api/timetables` (all routes) and `GET /api/polylines` (all polylines as a map),
  in addition to the per-ID endpoints. Neither is consumed by the apps today.

**The gap:** a route that exists only on the server (not in either app's bundle) is
invisible to `bundleRouteIds()`, so `TimetableCacheService` never fetches it and
`loadAllRoutes()` never lists it — regardless of disk cache contents.

---

## Target Architecture

```
Server:
  GET /api/routes  →  { "routeIds": ["m1", "m2", ..., "m12"] }   (NEW — lightweight manifest)

App startup (online):
  1. GET /api/routes                         → manifest
  2. union(bundleRouteIds, diskCacheRouteIds, manifest.routeIds)
  3. For each ID:
       - in bundle/disk already → existing per-route ETag fetch (unchanged)
       - new (server-only)      → GET /api/timetables/:routeId → write to disk cache
                                   → parse `variants[].id` → fetch
                                     GET /api/polylines/{ROUTEID}-{variantId} for each

TimetableLoader.loadAllRoutes():
  scan bundle assets ∪ scan disk cache dir → union by routeId → loadFile() per ID
  (loadFile() already prefers disk cache over bundle — no change needed there)
```

### Why a dedicated manifest endpoint (not the existing bulk `/api/timetables`)

`GET /api/timetables` returns full route JSON for everything and has a single ETag for
the whole payload — using it for discovery would mean any single route's data change
forces a full re-download of all routes' data on the next launch, defeating the
per-route ETag scheme described in the original version of this doc. A manifest
endpoint returning just IDs is tiny, so it's fine to fetch unconditionally on every
online launch (no ETag needed).

---

## Required Changes

### 1. Server (`services/InterSegoService`)

Add `GET /api/routes` to `src/routes/timetables.ts` (or a new `routes.ts`), gated by
`requireApiKey` like the other endpoints:

```ts
app.get('/api/routes', { preHandler: requireApiKey }, async (_request, reply) => {
  return reply.send({ routeIds: [...cache.routes.keys()] });
});
```

No new cache structure needed — `cache.routes` already holds one entry per route ID.

### 2. `TimetableCacheService` (Android & iOS)

- Add a manifest fetch (`GET /api/routes`) at the start of `fetchAllRoutes()`.
- Compute `knownRouteIds = bundleRouteIds ∪ diskCacheRouteIds` (disk cache dir is
  `filesDir/timetables/` on Android, `Caches/Timetables/` on iOS — already exists for
  ETag-updated bundled routes, just needs listing).
- For `routeId in manifest.routeIds`:
  - if in `knownRouteIds` → existing per-route ETag fetch (no behavioral change)
  - if new → `GET /api/timetables/:routeId` (no `If-None-Match`, nothing cached yet) →
    write to disk cache, add to `pendingUpdates`
- For new routes, after writing the timetable JSON, parse `variants[].id` from the
  response and call the polyline fetch (see below) for each
  `{ROUTEID}-{variantId}` — matching the casing convention used by bundled polyline
  filenames (e.g. `M12-outbound.json`).
- If the manifest fetch fails (offline / error), fall back to current behavior
  (`bundleRouteIds` only) — this is the existing offline path, unchanged.

### 3. `PolylineCacheService` (Android & iOS)

- Expose a `fetchPolyline(id)`-equivalent that can be called for IDs not derived from
  the bundle (it already exists as a private function — just needs to be reachable from
  `TimetableCacheService`'s new-route flow, or duplicated as a small internal helper).
- No change to `fetchAllPolylines()`'s existing bundle-driven loop.

### 4. `TimetableLoader.loadAllRoutes()` (Android & iOS)

- In addition to scanning bundle assets, list the disk cache directory
  (`filesDir/timetables/` / `Caches/Timetables/`) for `*.json` files.
- Union route IDs from both sources (dedupe — a route may be in both once cached).
- `loadFile(routeId)` already prefers disk cache, so loading is unchanged; this only
  affects which IDs get loaded at all.
- Sorting by `displayOrder` (from each route's JSON) continues to work unchanged —
  this is also what fixed the M10/M11 ordering bug (commit `593de78`), so no further
  sort logic is needed.

### 5. `PolylineLoader` (Android & iOS)

No change required — it already checks the disk cache before the bundle and returns
an empty list on any failure (map simply shows no line if a polyline is missing).

---

## Edge Cases & Notes

- **`BusStopRegistry.findById()`** (Android `data/BusStopRegistry.kt`, iOS
  `Models/BusStopRegistry.swift`) is a hardcoded stop lookup used as a fast path when
  resolving a `stopId` from a deep link / reminder. It will not contain stops from
  server-only routes. This is **not a blocker** — both platforms already fall back to
  searching `routeDataService.getSupportedRoutes()` → `getRouteViews()` →
  `stops.find { it.id == stopId }` (see `MainActivity.kt:898-913` and the iOS
  equivalent), which will include server-discovered routes once `loadAllRoutes()` is
  extended (step 4 above).
- **First launch, offline:** a server-only route simply doesn't appear until the first
  online launch successfully fetches the manifest. This matches the existing
  "disk cache / bundle as last resort" contract.
- **Partial failure:** if a new route's timetable fetch succeeds but one of its
  polyline fetches fails, `PolylineLoader` returns an empty list for that view — the
  map renders without a line rather than crashing. Acceptable for v1.
- **`pendingUpdates` (Android) / hot-swap:** newly discovered routes have nothing to
  evict from `TimetableService`'s in-memory cache (it's empty for an unknown route id),
  so adding the new ID to `pendingUpdates` is harmless but not strictly required.
- **Version bumps:** routes that *are* bundled still follow the existing
  "bump `version` on every JSON edit" discipline. Server-only routes have no bundle
  copy to keep in sync, so this discipline only applies until/unless a route is later
  folded into a bundled release.

---

## Implementation Plan

Each phase is independently testable and should be its own commit, per project
convention:

1. **Server**: add `GET /api/routes`, update `services/InterSegoService/docs/API.md`.
2. **Android**: `TimetableCacheService` manifest fetch + new-route handling,
   `TimetableLoader.loadAllRoutes()` disk-cache scan. Test by manually placing a
   `resources/timetables/m12.json` (test fixture) on the server only — confirm it
   appears in the app after a fresh launch online, without touching Android assets.
3. **iOS**: mirror the Android changes.
4. **Cross-platform feature tracker**: update root `CLAUDE.md` to mark "File-driven
   route discovery" as server-aware on both platforms.

---

## Testing Checklist

- [ ] New server-only route appears in route list, correctly positioned by `displayOrder`
- [ ] Its timetable (NextDeparture, DaySchedule, RouteStops) renders correctly
- [ ] Its map screen shows the polyline (or gracefully shows none if fetch failed)
- [ ] Closest-stop finder includes its stops
- [ ] Existing bundled routes still update via per-route ETag (no regression to
      per-route 304 behavior)
- [ ] Offline first launch: app functions normally with only bundled routes
- [ ] Removing a route from the server manifest does **not** remove it from a device
      that already cached it (out of scope for v1 — no eviction logic)
