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

import { fetchLinecar } from '../http.js';

function extractRouteId(filename: string): string | null {
  const segoviaMatch = filename.match(/SEGOVIA-([A-Z0-9]+)/i);
  if (segoviaMatch) return segoviaMatch[1].toUpperCase();

  const simpleMatch = filename.match(/^(M[0-9]+)\.pdf$/i);
  if (simpleMatch) return simpleMatch[1].toUpperCase();

  const suffixMatch = filename.match(/^(M[0-9]+)-.*\.pdf$/i);
  if (suffixMatch) return suffixMatch[1].toUpperCase();

  return null;
}

export async function scrapePDFURLs(): Promise<Map<string, string>> {
  const response = await fetchLinecar('https://www.linecar.es/metropolitano/segovia/');
  if (!response.ok) {
    throw new Error(`Linecar page returned HTTP ${response.status}`);
  }

  const html = await response.text();
  const urls = new Map<string, string>();

  for (const match of html.matchAll(/href="([^"]*\.pdf)"/gi)) {
    const href = match[1];
    let absoluteUrl: string;

    if (href.startsWith('http')) {
      absoluteUrl = href;
    } else if (href.startsWith('/')) {
      absoluteUrl = `https://www.linecar.es${href}`;
    } else {
      absoluteUrl = `https://www.linecar.es/metropolitano/segovia/${href}`;
    }

    const filename = absoluteUrl.split('/').pop() ?? '';
    const routeId = extractRouteId(filename);
    if (routeId && !urls.has(routeId)) {
      urls.set(routeId, absoluteUrl);
    }
  }

  return urls;
}
