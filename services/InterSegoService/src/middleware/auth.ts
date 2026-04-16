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

import type { FastifyReply, FastifyRequest } from 'fastify';

/**
 * Fastify preHandler that enforces Bearer token authentication.
 *
 * The expected token is read from the API_KEY environment variable.
 * Clients must include:  Authorization: Bearer <API_KEY>
 */
export async function requireApiKey(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  const auth = request.headers.authorization;

  if (!auth?.startsWith('Bearer ')) {
    reply.status(401).send({ error: 'Unauthorized' });
    return;
  }

  const token = auth.slice(7);

  if (token !== process.env.API_KEY) {
    reply.status(401).send({ error: 'Unauthorized' });
    return;
  }
}
