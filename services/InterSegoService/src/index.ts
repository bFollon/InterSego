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

import 'dotenv/config';
import Fastify from 'fastify';
import { initDb } from './db/index.js';
import { alertsRoutes } from './routes/alerts.js';
import { boardingsRoutes } from './routes/boardings.js';
import { reloadPolylines, polylinesRoutes } from './routes/polylines.js';
import { remindersRoutes } from './routes/reminders.js';
import { reloadTimetables, timetablesRoutes } from './routes/timetables.js';
import { initScheduler } from './scheduler.js';

const app = Fastify({ logger: true });

app.get('/health', async (_request, reply) => {
  return reply.send({ status: 'ok' });
});

app.register(alertsRoutes);
app.register(boardingsRoutes);
app.register(remindersRoutes);
app.register(timetablesRoutes);
app.register(polylinesRoutes);

// Re-read timetable and polyline files from disk without restarting the process.
// Usage: kill -HUP <pid>  or  pm2 sendSignal SIGHUP intersego-server
process.on('SIGHUP', () => {
  reloadTimetables();
  reloadPolylines();
});

async function start(): Promise<void> {
  const required = [
    'API_KEY', 'RELOAD_KEY',
    'APNS_KEY_PATH', 'APNS_KEY_ID', 'APNS_TEAM_ID', 'APNS_BUNDLE_ID',
    'FCM_SERVICE_ACCOUNT_PATH', 'FCM_PROJECT_ID',
  ];
  for (const key of required) {
    if (!process.env[key]) {
      console.error(`Fatal: ${key} environment variable is not set. Refusing to start.`);
      process.exit(1);
    }
  }

  await initDb();

  const port = Number(process.env.PORT ?? 3000);
  const host = process.env.HOST ?? '0.0.0.0';

  await app.listen({ port, host });
  initScheduler();
}

start().catch((err) => {
  console.error(err);
  process.exit(1);
});
