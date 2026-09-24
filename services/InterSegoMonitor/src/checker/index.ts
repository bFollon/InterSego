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

import { createHash } from 'crypto';
import type { FastifyBaseLogger } from 'fastify';
import { fetchLinecar } from '../http.js';
import { getRouteStates, upsertRouteStates } from '../db/index.js';
import { scrapePDFURLs } from '../scraper/index.js';
import { sendChangeNotification } from '../notifier/index.js';
import { trackEvent } from '../analytics.js';
import type { ChangeDetail, RouteCheckState } from '../types.js';

let checkInProgress = false;

async function downloadAndHash(url: string): Promise<string> {
  const response = await fetchLinecar(url);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const buffer = await response.arrayBuffer();
  return createHash('sha256').update(Buffer.from(buffer)).digest('hex');
}

export async function runCheck(log: FastifyBaseLogger): Promise<void> {
  if (checkInProgress) {
    log.info('PDF check already in progress, skipping');
    return;
  }
  checkInProgress = true;

  try {
    log.info('Starting PDF check');

    let scrapedUrls: Map<string, string>;
    try {
      scrapedUrls = await scrapePDFURLs();
      log.info(`Scraped ${scrapedUrls.size} PDF URLs`);
    } catch (err) {
      log.error(`Scraping failed: ${err}`);
      trackEvent('check_scrape_failed', { reason: String(err) });
      return;
    }

    if (scrapedUrls.size === 0) {
      log.error('Scraper returned 0 PDF URLs — possible page structure change; aborting check');
      trackEvent('check_scrape_failed', { reason: 'zero_urls' });
      return;
    }

    const existingStates = await getRouteStates();
    const stateMap = new Map(existingStates.map((s) => [s.routeId, s]));

    const updatedStates: RouteCheckState[] = [];
    const changes: ChangeDetail[] = [];
    const now = new Date().toISOString();

    for (const [routeId, currentUrl] of scrapedUrls) {
      const existing = stateMap.get(routeId) ?? null;

      let sha256: string;
      try {
        sha256 = await downloadAndHash(currentUrl);
      } catch (err) {
        log.error(`${routeId}: download failed: ${err}`);
        continue;
      }

      const isNew = existing === null;
      const urlChanged = !isNew && existing.url !== currentUrl;
      const hashChanged = !isNew && existing.sha256 !== sha256;
      const changed = urlChanged || hashChanged;

      updatedStates.push({
        routeId,
        url: currentUrl,
        sha256,
        lastChecked: now,
        lastChanged: changed ? now : (existing?.lastChanged ?? null),
      });

      if (!isNew && changed) {
        changes.push({
          routeId,
          previousUrl: existing.url,
          currentUrl,
          urlChanged,
          previousSha256: existing.sha256,
          currentSha256: sha256,
        });
        log.info(`${routeId}: CHANGED (url=${urlChanged}, hash=${hashChanged})`);
      } else {
        log.info(`${routeId}: ${isNew ? 'baseline established' : 'unchanged'}`);
      }
    }

    if (updatedStates.length > 0) {
      await upsertRouteStates(updatedStates);
      log.info(`Persisted state for ${updatedStates.length} routes`);
    }

    if (changes.length > 0) {
      try {
        await sendChangeNotification(changes);
        log.info(`Change notification sent for: ${changes.map((c) => c.routeId).join(', ')}`);
      } catch (err) {
        log.error(`Failed to send change notification: ${err}`);
        trackEvent('check_notification_failed', { reason: String(err) });
      }
    } else {
      log.info('No changes detected');
    }

    trackEvent('check_completed', {
      routesScraped: scrapedUrls.size,
      changesDetected: changes.length,
    });
  } finally {
    checkInProgress = false;
  }
}
