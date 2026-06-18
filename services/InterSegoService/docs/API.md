# InterSego Server — API Reference

Base URL: `https://<your-tunnel-domain>` (Cloudflare Tunnel) or `http://localhost:3000` locally.

## Authentication

Most endpoints require the app API key:

```
Authorization: Bearer <API_KEY>
```

The reload endpoint uses a separate operator-only key that never leaves the server:

```
Authorization: Bearer <RELOAD_KEY>
```

On error:

```json
{ "error": "Unauthorized" }
```
HTTP 401.

---

## GET /health

Health check. No authentication required.

**Response 200**
```json
{ "status": "ok" }
```

---

## POST /boardings

Record that a user has boarded a bus at a given stop.

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `stopId` | string | ✅ | Canonical stop ID from BusStopRegistry (e.g. `"azoguejo"`) |
| `routeId` | string | ✅ | Route identifier (e.g. `"M4"`) |
| `direction` | string | ✅ | Direction label from BusTimetable (e.g. `"Lastrilla → Sotillo"`) |
| `tripKey` | string | ✅ | Synthetic trip key — see format below |
| `boardedAt` | string | ✅ | ISO 8601 UTC timestamp of boarding (e.g. `"2026-04-01T07:31:42Z"`) |
| `scheduledDepartureTime` | string | ❌ | ISO 8601 UTC timestamp of the scheduled departure at the boarding stop |

#### tripKey format

```
{routeId}|{direction}|{dayType}|{HH:MM}
```

- `dayType`: `WEEKDAY`, `SATURDAY`, or `SUNDAY`
- `HH:MM`: scheduled departure time at the boarding stop, zero-padded, 24h

**Example:** `M4|Lastrilla → Sotillo|WEEKDAY|07:30`

#### Example request

```json
{
  "stopId": "azoguejo",
  "routeId": "M4",
  "direction": "Lastrilla → Sotillo",
  "tripKey": "M4|Lastrilla → Sotillo|WEEKDAY|07:30",
  "boardedAt": "2026-04-01T07:31:42Z",
  "scheduledDepartureTime": "2026-04-01T07:30:00Z"
}
```

```bash
curl -X POST https://<host>/boardings \
  -H "Authorization: Bearer <API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{
    "stopId": "azoguejo",
    "routeId": "M4",
    "direction": "Lastrilla → Sotillo",
    "tripKey": "M4|Lastrilla → Sotillo|WEEKDAY|07:30",
    "boardedAt": "2026-04-01T07:31:42Z",
    "scheduledDepartureTime": "2026-04-01T07:30:00Z"
  }'
```

### Response 201

The created boarding event with server-assigned `id` and `expiresAt`.

```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "stopId": "azoguejo",
  "routeId": "M4",
  "direction": "Lastrilla → Sotillo",
  "tripKey": "M4|Lastrilla → Sotillo|WEEKDAY|07:30",
  "boardedAt": "2026-04-01T07:31:42Z",
  "scheduledDepartureTime": "2026-04-01T07:30:00Z",
  "expiresAt": "2026-04-01T11:31:42Z"
}
```

### Errors

| Status | Condition |
|---|---|
| 400 | Missing required field or invalid body |
| 401 | Missing or invalid Authorization header |

---

## GET /boardings

Return all active (non-expired) boarding events. Expired events are removed from storage on this call.

```bash
curl https://<host>/boardings \
  -H "Authorization: Bearer <API_KEY>"
```

### Response 200

Array of `BoardingEvent` objects (may be empty).

```json
[
  {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "stopId": "azoguejo",
    "routeId": "M4",
    "direction": "Lastrilla → Sotillo",
    "tripKey": "M4|Lastrilla → Sotillo|WEEKDAY|07:30",
    "boardedAt": "2026-04-01T07:31:42Z",
    "scheduledDepartureTime": "2026-04-01T07:30:00Z",
    "expiresAt": "2026-04-01T11:31:42Z"
  }
]
```

### Errors

| Status | Condition |
|---|---|
| 401 | Missing or invalid Authorization header |

---

## How clients use GET /boardings

1. Fetch all active boardings.
2. Filter by `tripKey` to find events for the same trip the user is viewing.
3. Compute lateness: `lateness = boardedAt − scheduledDepartureTime` (in seconds). If `scheduledDepartureTime` is absent, lateness is unknown.
4. Apply lateness to the user's own scheduled departure time at their stop: `estimatedDeparture = scheduledAtMyStop + lateness`.
5. If any matching event exists, the departure is confirmed as running.

