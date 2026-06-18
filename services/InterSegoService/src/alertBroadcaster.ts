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
  deleteDevice,
  getAllAlerts,
  getAllDevices,
  markAlertBroadcastSent,
  pruneStaleDevices,
} from './db/index.js';
import type { ServiceAlert } from './types.js';

const SEVERITY_RANK: Record<string, number> = { info: 1, warning: 2, critical: 3 };

function meetsThreshold(alertSeverity: string, deviceMinSeverity: string): boolean {
  if (deviceMinSeverity === 'none') return false;
  return (SEVERITY_RANK[alertSeverity] ?? 0) >= (SEVERITY_RANK[deviceMinSeverity] ?? 0);
}

let apnsBaseConfig: { keyPath: string; keyId: string; teamId: string; bundleId: string };
let ticking = false;

export function initAlertBroadcaster(): void {
  apnsBaseConfig = {
    keyPath:  process.env.APNS_KEY_PATH!,
    keyId:    process.env.APNS_KEY_ID!,
    teamId:   process.env.APNS_TEAM_ID!,
    bundleId: process.env.APNS_BUNDLE_ID!,
  };

  broadcastTick();
  setInterval(broadcastTick, 5 * 60_000);
}

async function broadcastTick(): Promise<void> {
  if (ticking) return;
  ticking = true;
  try {
    await broadcastTickInner();
  } finally {
    ticking = false;
  }
}

async function broadcastTickInner(): Promise<void> {
  const now = new Date();
  const spainHour = parseInt(
    new Intl.DateTimeFormat('en-US', {
      timeZone: 'Europe/Madrid',
      hour: 'numeric',
      hour12: false,
    }).format(now),
    10,
  );

  let alerts: ServiceAlert[];
  try {
    alerts = await getAllAlerts();
  } catch (err) {
    console.error('alertBroadcaster: failed to read alerts', err);
    return;
  }

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

  const allDevices = await getAllDevices();

  for (const alert of pending) {
    const iosDevices = allDevices.filter(
      (d) => d.platform === 'ios' && meetsThreshold(alert.severity, d.minSeverity),
    );
    const iosProductionTokens = iosDevices.filter((d) => d.environment !== 'sandbox').map((d) => d.token);
    const iosSandboxTokens    = iosDevices.filter((d) => d.environment === 'sandbox').map((d) => d.token);
    const androidTokens = allDevices
      .filter((d) => d.platform === 'android' && meetsThreshold(alert.severity, d.minSeverity))
      .map((d) => d.token);
    console.log(`alertBroadcaster: broadcasting alert "${alert.id}" — iOS production=${iosProductionTokens.length} sandbox=${iosSandboxTokens.length} android=${androidTokens.length}`);
    if (iosProductionTokens.length > 0) await broadcastToIos(alert, iosProductionTokens, 'production');
    if (iosSandboxTokens.length > 0)    await broadcastToIos(alert, iosSandboxTokens, 'sandbox');
    await broadcastToAndroid(alert, androidTokens);
    await markAlertBroadcastSent(alert.id);
  }

  // Weekly stale-device prune (runs on every tick but the DB op is cheap)
  await pruneStaleDevices(90).catch((err) =>
    console.error('alertBroadcaster: pruneStaleDevices failed', err),
  );
}

async function broadcastToIos(alert: ServiceAlert, tokens: string[], environment: 'sandbox' | 'production'): Promise<void> {
  const production = environment === 'production';
  console.log(`alertBroadcaster: sending to APNs ${environment} endpoint (production=${production}) for ${tokens.length} token(s)`);
  const provider = new ApnsProvider({ ...apnsBaseConfig, production });
  for (const token of tokens) {
    const result = await provider.send(
      {
        title:  alert.title,
        body:   alert.message,
        sound:  'default',
        expiry: Math.floor(Date.now() / 1000) + 4 * 3600,
      },
      token,
    );
    if (result.failed.length === 0) {
      console.log(`alertBroadcaster: APNs ${environment} delivery OK for ${token.slice(0, 8)}…`);
    }
    for (const failure of result.failed) {
      if (failure.reason === 'BadDeviceToken' || failure.reason === 'Unregistered') {
        console.warn(`alertBroadcaster: removing stale iOS token ${token.slice(0, 8)}… (${failure.reason})`);
        await deleteDevice(failure.device);
      } else {
        console.error(`alertBroadcaster: APNs ${environment} error for ${token.slice(0, 8)}…: ${failure.reason}`);
      }
    }
  }
}

async function broadcastToAndroid(alert: ServiceAlert, tokens: string[]): Promise<void> {
  for (const token of tokens) {
    const result = await sendFcm({ title: alert.title, body: alert.message }, token);
    if (result.failed) {
      if (result.reason === 'UNREGISTERED' || result.reason === 'INVALID_ARGUMENT') {
        await deleteDevice(token);
      } else {
        console.error(`alertBroadcaster: FCM error for ${token.slice(0, 12)}…: ${result.reason}`);
      }
    }
  }
}
