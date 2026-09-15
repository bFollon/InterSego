# Runbook: LaLiga IP-Blocking Banner

How to port InterSego's "LaLiga blocking" feature to another cross-platform app that has
a backend behind a Cloudflare Tunnel. Already applied once — see the "Worked example"
section at the bottom for the FarmaciasDeGuardia port, including exact file paths.

## Background

During La Liga football matches, LaLiga obtains court orders (upheld since a December 2024
Barcelona Commercial Court ruling) requiring Spanish ISPs to block IP addresses used to
stream matches illegally. Because many of those streams sit behind Cloudflare, the blocks
often target whole Cloudflare anycast IP ranges rather than a specific domain — which
collaterally blocks every other site sharing that IP, including any of our own apps' backends
that happen to be proxied through Cloudflare (e.g. via `cloudflared` tunnel).

From the client's point of view this looks exactly like "server down" or "no internet," but
it isn't the user's fault, isn't really "offline" (the device has perfectly good internet),
and isn't a bug in our code. The point of this feature is to tell the user the truth instead
of silently falling back to cached data or showing a generic error.

**Detection data source**: [hayahora.futbol](https://hayahora.futbol) is a community-run
transparency tracker that publishes the live list of currently-blocked Cloudflare IPs (updated
automatically) at:

```
https://hayahora.futbol/estado/blocked-any.txt
```

Plain text, one IP per line, no auth required, `Cache-Control: no-store` (always fresh).
Per-ISP variants also exist (`blocked-movistar.txt`, `blocked-vodafone.txt`, etc.) but
`blocked-any.txt` (any operator) is the right one to check against — see
`https://hayahora.futbol` itself for the full list of files and a JSON history format
(`estado/data.json`) if a future feature needs more than "is it blocked right now."

## Prerequisites — verify before porting

1. **Confirm the target app's backend is actually behind Cloudflare.** This is not automatic —
   don't assume it from a comment in the codebase (we found one that was stale/wrong). Verify
   directly:
   ```bash
   dig +short <your-server-hostname>
   ```
   If the resolved IP(s) fall in a Cloudflare range (commonly `104.16.0.0/13`, `172.64.0.0/13`,
   `188.114.96.0/20`, etc. — or just check whether they show up in `blocked-any.txt` during a
   real blocking window), the feature is applicable. If the backend is NOT behind Cloudflare
   (e.g. a plain VPS IP, or Cloudflare's DNS-only/grey-cloud mode), this feature is dead code —
   skip the port.
2. **Find the "talk to our own server" hook point.** Every app in this family has some
   lightweight, frequent call to its own backend — a manifest/locations/routes list fetch run
   at startup or on every sync. That's the cheapest, most representative place to notice
   "our server specifically is unreachable" without adding a new network call. Find its
   success and failure branches before writing any code.
3. **Find the existing offline/unreachable UI convention.** Every app in this family already
   distinguishes (or should distinguish) "device offline" from "our own server is
   unreachable but the device has internet" — reuse whatever singleton/observable object
   already tracks that, rather than inventing a parallel one.

## Design

A single new service, `LaLigaBlockingService`, with two entry points fed by the existing
server-call hook point:

```
onServerReachable()              // clears any stale "likely blocked" state
onServerUnreachable() -> Bool    // returns whether LaLiga blocking is the likely cause
```

Internally, `onServerUnreachable()`:

1. Bails out immediately (returns `false`, clears state) if the device itself is offline —
   this is not "device offline," it's a distinct state.
2. Rate-limits itself (2 minutes between checks) so a burst of failing requests during an
   outage doesn't hammer hayahora.futbol.
3. Fetches `https://hayahora.futbol/estado/blocked-any.txt` **directly** — not through the
   app's own server or tunnel, since that's exactly what may be down. Use a short timeout
   (5s) and a throwaway HTTP client/session, not the app's main API client.
4. **Resolves the app's own server hostname via a plain DNS lookup** (see "Accuracy" below)
   and compares the resolved IP(s) against the blocked list:
   - Blocklist empty → not blocked (`false`). High confidence.
   - Resolved IP found in the blocklist → blocked (`true`). High confidence.
   - Own hostname can't be resolved at all → no way to confirm a specific match; fall back
     to correlation only (`true` if the blocklist is merely non-empty). This is deliberately
     the *weakest* signal, used only when nothing better is available.
5. Fails closed on any network error talking to hayahora.futbol (`false`) — never show the
   LaLiga-specific message without at least some evidence.

### Accuracy: resolve-and-match, not just "is blocking happening somewhere"

An earlier iteration of this feature only checked whether `blocked-any.txt` was non-empty —
i.e. "is *some* LaLiga blocking active right now, anywhere in Spain." That's a correlation,
not a diagnosis: it could be true while the app's specific server IP is untouched. Resolving
the app's own server hostname and checking for an *exact* IP match is strictly more accurate
and costs one extra DNS lookup:

- **Android/Kotlin**: `java.net.InetAddress.getAllByName(host)`, `.hostAddress` for each.
- **iOS/Swift**: no simple stdlib call exists — the standard approach is a raw POSIX
  `getaddrinfo`/`getnameinfo` pair (see the InterSego or FarmaciasDeGuardia source for the
  exact boilerplate). Both run under `Darwin` (`import Darwin` / `#if canImport(Darwin)`).

Extract the hostname from wherever the app's base server URL is configured
(`URI(baseUrl).host` in Kotlin, `URL(string: baseUrl)?.host` in Swift) — don't hardcode it,
since it may differ per build config or between apps.

### Where to hook it in

Wire `onServerReachable()`/`onServerUnreachable()` into the existing "talk to our own
server" hook point identified in step 2 of Prerequisites — typically a manifest/routes-list
fetch's success/non-200/exception branches, or a shared status-reporting function that
already runs after every sync attempt (whichever is closer to "the app's own server request
either worked or didn't," not per-item failures within a larger batch).

If that hook point isn't already `async`/`suspend`, it will need to become so (DNS + one HTTP
call). Check its callers are already in an async context before making this change — they
usually are, since the surrounding sync logic is already async.

### UI

- A **new, visually distinct banner** — don't reuse the generic offline/unreachable card's
  copy, since the whole point is to *not* look like generic offline. Same visual chrome
  (orange, warning-family) but a soccer-ball icon and LaLiga-specific copy, so it reads as
  "same family of warning, more specific."
- Shown with **higher priority** than the generic offline/unreachable card when both could
  apply (device online, server unreachable, LaLiga blocking confirmed) — it's strictly more
  informative.
- **Tap opens a detail sheet**, not just a static banner — the cause here is genuinely
  surprising to a user ("why would a bus/pharmacy app care about football?"), so it earns a
  bit of explanation:
  - What's happening (can't reach our server, matches a live LaLiga blocking window)
  - Why (Cloudflare IPs are shared across thousands of unrelated sites; LaLiga's blocks hit
    the whole IP, not just piracy streams)
  - That it's court-sanctioned but contested (Cloudflare and security researchers have
    called it a net-neutrality violation; cite this rather than editorializing personally)
  - That it's not a bug — cached/bundled data is still shown, just possibly stale
  - A link to `hayahora.futbol` for more information
