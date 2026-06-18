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

import { ApnsProvider } from './apns.js';
import { sendFcm } from './fcm.js';
import {
  getAllReminders, deleteReminder,
  deleteRemindersForToken, updateReminderNextFireAt,
} from './db/index.js';
import { nextOccurrence } from './reminderLogic.js';
import type { DeviceReminder } from './types.js';

type ApnsConfig = { keyPath: string; keyId: string; teamId: string; bundleId: string };
let apnsProductionConfig: ApnsConfig;
let apnsSandboxConfig: ApnsConfig;

export function initScheduler(): void {
  const shared = { teamId: process.env.APNS_TEAM_ID!, bundleId: process.env.APNS_BUNDLE_ID! };
  apnsProductionConfig = { ...shared, keyPath: process.env.APNS_KEY_PATH!, keyId: process.env.APNS_KEY_ID! };
  apnsSandboxConfig    = {
    ...shared,
    keyPath: process.env.APNS_SANDBOX_KEY_PATH ?? process.env.APNS_KEY_PATH!,
    keyId:   process.env.APNS_SANDBOX_KEY_ID   ?? process.env.APNS_KEY_ID!,
  };

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
  const notification = {
    title: `Línea ${reminder.routeNumber} · ${reminder.stopName}`,
    body: buildBody(reminder),
  };

  if (reminder.platform === 'android') {
    const result = await sendFcm(notification, reminder.deviceToken);
    if (result.failed) {
      if (result.reason === 'UNREGISTERED' || result.reason === 'INVALID_ARGUMENT') {
        console.warn(`scheduler: removing stale FCM token for reminder ${reminder.id} (${result.reason})`);
        await deleteRemindersForToken(reminder.deviceToken);
      } else {
        console.error(`scheduler: FCM error for reminder ${reminder.id}: ${result.reason}`);
      }
    }
    return;
  }

  // iOS — APNs
  const environment = reminder.environment ?? 'production';
  const config = environment === 'production' ? apnsProductionConfig : apnsSandboxConfig;
  const provider = new ApnsProvider({ ...config, production: environment === 'production' });
  const result = await provider.send(
    {
      title: notification.title,
      body: notification.body,
      sound: 'default',
      expiry: Math.floor(Date.now() / 1000) + 3600,
    },
    reminder.deviceToken,
  );

  for (const failure of result.failed) {
    if (failure.reason === 'BadDeviceToken' || failure.reason === 'Unregistered') {
      console.warn(`scheduler: removing stale APNs token for reminder ${reminder.id} (${failure.reason})`);
      await deleteRemindersForToken(reminder.deviceToken);
    } else {
      console.error(`scheduler: APNs error for reminder ${reminder.id}: ${failure.reason}`);
    }
  }
}

function buildBody(r: DeviceReminder): string {
  const time = `${String(r.departureHour).padStart(2, '0')}:${String(r.departureMinute).padStart(2, '0')}`;
  return `Sale en ${r.leadMinutes} min — ${time}`;
}
