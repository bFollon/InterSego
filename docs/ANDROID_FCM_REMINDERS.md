# Android FCM Reminders

Migrates Android reminders from local `AlarmManager` scheduling to server-sent FCM push
notifications — the same architecture iOS uses with APNs. After this is done, the server
is the single scheduler for both platforms.

**This is the prerequisite for `docs/SERVICE_ALERTS.md`**, because that feature reuses the
FCM sender and the `devices` concept built here.

---

## Current State

### iOS (done)

- `AppDelegate` registers for remote notifications at launch.
- `ReminderService` POSTs reminders to the server with the APNs device token.
- Server scheduler fires APNs push at the right time.
- Local `UNNotificationRequest` scheduling is still present as a fallback (Phase 3 of the
  original plan is to remove it — still pending).

### Android (current, to be replaced)

- `ReminderService` schedules `AlarmManager.setAndAllowWhileIdle()` alarms locally.
- `ReminderBroadcastReceiver` fires the notification and reschedules the next alarm for
  daily reminders (self-rescheduling chain).
- Nothing is sent to the server. The server knows nothing about Android reminders.

### Server

- `scheduler.ts` fires APNs for every reminder in the DB, every minute.
- `DeviceReminder` has no `platform` field — it's implicitly iOS-only today.
- No FCM sending capability.

---

## Goal

After this migration:

- Android devices obtain an FCM token via `FirebaseMessagingService`.
- `ReminderService` POSTs reminders to the same `/reminders` endpoint iOS uses, including
  the FCM token and a new `platform: "android"` field.
- The server scheduler sends FCM instead of APNs when `platform === "android"`.
- `AlarmManager` remains as an offline fallback during migration. A follow-up can remove it
  once server push is confirmed reliable.

---

## Codebase Context

### Server

- `src/types.ts` — `DeviceReminder` and `PostReminderBody` need a `platform` field.
- `src/scheduler.ts` — currently calls `provider.send()` (APNs only). Needs a branch for FCM.
- `src/apns.ts` — hand-rolled HTTP/2 APNs client; no changes needed.
- `src/fcm.ts` — does not exist yet; needs to be created.
- New env vars: `FCM_SERVICE_ACCOUNT_PATH`, `FCM_PROJECT_ID`.
- `src/index.ts` startup validation must check these new vars.

### Android

- `ReminderService.kt` — `Context`-based class (not singleton). Manages `_reminders` list,
  schedules via `AlarmManager`, persists to `SharedPreferences`. **Most of the migration work
  is here.**
- `BusReminder.kt` — `@Serializable` data class. Needs a `serverId: String?` field added
  (same as iOS). `alarmRequestCode` and `fireDateMillis` stay during migration (AlarmManager
  fallback); remove in a follow-up when AlarmManager is fully removed.
- `ReminderBroadcastReceiver.kt` — unchanged during migration (keeps the AlarmManager fallback
  working).
- `BuildConfig.SERVER_BASE_URL` / `BuildConfig.SERVER_API_KEY` — already available.
- OkHttp is already a dependency.
- Firebase: **not yet in the project**. See setup steps below.

---

## Server Changes

### `src/types.ts` — add `platform` to reminder types

```typescript
export interface DeviceReminder {
  id: string;
  deviceToken: string;
  platform: 'ios' | 'android';     // add this field
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
  createdAt: string;
  nextFireAt: string | null;
}

export interface PostReminderBody {
  deviceToken: string;
  platform: 'ios' | 'android';     // add this field
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
}
```

### `src/routes/reminders.ts` — accept `platform` in POST body

Add `platform` to the JSON schema for `POST /reminders`:

```typescript
const postReminderSchema = {
  body: {
    type: 'object',
    required: [
      'deviceToken', 'platform', 'routeId', 'routeNumber', 'stopId', 'stopName',
      'direction', 'departureHour', 'departureMinute', 'leadMinutes',
      'isDaily', 'dayType', 'seasonalAvailability',
    ],
    additionalProperties: false,
    properties: {
      deviceToken: { type: 'string', minLength: 1, maxLength: 512 },
      platform:    { type: 'string', enum: ['ios', 'android'] },     // add
      // ... rest unchanged
    },
  },
} as const;
```

### New file: `src/fcm.ts`

FCM HTTP v1 API, one request per token. OAuth2 access token cached for 55 minutes.
No external dependencies — uses Node built-ins only (same philosophy as `apns.ts`).

