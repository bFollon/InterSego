# Service Alerts

Service Alerts let you warn users about events that disrupt bus service (strikes, detours,
temporary suspensions). The feature has two surfaces:

1. **In-app banner** — a dismissible card at the top of the Landing screen, shown whenever
   there is at least one active alert. Fetched from the server at startup.
2. **Day-start push notification** — sent once per alert, at 08:00 Europe/Madrid on the
   day the alert begins (or immediately if the alert starts mid-day). Sent to every registered
   device.

**Prerequisite:** `docs/ANDROID_FCM_REMINDERS.md` must be implemented first. This document
assumes Firebase is set up, the FCM service account JSON is on the server, `src/fcm.ts`
exists, and `FCM_SERVICE_ACCOUNT_PATH` / `FCM_PROJECT_ID` env vars are configured.

---

## Codebase Context

### Server (`services/InterSegoService/`)

- Framework: **Fastify v5**, ESM modules — all imports need `.js` extensions.
- Auth: `requireApiKey` (app-facing), `requireReloadKey` (admin). Both in `src/middleware/auth.ts`.
  Alert management endpoints use `requireReloadKey` so the key is never bundled into app binaries.
- APNs client: `src/apns.ts` — hand-rolled HTTP/2 client. `ApnsProvider.send()` takes one token.
- Scheduler: `src/scheduler.ts` — reminder tick every 60s. The alert broadcaster is a separate
  interval, not part of the reminder loop.
- DB: lowdb. Adding a collection: interface in `types.ts` → add to `DbSchema` → add to
  `defaultData` → add CRUD helpers in `db/index.ts` via `serialize()`.

### iOS (`iOS/InterSego/`)

- Startup runs in `ContentView.initialize()` — this is where the alert fetch goes.
- `AppConfig.serverBaseURL` / `AppConfig.serverAPIKey` are the network config sources.
- `LandingView` is in `iOS/InterSego/Views/LandingView.swift`.
  - Takes named parameters; add `activeAlerts` + `onDismissAlert` to its signature.
  - The outer `VStack` has a `Spacer()` at the top then the card stack — insert the banner
    between them.

### Android (`android/`)

- Landing UI is in `android/.../ui/screens/LandingScreen.kt`.
- Alert fetch follows the same OkHttp pattern as `BoardingService`.
- The alert data model goes in `android/.../data/` or `android/.../ui/screens/`.

---

## Data Models

### `ServiceAlert`

```typescript
export interface ServiceAlert {
  id: string;                        // UUID, server-generated
  title: string;                     // e.g. "Huelga de transportes"
  message: string;                   // e.g. "Los autobuses pueden tener retrasos hoy."
  severity: 'info' | 'warning' | 'critical';
  affectedRoutes?: string[];         // e.g. ["M1","M3"]; absent means all routes
  startsAt: string;                  // ISO 8601 — when alert becomes active
  endsAt: string;                    // ISO 8601 — when alert expires
  broadcastSent: boolean;            // true after the day-start push has been sent
}
```

### `PostAlertBody`

```typescript
export interface PostAlertBody {
  title: string;
  message: string;
  severity: 'info' | 'warning' | 'critical';
  affectedRoutes?: string[];
  startsAt: string;
  endsAt: string;
}
```

### `DbSchema` additions

```typescript
export interface DbSchema {
  boardings: BoardingEvent[];
  reminders: DeviceReminder[];
  devices: RegisteredDevice[];       // from PUSH_BROADCAST_INFRASTRUCTURE
  alerts: ServiceAlert[];            // add for this feature
}
```

`defaultData` in `db/index.ts`:

```typescript
const defaultData: DbSchema = { boardings: [], reminders: [], devices: [], alerts: [] };
```

---

## Server Changes

### `src/types.ts`

Add `ServiceAlert` and `PostAlertBody` interfaces. Extend `DbSchema` with `alerts`.

### `src/db/index.ts` — alert CRUD helpers

