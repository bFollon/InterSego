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

import { randomUUID } from 'crypto';

// Tracks server-side event counts (e.g. how often each endpoint is called) in
// the same self-hosted Aptabase instance used by the Android/iOS apps, under
// a separate app key registered for this service. No official Aptabase SDK
// exists for Node — this posts directly to Aptabase's ingest API, which is
// the same wire format every official SDK uses under the hood.
//
// sessionId is generated fresh per event (see trackEvent), giving per-request
// granularity in the dashboard rather than collapsing the whole process
// lifetime into a single session.

const appKey = process.env.APTABASE_APP_KEY;
const host = process.env.APTABASE_HOST;

if (!appKey || !host) {
  console.warn('Aptabase analytics disabled: APTABASE_APP_KEY / APTABASE_HOST not set.');
}

type Props = Record<string, string | number | boolean>;

/**
 * Fire-and-forget event tracking. No-ops silently when analytics isn't
 * configured, and never throws — a dashboard being unreachable must never
 * affect request handling.
 */
export function trackEvent(eventName: string, props?: Props): void {
  if (!appKey || !host) return;

  const body = {
    timestamp: new Date().toISOString(),
    sessionId: randomUUID(),
    eventName,
    systemProps: {
      // Always false: this is a server process, not a mobile debug/release
      // build, and NODE_ENV isn't set in the deploy — leaving this tied to
      // NODE_ENV silently bucketed every event as debug in the dashboard.
      isDebug: false,
      osName: process.platform,
      osVersion: process.version,
      locale: 'es-ES',
      appVersion: '1.0.0',
      appBuildNumber: '1',
      sdkVersion: 'intersego-server-http/1.0.0',
    },
    props,
  };

  fetch(`${host}/api/v0/event`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'App-Key': appKey,
    },
    body: JSON.stringify(body),
  }).catch((err) => {
    console.warn('Aptabase event failed to send:', err);
  });
}
