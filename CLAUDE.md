# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**InterSego** ("Metropolitanos de Segovia") is a bus timetable app for Segovia, Spain. It loads bus timetable data from bundled JSON assets, provides offline-first access, and helps users find the nearest bus stop using geolocation.

**Current Status:** Android and iOS implementations at feature parity. All 8 routes (M1–M8) operational on both platforms via JSON-based loading.

## Cross-Platform Feature Tracker

**IMPORTANT: This tracker MUST be updated whenever a feature is added, modified, or removed on ANY platform.** When implementing changes, check this table and update the status for the affected platform. If a feature is added to one platform, add a row here even if the other platform doesn't have it yet — this is how we track drift.

### Core Infrastructure

| Feature | Android | iOS | Notes |
|---|---|---|---|
| Network monitoring | ✅ | ✅ | |
| LaLiga blocking banner | ✅ | ✅ | `LaLigaBlockingService` (Android: object, iOS: actor singleton `.shared`) distinguishes "our own backend is unreachable because of LaLiga's court-authorized IP blocking" from a generic offline state — the device can have perfectly good internet while our Cloudflare-tunneled server is unreachable during a match. `TimetableCacheService.fetchManifest()` (our most frequent server call, run at every app startup while online) reports each success/failure via `onServerReachable()`/`onServerUnreachable()`; on failure, if the device is online, the service checks `https://hayahora.futbol/estado/blocked-any.txt` directly (bypassing our own server, since that's exactly what may be down) and resolves our own server hostname via a plain DNS lookup (`InetAddress.getAllByName` on Android, `getaddrinfo` on iOS) to compare against the blocklist — an exact IP match is high-confidence evidence, an empty blocklist is high-confidence evidence against, and an unresolvable hostname (no way to confirm a specific match) falls back to correlation (blocklist merely non-empty); result cached 2 min, fails closed (never shows the LaLiga-specific message without evidence). Surfaced as an orange banner with a soccer-ball icon on the Landing screen (below the TopAppBar/alert-pill row on Android, below the top HStack on iOS), tap opens a detail sheet explaining the December 2024 Spanish court ruling, the Cloudflare collateral-damage mechanism, and linking to hayahora.futbol; deliberately factual/sourced rather than editorializing. `laliga_blocking_banner_tapped`/`laliga_blocking_link_tapped` Aptabase events; see `docs/LALIGA_BLOCKING_BANNER_RUNBOOK.md` for the porting recipe (already applied once, to FarmaciasDeGuardia) |
| Timetable cache service | ✅ | ✅ | `TimetableCacheService` — fetches per-route JSON from server (`GET /api/timetables/:routeId`) at startup when online; ETag/304 support (SharedPreferences on Android, UserDefaults on iOS); disk cache at `filesDir/timetables/` (Android) / `Caches/Timetables/` (iOS); `TimetableLoader.loadFile()` checks disk cache before bundle on both platforms; hot-swap via `pendingUpdates` flag — `TimetableService` evicts in-memory cache on next access; server key in `SERVER_API_KEY` (Android) / `AppConfig.serverAPIKey` (iOS) |
| TimetableLoader (JSON-based) | ✅ | ✅ | Reads `assets/timetables/{routeId}.json` (Android) / `Timetables/{routeId}.json` (iOS) from bundle; produces `List<BusTimetable>`; **`resources/timetables/` is source-of-truth for humans only — neither app reads it at runtime; when editing a JSON timetable you MUST update all three copies**: `resources/timetables/`, `android/app/src/main/assets/timetables/`, and `iOS/InterSego/Timetables/`; replaces `buildStaticTimetables()` in migrated parsers; supports trip-level `variantLabel` (fallback for all departures in a trip, overridable per cell); see `docs/TIMETABLE_JSON_REFACTOR.md`; also exposes `loadRoute(_:)`/`loadAllRoutes()` — route discovery is now fully file-driven (no hardcoded registry) |
| File-driven route discovery | ✅ | ✅ | `TimetableLoader.loadAllRoutes()` scans all timetable JSON assets at runtime; each JSON carries a top-level `"route"` block (`number`, `name`, `origin`, `destination`, `routeType`, `isCircular`, `displayOrder`); Android: `context.assets.list("timetables")` scan; iOS: `Bundle.main.urls(forResourcesWithExtension:subdirectory:)` scan; `BusRouteRegistry.knownRoutes()` (iOS) and `MainActivity.getKnownRoutes()` (Android) now delegate here — adding a new route requires only a new JSON file + re-run the polyline script |
| Dynamic (server-only) route discovery | ✅ | ✅ | `TimetableCacheService.fetchAllRoutes()` fetches `GET /api/routes` (manifest of all route IDs known to the server) and unions it with bundled/disk-cached IDs; routes new to this device have their timetable fetched, then their polylines fetched via `PolylineCacheService.fetchPolyline()` for each `variants[].id` (as `{ROUTEID}-{variantId}`); `TimetableLoader.loadAllRoutes()` unions the bundle asset scan with a disk-cache directory scan so newly-discovered routes appear in the route list on the next launch; see `docs/DYNAMIC_ROUTE_DISCOVERY.md` |
| PolylineLoader | ✅ | ✅ | Reads `route_polylines/{routeId}-{viewId}.json`; decodes `{"version": "1.0", "coordinates": [[lat, lon], ...]}` format; Android: `services/PolylineLoader.kt` (object, kotlinx.serialization); iOS: `Services/PolylineLoader.swift` (struct, Codable); checks `PolylineCacheService` disk cache before falling back to bundle (same precedence as `TimetableLoader.loadFile()`); replaces inline `loadPolylineFromAssets`/`loadBundledPolyline` in map views; **when editing a polyline you MUST update all three copies**: `resources/route_polylines/`, `android/app/src/main/assets/route_polylines/`, `iOS/InterSego/RoutePolylines/`; bump `"version"` on every coordinate change |
| Polyline cache service | ✅ | ✅ | `PolylineCacheService` — fetches each `{routeId}-{viewId}` polyline from server (`GET /api/polylines/:id`, id = `{routeId}-{viewId}` lowercased) at startup when online, alongside `TimetableCacheService.fetchAllRoutes()`; ETag/304 support (SharedPreferences on Android, UserDefaults on iOS); disk cache at `filesDir/route_polylines/` (Android) / `Caches/RoutePolylines/` (iOS), filenames preserve bundle casing (e.g. `M7-AVE-outbound.json`); reuses the same `SERVER_API_KEY`/`AppConfig.serverAPIKey`; **no `pendingUpdates`/hot-swap flag** — unlike timetables, `PolylineLoader` has no persistent in-memory cache to evict, so a disk-cache write is picked up the next time a route map screen is opened |
| Holiday calendar data | ✅ | ✅ | `resources/holidays/{year}.json` — source-of-truth festivo calendar (national + Castilla y León regional + Segovia local, 14/year); served by the server at `GET /api/holidays` (ETag/304, `POST /api/holidays/reload` + SIGHUP for admin refresh); see `docs/HOLIDAY_CALENDAR.md`, `services/InterSegoService/docs/API.md`. Android: `HolidayService` fetches + disk-caches (`filesDir/holidays/all.json`) + bundles `assets/holidays/2026.json`, stores each festivo's name (not just the date) via `holidayName(Calendar)`; iOS: `HolidayService` actor mirrors it — disk cache at `Caches/Holidays/all.json`, bundled `Holidays/2026.json` folder reference, `holidayName(Date)`. Both platforms' `dayTypesForDate` (Android `TimetableQueryUtils`, iOS `TimetableQuery`) now consult `HolidayService.isHoliday` and resolve any festivo to {SUNDAY, WEEKEND, HOLIDAY} regardless of weekday, not just literal Sundays — wired into every call site that derives day-type from "today" (NextDeparture/DaySchedule screens, closest-stop/landing-boarding flows, reminder scheduling on both platforms). Android has unit coverage (`TimetableQueryUtilsTest`); iOS has no test target yet, so this path is untested there. `NextDepartureScreen`/`NextDepartureView` also surface a purple "Festivo: {name} · horario de domingo" banner (shared `FestivoBanner` component, same copy/styling as the `DaySchedule` banner) whenever today is a real festivo — shown unconditionally (not gated on same-day service availability) and ordered before the future-day warning card, so a route with no holiday service still explains itself instead of just saying "next bus is tomorrow". Part of the "[E1] Check departures for a specific date" epic |
| Debug config / logging | ✅ | ✅ | |
| Boarding notification service | ✅ | ✅ | Node.js/TypeScript server at `server/`; stores boarding events; GET+POST /boardings; 4h TTL; Bearer auth; see `server/docs/API.md` |
| BoardingService client | ✅ | ✅ | Android: OkHttp object singleton + BuildConfig; iOS: URLSession actor + AppConfig |
| Analytics (Aptabase) | ✅ | ✅ (needs Xcode setup) | Self-hosted at analytics.bfollon.dev; app key A-SH-8450405077; consent-gated; tracks: app_launch, route_selected, stop_selected, next_departure_viewed, closest_stop_used, reminder_set, reminder_cancelled, boarding_confirmed, boarding_info_viewed, pdf_parse_failed, pdf_download_failed, direction_swapped, map_opened, alert_banner_tapped, otras_opciones_opened, main_card_tapped, journey_search_submitted, journey_result_selected, journey_leg_tapped, direction_picker_used, settings_changed, notification_consent_responded, feedback_triggered, about_link_tapped, source_link_tapped, whats_new_dismissed, tutorial_reopened, favorite_stop_toggled, laliga_blocking_banner_tapped, laliga_blocking_link_tapped (2026-09 coverage sweep — added 18 event types after an audit found most interactive elements untracked; guided_mode tutorial has no manual reopen button on either platform so is not tracked) |
| Error reporting (Bugsink/Sentry) | ✅ | ✅ (needs Xcode setup) | Self-hosted at errors.bfollon.dev; Sentry-compatible SDK; consent-gated; explicit captureError/captureMessage call sites only |
| Monitoring consent UI | ✅ | ✅ (needs Xcode setup) | Dual opt-in modal (error reporting + analytics independently); shown on first launch after splash; default off; re-accessible from Settings |
| MonitoringPreferencesService | ✅ | ✅ (needs Xcode setup) | Persists 4 UserDefaults/SharedPreferences keys; consent-gated SDK initialization |

### Route Timetables (all JSON-based via TimetableLoader)

| Route | Android | iOS | Notes |
|---|---|---|---|
| M4 (La Lastrilla - El Sotillo) | ✅ (JSON) | ✅ (JSON) | isCircular=true; weekday + Saturday (Jul/Aug only); YEAR_ROUND = JULIO Y AGOSTO buses; SCHOOL_ONLY = non-summer buses; SCHOOL_ONLY trips: regular 07:00, 10:10, 12:40, 17:00, 19:40, 21:10 + reverse 21:40*; all other regular/reverse trips (incl. 07:40*, 08:20*, 14:00, 14:40*) are YEAR_ROUND (fixed 2026-07-07, prior tagging was inverted/incomplete — verified against live Linecar PDF); 4 reverse trips use trip-level variantLabel="Sotillo" badge (El Sotillo-first); 14:40* and 21:40* skip PARROQ2 (index 15 of reverse); null at index 11 (paseo-cabanillas) on all non-school regular trips (school-only stop); hotel-av-sotillo + parroquia-sotillo ARE served on Saturday; merged-directions display: tabsLabel="Pasa primero por", mergedDirectionLabel="La Lastrilla · El Sotillo"; TimetableLoader on both platforms; v1.1; timetable in `resources/timetables/m4.json` |
| M6 (Segovia - Torrecaballeros) | ✅ (JSON) | ✅ (JSON) | Cluster-based stop estimation; v1.1; all cluster-estimated times pre-computed and stored in JSON; 7 variants (outbound/inbound merged regular+extended, circular, sat/sun outbound/inbound); Sat inbound: PlazaToros→LaPista→AndresLaguna→Jardinillos; Sun inbound ends at EstacionAutobuses; circular variant has separate `direction` ("Segovia → Torrecaballeros") from `label` ("Circular"); Sat entries use `viewIds` for single-direction display (no swap); weekday views use `tabGroups` for tab bar; timetable in `resources/timetables/m6.json`; TimetableLoader on both platforms; M6Parser is now a pure stub |
| M1 | ✅ (JSON) | ✅ (JSON) | isCircular=true; two circular directions (circularA: full outbound via villages + direct return; circularB: direct outbound + return via villages); Saturday: shorter Segovia↔Abades variant; ★=juneToSept (Casino on most trips), (*)=garcillan-gasolinera alternateId (8:40+10:40 in circularB), LYV=monFriOnly (circularB Martín Miguel+Valverde 9:40), #=friOnly (circularA Garcillán 19:50); circularB return uses `poligono-ind-m1-ret`/`poligono-ind-m1-2-ret`+`estacion-autobuses-circ-ret` to prevent departure merge; circularB weekday has 9 trips — was missing the 16:00/16:10 trip (fixed 2026-07-07, verified against live Linecar PDF); NOTE: circularB deliberately excludes trips that are full-loop duplicates of an existing circularA row (same physical bus, PDF just re-prints it in reverse column order) — only add a circularB trip when its stop values don't already appear in a circularA row; v1.2; timetable in `resources/timetables/m1.json`; TimetableLoader on both platforms |
| M2 | ✅ (JSON) | ✅ (JSON) | v1.2; isCircular=true; weekday only; two circular directions (circularA: Segovia→Valseca outbound view; circularB: Valseca→Segovia return via Los Huertos+Hontanares); 7:25 Valseca bus originates from Valseca (chronologically out of stop order in circularB, accepted as Option A); timetable data migrated to `resources/timetables/m2.json`; loaded via TimetableLoader on both platforms |
| M3 | ✅ (JSON) | ✅ (JSON) | Saturday only; linear (Segovia→Navacerrada); Segovia is a 4-stop cluster (Estación de Autobuses, Iglesia Santo Tomás, Frente Bar Norte, Plaza de Toros) with +2 min estimated times; v1.2; timetable data migrated to `resources/timetables/m3.json`; loaded via TimetableLoader on both platforms |
| M5 | ✅ (JSON) | ✅ (JSON) | linear (Azoguejo→Sto. Domingo de Pirón); weekday + Saturday; partial trips (some weekday services only Azoguejo–La Higuera); Azoguejo is primary stop; Saturday trips use Estación de Autobuses alternate (alternateLocationId="estacion-autobuses"); v1.3; timetable in `resources/timetables/m5.json`; TimetableLoader on both platforms; AlternateLocation badge in DaySchedule + NextDeparture timeline |
| M7 | ✅ (JSON) | ✅ (JSON) | weekday: 6-stop circular (Segovia→Tabanera 2-stop→Palazuelos 2-stop→Segovia); Sat/Sun: extended route (Segovia cluster→...→Torrecaballeros); Segovia cluster outbound: 5 sub-stops; inbound: 4 sub-stops; Sunday has schoolOnly/summerOnly seasonal trips; Saturday partial: 13:30 outbound ends at Trescasas; Sunday partial: 20:30/19:30 outbound ends at Trescasas, 20:55/19:55 inbound starts from Trescasas (fixed 2026-07-08, previously mis-anchored one stop late at Sonsoto — verified against live Linecar PDF, caught by parse-linecar-pdf skill eval iteration 2, see `.claude/skills/parse-linecar-pdf/references/lessons-learned.md` Case 3); circular return uses `estacion-autobuses-circ-ret` to prevent departure merge; v1.1 (file's actual version — this note previously claimed v1.6, which didn't match the file); timetable in `resources/timetables/m7.json`; TimetableLoader on both platforms |
| M8 | ✅ (JSON) | ✅ (JSON) | linear (Segovia→Valsaín via La Granja); weekday + Saturday + Sunday/Festivos; Segovia cluster all day types: 4 stops (Estación Bus→Iglesia Santo Tomás→Enfrente Bar Norte→Plaza de Toros); C. La Fuencisla is optional (null for trips that skip it); Saturday has 2 partial trips (outbound 14:30 ends at Ptas. Segovia; inbound 14:50 starts at F. Cristal); v1.2; timetable in `resources/timetables/m8.json`; TimetableLoader on both platforms |

### UI Screens

| Screen | Android | iOS | Notes |
|---|---|---|---|
| Splash screen | ✅ | ✅ | App icon + "InterSego" + "Metropolitanos de Segovia", fade transition |
| Landing screen (home hub) | ✅ | ✅ | Gradient title, then "Parada más cercana" (full-width), a 2-up square row (Estoy en el autobús + the configurable main action), and a 3-up pill row (Favoritos, Cómo llegar, Más opciones — always in that order, Más opciones always last); permanent root, back from route list returns here. Square cards use a left-aligned icon + bold title + gray subtitle layout (`SquareLandingCard`/`SquareCard`, shared with the "Más opciones" hub's own grid); pills are a lighter centered icon+label tile (`PillLandingCard`/`PillCard`), with "Más opciones" kept visually distinct (neutral white/gray vs. the others' blue tint) to read as a submenu launcher |
| Closest stop button | ✅ | ✅ | Geolocates user, finds nearest stop across all supported routes, navigates to NextDeparture; ties broken by soonest departure; inline error on failure |
| Route selection | ✅ | ✅ | |
| Route stops (visual line display) | ✅ | ✅ | Direction toggle on both |
| All routes screen | ✅ | ✅ | "Ver todas las rutas" button on stop list → new screen with dropdown selector + stop list; header shows current direction label; map button leads to map with same dropdown; tapping stop → NextDeparture (today) or DaySchedule (non-today). M6: 5 entries (L-V Regular/Circular, Sáb Ida/Vuelta, Dom); M1: 4 entries (L-V/Sáb × circularA/B) |
| Next departure (live countdown) | ✅ | ✅ | iOS uses MapKit, Android uses OSM tiles |
| Next departure — direction indicator | ✅ | ✅ | Pill below stop name; circular routes show "A → B", others "Dirección X" |
| Next departure — direction swap button | ✅ | ✅ | Toolbar button toggles direction in-place, mirrors stop list behaviour; hidden when route uses mergedDirectionLabel (e.g. M4) |
| Route map screen | ✅ | ✅ | Interactive map with stop markers + polyline; accessible via map button in stop list toolbar; direction swap supported; tap marker → NextDeparture. Android uses OSMDroid, iOS uses MapKit. All-routes mode adds dropdown above map |
| Day schedule (full day view) | ✅ | ✅ | All today's departures with "Ahora" marker, auto-scrolls; accepts overrideDayType for non-today route groups |
| Times disclaimer card | ✅ | ✅ | Expandable card on NextDeparture screen, explains approximate times |
| Source links footer (official schedule links) | ✅ | ✅ | `SourceLinksFooter` (renamed from `PDFLinksFooter`) on NextDeparture, driven by each route's unified `sourceUrl` JSON field (M1-M8: Linecar PDF URLs; M9-M11: infosegovia.com timetable pages); header copy, per-route link label, and icon (doc vs. globe) adapt based on whether the URL is a PDF or a web page, fixing the prior iOS/Android parity gap where M9-M11 showed either a dangling Linecar sentence or nothing at all; `pdfUrl` JSON field kept alongside `sourceUrl` for backward compat with older app versions still fetching from the server |
| Feedback prompt (footer entry point) | ✅ | ✅ | "¿Sugerencias o errores? Cuéntanoslo" line inside the source-links footer card on NextDeparture; tapping shows a choice dialog ("Reportar error" / "Enviar sugerencia") which routes through the same `FeedbackCategory`/`FeedbackCoordinator` + BugSink flow as the About screen |
| App store review prompt | ✅ | ✅ | `ReviewPromptService` singleton; conditions: ≥3 launches, ≥3 days since first launch, ≥10s active session, ≥90 days between prompts; clock manipulation guard; Android: Play In-App Review API (`play-review-ktx`), triggered in `onResume`/`onPause`; iOS: StoreKit `AppStore.requestReview` (iOS 18+) / `SKStoreReviewController`, triggered via `.onAppear` + foreground/background notifications in ContentView |
| Timetable (full schedule view) | ✅ (dead code) | ❌ | Android has it but no navigation to it |
| About screen | ✅ | ✅ | Sheet presented from "Acerca de" footer button on landing screen; shows app info, Ko-fi, GitHub, Linecar data source, contact links, legal notice. "Reportar error" and "Enviar sugerencia" both go through `FeedbackCategory`/`FeedbackCoordinator`: in-app form submits to BugSink (consent-gated via `MonitoringPreferencesService`), with email (`mailto:`) fallback if monitoring consent is not enabled |
| Store review button (About screen) | ✅ | ✅ | "¿Le gusta la app?" section between the Ko-fi support section and the source-code section; direct link to the store review flow (not the algorithmic `ReviewPromptService` popup — this is a manual, always-available entry point). Android: `market://details?id=<applicationId>` intent with `ActivityNotFoundException` fallback to `https://play.google.com/store/apps/details?id=<applicationId>`; iOS: `https://apps.apple.com/app/id6759936464?action=write-review` (opens straight to the review-writing screen). Tracked as `about_link_tapped` with destination `play_store_review`/`app_store_review` |
| Reminders screen | ✅ | ✅ | "Mis recordatorios" card inside "Más opciones" (see below); bell tap = one-off, long-press = daily (recurring); daily reminders show repeat badge (circular arrows + bell); smart-skip: day type + seasonal check at fire time silently skips if bus doesn't run; one-off uses "next occurrence" logic (finds next future date the bus runs, up to 30 days ahead); bells shown on ALL departures in DaySchedule + NextDeparture (not just today's future ones); separate lead times for one-off (default 10 min) and daily (default 15 min); cancel individual reminders; auto-pruned one-offs on app launch. Android: server-sent FCM push (parity with iOS APNs) + AlarmManager kept as offline fallback; FCM token stored in fcm_prefs SharedPreferences; serverId tracked on BusReminder for server-side cancel/token-rotation; iOS: 7-day rolling batch of UNNotificationRequest with deterministic IDs + replenishDailyReminders() at launch |
| Reminders tutorial | ✅ | ✅ | 4-slide onboarding shown on first open of RemindersScreen; re-accessible via "?" button in toolbar; slides: La campana → Aviso puntual → Aviso diario → Tus recordatorios; platform-specific screenshots; Android: ModalBottomSheet + SharedPreferences flag; iOS: .sheet + @AppStorage flag |
| Live updates tutorial | ✅ | ✅ | 3-slide onboarding shown on first visit to NextDepartureScreen where boarding button is visible (daysAhead==0); re-accessible via "?" button in toolbar (topBarTrailing, alongside swap button); slides: icon intro → boarding button screenshot → boarded/ETA screenshot; Android: ModalBottomSheet + SharedPreferences ("live_update_tutorial"); iOS: .sheet + @AppStorage("liveUpdateTutorialShown") |
| Live boarding confirmations | ✅ | ✅ | "Estoy en el autobús" button on NextDeparture (uses current direction directly, no picker) + card on Landing screen (geolocate → route picker if multiple → direction picker → POST); one-tap-per-session; badge + adjusted ETA when others confirmed same trip; 60s polling via BoardingService; server URL + API key in BuildConfig/AppConfig |
| Service alerts banner | ✅ | ✅ | Compact pill showing most severe active alert; severity-coded colour (critical=red, warning=orange, info=blue); `(+x)` suffix when multiple alerts active; tap opens detail sheet listing all active alerts with full message, date range, and affected routes; not dismissible; fetched from `GET /alerts` (no auth) at startup; server admin CRUD via `POST/DELETE/GET /admin/alerts` (RELOAD_KEY); Android: pill in TopAppBar left slot; iOS: pill pinned just below nav bar in view body (toolbar not suitable for custom-styled pills); see `docs/SERVICE_ALERTS.md` |
| Service alert push notification | ✅ | ✅ | Day-start broadcast at 08:00 Europe/Madrid via FCM (Android) / APNs (iOS); fires immediately for alerts active within 10 min of creation; `broadcastSent` flag prevents duplicate sends; all devices registered via `POST /device-tokens` (API_KEY auth) called from `FcmService.onNewToken` (Android) / `AppDelegate.didRegisterForRemoteNotificationsWithDeviceToken` (iOS); server: `src/alertBroadcaster.ts` (5-min tick) + `src/routes/devices.ts` + `devices` table in lowdb; stale tokens pruned on broadcast failure + 90-day TTL; `minSeverity` per device (none/info/warning/critical); server filters broadcaster per-device |
| Notification permission consent | ✅ | ✅ | Pre-prompt sheet on first launch (sequenced after monitoring consent); bell icon + explanation + severity picker (All / Solo importantes / Solo críticas / Desactivadas); "Activar" triggers system permission dialog then registers token; "Ahora no" saves Off without burning system prompt; Android: requests `POST_NOTIFICATIONS` at runtime via `ActivityResultContracts.RequestPermission`; iOS: calls `requestAuthorization` in consent handler |
| Alert severity preference | ✅ | ✅ | In Settings screen ("Notificaciones" section, radio-button group); stored in UserDefaults / SharedPreferences via `NotificationPreferencesService`; changing re-registers device token with new `minSeverity`; default "info" (all alerts) |
| "Más opciones" hub | ✅ | ✅ | Landing card "Más opciones" → menu screen (`OtrasOpcionesScreen`/`OtrasOpcionesView` — internal naming unchanged, screen title renamed from "Otras opciones" to "Más opciones", styled like Landing's square-card grid) grouping secondary features that don't need top-level landing real estate: "Mis recordatorios", "Consultar otro día", and "Favoritos" (see below). Replaced the earlier separate "Mis recordatorios" + "Planifica tu viaje" landing cards and the now-retired `TripPlannerScreen`/`TripPlannerView` |
| Favorite stops | ✅ | ✅ | "Favoritos" is one of 6 actions in Landing's configurable pool (see "Fully configurable landing layout" below) — wherever it's pinned (or, if not pinned, from within "Más opciones") it calls the same `onShowFavorites`/`FavoriteStopsScreen`/`FavoriteStopsView` — lists stop+route+direction combos the user starred, most-recently-added first, tapping one navigates straight to NextDeparture, swipe-to-delete removes one. A favorite is identified by the triple `(stopId, routeId, viewId)`, not just the stop — a physical stop can be served by multiple routes/directions — with a stop/route-number/direction snapshot stored alongside so the list renders without reloading route data (same pattern as `BusReminder`'s composite `matchKey`). Starred via a toolbar star toggle on `NextDepartureScreen`/`NextDepartureView`, shown only once a concrete single route+view context is resolved (mirrors the swap button's precondition). Backed by `FavoriteStopsPrefs` (SharedPreferences on Android, UserDefaults on iOS), same singleton pattern as `GuidedModePrefs`; no cap on favorites. `favorite_stop_toggled` Aptabase event on toggle |
| Fully configurable landing layout | ✅ | ✅ | Replaces the old single "Acción principal" setting (which could duplicate against a fixed "Cómo llegar" pill). Landing now has 4 independently configurable slots — 2 square "Acción principal 1/2" cards and 2 compact "Acción adicional 1/2" pills — each assignable to any of a 6-action pool: Estoy en el autobús (default slot 1), Líneas de bus (default slot 2), Planificar viaje ("Cómo llegar"), Mis recordatorios, Consultar otro día, Favoritos (default slot "adicional 2" is Mis recordatorios, "adicional 1" is Favoritos). "Parada más cercana" and "Más opciones" are fixed, not part of the pool. Mutual exclusion is enforced two ways: Settings' 4 dropdowns (`ExposedDropdownMenuBox` ×4 on Android, `Picker` ×4 on iOS) each hide options already picked in the other 3 slots, and the underlying `LandingLayoutPrefs.setAction`/`getSlots` (replaces `MainActionPrefs`; SharedPreferences on Android as `LandingLayoutPrefs.kt`, UserDefaults on iOS as `LandingLayoutPrefs` in `MainActionPrefs.swift`) self-heals any duplicate/missing/corrupted stored slot by falling back to the next unused pool action, and swaps on programmatic conflict. One-time migration: an existing single legacy "Acción principal" value moves into "Acción principal 2" on first read. `MainLandingAction` enum (unchanged name, now 6 cases) carries `label` + `subtitle`; square cards show icon+title+subtitle, pills show icon+title only. "Estoy en el autobús" keeps its live loading/confirmed visual state (spinner, checkmark, color/label swap) regardless of which of the 4 slots — or the "Más opciones" hub — it ends up in, via shared `LandingActionSquare`/`LandingActionPill` (Android) and `LandingActionSquare`/`LandingActionPill` (iOS) helper views that special-case `.boardBus`; `LandingActionCallbacks` centralizes the action→navigation-callback mapping, reused by both Landing and `OtrasOpcionesScreen`/`OtrasOpcionesView`. "Más opciones" now dynamically shows whichever of the 6 pool actions are **not** currently pinned to one of Landing's 4 slots (was: a fixed exclude-one-action list) — so it can range from 2 to 6 entries depending on configuration, always reachable regardless of setup. Android's Landing screen re-reads the slot config on `ON_RESUME` (`DisposableEffect` + `LifecycleEventObserver`) so returning from Settings reflects a change immediately; iOS does the same via the Settings sheet's `onDismiss`. Icon sizing was also fixed in this pass — square-card icons render at 34pt/dp (was disproportionately small, especially on Android where a leftover fixed 8dp inner padding calibrated for the old 40dp box shrank glyphs to ~14dp), pill icons at 22–24pt/dp |
| Trip planner / "Consultar otro día" | ✅ | ✅ | Reached from "Más opciones" (see above). Date is picked FIRST, on the "Más opciones" screen itself (Material3 `DatePickerDialog` / SwiftUI `DatePicker` sheet, bounded today→+90 days) — before route/stop selection, since a route's stops/views can differ completely by day type (e.g. M1 circularA/B, M6's 7 variants); route/stop selection then resolves views against that date's day type (not "today"), avoiding a stop list that doesn't match the day the user actually picked. Lands directly on DaySchedule (skipping NextDeparture) with the picked date pre-set and the date picker still enabled to change it. Shows a festivo banner ("Festivo: San Frutos · horario de domingo") when the picked date is a holiday; seasonal variants + day-type resolve against the picked date, not today; "Ahora" marker suppressed for non-today dates; empty-state copy adapts when a route has no service that day; confirming a date fires the `check_another_day` Aptabase event (consent-gated via `AnalyticsService`, same as `route_selected`/`stop_selected`/etc.). Part of the "[E1] Check departures for a specific date" epic |
| Trip planner search tuning (Settings) | ✅ | ✅ | "Planifica tu viaje" section in Settings, backed by `TripPlannerPrefs` (SharedPreferences on Android, UserDefaults on iOS; defaults 90/15/15/15/15); exposes `JourneyPlannerService`'s 5 connection-search parameters as numeric text fields with short explanations + a "Restaurar valores" reset button: max transfer wait (`maxWaitMin`, default 90 min — a mid-journey transfer wait longer than this is rejected as unreasonable) and 4 transfer buffers — same-stop/walk × transcribed(exact PDF time)/estimated(interpolated time), all defaulting to 15 min. `JourneyPlannerService.findJourneys` now takes these 5 as optional parameters; the real caller `JourneySearchCoordinator` reads them from `TripPlannerPrefs` on every search. Note: `JourneyPlannerService`/`JourneySearchCoordinator`/`JourneyPlannerScreen`/`JourneyPlannerView` (the underlying A→B route-finding "Cómo llegar" feature this configures) predate this settings section but aren't yet documented elsewhere in this tracker — pre-existing drift, not introduced here. "Planificar viaje"/"Cómo llegar" is now one entry in Landing's 6-action configurable pool (see "Fully configurable landing layout") — wherever it's pinned (or, if unpinned, from "Más opciones") it navigates to the same `JourneyPlannerScreen`/`JourneyPlannerView` |
| Journey planner result diversity fix | ✅ | ✅ | `JourneyPlannerService.rankAndDedupe` previously grouped candidate journeys by `routeId`/`variantId` pattern and collapsed each group to a single best-by-rank instance *before* ranking — meaning a whole day's worth of usable departures on the same route pair collapsed to one, while (separately) boarding the same trip and transferring at any of several shared stops with a connecting route still counted as 3 "different" options. Fixed by grouping on the actual trip identity ridden (`RideSegment.tripKey` / iOS equivalent, one per `Leg.Ride`) instead of route/variant, and applying that grouping *during* the final rank-and-take-3 step rather than before dominance filtering — so a later departure on the same route pair now gets its own shot at a results slot, while boarding-the-same-trip-different-shared-stop correctly collapses to one. See "One result per trip combination, not per route" in `docs/JOURNEY_PLANNER.md`. |
| Journey planner departure reminder | ✅ | ✅ | "Recuérdame salir" bell on each journey card in the results list (`JourneyResultsScreen`/`JourneyResultsView`) and a toolbar bell on the detail screen (`JourneyDetailScreen`/`JourneyDetailView`), reusing the existing `ReminderService`/`BusReminder` one-off reminder machinery (no long-press/daily — a planned trip is one-off). Fires against `journey.departureMin` (when to leave), not the first `Leg.Ride`'s own `depMin` — a journey can start with a `Leg.Walk` to a better-connected stop (see `JourneyPlannerService`), so the reminder must cover that walk too; identified/day-type-checked against the first ride's route+variant so the existing smart-skip logic applies, with `SeasonalAvailability.YEAR_ROUND` assumed (not preserved through the CSA search — acceptable since the journey was already validated against the searched date's day type/season at search time). New `JourneyReminderHelper` (Android: `services/`, iOS: `Services/`) resolves this identity from `(journey, date, originName, destinationName, routesById, stopsById)`, looking up the first ride's `RouteView.direction` via `RouteDataService.getRouteViews`. `BusReminder` gained an optional `journeyLabel` field ("Origin → Destination") shown as a badge in "Mis recordatorios" (`RemindersScreen`/`RemindersView`) so journey-planner reminders read as "remember to leave for this trip" rather than a normal bus-departure alert. `ReminderService.scheduleReminder` takes an optional `journeyLabel` param on both platforms and includes it in the `POST /reminders` payload; iOS's `rescheduleOneOff`/`rescheduleDaily` reconstruction sites were updated to carry `journeyLabel` forward (Kotlin's `.copy()` does this automatically), and iOS's `mergeFromServer`/`GET /reminders/device/:token` decode also round-trips it (Android has no such restore path — POST-only). Server (`services/InterSegoService`): `DeviceReminder`/`PostReminderBody` gained an optional `journeyLabel`; `scheduler.ts`'s `sendPush` swaps in trip-specific push copy when it's present — title "Prepárate para salir", body "Tu viaje con comienzo en {routeNumber} · {stopName} sale en {leadMinutes} min — {time}" (`buildJourneyBody`) — instead of the generic "Línea {routeNumber} · {stopName}" / "Sale en {leadMinutes} min — {time}". Plumbing: `JourneyPlannerFlowScreen`/`InterSegoApp`'s `JourneyDetailSelection` now also carry `routes`/`routesById`, `date`, `originName`, `destinationName` down to both screens |
| Tight transfer warning | ✅ | ✅ | Flags a journey whose smallest transfer margin (arrival at the boarding stop, whether by ride or walk, to the connecting bus's departure) falls below the fixed safety threshold `TripPlannerPrefs.recommendedMinBuffer`/`RECOMMENDED_MIN_BUFFER` (15 min) — deliberately independent of whatever (possibly lower) buffer the user has configured `JourneyPlannerService`'s search to accept, so a user who's loosened their own settings still sees which specific results are riskier than others, rather than the warning silently tracking their own threshold. `Journey.transferMargins()` (new, in the `Journey` model on both platforms) computes one margin per mid-journey `Ride` leg (`ride.depMin` minus the running clock at that point, mirroring the gap logic already in `stepsWithWaits()`) — one formula covers both same-stop and post-walk transfers, since a walk's minutes are already folded into the clock before the connecting ride's gap is computed. Results list: `JourneyResultsScreen`/`JourneyResultsView`'s `JourneyCard`/`JourneyRow` shows a subtle orange `TightTransferBadge` ("Transbordo ajustado · N min de margen") below the leg chips when the journey's tightest margin is below threshold — reuses the existing `warningColor()` helper on Android (`RouteOrange`/`RouteLightOrange`, no dedicated ColorScheme warning role) and plain `.orange` on iOS, matching the tone already used for the "Horarios orientativos" estimated-time note. Detail screen: `JourneyDetailScreen`/`JourneyDetailView` inserts a `TightTransferRow`/`tightTransferRow` per tight transfer (not just the worst one) inside the "Itinerario" section, directly under `ItineraryMapView`, naming the specific stop ("Transbordo ajustado en {stopName}") since a journey can have multiple transfers and only one might be tight. Not wired into the search/ranking logic itself — `JourneyPlannerService`'s own buffer parameters (`bufferSameStopTranscribed`/`bufferWalkTranscribed`/etc., user-tunable via `TripPlannerPrefs`, see "Trip planner search tuning" above) still gate which journeys are returned at all; this feature only decorates the returned results with a stricter, fixed-threshold safety read |
| Itinerary detail map | ✅ | ✅ | New `ItineraryMapView` composable/SwiftUI view shown at the top of the journey detail screen (`JourneyDetailScreen`/`JourneyDetailView`, reached after picking one itinerary from "Cómo llegar" results) — one colored polyline per ride leg (cycled from a fixed palette, since no route-color convention existed anywhere in the app before this) plus a gray dashed line for any walk leg, with green/checkered-flag/blue markers for origin/destination/transfer stops (the destination marker is a checkerboard pattern drawn in code — `Canvas` clipped to a circle on iOS, a `Canvas`-drawn `Bitmap` clipped the same way on Android — no bundled image asset). Android's waypoint markers were originally OSMDroid's default pin icon (no custom `Marker.icon` set); brought up to match iOS's colored-dot style as part of adding the flag, so both platforms now render identically. Each leg's polyline is loaded via the existing `PolylineLoader` (`routeId`+`variantId`, same id space as `RouteView.id`) then trimmed to just the boarding→alighting stretch by nearest-point matching against the stops' `routingLatitude`/`routingLongitude` — showing only the relevant part of the route, not the whole line. Reuses OSMDroid (Android, `MapView`/`Polyline`/`Marker`) and MapKit (iOS, SwiftUI `Map`/`MapPolyline`/`Annotation`) the same way `RouteMapScreen`/`RouteMapView` do, but is the first place in the app rendering more than one polyline on one map. iOS `JourneyDetailSelection` already carried full `stops: [String: BusStop]` (not just names), so no new plumbing was needed there; Android's `JourneyPlannerFlowScreen` gained a `stopsById` map alongside the existing `stopNames` one, sourced from the same `StopDirectoryService` call. The screen is now split into two labeled sections ("Itinerario" / "Pasos"); "Itinerario" opens with a trip-summary row (origin name + departure time, destination name + arrival time, derived from `journey.legs.first()/.last()` — no new data needed) above the map. The detail screen's title is duration + transfer count (e.g. "35 min · Directo") — the old departure-arrival time range was cut for being redundant with the new summary row. The results list screen (`JourneyResultsScreen`/`JourneyResultsView`, shown before picking an itinerary) got the same two-section treatment ("Resumen" / "Opciones"): a static "Resultados" title (the previous "origin → destination" title routinely overflowed the app bar) and a "Resumen" summary card (origin, destination, and the search date/time window) above the "Opciones" list of journey cards |
| Guided mode (direction picker) | ✅ | ✅ | Toggle in Settings, default ON, backed by `GuidedModePrefs` (SharedPreferences on Android, UserDefaults on iOS); when enabled, selecting a stop that's served by multiple route/direction combos shows a "¿A dónde vas?" picker (`DirectionPickerScreen`/`DirectionPickerView`) instead of guessing a direction; remembers + badges ("Última") the last-selected direction per stop+route; routes with `mergedDirectionLabel` (e.g. M4 circular) collapse to one option and auto-advance, skipping the picker; wired into stop selection from stop list, all-routes screen, and (Android only) the live-boarding direction picker; first use shows a one-time tutorial sheet (`GuidedModeTutorialSheet`/`GuidedModeTutorialView`), re-shown until dismissed once |
| "Novedades" what's-new notice | ✅ | ✅ | `WhatsNewService` (Android: `services/WhatsNewService.kt`, iOS: `Services/WhatsNewService.swift`); shown once after an app update via `WhatsNewScreen`/`WhatsNewView`. Version-*range*-aware (not latest-only): each `WhatsNewEntry` carries the SemVer `version` it shipped in; `entries` is append-only (never cleared/overwritten on release); `entriesToShow()` filters to entries with `version > lastSeenVersion` and `<= currentVersion` via a numeric MAJOR.MINOR.PATCH comparator (not string comparison), so a user who skips versions sees everything they missed, not just the latest release's notes; capped to the most recent 5 entries (`maxEntriesToShow`/`MAX_ENTRIES_TO_SHOW`) so a long-dormant install doesn't get a wall of old announcements |

### Location & Geolocation

| Feature | Android | iOS | Notes |
|---|---|---|---|
| Location manager | ✅ | ✅ | Android: FusedLocationProviderClient; iOS: CLLocationManager (in ClosestStopService) |
| Closest stop finder | ✅ | ✅ | Android: ClosestStopFinderService; iOS: ClosestStopService |
| Geocoding service | ✅ (backend only) | ❌ | Not used in any UI screen (all stops have embedded coordinates) |
| Coordinate cache | ✅ (backend only) | ❌ | Not used in any UI screen |

### Data Models

| Model | Android | iOS | Notes |
|---|---|---|---|
| BusRoute | ✅ | ✅ | |
| BusStop | ✅ | ✅ | |
| BusTimetable | ✅ | ✅ | |
| DepartureTime | ✅ | ✅ | |
| DayType | ✅ | ✅ | |
| RouteVariant | ✅ | ✅ | |
| RouteView | ✅ | ✅ | |
| SeasonalAvailability | ✅ | ✅ | |
| RouteType | ✅ | ✅ | |
| RouteCacheStatus | ✅ | ❌ | Not surfaced in UI |
| UpdateProgressState | ✅ | ❌ | Not surfaced in UI |
| ScheduleDate | ✅ | ❌ | Not used in any UI screen |

## Repository Structure

```
InterSego/
├── CLAUDE.md                    # This file - project overview & feature tracker
├── .gitignore                   # Repository-wide ignore patterns
├── android/                     # Android implementation
│   ├── CLAUDE.md               # Android-specific development guide
│   ├── app/                    # Android app source code
│   ├── docs/                   # Implementation documentation
│   └── build.gradle.kts        # Android build configuration
├── iOS/                         # iOS implementation
│   ├── InterSego/              # App source code
│   │   ├── Models/             # Data models
│   │   ├── Services/           # Business logic & infrastructure
│   │   └── Views/              # SwiftUI views
│   └── InterSego.xcodeproj/    # Xcode project
└── resources/                   # Shared resources across platforms
    ├── Icons/                  # App launcher icons
    ├── Route display/          # Route visualization assets
    └── timetables/             # Shared JSON timetable files (source of truth for migrated routes)
```

## Platform Implementations

### Android (`android/`)

**Technology Stack:**
- Jetpack Compose + Material3 for UI
- Kotlin Coroutines for async operations
- OkHttp for HTTP networking
- JSON-based timetable loading via `TimetableLoader`

See `android/CLAUDE.md` for detailed Android development guide.

### iOS (`iOS/`)

**Technology Stack:**
- SwiftUI with default iOS styling
- Swift actors for thread safety
- URLSession for HTTP networking
- JSON-based timetable loading via `TimetableLoader`

**Build:** Open `iOS/InterSego.xcodeproj` in Xcode. Deployment target: iOS 17.0.

### Web (Future)
Not yet implemented.

## External Data Source

**Linecar Bus Company Website:**
- URL: https://www.linecar.es/metropolitano/segovia/
- Contains: Bus route PDFs (schedules), route information
- Update frequency: Irregular (seasonal changes, service updates)

**Data Strategy:**
- Timetable data is pre-extracted into `resources/timetables/{routeId}.json` and bundled with each app
- Next milestone: load JSON from a remote server instead of bundle assets

## Timetable Version Tracking

Each timetable JSON has a top-level `"version"` field (e.g. `"3.4"`). `TimetableLoader` reads this and exposes it via `RouteDataService.getParserVersion()`.

**When to bump the version:** any change to timetable data — new stops, corrected times, seasonal rule changes, etc. Bump in the JSON file and increment on **both** platforms' copies together.

Polyline JSON files (`resources/route_polylines/`) also carry a `"version"` field (starting at `"1.0"`). Bump it whenever coordinates are edited — same discipline as timetable versions.

## App Release Versioning

App-store-facing version numbers are tracked independently per platform and are **not** the same as the timetable/polyline `"version"` fields above:

- **Android**: `versionCode` (integer, +1 per release) and `versionName` (SemVer, e.g. `"2.10.0"`) in `android/app/build.gradle.kts`
- **iOS**: `MARKETING_VERSION` (SemVer, e.g. `3.8.0`) in `iOS/InterSego.xcodeproj/project.pbxproj` (appears twice — Debug + Release build configs, must match); `CURRENT_PROJECT_VERSION` (build number) stays `1` and is not bumped per release

Android and iOS version numbers drift from each other by design (different histories) — there's no requirement they match.

**These are real [SemVer](https://semver.org/) numbers (`MAJOR.MINOR.PATCH`), not just an incrementing counter:**
- **Patch** — bug fixes only, no new user-facing capability.
- **Minor** — new backward-compatible functionality (new screen, new feature, new mode). This is the default for "a PR worth shipping" that adds something.
- **Major** — reserved for a breaking change, or when explicitly requested by the user for a given release regardless of what SemVer alone would say (e.g. a milestone worth marking). Don't infer "big feature" as sufficient reason on its own — ask if unsure whether a release warrants major.

**When to bump:** after merging a PR (or set of PRs) worth shipping, as a dedicated release commit — bump both platforms together (Android `versionCode` +1 too), commit message `chore(InterSego): Release Android (X.Y.Z) and iOS (A.B.C)`. Don't bump mid-feature-PR unless that PR's own commit already includes it.

**Release process, in order:**
1. **Look for new features since the last release.** Run `git log <last-release-tag-or-commit>..HEAD --oneline` (the previous release commit is `chore(InterSego): Release Android (...) and iOS (...)`) to see everything that shipped. Don't rely on memory of the conversation — a release can bundle work from earlier sessions too.
2. **Decide whether "Novedades" needs a new entry.** Skim those commits for anything a returning user would want a heads-up about — a new screen, feature, or mode (roughly: Minor/Major-worthy changes). If so, add a `WhatsNewEntry` tagged with the new version to **both** platforms' `WhatsNewService.entries` (`android/.../services/WhatsNewService.kt` and `iOS/InterSego/Services/WhatsNewService.swift`) — `entries` is append-only, never edit or remove past entries. Skip it for pure bug-fix/Patch releases or internal-only changes. See the feature tracker's "Novedades" row for how the version-range filtering works.
3. **Bump the version numbers** on both platforms as described above, in the same commit as any new `WhatsNewEntry`.

## Development Rules

### Feature Tracker Maintenance
- **ALWAYS update the feature tracker table above** when adding, modifying, or removing features on any platform
- Mark new features with ✅ on the platform where implemented and ❌ on others
- Add notes explaining platform-specific differences (e.g., "iOS uses MapKit, Android uses OSM tiles")
- If a feature exists on one platform but not the other, leave the row so drift is visible

### Commit Guidelines
- Do not put any Claude co-authoring reference or link to claude.ai in commits
- Before committing, allow the user to test the changes. Only commit after confirmation
- NEVER push anything to the remote repository unless explicitly asked

### Code Style
- Source-available license headers on all source files
- Use each platform's default styling (Material 3 on Android, system defaults on iOS)
- Prefer platform-native solutions over cross-platform libraries

## Quick Start

**For Android Development:**
```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```
See `android/CLAUDE.md` for full guide.

**For iOS Development:**
Open `iOS/InterSego.xcodeproj` in Xcode, build and run.

## License

Source available — see `LICENSE` and source file headers. Redistribution and commercial use prohibited without written permission.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
|------|----------|
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
