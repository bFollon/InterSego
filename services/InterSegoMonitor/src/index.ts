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

import 'dotenv/config';
import Fastify from 'fastify';
import cron from 'node-cron';
import { initDb } from './db/index.js';
import { verifySmtp } from './notifier/index.js';
import { statusRoutes } from './routes/status.js';
import { checkRoutes } from './routes/check.js';
import { runCheck } from './checker/index.js';

const app = Fastify({ logger: true });

app.get('/health', async (_request, reply) => {
  return reply.send({ status: 'ok' });
});

app.register(statusRoutes);
app.register(checkRoutes);

async function start(): Promise<void> {
  if (!process.env.API_KEY) {
    console.error('Fatal: API_KEY is not set. Refusing to start.');
    process.exit(1);
  }
  if (!process.env.SMTP_USER || !process.env.SMTP_PASS) {
    console.error('Fatal: SMTP_USER and SMTP_PASS are required. Refusing to start.');
    process.exit(1);
  }
  if (!process.env.NOTIFY_EMAIL) {
    console.error('Fatal: NOTIFY_EMAIL is not set. Refusing to start.');
    process.exit(1);
  }

  await initDb();
  await verifySmtp();

  const port = Number(process.env.PORT ?? 3701);
  const host = process.env.HOST ?? '0.0.0.0';
  await app.listen({ port, host });

  cron.schedule('0 8,20 * * *', () => {
    void runCheck(app.log);
  });

  void runCheck(app.log);
}

start().catch((err) => {
  console.error(err);
  process.exit(1);
});
