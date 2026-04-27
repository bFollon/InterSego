# Timetable JSON Refactor

## Motivation

The current parser classes conflate two concerns: **route structure** (stop sequences, UI variants, display metadata) and **timetable data** (the time matrices). This makes both hard to maintain independently:

- Adding or correcting times requires changing Kotlin *and* Swift in lockstep.
- The stop-major array layout hides the trip structure, making errors invisible until runtime.
- There is no shared source of truth between platforms.
- A future server API for live timetable updates has no clean data format to serve.

The refactor moves timetable data (and eventually full route structure) into shared JSON files. Both platforms read the same data; the server can serve the same format.

---

## Target Architecture

```
resources/timetables/
  m1.json
  m2.json
  ...
  m8.json

App (Android + iOS):
  Startup (online)  → GET /api/timetables/:routeId  → cache to disk
  Startup (offline) → use bundled JSON from app assets / bundle
  Disk cache takes priority over bundle (existing TimetableCacheService contract)

Parser classes become:
  - Route metadata only (variants, RouteViews, RouteEntries)
  - A shared TimetableLoader reads JSON → [BusTimetable]
  - buildStaticTimetables() is replaced by TimetableLoader.load("M2")

Server (new endpoints on existing server/):
  GET /api/timetables           → all routes
  GET /api/timetables/:routeId  → single route JSON
  (deferred — not part of this refactor)
```

**Why a single loader works:** every route-specific quirk becomes an expressive field in the JSON schema. The loader has no route-specific branching — it just interprets schema features. The "quirks" are data, not code.

---

## JSON Schema Specification

### Top-level structure

```json
{
  "routeId": "M2",
  "version": "1.1",
  "source": "Linecar M2 PDF, 2026-03-20",
  "stops": [...],
  "variants": [...],
  "routeDisplay": {...},
  "timetables": [...]
}
```

### `stops`

Each stop used by this route. Stops shared between routes are duplicated by value (each route file is self-contained). Coordinates use WGS-84 decimal degrees.

```json
"stops": [
  {
    "id": "estacion-autobuses",
    "name": "Estación de Autobuses",
    "lat": 40.9481,
    "lon": -4.1184
  },
  {
    "id": "garcillan",
    "name": "Garcillán",
    "lat": 40.9120,
    "lon": -4.2100,
    "alternates": [
      {
        "id": "garcillan-gasolinera",
        "name": "Garcillán (gasolinera)",
        "lat": 40.9115,
        "lon": -4.2090
      }
    ]
  }
]
```

**Stop IDs** use kebab-case and are stable across versions (changing an ID is a breaking change). When the same physical stop appears in both directions of a circular route (e.g., `estacion-autobuses` at position 0 and again at position 8), use the same ID — the stop sequence position disambiguates.

**`alternates`** are named sub-locations of the same physical stop where specific buses depart or arrive from a slightly different point (e.g., gasolinera variant in M1, Via Roma variant in M5). They are not independent stops — the closest-stop finder navigates to the parent stop, but the map, departure list, and "take me there" feature use the alternate's coordinates when relevant.

**Decision: nested under parent stop** (not top-level with a `parentStopId`). Authoring is simpler and alternates have no meaning outside the context of their parent stop. The loader resolves alternate IDs by searching the parent stop's `alternates` list.

### `variants`

Defines the route directions. Each variant has an ordered stop sequence that the timetable trips index into.

```json
"variants": [
  {
    "id": "circularA",
    "label": "Segovia → Valseca",
    "stopSequence": ["estacion-autobuses", "casino-union", "hontanares", "los-huertos", "valseca"],
    "swapTargetId": "circularB"
  },
  {
    "id": "circularB",
    "label": "Valseca → Segovia",
    "stopSequence": ["los-huertos", "hontanares", "valseca", "casino-union", "estacion-autobuses"],
    "swapTargetId": "circularA"
  }
]
```

`swapTargetId` is the variant ID the direction-swap button navigates to. Omit for routes with no swap button (e.g., Saturday-only linear routes). For M4's merged-direction display, see `routeDisplay` below.

### `routeDisplay`

Controls the All Routes selector entries and any special UI rendering (tabs, merged direction labels).

