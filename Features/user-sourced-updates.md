# Feature: User-sourced live updates

## Problem

All departure times are static and estimated. Linecar buses are historically unpunctual — they can depart early, late, or not at all, leaving users stranded with no warning. With buses often running hourly, this is a bad experience: you never know if you arrived slightly too late and the bus passed early, or if it's 20 minutes late and you should wait.

## Target solution

The ideal solution would be for drivers to report their location. Since this app is a standalone effort without bus company cooperation, the next best thing is for the app to report the user's location intelligently and only during the trip, so other users get a live update of the bus.

## MVP — boarding notifications

Users tap a "I'm on the bus" button. This solves two problems for downstream users:
- Confirms the departure is actually being served
- Provides a more accurate ETA based on how early or late the boarding happened

## Architecture decisions

### Dumb repository server

The server is a pure event store — it knows nothing about routes, stops, or timetables. All trip matching and ETA computation happen on the client, which already carries the full timetable locally. This keeps the server minimal and immune to timetable updates.

### Trip key

Since the data model has no first-class trip ID, the app synthesises one before submitting:

```
{routeId}|{direction}|{dayType}|{HH:MM}
```

Where `HH:MM` is the **scheduled departure time at the boarding stop** (zero-padded, 24h). Example:

```
M4|Lastrilla → Sotillo|WEEKDAY|07:30
```

Other devices filter GET /boardings by this key to find events for the same trip.

### ETA calculation (client-side)

```
lateness = boardedAt − scheduledDepartureTime
estimatedDeparture = scheduledAtMyStop + lateness
```

`scheduledDepartureTime` is optional in the POST body but strongly recommended — it's what makes the lateness calculation possible.

### Auth

Bearer token in the `Authorization` header. The same token is bundled into the apps at build time and stored in `.env` on the server. The server runs behind a Cloudflare Tunnel (HTTPS enforced).

### Event expiry

Events expire 4 hours after submission — longer than any route cycle. Expired events are pruned lazily on the next request.

## Server stack

- **Runtime:** Node.js 20+
- **Framework:** Fastify + TypeScript
- **Storage:** lowdb (JSON file, `server/data/boardings.json`)
- **Docs:** `server/docs/API.md` (endpoint reference) and `server/docs/ARCHITECTURE.md` (design decisions + app integration guide)

## Status

| Step | Status |
|---|---|
| Server implementation | ✅ Done |
| Android integration | ⬜ Pending |
| iOS integration | ⬜ Pending |

## API summary

**POST `/boardings`** — submit a boarding event

| Field | Required | Notes |
|---|---|---|
| `stopId` | ✅ | Canonical ID from BusStopRegistry |
| `routeId` | ✅ | e.g. `"M4"` |
| `direction` | ✅ | Exact string from BusTimetable |
| `tripKey` | ✅ | See format above |
| `boardedAt` | ✅ | ISO 8601 UTC |
| `scheduledDepartureTime` | ❌ | ISO 8601 UTC — enables lateness calculation |

**GET `/boardings`** — returns all active (non-expired) events as a JSON array.

See `server/docs/API.md` for full details and curl examples.
