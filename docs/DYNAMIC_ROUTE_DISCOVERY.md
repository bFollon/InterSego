# Dynamic Route Discovery

## Current Architecture

Routes are statically defined in two places:

- **`TimetableCacheService`** (iOS & Android): hardcoded list `["m1"…"m8"]` — determines which routes are fetched from the server.
- **`TimetableLoader`** (iOS & Android): scans the *app bundle* at runtime (`Bundle.main.urls(forResourcesWithExtension:subdirectory:)` on iOS / `context.assets.list("timetables")` on Android) — determines which routes appear in the UI.

The server's `GET /api/timetables` endpoint is not used by either app; both call `GET /api/timetables/:routeId` per route in parallel.

## Why per-route instead of bulk `/timetables`

Per-route requests preserve ETag/304 efficiency: if only M4 changes, only M4 is re-downloaded. A single bulk endpoint would have one ETag for the whole payload, forcing a full re-download whenever any route changes. Since fetches run in parallel, latency is comparable to a bulk call.

## The Dynamic Route Problem

If a new route (e.g. M9) is added to the server, it will **not** appear in the app because:

1. `TimetableCacheService` won't fetch it — the route list is hardcoded.
2. `TimetableLoader` won't discover it — it only scans the bundle, not the disk cache.

Both layers need to change for dynamic routes to work.

## What a Fix Would Require

### 1. Server: expose a route manifest

Either a dedicated endpoint or embed it in the existing `/api/timetables` response:

```json
GET /api/timetables
→ { "routes": ["m1", "m2", ..., "m9"] }
```

### 2. `TimetableCacheService`: replace hardcoded list with manifest

Fetch the manifest first, then fetch each listed route. Cache the manifest to disk so offline launches know which routes exist.

### 3. `TimetableLoader`: scan cache directory in addition to bundle

On both platforms, after scanning bundle assets, also scan `Caches/Timetables/` (iOS) / `filesDir/timetables/` (Android) for JSON files not present in the bundle. These are server-only routes and should be loaded the same way.

### 4. Route metadata

Currently each JSON carries a top-level `"route"` block with `number`, `name`, `origin`, `destination`, etc. Server-only routes must include this block so the app can register them in `BusRouteRegistry` / `getKnownRoutes()` without a hardcoded entry.

## Current Status

Not implemented. The route set is fixed at M1–M8. This is acceptable for now — adding a new route still requires an app release (new JSON bundled in the build).

Revisit if the route set grows or if over-the-air route additions become a requirement.
