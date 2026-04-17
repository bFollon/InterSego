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

import { Agent, fetch as undiciFetch } from 'undici';

// linecar.es has an incomplete SSL certificate chain; rejectUnauthorized is
// scoped to this agent so it only affects requests to the Linecar website.
const linecarAgent = new Agent({ connect: { rejectUnauthorized: false } });

const USER_AGENT = 'Mozilla/5.0 (Android; Mobile; rv:13.0) Gecko/13.0 Firefox/13.0';

export function fetchLinecar(url: string): ReturnType<typeof undiciFetch> {
  return undiciFetch(url, {
    dispatcher: linecarAgent,
    headers: { 'User-Agent': USER_AGENT },
  });
}