```typescript
// ---- Alerts ----------------------------------------------------------------

export function getActiveAlerts(): Promise<ServiceAlert[]> {
  return serialize(async () => {
    await db.read();
    const now = new Date().toISOString();
    return db.data.alerts.filter((a) => a.startsAt <= now && a.endsAt > now);
  });
}

export function getAllAlerts(): Promise<ServiceAlert[]> {
  return serialize(async () => {
    await db.read();
    return [...db.data.alerts];
  });
}

export function appendAlert(alert: ServiceAlert): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.alerts.push(alert);
    await db.write();
  });
}

export function deleteAlert(id: string): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.alerts = db.data.alerts.filter((a) => a.id !== id);
    await db.write();
  });
}

export function markAlertBroadcastSent(id: string): Promise<void> {
  return serialize(async () => {
    await db.read();
    const a = db.data.alerts.find((a) => a.id === id);
    if (a) a.broadcastSent = true;
    await db.write();
  });
}
```

### New file: `src/routes/alerts.ts`

```typescript
import { randomUUID } from 'crypto';
import type { FastifyInstance } from 'fastify';
import { appendAlert, deleteAlert, getActiveAlerts, getAllAlerts } from '../db/index.js';
import { requireReloadKey } from '../middleware/auth.js';
import type { PostAlertBody, ServiceAlert } from '../types.js';

const SEVERITIES = ['info', 'warning', 'critical'];

const postAlertSchema = {
  body: {
    type: 'object',
    required: ['title', 'message', 'severity', 'startsAt', 'endsAt'],
    additionalProperties: false,
    properties: {
      title:          { type: 'string', minLength: 1, maxLength: 256 },
      message:        { type: 'string', minLength: 1, maxLength: 1024 },
      severity:       { type: 'string', enum: SEVERITIES },
      affectedRoutes: { type: 'array', items: { type: 'string' }, nullable: true },
      startsAt:       { type: 'string' },
      endsAt:         { type: 'string' },
    },
  },
} as const;

export async function alertsRoutes(app: FastifyInstance): Promise<void> {
  // Public — no auth; apps poll this at startup
  app.get('/alerts', async (_request, reply) => {
    return reply.send(await getActiveAlerts());
  });

  // Admin — create an alert
  app.post<{ Body: PostAlertBody }>(
    '/admin/alerts',
    { preHandler: requireReloadKey, schema: postAlertSchema },
    async (request, reply) => {
      const alert: ServiceAlert = {
        id: randomUUID(),
        broadcastSent: false,
        ...request.body,
      };
      await appendAlert(alert);
      return reply.status(201).send({ id: alert.id });
    },
  );

  // Admin — delete an alert
  app.delete<{ Params: { id: string } }>(
    '/admin/alerts/:id',
    { preHandler: requireReloadKey },
    async (request, reply) => {
      await deleteAlert(request.params.id);
      return reply.status(204).send();
    },
  );

  // Admin — list all alerts (active + future + expired)
  app.get(
    '/admin/alerts',
    { preHandler: requireReloadKey },
    async (_request, reply) => {
      return reply.send(await getAllAlerts());
    },
  );
}
```

### New file: `src/alertBroadcaster.ts`

Runs independently of the reminder scheduler. Checks every 5 minutes.
The `broadcastSent` flag on each alert ensures the push fires exactly once.

