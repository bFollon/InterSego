// InterSego Monitor
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
import { requireApiKey } from '../middleware/auth.js';
import { runCheck } from '../checker/index.js';

export async function checkRoutes(app: FastifyInstance): Promise<void> {
  app.post('/check', { preHandler: requireApiKey }, async (_request, reply) => {
    void runCheck(app.log);
    return reply.status(202).send({ status: 'check started' });
  });
}