```typescript
import { readFileSync } from 'node:fs';
import { createSign } from 'node:crypto';
import https from 'node:https';

interface FcmPayload {
  title: string;
  body: string;
}

let cachedToken = '';
let tokenExpiresAt = 0;

async function getFcmAccessToken(): Promise<string> {
  if (Date.now() < tokenExpiresAt) return cachedToken;

  const keyPath = process.env.FCM_SERVICE_ACCOUNT_PATH!;
  const key = JSON.parse(readFileSync(keyPath, 'utf8')) as {
    client_email: string;
    private_key: string;
  };

  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({
    iss: key.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  })).toString('base64url');

  const signer = createSign('RSA-SHA256');
  signer.update(`${header}.${payload}`);
  const sig = signer.sign(key.private_key, 'base64url');
  const jwt = `${header}.${payload}.${sig}`;

  const body = new URLSearchParams({
    grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
    assertion: jwt,
  }).toString();

  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        hostname: 'oauth2.googleapis.com',
        path: '/token',
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => {
          try {
            const parsed = JSON.parse(data) as { access_token?: string; expires_in?: number };
            if (!parsed.access_token) throw new Error(`FCM token exchange failed: ${data}`);
            cachedToken = parsed.access_token;
            // Subtract 60s buffer so we don't use a token just before it expires
            tokenExpiresAt = Date.now() + ((parsed.expires_in ?? 3600) - 60) * 1000;
            resolve(cachedToken);
          } catch (e) { reject(e); }
        });
      },
    );
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

export async function sendFcm(payload: FcmPayload, deviceToken: string): Promise<{ failed: boolean; reason?: string }> {
  let accessToken: string;
  try {
    accessToken = await getFcmAccessToken();
  } catch (err) {
    return { failed: true, reason: String(err) };
  }

  const projectId = process.env.FCM_PROJECT_ID!;
  const body = JSON.stringify({
    message: {
      token: deviceToken,
      notification: { title: payload.title, body: payload.body },
      android: { priority: 'high' },
    },
  });

  return new Promise((resolve) => {
    const req = https.request(
      {
        hostname: 'fcm.googleapis.com',
        path: `/v1/projects/${projectId}/messages:send`,
        method: 'POST',
        headers: {
          'authorization': `Bearer ${accessToken}`,
          'content-type': 'application/json',
          'content-length': Buffer.byteLength(body),
        },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => {
          if (res.statusCode === 200) {
            resolve({ failed: false });
            return;
          }
          let reason = `HTTP ${res.statusCode}`;
          try {
            const parsed = JSON.parse(data) as { error?: { status?: string } };
            if (parsed.error?.status) reason = parsed.error.status;
          } catch { /* body was not JSON */ }
          resolve({ failed: true, reason });
        });
      },
    );
    req.on('error', (err) => resolve({ failed: true, reason: err.message }));
    req.write(body);
    req.end();
  });
}
```

### `src/scheduler.ts` — branch on platform

```typescript
import { sendFcm } from './fcm.js';

// In sendPush():
async function sendPush(reminder: DeviceReminder): Promise<void> {
  const notification = {
    title: `Línea ${reminder.routeNumber} · ${reminder.stopName}`,
    body: buildBody(reminder),
  };

  if (reminder.platform === 'android') {
    const result = await sendFcm(notification, reminder.deviceToken);
    if (result.failed) {
      if (result.reason === 'UNREGISTERED' || result.reason === 'INVALID_ARGUMENT') {
        await deleteRemindersForToken(reminder.deviceToken);
      } else {
        console.error(`scheduler: FCM error for reminder ${reminder.id}: ${result.reason}`);
      }
    }
    return;
  }

  // iOS — APNs (existing path)
  const result = await provider.send(
    { title: notification.title, body: notification.body, sound: 'default', expiry: Math.floor(Date.now() / 1000) + 3600 },
    reminder.deviceToken,
  );
  for (const failure of result.failed) {
    if (failure.reason === 'BadDeviceToken' || failure.reason === 'Unregistered') {
      await deleteRemindersForToken(reminder.deviceToken);
    } else {
      console.error(`scheduler: APNs error for reminder ${reminder.id}: ${failure.reason}`);
    }
  }
}
```

### `src/index.ts` — new env vars

```typescript
const required = [
  'API_KEY', 'RELOAD_KEY',
  'APNS_KEY_PATH', 'APNS_KEY_ID', 'APNS_TEAM_ID', 'APNS_BUNDLE_ID',
  'FCM_SERVICE_ACCOUNT_PATH', 'FCM_PROJECT_ID',    // add
];
```

### `src/db/index.ts` — DB migration

Existing reminders in the DB have no `platform` field. Add a migration in `initDb()`:

