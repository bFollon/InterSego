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

## Reloading via SIGHUP

As an alternative to the HTTP endpoint, send `SIGHUP` to the server process to trigger the same reload:

```bash
# With pm2
pm2 sendSignal SIGHUP intersego-server

# Directly (use pm2 pid to find the PID)
kill -HUP $(pm2 pid intersego-server)
# or
kill -HUP <pid>
```
