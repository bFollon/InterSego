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
import {
  appendReminder, deleteReminder, deleteRemindersForToken,
  getRemindersForToken, updateReminderLeadMinutes, updateReminderToken,
} from '../db/index.js';
import { requireApiKey } from '../middleware/auth.js';
import type { DeviceReminder, PostReminderBody } from '../types.js';

const DAY_TYPES = ['weekday', 'saturday', 'sunday', 'weekend', 'holiday'];
const SEASONAL = ['YEAR_ROUND', 'SCHOOL_ONLY', 'SUMMER_ONLY', 'JUNE_TO_SEPT_ONLY', 'MON_FRI_ONLY', 'FRI_ONLY'];

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
      deviceToken:          { type: 'string', minLength: 1, maxLength: 512 },
      platform:             { type: 'string', enum: ['ios', 'android'] },
      routeId:              { type: 'string', minLength: 1, maxLength: 16 },
      routeNumber:          { type: 'string', minLength: 1, maxLength: 16 },
      stopId:               { type: 'string', minLength: 1, maxLength: 128 },
      stopName:             { type: 'string', minLength: 1, maxLength: 256 },
      direction:            { type: 'string', minLength: 1, maxLength: 256 },
      departureHour:        { type: 'integer', minimum: 0, maximum: 23 },
      departureMinute:      { type: 'integer', minimum: 0, maximum: 59 },
      leadMinutes:          { type: 'integer', minimum: 0, maximum: 120 },
      isDaily:              { type: 'boolean' },
      dayType:              { type: ['string', 'null'], enum: [...DAY_TYPES, null] },
      seasonalAvailability: { type: ['string', 'null'], enum: [...SEASONAL, null] },
    },
  },
} as const;

export async function remindersRoutes(app: FastifyInstance): Promise<void> {
  /**
   * POST /reminders
   *
   * Register a new device reminder. The server stores it and the scheduler
   * fires a push notification at the next computed occurrence.
   */
  app.post<{ Body: PostReminderBody }>(
    '/reminders',
    { preHandler: requireApiKey, schema: postReminderSchema },
    async (request, reply) => {
      const reminder: DeviceReminder = {
        id: randomUUID(),
        createdAt: new Date().toISOString(),
        nextFireAt: null,
        ...request.body,
      };
      await appendReminder(reminder);
      return reply.status(201).send({ id: reminder.id });
    },
  );

  /**
   * PATCH /reminders/:id
   *
   * Update the lead time for an existing reminder.
   * Resets nextFireAt so the scheduler recomputes the fire time on the next tick.
   */
  app.patch<{ Params: { id: string }; Body: { leadMinutes: number } }>(
    '/reminders/:id',
    {
      preHandler: requireApiKey,
      schema: {
        body: {
          type: 'object',
          required: ['leadMinutes'],
          additionalProperties: false,
          properties: {
            leadMinutes: { type: 'integer', minimum: 0, maximum: 120 },
          },
        },
      },
    },
    async (request, reply) => {
      const found = await updateReminderLeadMinutes(request.params.id, request.body.leadMinutes);
      return reply.status(found ? 204 : 404).send();
    },
  );

  /**
   * DELETE /reminders/:id
   *
   * Remove a single reminder by its server-assigned UUID.
   */
  app.delete<{ Params: { id: string } }>(
    '/reminders/:id',
    { preHandler: requireApiKey },
    async (request, reply) => {
      await deleteReminder(request.params.id);
      return reply.status(204).send();
    },
  );

  /**
   * GET /reminders/device/:token
   *
   * List all reminders registered for a device token.
   */
  app.get<{ Params: { token: string } }>(
    '/reminders/device/:token',
    { preHandler: requireApiKey },
    async (request, reply) => {
      const reminders = await getRemindersForToken(request.params.token);
      return reply.send(reminders);
    },
  );

  /**
   * DELETE /reminders/device/:token
   *
   * Remove all reminders for a device (e.g. on app uninstall or token invalidation).
   */
  app.delete<{ Params: { token: string } }>(
    '/reminders/device/:token',
    { preHandler: requireApiKey },
    async (request, reply) => {
      await deleteRemindersForToken(request.params.token);
      return reply.status(204).send();
    },
  );

  /**
   * PUT /reminders/token
   *
   * Update all reminders from an old device token to a new one (APNs / FCM token rotation).
   */
  app.put<{ Body: { oldToken: string; newToken: string } }>(
    '/reminders/token',
    { preHandler: requireApiKey },
    async (request, reply) => {
      await updateReminderToken(request.body.oldToken, request.body.newToken);
      return reply.status(204).send();
    },
  );
}
