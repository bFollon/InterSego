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
import type { BoardingEvent, DbSchema, DeviceReminder } from '../types.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dbPath = path.resolve(__dirname, '../../data/boardings.json');

const adapter = new JSONFile<DbSchema>(dbPath);
const defaultData: DbSchema = { boardings: [], reminders: [] };
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
