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

import { createHash } from 'crypto';
import { readFileSync, readdirSync } from 'fs';
import path from 'path';
import type { FastifyInstance } from 'fastify';
import { requireApiKey, requireReloadKey } from '../middleware/auth.js';

const timetablesDir = process.env.TIMETABLES_DIR
  ?? path.resolve(process.cwd(), '../../resources/timetables');

interface TimetableEntry {
  data: unknown;
  etag: string;
}

interface TimetableCache {
  routes: Map<string, TimetableEntry>;
  allData: unknown[];
  allEtag: string;
}

function buildCache(): TimetableCache {
  const routes = new Map<string, TimetableEntry>();

  let files: string[];
  try {
    files = readdirSync(timetablesDir).filter((f) => f.endsWith('.json'));
  } catch (err) {
    console.warn(`[timetables] Could not read directory ${timetablesDir}:`, err);
    files = [];
  }

  for (const file of files) {
    const routeId = path.basename(file, '.json');
    const content = readFileSync(path.join(timetablesDir, file), 'utf-8');
    const etag = `"${createHash('sha256').update(content).digest('hex').slice(0, 16)}"`;
    routes.set(routeId, { data: JSON.parse(content), etag });
  }

  // Derive the all-routes ETag from the individual ETags — no need to re-serialize all data.
  const combinedEtags = [...routes.values()].map((e) => e.etag).join('');
  const allEtag = `"${createHash('sha256').update(combinedEtags).digest('hex').slice(0, 16)}"`;
  const allData = [...routes.values()].map((e) => e.data);

  console.log(`[timetables] Loaded ${routes.size} route(s) from ${timetablesDir}`);
  return { routes, allData, allEtag };
}

let cache = buildCache();

/** Re-read all timetable files from disk and swap the in-memory cache atomically. */
export function reloadTimetables(): void {
  cache = buildCache();
  console.log('[timetables] Cache reloaded.');
}

export async function timetablesRoutes(app: FastifyInstance): Promise<void> {
  /**
   * GET /api/timetables
   *
   * Returns a JSON array containing all route timetable objects.
   * Supports ETag-based conditional requests (If-None-Match → 304).
   */
  app.get(
    '/api/timetables',
    { preHandler: requireApiKey },
    async (request, reply) => {
      if (request.headers['if-none-match'] === cache.allEtag) {
        return reply.status(304).send();
      }
      return reply
        .header('ETag', cache.allEtag)
        .header('Cache-Control', 'no-cache')
        .send(cache.allData);
    },
  );

  /**
   * GET /api/timetables/:routeId
   *
   * Returns the timetable JSON for a single route (e.g. /api/timetables/m1).
   * Route IDs are case-insensitive. Supports If-None-Match → 304.
   */
  app.get<{ Params: { routeId: string } }>(
    '/api/timetables/:routeId',
    { preHandler: requireApiKey },
    async (request, reply) => {
      const routeId = request.params.routeId.toLowerCase();
      const entry = cache.routes.get(routeId);

      if (!entry) {
        return reply.status(404).send({ error: 'Route not found' });
      }

      if (request.headers['if-none-match'] === entry.etag) {
        return reply.status(304).send();
      }

      return reply
        .header('ETag', entry.etag)
        .header('Cache-Control', 'no-cache')
        .send(entry.data);
    },
  );

  /**
   * POST /api/timetables/reload
   *
   * Re-reads all timetable files from disk without restarting the server.
   * Use after dropping in an updated JSON file.
   */
  app.post(
    '/api/timetables/reload',
    { preHandler: requireReloadKey },
    async (_request, reply) => {
      reloadTimetables();
      return reply.send({
        reloaded: cache.routes.size,
        routes: [...cache.routes.keys()],
      });
    },
  );
}
