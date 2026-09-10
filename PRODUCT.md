# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

Residents and commuters in the Segovia metropolitan area (Spain) who ride Linecar interurban bus routes (M1–M8, plus growing route coverage). They check the app on the go — often deciding in the moment whether to rush for a stop, when the next bus arrives, or which stop is nearest to them — frequently without a reliable data connection.

## Product Purpose

InterSego gives Segovia-area bus riders fast, reliable access to official Linecar timetables: live next-departure countdowns, full-day schedules, nearest-stop lookup via geolocation, route maps, reminders, and live boarding confirmations from other riders — all working offline once data is cached, since transit decisions can't wait on a signal.

## Positioning

Official Linecar data kept automatically fresh (server-synced timetables/polylines with fallback to on-device scrape of the Linecar site when a source URL goes stale) + full offline-first operation (three-tier cache: memory, JSON, PDF/bundle) + live next-departure countdowns + nearest-stop geolocation + live rider boarding confirmations — delivered as genuinely native apps per platform (Jetpack Compose/Material 3 on Android, SwiftUI on iOS), not a wrapped web view.

## Operating Context

- Data source of truth: PDF timetables published by Linecar (linecar.es/metropolitano/segovia), transcribed into shared JSON (`resources/timetables/`) and served via InterSego's own backend (`server/`) for OTA updates between app releases.
- Riders use the app both at home planning ahead and at/near a physical stop deciding in real time — so screens must read fast outdoors, at a glance, one-handed.
- Day-type/seasonal schedule variation (weekday/Saturday/Sunday, school-year vs. summer, festivo calendar) is a first-class domain concept riders rely on, not an edge case.
- Network conditions are unreliable in parts of the service area — offline-first is a baseline expectation, not a fallback path.

## Capabilities and Constraints

- Routes M1–M8 fully modeled (JSON-based timetables + polylines); route set is expected to keep growing (dynamic, server-driven route discovery already supported).
- Native platform UI conventions are followed per OS (Material 3 on Android, iOS system defaults on iOS) rather than a shared cross-platform design system — "adaptive" platform, not "one design language, two runtimes."
- Spanish-only UI at present; internationalization/expansion to other languages is a possible future direction, not committed — don't hard-code Spanish-only assumptions that would be expensive to unwind, but don't build i18n infrastructure until asked.
- Offline-first is a hard constraint: every core flow (timetables, route maps, reminders) must degrade gracefully without network.
- Analytics (Aptabase) and error reporting (Bugsink/Sentry) are consent-gated and self-hosted; no third-party ad/tracking SDKs.

## Brand Commitments

- App name: **InterSego** ("Metropolitanos de Segovia").
- Existing app icon is locked — do not propose a replacement icon as part of design work; treat it as source-of-truth brand asset (`resources/Icons/`).
- Brand green: **#3CA27A** — binding brand color to build around/against, not to be replaced by an invented palette.
- Copyright Bruno Follon (@bFollon); source-available license, no redistribution/commercial use without permission.

## Evidence on Hand

- App icon assets at `resources/Icons/` (multiple export sizes/variants present).
- No app store screenshots yet ("Coming soon" per README) — do not fabricate screenshot content or store-listing copy.
- Shared timetable/polyline JSON in `resources/` is real production data, not placeholder.

## Product Principles

1. Offline-first, always — no core flow may assume connectivity.
2. Glanceable over exhaustive — riders are often making a rushed, one-handed decision; surface the next relevant fact first.
3. Official data, honestly presented — schedules trace back to real Linecar PDFs; day-type/seasonal nuance is never simplified away.
4. Platform-native over cross-platform-consistent — each OS gets its own idiomatic UI, not a shared design system forced onto both.
5. Brand identity (name, icon, #3CA27A green) is settled; design work builds around it rather than re-litigating it.

## Accessibility & Inclusion

No specific accessibility standard has been mandated beyond each platform's default expectations (VoiceOver on iOS, TalkBack/Accessibility Services on Android) — follow standard native accessibility practices. Spanish-only UI for now (see Capabilities and Constraints).
