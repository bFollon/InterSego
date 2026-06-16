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