Multiple events for the same `tripKey` may exist (multiple passengers boarded).
Average the lateness values across all events for a more robust estimate.

---

## GET /api/routes

Returns the list of all route IDs known to the server. Lightweight manifest endpoint
used by clients to discover routes that exist on the server but not in their local
bundle/disk cache — fetched unconditionally on every online launch (no ETag).

```bash
curl https://<host>/api/routes \
  -H "Authorization: Bearer <API_KEY>"
```

### Response 200

```json
{ "routeIds": ["m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10", "m11"] }
```

### Errors

| Status | Condition |
|---|---|
| 401 | Missing or invalid Authorization header |

---

## GET /api/timetables

Returns a JSON array containing the full timetable object for every route.

```bash
curl https://<host>/api/timetables \
  -H "Authorization: Bearer <API_KEY>"
```

### Caching

The response includes an `ETag` header. Send the value back in subsequent requests via `If-None-Match` to receive a `304 Not Modified` when nothing has changed:

```bash
curl https://<host>/api/timetables \
  -H "Authorization: Bearer <API_KEY>" \
  -H 'If-None-Match: "abc123def456789a"'
```

### Response 200

JSON array of route timetable objects (same schema as the individual route endpoint).

### Response 304

No body. The cached copy is still current.

### Errors

| Status | Condition |
|---|---|
| 401 | Missing or invalid Authorization header |

---

## GET /api/timetables/:routeId

Returns the timetable JSON for a single route. Route IDs are case-insensitive (`m1` and `M1` both work).

```bash
curl https://<host>/api/timetables/m4 \
  -H "Authorization: Bearer <API_KEY>"
```

### Caching

Same ETag / `If-None-Match` behaviour as the all-routes endpoint, scoped to this route's content.

### Response 200

The route timetable object (the full contents of the corresponding JSON file).

### Response 304

No body. The cached copy is still current.

### Errors

| Status | Condition |
|---|---|
| 401 | Missing or invalid Authorization header |
| 404 | No timetable file found for the given routeId |

---

## POST /api/timetables/reload

Re-reads all timetable files from disk and replaces the in-memory cache. Use after editing or dropping in a new JSON file — no server restart needed.

```bash
curl -X POST https://<host>/api/timetables/reload \
  -H "Authorization: Bearer <RELOAD_KEY>"
```

### Response 200

```json
{
  "reloaded": 8,
  "routes": ["m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8"]
}
```

### Errors

| Status | Condition |
|---|---|
| 401 | Missing or invalid Authorization header |

---

## GET /api/polylines

Returns a JSON object mapping every polyline id to its polyline data.

```bash
curl https://<host>/api/polylines \
  -H "Authorization: Bearer <API_KEY>"
```

### Caching

The response includes an `ETag` header. Send the value back in subsequent requests via `If-None-Match` to receive a `304 Not Modified` when nothing has changed.

### Response 200

```json
{
  "m1-circulara": { "version": "1.0", "coordinates": [[40.944672, -4.121719], ...] },
  "m7-ave-outbound": { "version": "1.0", "coordinates": [[40.95, -4.11], ...] }
}
```

### Response 304

No body. The cached copy is still current.

### Errors

| Status | Condition |
|---|---|
| 401 | Missing or invalid Authorization header |

---

## GET /api/polylines/:id

Returns the polyline JSON for a single route view. Ids are case-insensitive (`m1-circulara` and `M1-circularA` both work) and combine the route id and view id as `{routeId}-{viewId}`.

```bash
curl https://<host>/api/polylines/m1-circulara \
  -H "Authorization: Bearer <API_KEY>"
```

### Caching

Same ETag / `If-None-Match` behaviour as the all-polylines endpoint, scoped to this polyline's content.

### Response 200

The polyline object (the full contents of the corresponding JSON file).

```json
{ "version": "1.0", "coordinates": [[40.944672, -4.121719], ...] }
```

### Response 304

No body. The cached copy is still current.

### Errors

| Status | Condition |
|---|---|
| 401 | Missing or invalid Authorization header |
| 404 | No polyline file found for the given id |

---

## POST /api/polylines/reload

Re-reads all polyline files from disk and replaces the in-memory cache. Use after editing or dropping in a new JSON file — no server restart needed.

```bash
curl -X POST https://<host>/api/polylines/reload \
  -H "Authorization: Bearer <RELOAD_KEY>"
```

### Response 200

```json
{
  "reloaded": 32,
  "polylines": ["m1-circulara", "m1-circularb", "..."]
}
```

### Errors

| Status | Condition |
|---|---|
| 401 | Missing or invalid Authorization header |

