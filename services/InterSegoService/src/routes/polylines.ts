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

const polylinesDir = process.env.POLYLINES_DIR
  ?? path.resolve(process.cwd(), '../../resources/route_polylines');

interface PolylineEntry {
  data: unknown;
  etag: string;
}

interface PolylineCache {
  polylines: Map<string, PolylineEntry>;
  allData: Record<string, unknown>;
  allEtag: string;
}

function buildCache(): PolylineCache {
  const polylines = new Map<string, PolylineEntry>();

  let files: string[];
  try {
    files = readdirSync(polylinesDir).filter((f) => f.endsWith('.json'));
  } catch (err) {
    console.warn(`[polylines] Could not read directory ${polylinesDir}:`, err);
    files = [];
  }

  for (const file of files) {
    const id = path.basename(file, '.json').toLowerCase();
    const content = readFileSync(path.join(polylinesDir, file), 'utf-8');
    const etag = `"${createHash('sha256').update(content).digest('hex').slice(0, 16)}"`;
    polylines.set(id, { data: JSON.parse(content), etag });
  }

  // Derive the all-polylines ETag from the individual ETags — no need to re-serialize all data.
  const combinedEtags = [...polylines.values()].map((e) => e.etag).join('');
  const allEtag = `"${createHash('sha256').update(combinedEtags).digest('hex').slice(0, 16)}"`;
  const allData = Object.fromEntries([...polylines.entries()].map(([id, e]) => [id, e.data]));

  console.log(`[polylines] Loaded ${polylines.size} polyline(s) from ${polylinesDir}`);
  return { polylines, allData, allEtag };
}

let cache = buildCache();

/** Re-read all polyline files from disk and swap the in-memory cache atomically. */
export function reloadPolylines(): void {
  cache = buildCache();
  console.log('[polylines] Cache reloaded.');
}

export async function polylinesRoutes(app: FastifyInstance): Promise<void> {
  /**
   * GET /api/polylines
   *
   * Returns a JSON object mapping every polyline id (e.g. "m1-circulara") to its
   * polyline data. Supports ETag-based conditional requests (If-None-Match → 304).
   */
  app.get(
    '/api/polylines',
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
   * GET /api/polylines/:id
   *
   * Returns the polyline JSON for a single route view (e.g. /api/polylines/m1-circulara).
   * Ids are case-insensitive. Supports If-None-Match → 304.
   */
  app.get<{ Params: { id: string } }>(
    '/api/polylines/:id',
    { preHandler: requireApiKey },
    async (request, reply) => {
      const id = request.params.id.toLowerCase();
      const entry = cache.polylines.get(id);

      if (!entry) {
        return reply.status(404).send({ error: 'Polyline not found' });
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
   * POST /api/polylines/reload
   *
   * Re-reads all polyline files from disk without restarting the server.
   * Use after dropping in an updated JSON file.
   */
  app.post(
    '/api/polylines/reload',
    { preHandler: requireReloadKey },
    async (_request, reply) => {
      reloadPolylines();
      return reply.send({
        reloaded: cache.polylines.size,
        polylines: [...cache.polylines.keys()],
      });
    },
  );
}
