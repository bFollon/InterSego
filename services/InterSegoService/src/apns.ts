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

/**
 * Minimal APNs HTTP/2 client using Node.js built-ins only.
 *
 * Uses token-based auth (ES256 JWT from a .p8 key). The HTTP/2 connection
 * is kept alive and reused across notifications; it reconnects automatically
 * if the server closes it. The JWT is renewed every 55 minutes (APNs enforces
 * a 1-hour maximum).
 */

import crypto from 'node:crypto';
import fs from 'node:fs';
import http2 from 'node:http2';

const SANDBOX_HOST = 'https://api.sandbox.push.apple.com';
const PRODUCTION_HOST = 'https://api.push.apple.com';
const JWT_TTL_SECONDS = 55 * 60;
const REQUEST_TIMEOUT_MS = 10_000;

export interface ApnsConfig {
  keyPath: string;
  keyId: string;
  teamId: string;
  bundleId: string;
  production: boolean;
}

export interface ApnsSendResult {
  failed: Array<{ device: string; reason: string }>;
}

export interface ApnsNotification {
  title: string;
  body: string;
  sound: string;
  expiry: number;
}

export class ApnsProvider {
  private readonly config: ApnsConfig;
  private readonly key: Buffer;
  private readonly host: string;
  private jwt = '';
  private jwtIssuedAt = 0;
  private session: http2.ClientHttp2Session | null = null;

  constructor(config: ApnsConfig) {
    this.config = config;
    this.key = fs.readFileSync(config.keyPath);
    this.host = config.production ? PRODUCTION_HOST : SANDBOX_HOST;
  }

  async send(notification: ApnsNotification, deviceToken: string): Promise<ApnsSendResult> {
    const payload = JSON.stringify({
      aps: { alert: { title: notification.title, body: notification.body }, sound: notification.sound },
    });

    return new Promise((resolve) => {
      let session: http2.ClientHttp2Session;
      try {
        session = this.getSession();
      } catch (err) {
        resolve({ failed: [{ device: deviceToken, reason: String(err) }] });
        return;
      }

      const req = session.request({
        ':method': 'POST',
        ':path': `/3/device/${deviceToken}`,
        'authorization': `bearer ${this.getJwt()}`,
        'apns-topic': this.config.bundleId,
        'apns-expiration': String(notification.expiry),
        'apns-priority': '10',
        'content-type': 'application/json',
        'content-length': String(Buffer.byteLength(payload)),
      });

      const timer = setTimeout(() => {
        req.destroy(new Error('APNs request timeout'));
      }, REQUEST_TIMEOUT_MS);

      req.write(payload);
      req.end();

      let statusCode = 0;
      req.on('response', (headers) => {
        statusCode = Number(headers[':status']);
      });

      let body = '';
      req.on('data', (chunk: Buffer) => { body += chunk.toString(); });

      req.on('end', () => {
        clearTimeout(timer);
        if (statusCode === 200) {
          resolve({ failed: [] });
          return;
        }
        let reason = `HTTP ${statusCode}`;
        try {
          const parsed = JSON.parse(body) as { reason?: string };
          if (parsed.reason) reason = parsed.reason;
        } catch { /* body was not JSON */ }
        resolve({ failed: [{ device: deviceToken, reason }] });
      });

      req.on('error', (err: Error) => {
        clearTimeout(timer);
        this.destroySession();
        resolve({ failed: [{ device: deviceToken, reason: err.message }] });
      });
    });
  }

  shutdown(): void {
    this.destroySession();
  }

  private getSession(): http2.ClientHttp2Session {
    if (!this.session || this.session.closed || this.session.destroyed) {
      const s = http2.connect(this.host);
      s.on('error', (err) => {
        console.error('APNs session error:', err.message);
        this.destroySession();
      });
      s.on('goaway', () => {
        // Server is closing the connection — let the next send reconnect.
        this.destroySession();
      });
      this.session = s;
    }
    return this.session;
  }

  private destroySession(): void {
    this.session?.destroy();
    this.session = null;
  }

  private getJwt(): string {
    const now = Math.floor(Date.now() / 1000);
    if (now - this.jwtIssuedAt > JWT_TTL_SECONDS) {
      const header = Buffer.from(JSON.stringify({ alg: 'ES256', kid: this.config.keyId })).toString('base64url');
      const payload = Buffer.from(JSON.stringify({ iss: this.config.teamId, iat: now })).toString('base64url');
      const signingInput = `${header}.${payload}`;
      const sig = crypto.sign('sha256', Buffer.from(signingInput), {
        key: this.key,
        dsaEncoding: 'ieee-p1363',
      });
      this.jwt = `${signingInput}.${sig.toString('base64url')}`;
      this.jwtIssuedAt = now;
    }
    return this.jwt;
  }
}