```typescript
export async function initDb(): Promise<void> {
  await db.read();
  // Existing migrations...
  if (!db.data.reminders) {
    db.data.reminders = [];
    await db.write();
  }
  // Backfill platform for existing iOS reminders (all existing ones are iOS)
  let migrated = false;
  db.data.reminders.forEach((r) => {
    if (!(r as any).platform) {
      (r as DeviceReminder).platform = 'ios';
      migrated = true;
    }
  });
  if (migrated) await db.write();
}
```

### New env vars (add to `.env` and `.env.example`)

```
FCM_SERVICE_ACCOUNT_PATH=./fcm_service_account.json
FCM_PROJECT_ID=your-firebase-project-id
```

---

## Firebase Setup (one-time manual steps)

### 1. Create Firebase project and Android app

1. Go to [console.firebase.google.com](https://console.firebase.google.com).
2. Create a new project named `InterSego` (or add to an existing one).
3. Add an **Android app**: package name `com.github.bfollon.intersego`.
4. Download `google-services.json` → place at `android/app/google-services.json`.
   > This file is already in `.gitignore`. Do not commit it.
5. In Project Settings → Cloud Messaging, confirm the API is enabled.

### 2. Create FCM service account (for server)

1. Firebase Console → Project Settings → Service Accounts.
2. Click **Generate new private key** → download JSON.
3. Store the JSON on the Raspberry Pi alongside the APNs `.p8` key (outside the repo).
4. Note the **Project ID** (Project Settings → General).

---

## Android Changes

### 1. `android/build.gradle.kts` (top-level) — add Google Services plugin

```kotlin
plugins {
    // existing...
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

### 2. `android/app/build.gradle.kts` — apply plugin + add Firebase dependency

```kotlin
plugins {
    // existing...
    id("com.google.gms.google-services")
}

dependencies {
    // existing...
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
```

### 3. New file: `android/.../services/FcmService.kt`

Full path: `android/app/src/main/java/com/github/bfollon/intersego/services/FcmService.kt`

```kotlin
/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Store locally so ReminderService can read it on next launch/sync
        getSharedPreferences("fcm_prefs", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
        // Re-sync all active reminders to the new token
        ReminderService(applicationContext).syncTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // FCM shows the notification automatically when the app is in background.
        // When the app is in the foreground, we receive it here but don't need
        // to do anything — reminders are not expected to arrive while the user
        // is actively using the app.
    }
}
```

### 4. `AndroidManifest.xml` — register the service

Inside the `<application>` block:

```xml
<service
    android:name=".services.FcmService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

### 5. `BusReminder.kt` — add `serverId` field

```kotlin
@Serializable
data class BusReminder(
    val id: String,
    val routeId: String,
    val routeNumber: String,
    val stopId: String,
    val stopName: String,
    val direction: String,
    val departureHour: Int,
    val departureMinute: Int,
    val leadMinutes: Int,
    val fireDateMillis: Long,
    val alarmRequestCode: Int,
    val seasonalNote: String? = null,
    val isDaily: Boolean = false,
    val seasonalAvailability: SeasonalAvailability? = null,
    val dayType: DayType? = null,
    val serverId: String? = null,    // add: server-assigned UUID after POST /reminders
) {
    // ... existing computed properties unchanged
}
```

### 6. `ReminderService.kt` — add server sync

Add a `SharedPreferences` key for the FCM token and methods to read it, post to the server,
and sync on token rotation.

#### Reading the FCM token

```kotlin
private fun getFcmToken(): String? =
    context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        .getString("fcm_token", null)
```

#### Posting a reminder to the server (fire-and-forget, best effort)

Add this method. Call it at the end of `scheduleReminder()` after `persist()`.

```kotlin
fun postReminderToServer(reminder: BusReminder) {
    val token = getFcmToken() ?: return
    val client = OkHttpClient()
    val body = JSONObject().apply {
        put("deviceToken", token)
        put("platform", "android")
        put("routeId", reminder.routeId)
        put("routeNumber", reminder.routeNumber)
        put("stopId", reminder.stopId)
        put("stopName", reminder.stopName)
        put("direction", reminder.direction)
        put("departureHour", reminder.departureHour)
        put("departureMinute", reminder.departureMinute)
        put("leadMinutes", reminder.leadMinutes)
        put("isDaily", reminder.isDaily)
        put("dayType", reminder.dayType?.name?.lowercase())
        put("seasonalAvailability", reminder.seasonalAvailability?.name)
    }.toString().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("${BuildConfig.SERVER_BASE_URL}/reminders")
        .addHeader("Authorization", "Bearer ${BuildConfig.SERVER_API_KEY}")
        .post(body)
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return
                val serverId = JSONObject(responseBody).optString("id")
                if (serverId.isNotEmpty()) {
                    val idx = _reminders.indexOfFirst { it.id == reminder.id }
                    if (idx >= 0) {
                        _reminders[idx] = _reminders[idx].copy(serverId = serverId)
                        persist()
                    }
                }
            }
        }
    } catch (_: Exception) {
        // Best effort — AlarmManager fallback still fires
    }
}
```

> `postReminderToServer` runs synchronously. Wrap the call site in a background coroutine
> (`CoroutineScope(Dispatchers.IO).launch { postReminderToServer(reminder) }`) so it doesn't
> block the calling thread.

#### Deleting a reminder from the server

Call at the end of `cancelReminder()` after `persist()`.

```kotlin
fun deleteReminderFromServer(serverId: String) {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("${BuildConfig.SERVER_BASE_URL}/reminders/$serverId")
        .addHeader("Authorization", "Bearer ${BuildConfig.SERVER_API_KEY}")
        .delete()
        .build()
    try {
        client.newCall(request).execute().close()
    } catch (_: Exception) { /* best effort */ }
}
```

#### Token rotation sync

Called by `FcmService.onNewToken()` when FCM issues a new token.

```kotlin
fun syncTokenToServer(newToken: String) {
    val oldToken = getFcmToken() ?: return
    if (oldToken == newToken) return

    val client = OkHttpClient()
    val body = JSONObject()
        .put("oldToken", oldToken)
        .put("newToken", newToken)
        .toString()
        .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("${BuildConfig.SERVER_BASE_URL}/reminders/token")
        .addHeader("Authorization", "Bearer ${BuildConfig.SERVER_API_KEY}")
        .put(body)
        .build()
    try {
        client.newCall(request).execute().close()
    } catch (_: Exception) { /* best effort */ }
}
```

#### Where to add the calls

In `scheduleReminder()`, after `persist()`:

```kotlin
val savedReminder = _reminders.last()
CoroutineScope(Dispatchers.IO).launch { postReminderToServer(savedReminder) }
```

In `cancelReminder(id: String)`, after `persist()`:

```kotlin
val serverId = _reminders.find { it.id == id }?.serverId  // capture before removal
_reminders.removeAll { it.id == id }
persist()
serverId?.let { CoroutineScope(Dispatchers.IO).launch { deleteReminderFromServer(it) } }
```

In `cancelReminder(routeId, stopId, direction, hour, minute)`, same pattern — collect all
`serverId` values for the matched reminders before removal, then delete them in background.

### 7. Imports to add in `ReminderService.kt`

```kotlin
import com.github.bfollon.intersego.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
```

---

## What Stays Unchanged

- `ReminderBroadcastReceiver.kt` — untouched. AlarmManager continues as fallback.
- `RemindersScreen.kt` — no UI changes needed.
- The server `reminderLogic.ts` — no changes (timezone logic is correct for both platforms).
- iOS `ReminderService.swift` — no changes.
- `AlarmManager` scheduling in `scheduleAlarm()` / `cancelAlarm()` — keep as-is during migration.

---

## Migration Path

**Phase 1 (this document):** Dual-write. Server receives the reminder and will push FCM.
AlarmManager also fires as a fallback. Users may get two notifications briefly — acceptable
during the transition.

**Phase 2 (follow-up):** Once FCM push is confirmed reliable on real devices, remove:
- `scheduleAlarm()` / `cancelAlarm()` in `ReminderService`
- `ReminderBroadcastReceiver.kt`
- `alarmRequestCode` and `fireDateMillis` from `BusReminder`
- The `BootReceiver` (if it exists) for alarm re-registration after reboot
- The `AlarmManager` permission in `AndroidManifest.xml`

---

## Verification

1. Run the app on a real Android device.
2. Create a reminder for a departure 2 minutes from now.
3. Check the server DB (`data/boardings.json` → `reminders` array) — the reminder should
   appear with `platform: "android"`.
4. The server scheduler should fire FCM 2 minutes later → notification arrives on device.
5. Also verify the AlarmManager fallback fires (two notifications at the same time — expected
   during Phase 1).
6. Cancel the reminder in the app → check the server DB entry is gone.

---

## Feature Tracker Update

When complete, update the root `CLAUDE.md` table:

| Feature | Android | iOS | Notes |
|---|---|---|---|
| Reminders screen | ✅ | ✅ | Android now server-sent FCM (parity with iOS APNs); AlarmManager kept as offline fallback |
