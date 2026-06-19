// InterSego Server
// Copyright (C) 2026 Bruno Follon (@bFollon)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

import { Low } from 'lowdb';
import { JSONFile } from 'lowdb/node';
import path from 'path';
import { fileURLToPath } from 'url';
import type { BoardingEvent, DbSchema, DeviceReminder, RegisteredDevice, ServiceAlert } from '../types.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dbPath = path.resolve(__dirname, '../../data/boardings.json');

const adapter = new JSONFile<DbSchema>(dbPath);
const defaultData: DbSchema = { boardings: [], reminders: [], alerts: [], devices: [] };
const db = new Low<DbSchema>(adapter, defaultData);

/**
 * All mutations and reads are serialized through this queue so that
 * concurrent requests cannot interleave a read–mutate–write cycle and
 * silently drop each other's data (last-write-wins race).
 */
let writeQueue: Promise<void> = Promise.resolve();

function serialize<T>(fn: () => Promise<T>): Promise<T> {
  const next = writeQueue.then(fn);
  // Detached tail: swallow errors so the queue never stalls on rejection.
  writeQueue = next.then(
    () => {},
    () => {},
  );
  return next;
}

/** Read the DB file once at startup to surface any I/O errors early. */
export async function initDb(): Promise<void> {
  await db.read();
  // Migration: existing installs have boardings.json without the reminders key.
  if (!db.data.reminders) {
    db.data.reminders = [];
    await db.write();
  }
  // Migration: existing installs may not have the alerts key.
  if (!db.data.alerts) {
    db.data.alerts = [];
    await db.write();
  }
  // Migration: existing installs may not have the devices key.
  if (!db.data.devices) {
    db.data.devices = [];
    await db.write();
  }
  // Migration: backfill minSeverity for devices registered before severity preference was added.
  let devicesMigrated = false;
  db.data.devices.forEach((d) => {
    if (!(d as RegisteredDevice).minSeverity) {
      (d as RegisteredDevice).minSeverity = 'info';
      devicesMigrated = true;
    }
    // Migration: backfill environment for iOS devices registered before per-environment APNs routing.
    if (d.platform === 'ios' && !(d as RegisteredDevice).environment) {
      (d as RegisteredDevice).environment = 'production';
      devicesMigrated = true;
    }
  });
  if (devicesMigrated) await db.write();
  // Migration: backfill environment for iOS reminders registered before per-environment APNs routing.
  let remindersMigrated = false;
  db.data.reminders.forEach((r) => {
    if (r.platform === 'ios' && !(r as DeviceReminder).environment) {
      (r as DeviceReminder).environment = 'production';
      remindersMigrated = true;
    }
  });
  if (remindersMigrated) await db.write();
  // Migration: backfill platform for reminders created before FCM support (all existing are iOS).
  let migrated = false;
  db.data.reminders.forEach((r) => {
    if (!(r as DeviceReminder).platform) {
      (r as DeviceReminder).platform = 'ios';
      migrated = true;
    }
  });
  if (migrated) await db.write();
}

/** Append a boarding event and flush to disk atomically. */
export function appendBoarding(event: BoardingEvent): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.boardings.push(event);
    await db.write();
  });
}

/**
 * Return all non-expired boarding events, pruning expired ones from disk
 * in the same atomic operation.
 */
export function getActiveboardings(): Promise<BoardingEvent[]> {
  return serialize(async () => {
    await db.read();
    const now = new Date().toISOString();
    const active = db.data.boardings.filter((b) => b.expiresAt > now);
    if (active.length !== db.data.boardings.length) {
      db.data.boardings = active;
      await db.write();
    }
    return active;
  });
}

// ---------------------------------------------------------------------------
// Reminders
// ---------------------------------------------------------------------------

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

export function getAllReminders(): Promise<DeviceReminder[]> {
  return serialize(async () => {
    await db.read();
    return [...db.data.reminders];
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

export function updateReminderNextFireAt(id: string, nextFireAt: string | null): Promise<void> {
  return serialize(async () => {
    await db.read();
    const r = db.data.reminders.find((r) => r.id === id);
    if (r) r.nextFireAt = nextFireAt;
    await db.write();
  });
}

export function updateReminderLeadMinutes(id: string, leadMinutes: number): Promise<boolean> {
  return serialize(async () => {
    await db.read();
    const r = db.data.reminders.find((r) => r.id === id);
    if (!r) return false;
    r.leadMinutes = leadMinutes;
    r.nextFireAt = null;
    await db.write();
    return true;
  });
}

// ---------------------------------------------------------------------------
// Alerts
// ---------------------------------------------------------------------------

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

/** Append multiple alerts in a single read-write cycle. */
export function appendAlerts(alerts: ServiceAlert[]): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.alerts.push(...alerts);
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

/** Delete multiple alerts by id in a single read-write cycle. */
export function deleteAlerts(ids: string[]): Promise<void> {
  return serialize(async () => {
    await db.read();
    const idSet = new Set(ids);
    db.data.alerts = db.data.alerts.filter((a) => !idSet.has(a.id));
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

// ---------------------------------------------------------------------------
// Devices
// ---------------------------------------------------------------------------

export function upsertDevice(
  token: string,
  platform: 'ios' | 'android',
  minSeverity: 'none' | 'info' | 'warning' | 'critical' = 'info',
  environment?: 'sandbox' | 'production',
): Promise<void> {
  return serialize(async () => {
    await db.read();
    const existing = db.data.devices.find((d) => d.token === token);
    if (existing) {
      existing.platform = platform;
      existing.minSeverity = minSeverity;
      if (environment) existing.environment = environment;
      existing.updatedAt = new Date().toISOString();
    } else {
      db.data.devices.push({
        token, platform, minSeverity,
        ...(environment ? { environment } : {}),
        updatedAt: new Date().toISOString(),
      });
    }
    await db.write();
  });
}

export function getAllDevices(): Promise<RegisteredDevice[]> {
  return serialize(async () => {
    await db.read();
    return [...db.data.devices];
  });
}

export function deleteDevice(token: string): Promise<void> {
  return serialize(async () => {
    await db.read();
    db.data.devices = db.data.devices.filter((d) => d.token !== token);
    await db.write();
  });
}

/** Remove devices not seen in the last `maxAgeDays` days. */
export function pruneStaleDevices(maxAgeDays = 90): Promise<void> {
  return serialize(async () => {
    await db.read();
    const cutoff = new Date(Date.now() - maxAgeDays * 86_400_000).toISOString();
    db.data.devices = db.data.devices.filter((d) => d.updatedAt >= cutoff);
    await db.write();
  });
}
