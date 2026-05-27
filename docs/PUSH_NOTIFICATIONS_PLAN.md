# Push Notifications Plan

## Problem Statement

### iOS

The current iOS reminder system pre-schedules up to 7 `UNNotificationRequest`s per daily
reminder (a rolling 7-day batch). This causes two problems:

1. **64-notification cap** — iOS limits each app to 64 pending notifications. With multiple
   daily reminders the quota fills up. When it overflows, one-off reminder scheduling throws
   a system error that surfaces as "Error al programar el recordatorio" — even when the user
   has notifications fully enabled. Daily batch failures are silently swallowed (`try?`), so
   the user doesn't know those reminders are broken either.

2. **7-day expiry** — the rolling batch is replenished in `replenishDailyReminders()`, which
   is only called on app launch. If the user doesn't open the app for 7+ days, the batch runs
   out and daily reminders stop firing.

### Android

Android uses `AlarmManager.setAndAllowWhileIdle()` with a self-rescheduling chain inside
`ReminderBroadcastReceiver`. Each daily reminder holds exactly one pending alarm at a time,
which reschedules itself for tomorrow on every fire. This means:

- No equivalent of the iOS 64-notification cap — one alarm per reminder at all times
- Daily reminders don't expire — the chain continues indefinitely
- The main Android risk is OEM battery optimization (Xiaomi, OPPO, Huawei, etc.) aggressively
  killing background alarms — a hardware/OS fragmentation issue, not a code bug

**Conclusion:** the reported "Error al programar el recordatorio" bug is iOS-only. Android's
reminder architecture is sound and requires no changes as part of this plan. The server-side
push system is implemented for iOS only.

---

## Solution: Server-Side Push Notifications (iOS only)

The server (`services/InterSegoService`) sends push notifications to iOS devices at the right
moment via **APNs directly** — no Firebase, no GCP, fully self-hosted. The Raspberry Pi server
sends HTTP/2 requests to `api.push.apple.com` using a `.p8` auth key from the Apple Developer
portal.

Android keeps the existing `AlarmManager` approach unchanged.

### Why direct APNs instead of Firebase

- No Firebase project or GCP account required
- The `.p8` auth key never expires and covers all apps under the same Apple Developer team
- The `apn` npm package handles HTTP/2 connection management and JWT signing
- The iOS app needs no additional SDK — `registerForRemoteNotifications()` is a built-in
  iOS system call

---

## Architecture Overview

```
iOS device
  │  registers APNs token + reminder prefs  (POST /reminders)
  ▼
InterSegoService (Raspberry Pi)
  │  stores preferences in lowdb  (data/reminders.json)
  │  scheduler runs every minute
  │  computes nextOccurrence() in Spain timezone (Europe/Madrid)
  │  sends HTTP/2 request to api.push.apple.com
  ▼
APNs (Apple's servers)
  │  delivers to device
  ▼
iOS device shows notification (app does not need to be open)

Android device — unchanged, uses AlarmManager chain as before
```

---

## APNs Auth Key Setup (one-time)