**Standard swap display (most routes):**
```json
"routeDisplay": {
  "type": "swap",
  "entries": [
    {
      "id": "entry-lv-a",
      "label": "L-V - Segovia → Valseca",
      "variantId": "circularA",
      "dayType": "weekday"
    },
    {
      "id": "entry-lv-b",
      "label": "L-V - Valseca → Segovia",
      "variantId": "circularB",
      "dayType": "weekday"
    }
  ]
}
```

**Tabbed merged-direction display (M4):**
```json
"routeDisplay": {
  "type": "tabs",
  "tabsLabel": "Pasa primero por",
  "mergedDirectionLabel": "La Lastrilla · El Sotillo",
  "tabs": [
    { "label": "La Lastrilla", "variantId": "regular" },
    { "label": "El Sotillo",   "variantId": "reverse" }
  ],
  "entries": [
    { "id": "entry-lv-lastrilla", "label": "L-V La Lastrilla primero", "variantId": "regular",  "dayType": "weekday"   },
    { "id": "entry-lv-sotillo",   "label": "L-V El Sotillo primero",   "variantId": "reverse",  "dayType": "weekday"   },
    { "id": "entry-sabado",       "label": "Sábados",                   "variantId": "regular",  "dayType": "saturday"  }
  ]
}
```

### `timetables`

Each entry covers one variant × one day type. Trips are in the **trip-major** layout: one row per bus, columns indexed by `stopSequence`.

```json
"timetables": [
  {
    "variantId": "circularA",
    "dayType": "weekday",
    "trips": [
      { "departures": [900,  905,  910,  915,  930]  },
      { "departures": [1345, 1350, 1355, 1400, 1415] },
      { "departures": [1615, 1620, 1625, 1630, 1640] },
      { "departures": [1915, 1920, 1925, 1930, 1940] }
    ]
  }
]
```

#### Departure values

Each element of `departures` maps to the stop at the same index in `stopSequence`. It can be:

| Value | Meaning |
|---|---|
| `900` | Bus serves this stop at 09:00, `yearRound` season |
| `null` | Bus does not serve this stop on this trip |
| `{ "hhmm": 900 }` | Explicit object form (for adding metadata) |
| `{ "hhmm": 900, "season": "schoolOnly" }` | Per-departure season override |
| `{ "hhmm": 900, "variantLabel": "Sotillo" }` | Badge shown in departure row (e.g., asterisk trips) |
| `{ "hhmm": 900, "alternateId": "garcillan-gasolinera" }` | Departs from / arrives at an alternate location of this stop |

Multiple fields can be combined in the object form.

#### Trip-level `season`

When all stops of a trip share the same season, set it at the trip level. Per-departure season overrides take precedence.

```json
{ "season": "schoolOnly", "departures": [740, 743, 746, null, 750, ...] }
```

Valid season values:

| JSON value | Meaning |
|---|---|
| `"yearRound"` | All year (default — can be omitted) |
| `"schoolOnly"` | School term only (not July/August) |
| `"juneToSept"` | June 13 – September 13 only |
| `"summerOnly"` | July–August only |
| `"monFriOnly"` | Mondays and Fridays only |
| `"friOnly"` | Fridays only |

#### Valid day type values

`"weekday"`, `"saturday"`, `"sunday"`

#### Time encoding

Times are 4-digit integers: `HHMM` where `HH` is the hour (00–23) and `MM` is the minute (00–59). Examples: `700` = 07:00, `1345` = 13:45, `2108` = 21:08. Single-digit hours have no leading zero in the integer (i.e., `700` not `0700`).

#### Stop times do not need to be chronologically ordered within a trip

