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

import type { FastifyInstance } from 'fastify';
import { deleteDevice, upsertDevice } from '../db/index.js';
import { requireApiKey } from '../middleware/auth.js';

const deleteDeviceTokenSchema = {
  params: {
    type: 'object',
    required: ['token'],
    properties: {
      token: { type: 'string', minLength: 1, maxLength: 4096 },
    },
  },
} as const;

const postDeviceTokenSchema = {
  body: {
    type: 'object',
    required: ['token', 'platform'],
    additionalProperties: false,
    properties: {
      token:    { type: 'string', minLength: 1 },
      platform: { type: 'string', enum: ['ios', 'android'] },
    },
  },
} as const;

export async function devicesRoutes(app: FastifyInstance): Promise<void> {
  app.post<{ Body: { token: string; platform: 'ios' | 'android' } }>(
    '/device-tokens',
    { preHandler: requireApiKey, schema: postDeviceTokenSchema },
    async (request, reply) => {
      await upsertDevice(request.body.token, request.body.platform);
      return reply.status(204).send();
    },
  );

  app.delete<{ Params: { token: string } }>(
    '/device-tokens/:token',
    { preHandler: requireApiKey, schema: deleteDeviceTokenSchema },
    async (request, reply) => {
      await deleteDevice(request.params.token);
      return reply.status(204).send();
    },
  );
}
