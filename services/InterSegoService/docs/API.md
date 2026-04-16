# InterSego Server — API Reference

Base URL: `https://<your-tunnel-domain>` (Cloudflare Tunnel) or `http://localhost:3000` locally.

## Authentication

All endpoints except `/health` require a Bearer token:

```
Authorization: Bearer <API_KEY>
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
