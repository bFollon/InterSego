# InterSego Server — Architecture

## Purpose

The InterSego server is a lightweight boarding notification repository.
It lets app users signal "I just boarded this bus" so that other users on the same route can get confirmation that the departure is actually running and a rough sense of whether it is on time.

## Design Philosophy: Dumb Repository

The server knows nothing about routes, stops, or timetables. It is a pure event store.

All trip identification, ETA computation, and timetable matching happen on the client devices, which already carry the full timetable locally. This keeps the server minimal, stateless with respect to schedule data, and immune to timetable updates — there is nothing to keep in sync.

The only intelligence the server applies is expiring old events so clients never see stale data.

## Data Flow

```
User boards bus
      │
      ▼
App computes trip context from local timetable
  (stopId, routeId, direction, tripKey, boardedAt, scheduledDepartureTime)
      │
      ▼
POST /boardings  →  server assigns id + expiresAt  →  stored in boardings.json
      │
      ▼
Other devices poll GET /boardings
      │
      ▼
Each device filters by tripKey to find relevant events
      │
      ▼
Device computes ETA delta: boardedAt − scheduledDepartureTime = lateness
Device applies lateness to its own scheduled stop time
```

## Trip Key Format

Because the data model has no first-class trip identifier, the app synthesises one:

```
{routeId}|{direction}|{dayType}|{HH:MM}
```

| Segment | Example | Notes |
|---|---|---|
| `routeId` | `M4` | Matches BusRoute.id |
| `direction` | `Lastrilla → Sotillo` | Exact string from BusTimetable.direction |
| `dayType` | `WEEKDAY` | One of: `WEEKDAY`, `SATURDAY`, `SUNDAY` |
| `HH:MM` | `07:30` | Scheduled departure at the **boarding stop**, zero-padded, 24h |

**Full example:** `M4|Lastrilla → Sotillo|WEEKDAY|07:30`

Other devices resolve the event by finding the `BusTimetable` entry for `(M4, "Lastrilla → Sotillo", WEEKDAY)` at their own stop and checking whether their scheduled time is after `07:30`. If it is, the boarding confirms the bus is running and provides a lateness reference.

### Why the boarding stop departure time, not the origin?

The app already knows the scheduled time at the boarding stop — it is the time displayed in NextDeparture. Computing the origin time would require walking backwards through the stop sequence and is unnecessary: any stop further down the line can infer origin time from the timetable too.

## Event Expiry

Events expire 4 hours after submission (`expiresAt = boardedAt + 4h`).

Four hours exceeds the maximum cycle time of any Linecar route, so no boarding event will ever linger past the trip it refers to. Expired events are removed from disk lazily on the next read or write request.

## Authentication

All endpoints except `GET /health` require:

```
Authorization: Bearer <API_KEY>
```

The token is set via the `API_KEY` environment variable on the server. The same token is bundled into the app at build time. Since the server runs behind a Cloudflare tunnel (HTTPS enforced), the token is protected in transit.

There is no per-user identity. Any client with a valid API key can submit and read boarding events. This is intentional for an MVP: the user base is small and trusted.

A stronger alternative — cryptographic app attestation via Apple App Attest and Android Play Integrity — was researched but deferred. See `APP-ATTESTATION.md` for the full design.

## Storage

Events are persisted in `data/boardings.json` via **lowdb** (a thin JSON file adapter). The file is loaded into memory at startup and written after each mutation. With the expected traffic volume (tens of events per day at most), this is more than sufficient.

The file is excluded from version control (`.gitignore`). A missing file is automatically created with an empty boardings array on first write.

## Running on Raspberry Pi 5

The server is a standard Node.js process. Recommended setup:

- Run via **pm2** for automatic restart on crash and on reboot
- Expose externally via a **Cloudflare Tunnel** (no open inbound ports needed)
- Keep the `.env` file on the Pi only, never commit it

See `README.md` for step-by-step setup instructions.

## Future Extension Points

- **`GET /boardings?routeId=M4&direction=...`** — server-side filtering to reduce payload
- **Rate limiting** — prevent a single client from flooding the event store
- **Deduplication** — discard re-submissions of the same `tripKey` + `stopId` within a short window
- **Live location phase** — replace boarding events with periodic GPS pings while the user is on the bus; the server would then serve a live position rather than a single boarding snapshot