- Track two analytics events: banner tapped, and detail-sheet link tapped. No properties
  needed — presence/absence of the event is the signal.

### Wording (Spanish, used verbatim in both existing implementations)

Banner: *"Sin conexión al servidor: posible bloqueo de LaLiga en curso"*

Detail sheet body (four short paragraphs + link):
1. States the correlation with a live match and LaLiga's IP-blocking mechanism.
2. Explains the Cloudflare collateral-damage mechanism (shared IPs, no wrongdoing).
3. Cites the December 2024 Spanish court ruling and the criticism from Cloudflare/security
   researchers (net-neutrality concerns) — factual, sourced, not the app's own opinion.
4. Reassures: not a bug, cached data is still shown, may just be stale.
   Then a `hayahora.futbol` link.

## Checklist

- [ ] Confirm target backend is behind Cloudflare (`dig` the hostname)
- [ ] Identify the "talk to our own server" hook point on both platforms
- [ ] Identify (or create) the shared offline/unreachable status singleton the UI observes
- [ ] Add `LaLigaBlockingService` (new file, both platforms) — DNS resolve + IP match against
      `hayahora.futbol/estado/blocked-any.txt`, 2-minute rate limit, fails closed
- [ ] Wire `onServerReachable()`/`onServerUnreachable()` into the hook point (may require
      making it `async`/`suspend`)