1. Go to [developer.apple.com](https://developer.apple.com) → Certificates, IDs & Profiles → Keys
2. Create a new key, enable **Apple Push Notifications service (APNs)**
3. Download the `.p8` file — **this is the only time you can download it**, save it securely
4. Note the **Key ID** shown on the key detail page
5. Note your **Team ID** (top-right of the Developer portal, 10-character alphanumeric)

Store the `.p8` file on the Raspberry Pi (outside the repo). The key does not expire.

---

## Codebase Context

### Server (`services/InterSegoService/`)

**Framework:** Fastify v5 (not Express). Routes are registered via `app.register(routeFunction)`.
Auth uses a `preHandler: requireApiKey` option on each route. All files use **ESM modules**
(`"type": "module"` in package.json) — imports must use `.js` extensions.

**Key files:**
- `src/index.ts` — app entry point; registers routes; validates required env vars on boot
- `src/types.ts` — all shared TypeScript interfaces including `DbSchema` (the lowdb schema)
- `src/db/index.ts` — lowdb setup; all DB operations go through a serial write queue to prevent races
- `src/middleware/auth.ts` — `requireApiKey` and `requireReloadKey` preHandlers
- `src/routes/boardings.ts` — example of a complete route module (POST + GET pattern)

**DB pattern:** `DbSchema` in `types.ts` is the single source of truth for the lowdb shape.
Adding a new collection means:
1. Adding the interface to `types.ts`
2. Adding the field to `DbSchema`
3. Adding it to `defaultData` in `db/index.ts`
4. Adding CRUD helpers in `db/index.ts` following the serialized queue pattern

**Env var validation:** `src/index.ts` checks `API_KEY` and `RELOAD_KEY` at startup and exits
if missing. Any new required env vars (APNs keys) must be added to the same startup block.

**Data directory:** `data/reminders.json` (alongside `data/boardings.json`). Both are gitignored.

### iOS (`iOS/InterSego/`)

**`AppDelegate` location:** There is no separate `AppDelegate.swift` file. The class is defined
at the **bottom of `iOS/InterSego/InterSegoApp.swift`** (around line 729). This is where APNs
token callbacks must be added.

**`@main` struct:** `InterSegoApp` (also in `InterSegoApp.swift`) uses
`@UIApplicationDelegateAdaptor(AppDelegate.self)` to wire up the delegate.

**`ReminderService`:** `iOS/InterSego/Services/ReminderService.swift` — Swift actor managing
the `_reminders` array, local `UNNotificationRequest` scheduling, and UserDefaults persistence.

---

## Server Changes

### New dependency

```bash
npm install apn
```

> `apn` v3.x is ESM-compatible. No additional type package needed — it ships its own types.

### New environment variables (`.env` and `.env.example`)

```
APNS_KEY_PATH=./apns_key.p8
APNS_KEY_ID=XXXXXXXXXX
APNS_TEAM_ID=XXXXXXXXXX
APNS_BUNDLE_ID=com.github.bfollon.intersego
APNS_PRODUCTION=false   # set to true for App Store / TestFlight builds
```

These must also be added to the startup validation block in `src/index.ts` (alongside the
existing `API_KEY` / `RELOAD_KEY` checks).

### `src/types.ts` — additions

```typescript
export type DayType =
  | "weekday" | "saturday" | "sunday" | "weekend" | "holiday";

export type SeasonalAvailability =
  | "YEAR_ROUND" | "SCHOOL_ONLY" | "SUMMER_ONLY"
  | "JUNE_TO_SEPT_ONLY" | "MON_FRI_ONLY" | "FRI_ONLY";

export interface DeviceReminder {
  id: string;                          // UUID, server-generated
  deviceToken: string;                 // APNs hex token from iOS
  routeId: string;
  routeNumber: string;
  stopId: string;
  stopName: string;
  direction: string;
  departureHour: number;
  departureMinute: number;
  leadMinutes: number;
  isDaily: boolean;
  dayType: DayType | null;
  seasonalAvailability: SeasonalAvailability | null;
  createdAt: string;                   // ISO 8601
  nextFireAt: string | null;           // ISO 8601, computed/updated by scheduler
}

// Extend DbSchema:
export interface DbSchema {
  boardings: BoardingEvent[];
  reminders: DeviceReminder[];         // add this line
}
```

### `src/db/index.ts` — additions

Update `defaultData`:
```typescript
const defaultData: DbSchema = { boardings: [], reminders: [] };
```

Add CRUD helpers following the existing serialized queue pattern:

```typescript
export function appendReminder(reminder: DeviceReminder): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.reminders.push(reminder);
    await db.write();
  });
}

export function getRemindersForToken(token: string): Promise<DeviceReminder[]> {
  return serialize(async () => {
    await db.read();
    return db.data.reminders.filter((r) => r.deviceToken === token);
  });
}

export function deleteReminder(id: string): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.reminders = db.data.reminders.filter((r) => r.id !== id);
    await db.write();
  });
}

export function deleteRemindersForToken(token: string): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.reminders = db.data.reminders.filter((r) => r.deviceToken !== token);
    await db.write();
  });
}

export function updateReminderToken(oldToken: string, newToken: string): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.reminders.forEach((r) => {
      if (r.deviceToken === oldToken) r.deviceToken = newToken;
    });
    await db.write();
  });
}

export function getAllReminders(): Promise<DeviceReminder[]> {
  return serialize(async () => {
    await db.read();
    return [...db.data.reminders];
  });
}

export function updateReminderNextFireAt(id: string, nextFireAt: string | null): Promise<void> {
  return serialize(async () => {
    await db.read();
    const r = db.data.reminders.find((r) => r.id === id);
    if (r) r.nextFireAt = nextFireAt;
    await db.write();
  });
}
```

### `src/reminderLogic.ts` — new file

Port of the Swift/Kotlin `nextOccurrence` + seasonal availability logic. **Important:** all
date computation must use Spain timezone (`Europe/Madrid`), not the server's local timezone
(the Raspberry Pi may be configured as UTC). Use `Intl.DateTimeFormat` to extract weekday and
month in the correct timezone rather than `Date` methods which return UTC or system-local
values.

```typescript
import type { DayType, SeasonalAvailability } from './types.js';

const SPAIN_TZ = 'Europe/Madrid';

// JS getDay()-equivalent but in Spain timezone: 0=Sun, 1=Mon, …, 6=Sat
function weekdayInSpain(date: Date): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: SPAIN_TZ,
    weekday: 'short',
  }).formatToParts(date);
  const day = parts.find((p) => p.type === 'weekday')?.value;
  return ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].indexOf(day ?? 'Mon');
}

function monthInSpain(date: Date): number {
  return parseInt(
    new Intl.DateTimeFormat('en-US', { timeZone: SPAIN_TZ, month: 'numeric' })
      .format(date),
    10,
  );
}

// Build a Date representing YYYY-MM-DD HH:MM:00 in Spain timezone
function fireTimeInSpain(baseDate: Date, daysAhead: number, hour: number, minute: number): Date {
  // Get the Spain-local date parts for baseDate + daysAhead days
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: SPAIN_TZ,
    year: 'numeric', month: '2-digit', day: '2-digit',
  });
  const shifted = new Date(baseDate);
  shifted.setUTCDate(shifted.getUTCDate() + daysAhead);
  const dateStr = formatter.format(shifted); // "YYYY-MM-DD"

  // Build an ISO string interpreted as Spain local time
  const pad = (n: number) => String(n).padStart(2, '0');
  const localStr = `${dateStr}T${pad(hour)}:${pad(minute)}:00`;

  // Convert Spain local → UTC via the Intl API
  const utcMs = new Date(
    new Date(localStr + 'Z').getTime() -
    getSpainOffsetMs(new Date(localStr + 'Z'))
  );
  // Simpler: just use Date.parse with a hack-free approach
  // Parse in Spain tz by using a formatted UTC-equivalent
  return toUTCFromSpainLocal(dateStr, hour, minute);
}

// Converts a Spain-local YYYY-MM-DD + HH:MM to a UTC Date
function toUTCFromSpainLocal(dateStr: string, hour: number, minute: number): Date {
  const pad = (n: number) => String(n).padStart(2, '0');
  // Use a temporary date to probe the Spain offset on that specific day (handles DST)
  const probe = new Date(`${dateStr}T12:00:00Z`);
  const spainHour = parseInt(
    new Intl.DateTimeFormat('en-US', { timeZone: SPAIN_TZ, hour: 'numeric', hour12: false })
      .format(probe),
    10,
  );
  const utcHour = 12;
  const offsetHours = spainHour - utcHour; // e.g. +1 or +2
  const localMs = new Date(`${dateStr}T${pad(hour)}:${pad(minute)}:00Z`).getTime();
  return new Date(localMs - offsetHours * 3600 * 1000);
}

function dayTypeMatches(dayType: DayType, jsWeekday: number): boolean {
  switch (dayType) {
    case 'weekday':  return jsWeekday >= 1 && jsWeekday <= 5;
    case 'saturday': return jsWeekday === 6;
    case 'sunday':   return jsWeekday === 0;
    case 'weekend':  return jsWeekday === 0 || jsWeekday === 6;
    case 'holiday':  return jsWeekday === 0;
  }
}

const SUMMER_MONTHS = new Set([7, 8]);
const JUNE_TO_SEPT_MONTHS = new Set([6, 7, 8, 9]);

function runsIn(seasonal: SeasonalAvailability, month: number, jsWeekday: number): boolean {
  switch (seasonal) {
    case 'YEAR_ROUND':        return true;
    case 'SUMMER_ONLY':       return SUMMER_MONTHS.has(month);
    case 'JUNE_TO_SEPT_ONLY': return JUNE_TO_SEPT_MONTHS.has(month);
    case 'SCHOOL_ONLY':       return !SUMMER_MONTHS.has(month);
    case 'MON_FRI_ONLY':      return jsWeekday === 1 || jsWeekday === 5;
    case 'FRI_ONLY':          return jsWeekday === 5;
  }
}

export function nextOccurrence(
  hour: number,
  minute: number,
  leadMins: number,
  dayType: DayType | null,
  seasonal: SeasonalAvailability | null,
  from: Date = new Date(),
): Date | null {
  const fireHour = Math.floor((hour * 60 + minute - leadMins) / 60);
  const fireMinute = (hour * 60 + minute - leadMins) % 60;
  if (hour * 60 + minute - leadMins < 0) return null;

  for (let daysAhead = 0; daysAhead < 30; daysAhead++) {
    const candidate = toUTCFromSpainLocal(
      spainDateString(from, daysAhead),
      fireHour,
      fireMinute,
    );
    const jsWeekday = weekdayInSpain(candidate);
    const month = monthInSpain(candidate);

    if (dayType && !dayTypeMatches(dayType, jsWeekday)) continue;
    if (seasonal && !runsIn(seasonal, month, jsWeekday)) continue;
    if (candidate <= from) continue;

    return candidate;
  }
  return null;
}

// Returns "YYYY-MM-DD" in Spain timezone for (now + daysAhead)
function spainDateString(base: Date, daysAhead: number): string {
  const shifted = new Date(base.getTime() + daysAhead * 86400 * 1000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: SPAIN_TZ }).format(shifted);
}
```

> **Note:** the timezone math above handles DST (Spain uses UTC+1 in winter, UTC+2 in summer).
> If the implementation feels fragile, a simpler alternative is to add the `luxon` package
> (`npm install luxon`) which handles Spain-local date arithmetic cleanly:
> ```typescript
> import { DateTime } from 'luxon';
> const candidate = DateTime.fromObject(
>   { year, month, day, hour: fireHour, minute: fireMinute },
>   { zone: 'Europe/Madrid' }
> ).toJSDate();
> ```
> The `luxon` approach is recommended if the vanilla Intl implementation proves error-prone.

### `src/routes/reminders.ts` — new file

```typescript
import { randomUUID } from 'crypto';
import type { FastifyInstance } from 'fastify';
import apn from 'apn';
import {
  appendReminder, deleteReminder, deleteRemindersForToken,
  getRemindersForToken, updateReminderToken,
} from '../db/index.js';
import { requireApiKey } from '../middleware/auth.js';
import type { DeviceReminder, PostReminderBody } from '../types.js';

export async function remindersRoutes(app: FastifyInstance): Promise<void> {
  app.post<{ Body: PostReminderBody }>(
    '/reminders',
    { preHandler: requireApiKey },
    async (request, reply) => {
      const reminder: DeviceReminder = {
        id: randomUUID(),
        createdAt: new Date().toISOString(),
        nextFireAt: null,
        ...request.body,
      };
      await appendReminder(reminder);
      return reply.status(201).send({ id: reminder.id });
    },
  );

  app.delete<{ Params: { id: string } }>(
    '/reminders/:id',
    { preHandler: requireApiKey },
    async (request, reply) => {
      await deleteReminder(request.params.id);
      return reply.status(204).send();
    },
  );

  app.delete<{ Params: { token: string } }>(
    '/reminders/device/:token',
    { preHandler: requireApiKey },
    async (request, reply) => {
      await deleteRemindersForToken(request.params.token);
      return reply.status(204).send();
    },
  );

  app.get<{ Params: { token: string } }>(
    '/reminders/device/:token',
    { preHandler: requireApiKey },
    async (request, reply) => {
      const reminders = await getRemindersForToken(request.params.token);
      return reply.send(reminders);
    },
  );

  app.put<{ Body: { oldToken: string; newToken: string } }>(
    '/reminders/token',
    { preHandler: requireApiKey },
    async (request, reply) => {
      await updateReminderToken(request.body.oldToken, request.body.newToken);
      return reply.status(204).send();
    },
  );
}
```

### `src/scheduler.ts` — new file

```typescript
import apn from 'apn';
import {
  getAllReminders, deleteReminder,
  deleteRemindersForToken, updateReminderNextFireAt,
} from './db/index.js';
import { nextOccurrence } from './reminderLogic.js';
import type { DeviceReminder } from './types.js';

let provider: apn.Provider;

export function initScheduler(): void {
  provider = new apn.Provider({
    token: {
      key: process.env.APNS_KEY_PATH!,
      keyId: process.env.APNS_KEY_ID!,
      teamId: process.env.APNS_TEAM_ID!,
    },
    production: process.env.APNS_PRODUCTION === 'true',
  });

  // Run once immediately, then every 60 seconds
  tick();
  setInterval(tick, 60_000);
}

async function tick(): Promise<void> {
  const now = new Date();
  const windowEnd = new Date(now.getTime() + 60_000);
  const reminders = await getAllReminders();

  for (const reminder of reminders) {
    // Compute nextFireAt if missing
    if (!reminder.nextFireAt) {
      const next = nextOccurrence(
        reminder.departureHour, reminder.departureMinute, reminder.leadMinutes,
        reminder.dayType, reminder.seasonalAvailability, now,
      );
      if (!next) continue;
      await updateReminderNextFireAt(reminder.id, next.toISOString());
      reminder.nextFireAt = next.toISOString();
    }

    const fireTime = new Date(reminder.nextFireAt);
    if (fireTime >= now && fireTime < windowEnd) {
      await sendPush(reminder);

      if (reminder.isDaily) {
        // Advance to next occurrence
        const next = nextOccurrence(
          reminder.departureHour, reminder.departureMinute, reminder.leadMinutes,
          reminder.dayType, reminder.seasonalAvailability, fireTime,
        );
        await updateReminderNextFireAt(reminder.id, next?.toISOString() ?? null);
      } else {
        await deleteReminder(reminder.id);
      }
    }
  }
}

async function sendPush(reminder: DeviceReminder): Promise<void> {
  const note = new apn.Notification();
  note.expiry = Math.floor(Date.now() / 1000) + 3600;
  note.sound = 'default';
  note.alert = {
    title: `Línea ${reminder.routeNumber} · ${reminder.stopName}`,
    body: buildBody(reminder),
  };
  note.topic = process.env.APNS_BUNDLE_ID!;

  const result = await provider.send(note, reminder.deviceToken);
  for (const failure of result.failed) {
    const reason = failure.response?.reason;
    if (reason === 'BadDeviceToken' || reason === 'Unregistered') {
      await deleteRemindersForToken(reminder.deviceToken);
    }
  }
}

function buildBody(r: DeviceReminder): string {
  const time = `${String(r.departureHour).padStart(2, '0')}:${String(r.departureMinute).padStart(2, '0')}`;
  return `Sale en ${r.leadMinutes} min — ${time}`;
}
```

### `src/index.ts` — additions

Register the new route and start the scheduler:

```typescript
import { remindersRoutes } from './routes/reminders.js';  // add
import { initScheduler } from './scheduler.js';            // add

app.register(remindersRoutes);  // alongside existing app.register calls

// In the start() function, add APNs env var checks:
const required = ['API_KEY', 'RELOAD_KEY', 'APNS_KEY_PATH', 'APNS_KEY_ID', 'APNS_TEAM_ID', 'APNS_BUNDLE_ID'];
for (const key of required) {
  if (!process.env[key]) {
    console.error(`Fatal: ${key} environment variable is not set. Refusing to start.`);
    process.exit(1);
  }
}

// After await app.listen(...):
initScheduler();
```

---

## iOS Changes

### 1. `InterSegoApp.swift` — `AppDelegate` (bottom of file, ~line 729)

The `AppDelegate` class already exists. Add two methods alongside `didFinishLaunchingWithOptions`:

```swift
func application(_ application: UIApplication,
                 didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
    let token = deviceToken.map { String(format: "%02x", $0) }.joined()
    Task { await ReminderService.shared.updateDeviceToken(token) }
}

func application(_ application: UIApplication,
                 didFailToRegisterForRemoteNotificationsWithError error: Error) {
    // Remote push unavailable — local scheduling remains as fallback
}
```

### 2. Where `registerForRemoteNotifications()` is called

In `ReminderService.scheduleReminder(...)`, after the permission grant succeeds
(currently the `default: break` branch and after `.notDetermined` grant), add:

```swift
await MainActor.run {
    UIApplication.shared.registerForRemoteNotifications()
}
```

This ensures remote registration happens at the same time the user grants notification
permission, which is the natural moment.

### 3. `ReminderService.swift` — new properties and methods

```swift
private let deviceTokenKey = "apnsDeviceToken"

private var deviceToken: String? {
    get { UserDefaults.standard.string(forKey: deviceTokenKey) }
    set { UserDefaults.standard.set(newValue, forKey: deviceTokenKey) }
}

/// Called by AppDelegate when APNs issues or refreshes a token.
func updateDeviceToken(_ newToken: String) async {
    let old = deviceToken
    deviceToken = newToken
    guard newToken != old else { return }

    if let old {
        // Token rotated — update server records
        _ = try? await serverRequest(method: "PUT", path: "/reminders/token",
                                     body: ["oldToken": old, "newToken": newToken])
    }
    // Re-sync all active reminders to new token
    await syncRemindersToServer()
}

/// POST each active reminder to the server (fire-and-forget, best effort).
private func syncRemindersToServer() async {
    guard let token = deviceToken else { return }
    for reminder in _reminders {
        _ = try? await serverRequest(method: "POST", path: "/reminders",
                                     body: reminder.serverPayload(deviceToken: token))
    }
}
```

In `scheduleReminder(...)` — after the existing local scheduling, add a best-effort server POST:

```swift
if let token = deviceToken {
    let payload = reminder.serverPayload(deviceToken: token)
    _ = try? await serverRequest(method: "POST", path: "/reminders", body: payload)
}
```

In `cancelReminder(...)` — after local cancellation, add a best-effort server DELETE.
The server `id` must be stored on `BusReminder` (add a `serverId: String?` field).

### 4. `BusReminder.swift` — add `serverId` and `serverPayload`

```swift
var serverId: String?   // populated after successful POST /reminders

func serverPayload(deviceToken: String) -> [String: Any] {
    [
        "deviceToken": deviceToken,
        "routeId": routeId,
        "routeNumber": routeNumber,
        "stopId": stopId,
        "stopName": stopName,
        "direction": direction,
        "departureHour": departureHour,
        "departureMinute": departureMinute,
        "leadMinutes": leadMinutes,
        "isDaily": isDaily,
        "dayType": dayType?.rawValue as Any,
        "seasonalAvailability": seasonalAvailability?.rawValue as Any,
    ]
}
```

### 5. App-level network helper

The iOS app already uses `URLSession` for `BoardingService`. Add a small equivalent helper
to `ReminderService` for posting to the server — follow the same pattern as `BoardingService`
(Bearer auth header with `AppConfig.serverAPIKey`, base URL from `AppConfig.serverBaseURL`).

---

## Android Changes

**None.** Android's `AlarmManager` approach is not affected by the iOS bug and is
architecturally sound. The self-rescheduling chain in `ReminderBroadcastReceiver` handles
daily reminders indefinitely without an expiry window or notification count cap.

---

## Migration Strategy

### Phase 1 — Server infrastructure (no app changes yet)
- Add `DeviceReminder` to `types.ts` and extend `DbSchema`
- Update `defaultData` in `db/index.ts` to include `reminders: []`
- Add CRUD helpers to `db/index.ts`
- Write `reminderLogic.ts` (port `nextOccurrence` + seasonal logic, Spain timezone)
- Write `src/routes/reminders.ts`
- Write `src/scheduler.ts`
- Register route + start scheduler in `index.ts`
- Add APNs env vars to `.env.example` and startup validation
- **Test:** start server locally; POST a reminder manually via curl; verify scheduler fires
  a push to a real device (use APNs sandbox, `APNS_PRODUCTION=false`)

### Phase 2 — iOS dual-write
- Add `registerForRemoteNotifications()` call in `ReminderService.scheduleReminder`
- Add APNs callbacks to `AppDelegate` in `InterSegoApp.swift`
- Add `updateDeviceToken` + server POST/DELETE to `ReminderService`
- Local `UNNotificationRequest` scheduling continues unchanged (fallback)
- Validate pushes arrive on a real device

### Phase 3 — Remove local batch scheduling
- Once server push is confirmed reliable, remove `scheduleDailyBatch`,
  `scheduleMissingBatchDays`, and `replenishDailyReminders()`
- The 64-notification cap issue is fully resolved

---

## Privacy Considerations

Reminder preferences (route, stop, time, day type) are low-sensitivity data comparable to
boarding events already stored server-side. The existing consent gate covers this — no new
consent UI is needed. APNs tokens are opaque device identifiers; they are never logged,
never exposed in API responses, and are deleted when invalidated by APNs.

---

## Scalability Note

`lowdb` (JSON file store) is sufficient for the expected user volume. The scheduler does a
full scan every minute — fine at small scale. If the subscriber count grows significantly,
replace `nextFireAt` filtering with a time-sorted index or a lightweight SQLite store.
