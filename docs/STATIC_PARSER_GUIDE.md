# How to Add a Static Data Parser for a New Bus Route

This guide documents the step-by-step process for implementing a new bus route parser using hardcoded timetable data extracted from a PDF screenshot. This is the approach used for M1, M2, and M3.

## When to Use This Approach

Use a static data parser when:
- The PDF layout is too complex or irregular for reliable automated parsing
- The route has very few departures (making manual extraction quick)
- You have a screenshot of the timetable PDF

## Prerequisites

- A screenshot of the route's timetable PDF
- The route's stop names and (if known) GPS coordinates
- Knowledge of the service days (weekday, Saturday, Sunday)

## Step-by-Step Process

### 1. Extract Data from the PDF Screenshot

From the screenshot, identify:
- **Route name and endpoints** (e.g., "Segovia - Navacerrada")
- **Service days** (e.g., "Servicio de los Sábados" = Saturday only)
- **Stop names** for each direction (outbound and inbound/return)
- **Departure times** for each stop × each service
- **Footnotes** about seasonal restrictions, clusters, or special conditions
- **Whether the route is circular or linear**

### 2. Handle Stop Clusters

If the PDF shows a single stop name (e.g., "Segovia") but a footnote lists multiple sub-stops:
- Create individual `BusStop` entries for each sub-stop
- Use the PDF's time as the **anchor** (first sub-stop in the travel direction)
- Estimate subsequent sub-stop times at **+2 minutes per position**
- Example: PDF says "Segovia 8:30" with 4 sub-stops → 8:30, 8:32, 8:34, 8:36

#### How to discover clusters

Clusters are identified from **footnotes or annotations** in the PDF that describe the urban route ("recorrido urbano") through a city. Common patterns:

- **Footnote listing sub-stops:** e.g., "RECORRIDO URBANO: ESTACION BUS-HOSPITAL-ANDRES LAGUNA-LA PISTA-PLAZA DE TOROS" — each dash-separated name becomes a sub-stop.
- **Different cluster for each direction:** The outbound and inbound urban routes through a city may use different sub-stops. For example, M7 Saturday outbound goes Estación Bus → Hospital → Andrés Laguna → La Pista → Plaza de Toros (5 stops), but inbound goes Plaza de Toros → La Pista → Andrés Laguna → Jardinillos y Hospital (4 stops).
- **Day-type-dependent clusters:** A route may have clusters on weekends but not weekdays. For example, M7 weekdays use a single "Segovia" stop, but Saturday/Sunday expand it into a 4-5 stop cluster.

When the same physical stops appear in clusters across multiple routes (e.g., "Estación de Autobuses", "Plaza de Toros"), reuse the GPS coordinates but create route-specific stop IDs (e.g., `m7-estacion-bus` vs `m6-estacion-bus`).

### 3. Assign Stop IDs and Coordinates

- Stop IDs follow the pattern: `m{route}-{stop-name-kebab}` (e.g., `m3-la-granja`)
- For stops that appear in both directions, the **inbound** copy gets a `-in` suffix (e.g., `m3-estacion-bus-in`)
- Use known coordinates where available; use `"0.0, 0.0"` for unknown locations
- Reuse coordinates from other parsers if the same physical stop appears (e.g., "Estación de Autobuses" coordinates from M6)

### 4. Create the Parser File

**Android:** `android/app/src/main/java/.../services/pdfparsing/strategies/M{N}Parser.kt`
**iOS:** `iOS/InterSego/Services/PDFParsing/Strategies/M{N}Parser.swift`

Use an existing static parser as template (M2 for simple routes, M1 for complex ones). The parser must:

1. **Extend `CapableParser` and `RouteStopsProvider`**
2. **Declare `capabilities`** with `supportedRoutes`, `mode = PRODUCTION`, and `version = "1.0"`
3. **Define stops** in a private `Stops` enum/object
4. **Define stop lists** for each direction (outbound/inbound or circularA/circularB)
5. **Implement protocol methods:**
   - `canParse()` — route ID check
   - `getRoutesForId()` — return all stop lists (day-type agnostic)
   - `getRouteVariants()` — return variants filtered by day type
   - `getRouteViews()` — wrap variants into views with swap actions
   - `getRouteEntries()` — build selector entries with `isActiveToday` logic
   - `parse()` — ignore PDF path, call `buildStaticTimetables()`
6. **Implement `buildStaticTimetables()`** with departure time arrays

#### Key decisions based on route type:

| Route Type | Variant IDs | Direction Labels | `isCircular` |
|---|---|---|---|
| Linear | `"regular"`, `"reverse"` | `"A → B"`, `"B → A"` | `false` |
| Circular | `"circularA"`, `"circularB"` | `"A → B"`, `"B → A"` | `true` |

#### Key decisions based on service days:

| Service Days | `getRouteVariants` returns | `getRouteEntries` returns |
|---|---|---|
| Weekday only | variants for `.weekday`, empty for others | 1 entry: "Lunes a Viernes" |
| Saturday only | variants for `.saturday`, empty for others | 1 entry: "Sábados" |
| Weekday + Saturday | variants for both | 2 entries |
| All days | variants for all three | 3 entries |

### 5. Register the Parser

**Android — `PDFProcessingService.kt`:**
1. Add import: `import ...strategies.M{N}Parser`
2. Add to `init`: `registerParser(M{N}Parser())`

**iOS — `PDFProcessingService.swift`:**
1. Add `M{N}Parser()` to the `allParsers` array

### 6. Update the Route Definition

**Android — `MainActivity.kt` → `getKnownRoutes()`:**
Update the `BusRoute` entry for M{N} with correct `name`, `origin`, `destination`, and `isCircular`.

**iOS — `BusRouteRegistry.swift` → `knownRoutes()`:**
Same updates as Android.

### 7. Add to Xcode Project (iOS only)

Edit `iOS/InterSego.xcodeproj/project.pbxproj` to add the new Swift file:
1. Add a `PBXBuildFile` entry (A1... prefix)
2. Add a `PBXFileReference` entry (A2... prefix)
3. Add the file reference to the `Strategies` group's `children` list
4. Add the build file to the `Sources` build phase

Use the next available sequential ID (check existing entries for the pattern).

### 8. Update CLAUDE.md Feature Tracker

In the root `CLAUDE.md`, update the M{N} row in the PDF Parsers table with:
- `✅ (static data)` for both platforms
- Notes: source date, version, service days, route type, any special handling

### 9. Build and Test

1. **Android:** `./gradlew assembleDebug` — verify compilation
2. **iOS:** Build in Xcode — verify compilation
3. **Manual testing:** Navigate to the route, check stop list, verify departure times match the PDF
4. **Edge cases to verify:**
   - Direction swap works correctly
   - "Parada más cercana" (closest stop) finds M{N} stops
   - Days with no service show appropriate "no service" message
   - All Routes screen shows the correct selector entries

### 10. Bump Version When Fixing Data

If you later correct times, stops, or coordinates, **always bump `capabilities.version`** on both platforms. This forces devices to discard their cached timetable and re-parse from the updated static data.

## Checklist

- [ ] Data extracted from PDF screenshot
- [ ] Stop clusters expanded with estimated times
- [ ] Android parser created and registered
- [ ] iOS parser created, registered, and added to Xcode project
- [ ] Route definitions updated on both platforms
- [ ] CLAUDE.md feature tracker updated
- [ ] Android builds successfully
- [ ] iOS builds successfully
- [ ] Manual testing passed
