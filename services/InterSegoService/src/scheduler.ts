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

import { Provider, Notification } from 'apn';
import {
  getAllReminders, deleteReminder,
  deleteRemindersForToken, updateReminderNextFireAt,
} from './db/index.js';
import { nextOccurrence } from './reminderLogic.js';
import type { DeviceReminder } from './types.js';

let provider: Provider;

export function initScheduler(): void {
  provider = new Provider({
    token: {
      key: process.env.APNS_KEY_PATH!,
      keyId: process.env.APNS_KEY_ID!,
      teamId: process.env.APNS_TEAM_ID!,
    },
    production: process.env.APNS_PRODUCTION === 'true',
  });

  tick();
  setInterval(tick, 60_000);
}

async function tick(): Promise<void> {
  const now = new Date();
  const windowEnd = new Date(now.getTime() + 60_000);

  let reminders: DeviceReminder[];
  try {
    reminders = await getAllReminders();
  } catch (err) {
    console.error('scheduler: failed to read reminders', err);
    return;
  }

  for (const reminder of reminders) {
    try {
      await processReminder(reminder, now, windowEnd);
    } catch (err) {
      console.error(`scheduler: error processing reminder ${reminder.id}`, err);
    }
  }
}

async function processReminder(reminder: DeviceReminder, now: Date, windowEnd: Date): Promise<void> {
  // Compute nextFireAt if missing or null
  if (!reminder.nextFireAt) {
    const next = nextOccurrence(
      reminder.departureHour, reminder.departureMinute, reminder.leadMinutes,
      reminder.dayType, reminder.seasonalAvailability, now,
    );
    if (!next) return;
    await updateReminderNextFireAt(reminder.id, next.toISOString());
    reminder.nextFireAt = next.toISOString();
  }

  const fireTime = new Date(reminder.nextFireAt);
  if (fireTime < now || fireTime >= windowEnd) return;

  await sendPush(reminder);

  if (reminder.isDaily) {
    const next = nextOccurrence(
      reminder.departureHour, reminder.departureMinute, reminder.leadMinutes,
      reminder.dayType, reminder.seasonalAvailability, fireTime,
    );
    await updateReminderNextFireAt(reminder.id, next?.toISOString() ?? null);
  } else {
    await deleteReminder(reminder.id);
  }
}

async function sendPush(reminder: DeviceReminder): Promise<void> {
  const note = new Notification();
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
      console.warn(`scheduler: removing stale token for reminder ${reminder.id} (${reason})`);
      await deleteRemindersForToken(reminder.deviceToken);
    } else if (reason) {
      console.error(`scheduler: APNs error for reminder ${reminder.id}: ${reason}`);
    }
  }
}

function buildBody(r: DeviceReminder): string {
  const time = `${String(r.departureHour).padStart(2, '0')}:${String(r.departureMinute).padStart(2, '0')}`;
  return `Sale en ${r.leadMinutes} min — ${time}`;
}