- [ ] Add a boolean to the shared status singleton (e.g. `isLikelyLaLigaBlocked`)
- [ ] Add the banner component (new UI file, both platforms) — distinct icon/copy from the
      generic offline card, higher display priority
- [ ] Add the detail sheet (same file or adjacent) with the four-paragraph explanation + link
- [ ] Wire the banner into the home/landing screen, above the generic offline/unreachable
      branch
- [ ] Add two analytics events (banner tapped, link tapped) matching the app's existing
      `AnalyticsService.track(...)` convention
- [ ] Update the app's feature tracker / architecture doc + analytics event table
- [ ] Build both platforms before considering it done — `SourceKit`/editor diagnostics on
      newly-added Swift files are frequently stale-index noise (they'll also flag long-
      existing, definitely-compiling symbols in the same file); trust an actual
      `xcodebuild`/`gradlew compileDebugKotlin` run instead

## Worked example: FarmaciasDeGuardia (2026-09)

Ported from this InterSego implementation into `../FarmaciasDeGuardia`. Confirmed
`pharmacies-api.bfollon.dev` resolves to Cloudflare anycast IPs (`188.114.96.5` /
`188.114.97.5`) — the feature applies despite a stale code comment claiming otherwise.

| Concern | iOS | Android |
|---|---|---|
| New detection service | `ios/FarmaciasDeGuardiaEnSegovia/Services/LaLigaBlockingService.swift` (actor) | `android/.../services/LaLigaBlockingService.kt` (object) |
| Hook point | `ScheduleService.updateSyncStatus` (made `async`), on `summary.manifestFailure` / success | `PharmacyScheduleRepository.updateSyncStatus` (made `suspend`), same branches |
| Server call being watched | `ScheduleSyncService.fetchManifest()` → `GET {baseURL}/api/locations` | Same, Kotlin equivalent |
| Own server hostname source | `Secrets.scheduleServerBaseURL` | `Secrets.scheduleServerBaseUrl` |
| Status singleton | `ScheduleSyncStatus` (`ObservableObject`) — added `@Published isLikelyLaLigaBlocked` + `reportLaLigaBlocking(_:)` | `ScheduleSyncStatus` (Compose `mutableStateOf` object) — added `isLikelyLaLigaBlocked` + `reportLaLigaBlocking(...)` |
| Banner + sheet | `Views/LaLigaBlockingBanner.swift` (`LaLigaBlockingBanner` + `LaLigaBlockingDetailSheet`) | `ui/components/LaLigaBlockingBanner.kt` (`LaLigaBlockingBanner` + `LaLigaBlockingDetailSheet` composables) |
| Wired into | `ContentView.swift`, as a branch above the existing `syncStatus.isServerUnreachable` `OfflineWarningCard` | `MainScreen.kt`, as a branch above a new `ScheduleSyncStatus.isServerUnreachable` `OfflineWarningCard` branch — Android's `MainScreen.kt` didn't previously show a generic "server unreachable" card at all (only `isOffline`); that pre-existing iOS/Android drift was fixed as a follow-up once the LaLiga banner surfaced it |
| Analytics | `laliga_blocking_banner_tapped`, `laliga_blocking_link_tapped` (both platforms, no props) | same |
| License header | GPL-v3 (this repo's convention — differs from InterSego's source-available header) | same |
| Xcode project registration | None needed — this project uses `PBXFileSystemSynchronizedRootGroup`, so new files under `Services/`/`Views/` are picked up automatically | N/A (Gradle compiles all sources under `src/`) |

Differences from the InterSego original, and why:
- FarmaciasDeGuardia's `ScheduleSyncStatus` was already a proper `ObservableObject`/Compose
  state singleton shared by four screens, so the new boolean was added there directly instead
  of introducing a second parallel status object and manually polling it (InterSego predates
  that pattern and threads state through `@State` + explicit polling after a `Task` instead).
- The hook point is a shared `updateSyncStatus` function called after *every* sync attempt
  (single-location or batched preload), rather than being inlined into the manifest-fetch
  method itself — cleaner in this codebase since that function already exists as the single
  place sync outcomes get turned into UI state and analytics events.
