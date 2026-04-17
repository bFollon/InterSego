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

import nodemailer from 'nodemailer';
import type { ChangeDetail } from '../types.js';

const transport = nodemailer.createTransport({
  host: 'smtp.mail.me.com',
  port: 587,
  secure: false,
  auth: {
    user: process.env.SMTP_USER,
    pass: process.env.SMTP_PASS,
  },
});

export async function verifySmtp(): Promise<void> {
  await transport.verify();
}

export async function sendChangeNotification(changes: ChangeDetail[]): Promise<void> {
  const routeList = changes
    .map((c) => {
      const lines = [`• ${c.routeId}`];
      if (c.urlChanged) {
        lines.push(`  URL: ${c.previousUrl ?? '(none)'} → ${c.currentUrl}`);
      } else {
        lines.push(`  URL: ${c.currentUrl}`);
      }
      if (c.previousSha256 !== null) {
        lines.push(`  SHA-256: ${c.previousSha256.slice(0, 16)}… → ${c.currentSha256.slice(0, 16)}…`);
      }
      return lines.join('\n');
    })
    .join('\n\n');

  const routeIds = changes.map((c) => c.routeId).join(', ');

  await transport.sendMail({
    from: `InterSego Monitor <${process.env.SMTP_USER}>`,
    to: process.env.NOTIFY_EMAIL,
    subject: `[InterSego] PDF update detected: ${routeIds}`,
    text: [
      'The following bus route PDFs have changed on the Linecar website:',
      '',
      routeList,
      '',
      'Review and update the affected static parsers as needed.',
    ].join('\n'),
  });
}