```typescript
import { ApnsProvider } from './apns.js';
import { sendFcmBroadcast } from './fcm.js';
import {
  getAllAlerts, getAllDevices,
  markAlertBroadcastSent, deleteDevice,
} from './db/index.js';
import type { ServiceAlert } from './types.js';

let apnsProvider: ApnsProvider;

export function initAlertBroadcaster(): void {
  apnsProvider = new ApnsProvider({
    keyPath: process.env.APNS_KEY_PATH!,
    keyId: process.env.APNS_KEY_ID!,
    teamId: process.env.APNS_TEAM_ID!,
    bundleId: process.env.APNS_BUNDLE_ID!,
    production: process.env.APNS_PRODUCTION === 'true',
  });

  broadcastTick();
  setInterval(broadcastTick, 5 * 60_000);
}

async function broadcastTick(): Promise<void> {
  const now = new Date();
  const spainHour = parseInt(
    new Intl.DateTimeFormat('en-US', {
      timeZone: 'Europe/Madrid',
      hour: 'numeric',
      hour12: false,
    }).format(now),
    10,
  );

  const alerts = await getAllAlerts();
  const pending = alerts.filter((a) => {
    if (a.broadcastSent) return false;
    const starts = new Date(a.startsAt);
    const ends = new Date(a.endsAt);
    if (now < starts || now >= ends) return false;
    // Fire in the 08:00 window OR if the alert became active in the last 10 minutes
    const recentlyStarted = now.getTime() - starts.getTime() < 10 * 60_000;
    return spainHour === 8 || recentlyStarted;
  });

  if (pending.length === 0) return;

  const devices = await getAllDevices();
  const iosTokens = devices.filter((d) => d.platform === 'ios').map((d) => d.token);
  const androidTokens = devices.filter((d) => d.platform === 'android').map((d) => d.token);

  for (const alert of pending) {
    await broadcastToIos(alert, iosTokens);
    if (androidTokens.length > 0) await sendFcmBroadcast(alert, androidTokens);
    await markAlertBroadcastSent(alert.id);
  }
}

async function broadcastToIos(alert: ServiceAlert, tokens: string[]): Promise<void> {
  for (const token of tokens) {
    const result = await apnsProvider.send(
      {
        title: alert.title,
        body: alert.message,
        sound: 'default',
        expiry: Math.floor(Date.now() / 1000) + 4 * 3600,
      },
      token,
    );
    for (const failure of result.failed) {
      if (failure.reason === 'BadDeviceToken' || failure.reason === 'Unregistered') {
        await deleteDevice(token);
      } else {
        console.error(`alertBroadcaster: APNs error for ${token.slice(0, 8)}…: ${failure.reason}`);
      }
    }
  }
}
```

### `src/alertBroadcaster.ts` — FCM dependency

`broadcastToAndroid` calls `sendFcm` from `src/fcm.ts`, which is created in
`docs/ANDROID_FCM_REMINDERS.md`. Import it:

```typescript
import { sendFcm } from './fcm.js';
```

And in `broadcastToAndroid`:

```typescript
async function broadcastToAndroid(alert: ServiceAlert, tokens: string[]): Promise<void> {
  for (const token of tokens) {
    const result = await sendFcm({ title: alert.title, body: alert.message }, token);
    if (result.failed) {
      console.error(`alertBroadcaster: FCM error for ${token.slice(0, 12)}…: ${result.reason}`);
    }
  }
}
```

### `src/index.ts` — additions

```typescript
import { alertsRoutes } from './routes/alerts.js';
import { initAlertBroadcaster } from './alertBroadcaster.js';

app.register(alertsRoutes);

// After initScheduler():
initAlertBroadcaster();
```

No new env vars — `FCM_SERVICE_ACCOUNT_PATH` and `FCM_PROJECT_ID` are already required
after the Phase 1 migration.

---

## iOS Changes

### New file: `iOS/InterSego/Services/AlertService.swift`

```swift
/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

struct ServiceAlert: Identifiable, Codable {
    let id: String
    let title: String
    let message: String
    let severity: String              // "info" | "warning" | "critical"
    let affectedRoutes: [String]?
    let startsAt: String
    let endsAt: String
}

actor AlertService {
    static let shared = AlertService()

    func fetchActiveAlerts() async -> [ServiceAlert] {
        guard let url = URL(string: "\(AppConfig.serverBaseURL)/alerts") else { return [] }
        guard let (data, _) = try? await URLSession.shared.data(from: url) else { return [] }
        return (try? JSONDecoder().decode([ServiceAlert].self, from: data)) ?? []
    }
}
```

### `InterSegoApp.swift` — wire into startup

In `ContentView`, add state:

```swift
@State private var activeAlerts: [ServiceAlert] = []
```

In `ContentView.initialize()`, after loading routes (network-gated):

```swift
if NetworkMonitor.shared.isOnline {
    Task { await TimetableCacheService.shared.fetchAllRoutes() }
    Task { await PolylineCacheService.shared.fetchAllPolylines() }
    activeAlerts = await AlertService.shared.fetchActiveAlerts()  // add
}
```

Pass to `LandingView`:

```swift
LandingView(
    ...existing params...,
    activeAlerts: activeAlerts,
    onDismissAlert: { id in activeAlerts.removeAll { $0.id == id } }
)
```

### `LandingView.swift` — alert banner

Add to the parameter list:

```swift
let activeAlerts: [ServiceAlert]
let onDismissAlert: (String) -> Void
```

