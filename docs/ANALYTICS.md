# Analytics — InterSego

## Decision

**Tool: [Aptabase](https://aptabase.com) (self-hosted)**

Aptabase is a lightweight, privacy-first analytics platform designed specifically for app event tracking. It runs comfortably on a Raspberry Pi 5 (~256 MB RAM), ships as a simple Docker Compose stack, has official Swift and Android SDKs, and its data model (`eventName` + arbitrary `props` JSON object) maps directly to the events we want to track.

Other options considered and rejected:
- **PostHog OSS**: ideal data model and SDKs, but requires Kafka + ClickHouse — too heavy for Pi 5 (6–8 GB RAM minimum)
- **Umami / Plausible**: lightweight, but web-centric dashboards and no mobile SDKs
- **LGTM stack**: suited for infrastructure telemetry, not app-level product events

The same Aptabase instance deployed for FarmaciasDeGuardia is reused here — Aptabase supports multiple apps under separate API keys on the same server.

---

## Current Monitoring State

InterSego has **no analytics or error reporting** at present. This is the first monitoring integration.

InterSego will have two separate monitoring toggles (same decision as FarmaciasDeGuardia):

| System | Purpose | Consent gate |
|---|---|---|
| Bugsink (Sentry SDK) | Error reports, crash diagnostics | `MonitoringPreferencesService` — `monitoring_enabled` key |
| Aptabase | Product/operational events, usage analytics | `MonitoringPreferencesService` — `analytics_enabled` key |

Both systems and the consent UI need to be added from scratch. FarmaciasDeGuardia's `ErrorReportingService`, `MonitoringPreferencesService`, and `MonitoringConsentView` are direct references for the implementation.

The closest existing HTTP pattern is `BoardingService` — a Swift `actor` + `URLSession` that POSTs to a self-hosted server with Bearer auth, configured via `AppConfig.swift` (URL) and `Secrets.swift` (API key). On Android, configuration lives in `BuildConfig`. The `AnalyticsService` should follow this same configuration pattern.

---

## Integration Plan

### iOS

- New service: `AnalyticsService.swift` in `iOS/InterSego/Services/`
- Model after `BoardingService.swift`: Swift `actor`, `URLSession` with configured timeout, reads from `AppConfig` + `Secrets`
- SDK: `AptabaseSwift` (add via Swift Package Manager)
- API key: add `aptabaseKey: String` to `Secrets.swift` (same pattern as `boardingAPIKey`)
- Host URL: add `aptabaseHost: String` to `AppConfig.swift` (same pattern as `boardingServerURL`)
- Initialization: call from `InterSegoApp.swift` on startup
- Consent: InterSego currently has no consent UI — a user consent flow needs to be added before initializing Aptabase (see FarmaciasDeGuardia's `MonitoringConsentView` + `MonitoringPreferencesService` as a reference)

### Android

- New service: `AnalyticsService.kt` in `services/`
- SDK: Aptabase Android SDK (add via Gradle)
- API key: add `APTABASE_KEY` to `local.properties` → exposed via `BuildConfig` (same pattern as `BOARDING_API_KEY`)
- Initialization: call from `MainActivity.kt` or `Application` subclass, gated on consent

---

## Events to Implement

### App Lifecycle

| Event | Props | Where to fire (iOS) |
|---|---|---|
| `app_launch` | `version: String`, `platform: "ios"` | `InterSegoApp.swift` — on root `.onAppear` |

### PDF URL Scraping

InterSego uses the same self-healing URL scraping pattern as FarmaciasDeGuardia. `PDFURLScrapingService` GETs `https://www.linecar.es/metropolitano/segovia/`, extracts `.pdf` links, and maps them to routes by filename keyword (e.g. `M4`, `M6`).

| Event | Props | Where to fire (iOS) |
|---|---|---|
| `pdf_url_scrape_complete` | `urls_found: Int`, `urls_changed: Bool` | `PDFURLScrapingService.swift` or startup coordinator — after scrape |
| `pdf_url_scrape_failed` | `error: String` | `PDFURLScrapingService.swift` — on failure |

### Timetable Parsing (per route)

Parsing flows through `PDFProcessingService` → route-specific strategy (e.g. `M4Parser`, `M6Parser`). Results are cached via `TimetableCacheService`.

| Event | Props | Where to fire (iOS) |
|---|---|---|
| `timetable_parsed` | `route: String`, `stops_count: Int`, `parser_version: String` | `PDFProcessingService.swift` — after successful parse |
| `timetable_loaded_from_cache` | `route: String`, `stops_count: Int` | `TimetableService.swift` — on cache hit |
| `timetable_parse_failed` | `route: String`, `error: String` | `PDFProcessingService.swift` — on parse exception |

**Context — route details:**
- `M4` — La Lastrilla ↔ El Sotillo; coordinate-based PDF parsing; parser v1.2
- `M6` — Segovia ↔ Torrecaballeros; cluster-based stop estimation; iOS-specific PDFKit text artifacts handled by `reorderSwappedLines()` and `preprocessLines()`; parser v0.5
- `M1`–`M3`, `M5`, `M7`–`M8` — static data (hardcoded from PDF screenshots); no runtime parsing; these routes never produce `timetable_parsed` events

The `parser_version` prop corresponds to `capabilities.version` in each parser — useful for detecting when a cache re-parse was triggered by a version bump.

### User Interactions

> Only meaningful, intentional interactions — not every tap.

| Event | Props | Where to fire (iOS) |
|---|---|---|
| `route_opened` | `route: String` | Route selection → stop list navigation |
| `stop_viewed` | `route: String`, `stop_id: String` | `NextDeparture` screen appears |
| `closest_stop_used` | `result: "found"` or `"not_found"` | `ClosestStopService.swift` — after geolocation attempt |
| `reminder_set` | `route: String`, `type: "one_off"` or `"daily"` | `ReminderService.swift` — on new reminder creation |
| `boarding_posted` | `route: String`, `direction: String` | `BoardingService.swift` — after successful POST (already has analytics-adjacent data; just add Aptabase call here) |
| `day_schedule_opened` | `route: String` | `DayScheduleView` appears |
| `map_opened` | `route: String` | `RouteMapScreen` appears |

---

## Open Decisions

1. **Consent UI**: ~~InterSego has no consent flow yet.~~ **DECIDED: add two separate toggles** (error reporting + analytics), matching FarmaciasDeGuardia. Needs a `MonitoringConsentView` equivalent + `MonitoringPreferencesService` with both preference keys (`monitoring_enabled` + `analytics_enabled`). FarmaciasDeGuardia's implementations are the direct reference.
2. **Anonymous device ID**: Aptabase SDK generates an anonymous session ID by default. No PII collected; no action needed.
3. **Offline queuing**: Aptabase SDK queues events locally and flushes on reconnect — default behaviour, nothing to configure.
4. **Retention**: configure via `APTABASE_DATA_RETENTION_DAYS` on the shared server. Recommended: 365 days.
5. **Shared server, separate API keys**: one Aptabase deployment covers both apps. Each app gets its own API key from the Aptabase admin UI, so dashboards are per-app.
