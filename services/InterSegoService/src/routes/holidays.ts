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

const holidaysDir = process.env.HOLIDAYS_DIR
  ?? path.resolve(process.cwd(), '../../resources/holidays');

interface HolidayCache {
  allData: unknown[];
  allEtag: string;
}

function buildCache(): HolidayCache {
  let files: string[];
  try {
    files = readdirSync(holidaysDir).filter((f) => f.endsWith('.json'));
  } catch (err) {
    console.warn(`[holidays] Could not read directory ${holidaysDir}:`, err);
    files = [];
  }

  const allData = files.map((file) => JSON.parse(readFileSync(path.join(holidaysDir, file), 'utf-8')));
  const combinedContent = files.map((file) => readFileSync(path.join(holidaysDir, file), 'utf-8')).join('');
  const allEtag = `"${createHash('sha256').update(combinedContent).digest('hex').slice(0, 16)}"`;

  console.log(`[holidays] Loaded ${allData.length} year(s) from ${holidaysDir}`);
  return { allData, allEtag };
}

let cache = buildCache();

/** Re-read all holiday files from disk and swap the in-memory cache atomically. */
export function reloadHolidays(): void {
  cache = buildCache();
  console.log('[holidays] Cache reloaded.');
}

export async function holidaysRoutes(app: FastifyInstance): Promise<void> {
  /**
   * GET /api/holidays
   *
   * Returns a JSON array containing every known year's holiday calendar
   * (the full contents of each resources/holidays/{year}.json file).
   * Supports ETag-based conditional requests (If-None-Match → 304).
   */
  app.get(
    '/api/holidays',
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
   * POST /api/holidays/reload
   *
   * Re-reads all holiday files from disk without restarting the server.
   * Use after dropping in a new or corrected year's JSON file.
   */
  app.post(
    '/api/holidays/reload',
    { preHandler: requireReloadKey },
    async (_request, reply) => {
      reloadHolidays();
      return reply.send({ reloaded: cache.allData.length });
    },
  );
}