In `body`, insert above the card stack (between the first `Spacer()` and the `VStack(spacing: 16)`):

```swift
if let alert = activeAlerts.first {
    AlertBanner(alert: alert, onDismiss: { onDismissAlert(alert.id) })
        .padding(.horizontal, 24)
        .padding(.bottom, 4)
}
```

### New private component `AlertBanner` (add to `LandingView.swift`)

```swift
private struct AlertBanner: View {
    let alert: ServiceAlert
    let onDismiss: () -> Void

    private var accentColor: Color {
        switch alert.severity {
        case "critical": return .red
        case "warning":  return .orange
        default:         return .blue
        }
    }

    private var iconName: String {
        switch alert.severity {
        case "critical": return "exclamationmark.triangle.fill"
        case "warning":  return "exclamationmark.circle.fill"
        default:         return "info.circle.fill"
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: iconName)
                .foregroundColor(accentColor)
                .font(.system(size: 18))
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 4) {
                Text(alert.title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                Text(alert.message)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.secondary)
                    .padding(4)
            }
        }
        .padding(14)
        .background(accentColor.opacity(0.1))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(accentColor.opacity(0.3), lineWidth: 1)
        )
    }
}
```

---

## Android Changes

### New data class: `ServiceAlert.kt`

Place in `android/.../data/ServiceAlert.kt` or alongside other data models:

```kotlin
data class ServiceAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: String,              // "info" | "warning" | "critical"
    val affectedRoutes: List<String>?,
    val startsAt: String,
    val endsAt: String,
)
```

### New service: `AlertService.kt`

Follow the same OkHttp pattern as `BoardingService`. Place in `.../services/AlertService.kt`.

```kotlin
object AlertService {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class AlertDto(
        val id: String,
        val title: String,
        val message: String,
        val severity: String,
        val affectedRoutes: List<String>? = null,
        val startsAt: String,
        val endsAt: String,
    )

    fun fetchActiveAlerts(): List<ServiceAlert> {
        val request = Request.Builder()
            .url("${BuildConfig.SERVER_BASE_URL}/alerts")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                json.decodeFromString<List<AlertDto>>(body).map {
                    ServiceAlert(it.id, it.title, it.message, it.severity, it.affectedRoutes, it.startsAt, it.endsAt)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
```

### `LandingScreen.kt` — fetch and display banner

In the `ViewModel` (or wherever startup data loading happens), fetch alerts:

```kotlin
private val _activeAlerts = mutableStateOf<List<ServiceAlert>>(emptyList())
val activeAlerts: State<List<ServiceAlert>> = _activeAlerts

init {
    viewModelScope.launch(Dispatchers.IO) {
        _activeAlerts.value = AlertService.fetchActiveAlerts()
    }
}
```

In `LandingScreen` composable, render the banner above the main card stack:

```kotlin
val alerts by viewModel.activeAlerts

if (alerts.isNotEmpty()) {
    AlertBanner(
        alert = alerts.first(),
        onDismiss = { viewModel.dismissAlert(alerts.first().id) },
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 4.dp),
    )
}
```

### New composable: `AlertBanner`

```kotlin
@Composable
private fun AlertBanner(
    alert: ServiceAlert,
    onDismiss: () -> Void,
    modifier: Modifier = Modifier,
) {
    val color = when (alert.severity) {
        "critical" -> MaterialTheme.colorScheme.error
        "warning"  -> Color(0xFFE65100)
        else       -> MaterialTheme.colorScheme.primary
    }
    val icon = when (alert.severity) {
        "critical", "warning" -> Icons.Default.Warning
        else                  -> Icons.Default.Info
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
```

---

