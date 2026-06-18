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
import type { FastifyInstance } from 'fastify';
import { DateTime } from 'luxon';
import { appendAlert, deleteAlert, getActiveAlerts, getAllAlerts } from '../db/index.js';
import { requireReloadKey } from '../middleware/auth.js';
import type { PostAlertBody, ServiceAlert } from '../types.js';

const SEVERITIES = ['info', 'warning', 'critical'] as const;

/**
 * Accept naive local timestamps (e.g. "2026-06-18T11:47:00"), UTC ("...Z"),
 * or explicit offset ("...+02:00"). Naive timestamps are interpreted as
 * Europe/Madrid and normalised to UTC before storage.
 */
const ISO8601_FLEXIBLE = '^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?$';

function toUtcIso(input: string): string {
  if (/Z$|[+-]\d{2}:\d{2}$/.test(input)) {
    return new Date(input).toISOString();
  }
  return DateTime.fromISO(input, { zone: 'Europe/Madrid' }).toUTC().toISO()!;
}

const postAlertSchema = {
  body: {
    type: 'object',
    required: ['title', 'message', 'severity', 'startsAt', 'endsAt'],
    additionalProperties: false,
    properties: {
      title:          { type: 'string', minLength: 1, maxLength: 256 },
      message:        { type: 'string', minLength: 1, maxLength: 1024 },
      severity:       { type: 'string', enum: [...SEVERITIES] },
      affectedRoutes: { type: 'array', items: { type: 'string' }, nullable: true },
      startsAt:       { type: 'string', pattern: ISO8601_FLEXIBLE },
      endsAt:         { type: 'string', pattern: ISO8601_FLEXIBLE },
    },
  },
} as const;

export async function alertsRoutes(app: FastifyInstance): Promise<void> {
  // Public — no auth; apps poll this at startup.
  // broadcastSent is an internal server field — strip it from the public response.
  app.get('/alerts', async (_request, reply) => {
    const alerts = await getActiveAlerts();
    return reply.send(alerts.map(({ broadcastSent: _, ...a }) => a));
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
        startsAt: toUtcIso(request.body.startsAt),
        endsAt:   toUtcIso(request.body.endsAt),
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