---

## GET /alerts

Returns all currently active alerts. No authentication required — apps poll this at startup.

```bash
curl https://<host>/alerts
```

### Response 200

```json
[
  {
    "id": "8af8bd1a-a451-4e42-9a41-9aa817e79523",
    "title": "Huelga de transportes",
    "message": "Los autobuses pueden tener retrasos hoy.",
    "severity": "warning",
    "affectedRoutes": ["M1", "M3"],
    "startsAt": "2026-06-18T08:00:00.000Z",
    "endsAt": "2026-06-18T20:00:00.000Z"
  }
]
```

`severity` is one of `info`, `warning`, `critical`. `affectedRoutes` is omitted when all routes are affected.

---

## POST /admin/alerts

Create a service alert. Requires `RELOAD_KEY`.

Timestamps accept UTC (`Z`), explicit offset (`+02:00`), or naive local time (interpreted as `Europe/Madrid`).

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | string | ✅ | Short title (max 256 chars) |
| `message` | string | ✅ | Full description (max 1024 chars) |
| `severity` | string | ✅ | `info`, `warning`, or `critical` |
| `startsAt` | string | ✅ | ISO 8601 timestamp — when alert becomes active |
| `endsAt` | string | ✅ | ISO 8601 timestamp — when alert expires |
| `affectedRoutes` | string[] | ❌ | Route IDs affected; omit for all routes |

```bash
curl -X POST https://<host>/admin/alerts \
  -H "Authorization: Bearer <RELOAD_KEY>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Huelga de transportes",
    "message": "Los autobuses pueden tener retrasos hoy.",
    "severity": "warning",
    "affectedRoutes": ["M1", "M3"],
    "startsAt": "2026-06-18T08:00:00",
    "endsAt": "2026-06-18T20:00:00"
  }'
```

### Response 201

```json
{ "id": "8af8bd1a-a451-4e42-9a41-9aa817e79523" }
```

---

## DELETE /admin/alerts/:id

Delete an alert by ID. Requires `RELOAD_KEY`.

```bash
curl -X DELETE https://<host>/admin/alerts/8af8bd1a-a451-4e42-9a41-9aa817e79523 \
  -H "Authorization: Bearer <RELOAD_KEY>"
```

### Response 204

No body.

---

## GET /admin/alerts

List all alerts — active, future, and expired. Requires `RELOAD_KEY`.

```bash
curl https://<host>/admin/alerts \
  -H "Authorization: Bearer <RELOAD_KEY>"
```

### Response 200

Array of `ServiceAlert` objects including the internal `broadcastSent` flag.

---

## POST /admin/alerts/broadcast

Force an immediate alert broadcast tick. Useful for testing — normally the broadcaster runs every 5 minutes. Requires `RELOAD_KEY`.

```bash
curl -X POST https://<host>/admin/alerts/broadcast \
  -H "Authorization: Bearer <RELOAD_KEY>"
```

### Response 202

```json
{ "message": "broadcast triggered" }
```

The tick runs in the background. Check server logs for delivery results.

---

## POST /device-tokens

Register or update a device token for alert push broadcasts. Called by both platforms at app launch and when the token rotates. Requires `API_KEY`.

### Request body

| Field | Type | Required | Description |
|---|---|---|---|
| `token` | string | ✅ | FCM token (Android) or APNs hex token (iOS) |
| `platform` | string | ✅ | `ios` or `android` |
| `minSeverity` | string | ❌ | Minimum alert severity to receive: `info` (default), `warning`, `critical`, or `none` |
| `environment` | string | ❌ | iOS only: `sandbox` (dev/TestFlight) or `production` (App Store). Used to route to the correct APNs endpoint. |

```bash
curl -X POST https://<host>/device-tokens \
  -H "Authorization: Bearer <API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{ "token": "abc123...", "platform": "ios", "environment": "sandbox", "minSeverity": "info" }'
```

### Response 204

No body.

---

## DELETE /device-tokens/:token

Remove a device token (e.g. on uninstall or explicit opt-out). Requires `API_KEY`.

```bash
curl -X DELETE https://<host>/device-tokens/abc123... \
  -H "Authorization: Bearer <API_KEY>"
```

### Response 204

No body.

---

## Reloading via SIGHUP

As an alternative to the HTTP endpoints, send `SIGHUP` to the server process to reload both the timetable and polyline caches:

```bash
# With pm2
pm2 sendSignal SIGHUP intersego-server

# Directly (use pm2 pid to find the PID)
kill -HUP $(pm2 pid intersego-server)
# or
kill -HUP <pid>
```