## API Summary

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/alerts` | none | Active alerts (apps poll at startup) |
| `POST` | `/admin/alerts` | `RELOAD_KEY` | Create an alert |
| `DELETE` | `/admin/alerts/:id` | `RELOAD_KEY` | Delete an alert |
| `GET` | `/admin/alerts` | `RELOAD_KEY` | List all alerts (active + future + expired) |
| `POST` | `/admin/alerts/broadcast` | `RELOAD_KEY` | Force an immediate broadcast tick (testing) |

### Timestamps

`startsAt` / `endsAt` accept UTC (`Z`), explicit offset (`+02:00`), or naive local time with no suffix — naive timestamps are interpreted as `Europe/Madrid` and normalised to UTC before storage.

### APNs environment routing

The broadcaster maintains separate APNs keys for sandbox and production:

- `APNS_KEY_PATH` / `APNS_KEY_ID` — production key (App Store builds)
- `APNS_SANDBOX_KEY_PATH` / `APNS_SANDBOX_KEY_ID` — sandbox key (dev/debug builds); falls back to production key if not set

Each device registers with an `environment` field (`sandbox` or `production`) via `POST /device-tokens`. The broadcaster routes to the matching APNs endpoint per device, so both build types coexist on the same server without env var changes. Same per-device routing applies to the reminder scheduler.

---

## Device Registration for Broadcast

The alert broadcaster needs to push to **all users**, not just reminder users. The reminder
system already stores per-user tokens in `DeviceReminder.deviceToken`, but only for users who
have created reminders.

Add a separate `devices` table and `POST /device-tokens` endpoint. Both apps call this at launch.

### Server additions (alongside alert routes)

Add to `types.ts`, `db/index.ts`, and a new `src/routes/devices.ts` — see the full spec in
`docs/ANDROID_FCM_REMINDERS.md` (the same FCM infrastructure doc). The `devices` table and
`POST /device-tokens` endpoint belong in this feature rather than Phase 1, because they are
only needed for broadcast.

**`types.ts`:**

```typescript
export interface RegisteredDevice {
  token: string;
  platform: 'ios' | 'android';
  environment?: 'sandbox' | 'production'; // iOS only; routes to correct APNs endpoint
  minSeverity: 'none' | 'info' | 'warning' | 'critical';
  updatedAt: string;
}
```

Add to `DbSchema`: `devices: RegisteredDevice[]`.

**`db/index.ts`:** add `upsertDevice`, `getAllDevices`, `deleteDevice`, `pruneStaleDevices`
— follow the same `serialize()` queue pattern as all other DB helpers.

**`src/routes/devices.ts`:** `POST /device-tokens` (auth: `requireApiKey`), upserts the token.

Register it in `src/index.ts`.

### iOS — register token at launch

In `AppDelegate.didRegisterForRemoteNotificationsWithDeviceToken`, call the new endpoint
alongside the existing `ReminderService` call:

```swift
Task { await DeviceTokenService.shared.registerToken(token, platform: "ios") }
```

Create `iOS/InterSego/Services/DeviceTokenService.swift` — a simple actor that POSTs to
`/device-tokens` with `Bearer` auth. Fire-and-forget.

### Android — register token at launch

In `FcmService.onNewToken` (already created in Phase 1), also call `POST /device-tokens`
in addition to storing the token locally for reminder sync.

---

## Implementation Order

1. **Server** — types + DB helpers for `alerts` and `devices`, `alerts.ts` route,
   `devices.ts` route, `alertBroadcaster.ts`. Register in `index.ts`.
   Test: `POST /admin/alerts` via curl to create a strike alert, `GET /alerts` to verify
   it appears. Confirm it disappears after `endsAt`.
2. **iOS** — `DeviceTokenService.swift`, `AlertService.swift`, wire both into
   `ContentView.initialize()`, add banner to `LandingView`.
3. **Android** — add `POST /device-tokens` call to `FcmService.onNewToken`, `ServiceAlert.kt`,
   `AlertService.kt`, banner in `LandingScreen`.
4. **Push broadcast** — testable end-to-end once both platforms are registering tokens.
   Create an alert with `startsAt` in the past 10 minutes to trigger the "recently started"
   broadcast path without waiting for 08:00 Madrid.
5. **Update root `CLAUDE.md`** feature tracker.

---

## Feature Tracker Update

When implementation is complete, add to root `CLAUDE.md`:

| Feature | Android | iOS | Notes |
|---|---|---|---|
| Service alerts banner | ✅ | ✅ | Fetched from `GET /alerts` at startup; dismissible per session; severity-coded colour (info=blue, warning=orange, critical=red) |
| Service alert push notification | ✅ | ✅ | Day-start broadcast at 08:00 Madrid via FCM (Android) / APNs (iOS); fires immediately for alerts active within 10 min of creation |
