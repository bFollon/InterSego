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
import { appendBoarding, getActiveboardings } from '../db/index.js';
import { requireApiKey } from '../middleware/auth.js';
import type { BoardingEvent, PostBoardingBody } from '../types.js';

/** Boarding events expire after 4 hours — longer than any route cycle. */
const TTL_MS = 4 * 60 * 60 * 1000;

/** ISO 8601 UTC timestamp: e.g. 2026-04-01T07:30:00Z or 2026-04-01T07:30:00.000Z */
const ISO8601_UTC = '^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$';

const postBoardingSchema = {
  body: {
    type: 'object',
    required: ['stopId', 'routeId', 'direction', 'tripKey', 'boardedAt'],
    additionalProperties: false,
    properties: {
      stopId:    { type: 'string', minLength: 1, maxLength: 128 },
      routeId:   { type: 'string', minLength: 1, maxLength: 16 },
      direction: { type: 'string', minLength: 1, maxLength: 256 },
      tripKey:   { type: 'string', minLength: 1, maxLength: 512 },
      boardedAt:              { type: 'string', pattern: ISO8601_UTC },
      scheduledDepartureTime: { type: 'string', pattern: ISO8601_UTC },
    },
  },
} as const;

export async function boardingsRoutes(app: FastifyInstance): Promise<void> {
  /**
   * POST /boardings
   *
   * Record that a user has boarded a bus at a given stop.
   * The app supplies all trip context; the server assigns an ID and expiry.
   */
  app.post<{ Body: PostBoardingBody }>(
    '/boardings',
    { preHandler: requireApiKey, schema: postBoardingSchema },
    async (request, reply) => {
      const { stopId, routeId, direction, tripKey, boardedAt, scheduledDepartureTime } =
        request.body;

      const event: BoardingEvent = {
        id: randomUUID(),
        stopId,
        routeId,
        direction,
        tripKey,
        boardedAt,
        ...(scheduledDepartureTime !== undefined && { scheduledDepartureTime }),
        expiresAt: new Date(Date.now() + TTL_MS).toISOString(),
      };

      await appendBoarding(event);

      return reply.status(201).send(event);
    },
  );

  /**
   * GET /boardings
   *
   * Return all active (non-expired) boarding events.
   * Clients filter by tripKey to find events relevant to their current view
   * and use boardedAt vs scheduledDepartureTime to estimate punctuality.
   *
   * Expired events are pruned from disk on each read.
   */
  app.get(
    '/boardings',
    { preHandler: requireApiKey },
    async (_request, reply) => {
      const active = await getActiveboardings();
      return reply.send(active);
    },
  );
}
