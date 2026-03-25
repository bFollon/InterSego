# Static Parser — Pending Work

This document tracks incomplete items across all static data parsers (M1–M5 and future ones). Use it as a checklist when doing a polish pass after all skeletons are in place.

## Per-Parser Status

### M1 (Segovia circular via villages)
- [x] Coordinates — all stops have real coordinates
- [x] Route polylines — M1-outbound.json, M1-inbound.json (iOS only)
- [ ] Route polylines — Android (no polyline support yet)

### M2 (Segovia – Valseca)
- [x] Coordinates — all stops have real coordinates
- [x] Route polylines — M2-circularA.json, M2-circularB.json (iOS only)
- [ ] Route polylines — Android (no polyline support yet)

### M3 (Segovia – Navacerrada)
- [ ] **Coordinates** — 8 stops missing (both platforms):
  - `m3-iglesia-sto-tomas` — Iglesia Santo Tomás
  - `m3-frente-bar-norte` — Frente Bar Norte
  - `m3-urb-carrascalejo` — Urb. Carrascalejo
  - `m3-parque-robledo` — Parque Robledo
  - `m3-boca-del-asno` — Boca del Asno
  - `m3-puente-mosquitos` — Puente de los Mosquitos
  - `m3-frente-bar-norte-in` — (inbound copy)
  - `m3-iglesia-sto-tomas-in` — (inbound copy)
- [ ] **Route polylines** — none created yet
- [ ] **Version bump** — bump `capabilities.version` on both platforms after coordinate/data fixes

### M5 (Segovia – Sto. Domingo de Pirón)
- [ ] **Coordinates** — 12 stops missing (both platforms):
  - `m5-tizneros` — Tizneros
  - `m5-espirdo` — Espirdo
  - `m5-la-higuera` — La Higuera
  - `m5-brieva` — Brieva
  - `m5-basardilla` — Basardilla
  - `m5-sto-domingo-piron` — Sto. Domingo de Pirón
  - Plus all 6 inbound `-in` copies of the above
- [ ] **Route polylines** — none created yet
- [ ] **Version bump** — bump `capabilities.version` on both platforms after coordinate/data fixes

### M7 (Segovia – Torrecaballeros)
- [ ] **Coordinates** — 5 stops missing (both platforms):
  - `m7-hospital` — Hospital
  - `m7-s-cristobal` — S. Cristóbal
  - `m7-sonsoto` — Sonsoto
  - `m7-trescasas` — Trescasas
  - `m7-cabanillas` — Cabanillas
  - `m7-torrecaballeros` — Torrecaballeros
  - Plus inbound copies where applicable
- [ ] **Route polylines** — none created yet
- [ ] **Version bump** — bump `capabilities.version` on both platforms after coordinate/data fixes
- [ ] **Valsaín–La Granja feeder** — deferred to separate "M7-AVE" parser (weekday only, 1 trip at 6:00 AM, 11 stops from Valsaín to Ave Segovia)
- [ ] **Weekday last trip anomaly** — PDF shows `*21:20` (Segovia), `*21:37` (Tabanera), but Palazuelos at `21:35` which is BEFORE Tabanera. The `*` is not explained in the weekday footnotes. Times included as-is — verify with Linecar if possible.
- [ ] **Sunday seasonal refinement** — currently using SCHOOL_ONLY (Sep–Jun) and SUMMER_ONLY (Jul–Aug) as scaffolding. Actual school-term dates may differ ("periodo lectivo" vs "vacaciones escolares estivales").

## Common Items (all static parsers, including future M8)

### Coordinates
- Placeholder coordinates use `"0.0, 0.0"` — these must be replaced with real GPS coordinates
- When updating coordinates, update **both** Android (.kt) and iOS (.swift) files
- Inbound `-in` copies share coordinates with their outbound counterpart
- Reuse coordinates from other parsers when the same physical stop appears (e.g., "Estación de Autobuses" coords are shared across M3, M6)

### Route Polylines
- Polyline JSON files define the map path between stops (used by RouteMapView on iOS)
- Currently stored in `iOS/InterSego/RoutePolylines/`
- Android does not have polyline support yet
- Each direction needs its own JSON file: `M{N}-regular.json`, `M{N}-reverse.json` (or `circularA`/`circularB`)

### Version Bumping
- After any data correction (coordinates, times, stop names), bump `capabilities.version` in the parser on **both** platforms
- This forces devices to discard cached timetables and re-parse

### Checklist Template for New Parsers

When adding a new static parser (M7, M8, etc.), copy this checklist:

- [ ] Data extracted from PDF screenshot
- [ ] Android parser created and registered in PDFProcessingService
- [ ] iOS parser created, registered in PDFProcessingService, added to project.pbxproj
- [ ] Route definition updated in MainActivity.kt and BusRouteRegistry.swift
- [ ] CLAUDE.md feature tracker updated
- [ ] Android builds successfully
- [ ] iOS builds successfully
- [ ] Coordinates filled in for all stops (both platforms)
- [ ] Route polylines created (iOS)
- [ ] Version bumped if data was corrected after initial implementation
- [ ] Manual testing passed