Some buses originate mid-route (e.g., M2's 7:25 service originates from Valseca). The time for the originating stop will precede times for earlier stops in the sequence. This is correct — record the times as they appear in the PDF.

---

## Full example: M2

```json
{
  "routeId": "M2",
  "version": "1.1",
  "source": "Linecar M2 PDF, 2026-03-20",

  "stops": [
    { "id": "estacion-autobuses", "name": "Estación de Autobuses", "lat": 40.9481, "lon": -4.1184 },
    { "id": "casino-union",       "name": "Casino Unión",           "lat": 40.9472, "lon": -4.1053 },
    { "id": "hontanares",         "name": "Hontanares",             "lat": 40.9503, "lon": -3.9784 },
    { "id": "los-huertos",        "name": "Los Huertos",            "lat": 40.9441, "lon": -3.9612 },
    { "id": "valseca",            "name": "Valseca",                "lat": 40.9274, "lon": -3.9422 }
  ],

  "variants": [
    {
      "id": "circularA",
      "label": "Segovia → Valseca",
      "stopSequence": ["estacion-autobuses", "casino-union", "hontanares", "los-huertos", "valseca"],
      "swapTargetId": "circularB"
    },
    {
      "id": "circularB",
      "label": "Valseca → Segovia",
      "stopSequence": ["los-huertos", "hontanares", "valseca", "casino-union", "estacion-autobuses"],
      "swapTargetId": "circularA"
    }
  ],

  "routeDisplay": {
    "type": "swap",
    "entries": [
      {
        "id": "entry-lv",
        "label": "Lunes a Viernes",
        "variantId": "circularA",
        "dayType": "weekday"
      }
    ]
  },

  "timetables": [
    {
      "variantId": "circularA",
      "dayType": "weekday",
      "trips": [
        { "departures": [900,  905,  910,  915,  930]  },
        { "departures": [1345, 1350, 1355, 1400, 1415] },
        { "departures": [1615, 1620, 1625, 1630, 1640] },
        { "departures": [1915, 1920, 1925, 1930, 1940] }
      ]
    },
    {
      "variantId": "circularB",
      "dayType": "weekday",
      "trips": [
        { "departures": [735, 740, 725, 742, 755]  },
        { "departures": [915, 920, 930, 935, 950]  },
        { "departures": [1400, 1405, 1415, 1420, 1435] },
        { "departures": [1630, 1635, 1645, 1650, 1705] },
        { "departures": [1930, 1935, 1945, 1950, 2005] }
      ]
    }
  ]
}
```

---

## Loader Design

A single `TimetableLoader` replaces `buildStaticTimetables()` in every parser.

**Input:** a parsed JSON document for one route  
**Output:** `[BusTimetable]` — identical contract to the current parsers

**Algorithm:**

```
for each timetable section (variantId × dayType):
  variant = variants[variantId]
  stops   = variant.stopSequence (resolved to BusStop objects)

  for each trip in section.trips:
    tripSeason = trip.season ?? .yearRound

    for each (index, stopId) in stops.enumerated():
      departure = trip.departures[index]

      if departure == null  → skip
      if departure is Int   → DepartureTime(hhmm, season: tripSeason)
      if departure is Object:
        hhmm        = departure.hhmm
        season      = departure.season ?? tripSeason
        variantLabel = departure.variantLabel ?? nil
        alternateId = departure.alternateId ?? nil   ← alternate location ID within this stop

        → DepartureTime(hhmm, season, variantLabel, alternateLocationId: alternateId)
        → BusTimetable stopId stays as the parent stop ID (not the alternate)

    → BusTimetable(routeId, stopId, dayType, direction: variant.label, departures)
```

No route-specific branching. All quirks are handled by the schema.

---

## Migration Plan

Migrate one route at a time. For each route:

1. Create `resources/timetables/m{N}.json` with stop, variant, routeDisplay, and timetable data
2. Implement `TimetableLoader` (Android: Kotlin, iOS: Swift) — do this once on M2, reuse for all others
3. Replace `buildStaticTimetables()` in the parser with `TimetableLoader.load("M{N}")`
4. Bundle the JSON in the app (Android: `assets/`, iOS: app bundle)
5. Bump parser version
6. Build and test both platforms
7. Update root CLAUDE.md feature tracker

### Route order (simplest → most complex)

| Route | Complexity | Notes |
|---|---|---|
| **M2** | ★☆☆☆ | Weekday only, 5 stops, no seasonal, no clusters — ideal first target |
| **M3** | ★★☆☆ | Saturday only, linear, 1 direction, Segovia cluster |
| **M5** | ★★☆☆ | Weekday + Saturday, partial trips (null stops), no clusters |
| **M8** | ★★★☆ | 3 day types, optional stop, partial Sat trips, Segovia cluster |
| **M7** | ★★★★ | 3 day types, multiple clusters, seasonal, large volume |
| **M1** | ★★★★ | Most complex: 2 circular directions, alternate departure locations, mixed seasons |
| **M4** | — | Already refactored to trip-major in code; migrate to JSON after loader exists |
| **M6** | — | Live PDF parser — out of scope for this refactor |

### Definition of done (per route)

- [ ] `resources/timetables/m{N}.json` created and validated against schema
- [ ] `TimetableLoader` reads the JSON and produces correct `[BusTimetable]`
- [ ] `M{N}Parser.buildStaticTimetables()` removed; replaced with loader call
- [ ] JSON bundled in Android assets and iOS app bundle
- [ ] Parser version bumped on both platforms
- [ ] Android builds and manual test passes
- [ ] iOS builds and manual test passes
- [ ] Root CLAUDE.md feature tracker updated

---

## Alternate Boarding Points — Full Architecture

This section captures the design decisions made before implementation so they aren't re-derived each time.

### What this models

Some stops have multiple physical sub-locations where a bus can depart or arrive, depending on the specific trip. Examples:

- **M5** — "Estación de Autobuses" has a Via Roma sub-location (~1 km away); certain outbound trips depart from Via Roma instead of the main station.
- **M1** — "Garcillán" has a gasolinera variant used by some services.

These are **not** independent stops on the route. They are alternate pickup/dropoff points for the same conceptual stop, used only by specific departures.

### Data model

`BusStop` owns its alternate locations:

```kotlin
// Kotlin / Swift equivalent
data class AlternateLocation(
    val id: String,          // e.g. "via-roma" — stable, kebab-case
    val name: String,        // "Via Roma"
    val coordinates: String  // "40.948502, -4.115979"
)

// BusStop gains:
val alternates: List<AlternateLocation> = emptyList()
```

`DepartureTime` carries an optional reference to which alternate applies:

```kotlin
val alternateLocationId: String? = null
// null  → bus uses the parent stop's primary coordinates
// "via-roma" → look up in stop.alternates to get coordinates and display name
```

Coordinates are **not** embedded in `DepartureTime` — they stay canonical on the stop. If Via Roma's coordinates need correcting, fix it in one place (the stop definition in the JSON).

### JSON authoring

Define the alternate on the stop:

```json
{
  "id": "estacion-autobuses",
  "name": "Estación de Autobuses",
  "lat": 40.944768,
  "lon": -4.121823,
  "alternates": [
    { "id": "via-roma", "name": "Via Roma", "lat": 40.948502, "lon": -4.115979 }
  ]
}
```

Reference it on the departure cell using `alternateId`:

```json
{ "departures": [{ "hhmm": 715, "alternateId": "via-roma" }, null, null, null, null, null, null] }
```

The `alternateId` applies to the specific (trip, stop) cell only — it does **not** cascade to other stops in the same trip. An intermediate stop can have its own `alternateId` independently.

### Service behaviour

**Closest stop finder:**
For each stop, compute candidate distances using the stop's primary coordinates **and** the coordinates of every alternate defined on that stop (regardless of which departures use them — the user standing near an alternate should still find the route). The winner is the candidate with minimum distance. The result still returns the parent `stopId`; alternate context is surfaced through the departure data.

**"Take me there" (navigation):**
When the user taps "take me there" from a NextDeparture screen, if the stop has **any** departures with alternates, show a picker before opening maps:

> **¿Cuándo quieres salir?**
> *Dependiendo de la hora de salida, el bus sale en diferentes sitios*
>
> - 07:15 — Via Roma
> - 13:45 — Estación de Autobuses
> - 15:15 — Estación de Autobuses

User picks a departure → resolve `alternateLocationId` on that `DepartureTime` → navigate to that alternate's coordinates, or the stop's primary coordinates if `alternateLocationId` is null. If the stop has no departures with alternates, navigate directly without the picker.

**Departure list display:**
Show `alternate.name` as a badge on any `DepartureTime` where `alternateLocationId` is non-null — same display mechanism as `variantLabel`. Applied in NextDeparture (following departures) and DaySchedule.

**Inline map tile (NextDeparture):**
Render using the next departure's resolved location (alternate if set, primary otherwise).

### What this is not

`BusStop` now carries alternate locations, so it is not entirely "clean" — but the stop remains the canonical owner of all its physical points. The departure only carries an ID reference, not coordinates. This keeps coordinates DRY and keeps `BusStopRegistry` and route stop lists working without modification (they reference parent stop IDs, which are unchanged).

---

## Deferred / Out of Scope

- **Server API** (`GET /api/timetables/:routeId`) — schema is designed to be served as-is, but the server implementation is a separate task.
- **M6** — live PDF parser with its own stateful pipeline; not affected by this refactor.
- **Route polylines** — remain in `iOS/InterSego/RoutePolylines/`; no change.
- **`BusStopRegistry`** — currently stays in code. Once all routes are in JSON, the registry could be derived from the JSON files, but that is a follow-up.
