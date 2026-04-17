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

import { Low } from 'lowdb';
import { JSONFile } from 'lowdb/node';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import type { DbSchema, RouteCheckState } from '../types.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.resolve(__dirname, '../../data');
const dbPath = path.join(dataDir, 'checksums.json');

const adapter = new JSONFile<DbSchema>(dbPath);
const defaultData: DbSchema = { routes: [] };
const db = new Low<DbSchema>(adapter, defaultData);

let writeQueue: Promise<void> = Promise.resolve();

function serialize<T>(fn: () => Promise<T>): Promise<T> {
  const next = writeQueue.then(fn);
  writeQueue = next.then(
    () => {},
    () => {},
  );
  return next;
}

export async function initDb(): Promise<void> {
  fs.mkdirSync(dataDir, { recursive: true });
  await db.read();
}

export async function getRouteStates(): Promise<RouteCheckState[]> {
  await db.read();
  return [...db.data.routes];
}

export function upsertRouteStates(states: RouteCheckState[]): Promise<void> {
  return serialize(async () => {
    await db.read();
    for (const state of states) {
      const index = db.data.routes.findIndex((r) => r.routeId === state.routeId);
      if (index >= 0) {
        db.data.routes[index] = state;
      } else {
        db.data.routes.push(state);
      }
    }
    await db.write();
  });
}
