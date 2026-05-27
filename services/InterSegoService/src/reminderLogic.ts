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

import { DateTime } from 'luxon';
import type { DayType, SeasonalAvailability } from './types.js';

const SPAIN_TZ = 'Europe/Madrid';
const SUMMER_MONTHS = new Set([7, 8]);
const JUNE_TO_SEPT_MONTHS = new Set([6, 7, 8, 9]);

// Luxon weekday: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
function dayTypeMatches(dayType: DayType, luxonWeekday: number): boolean {
  switch (dayType) {
    case 'weekday':  return luxonWeekday >= 1 && luxonWeekday <= 5;
    case 'saturday': return luxonWeekday === 6;
    case 'sunday':   return luxonWeekday === 7;
    case 'weekend':  return luxonWeekday === 6 || luxonWeekday === 7;
    case 'holiday':  return luxonWeekday === 7;
  }
}

function runsIn(seasonal: SeasonalAvailability, month: number, luxonWeekday: number): boolean {
  switch (seasonal) {
    case 'YEAR_ROUND':        return true;
    case 'SUMMER_ONLY':       return SUMMER_MONTHS.has(month);
    case 'JUNE_TO_SEPT_ONLY': return JUNE_TO_SEPT_MONTHS.has(month);
    case 'SCHOOL_ONLY':       return !SUMMER_MONTHS.has(month);
    case 'MON_FRI_ONLY':      return luxonWeekday === 1 || luxonWeekday === 5;
    case 'FRI_ONLY':          return luxonWeekday === 5;
  }
}

/**
 * Find the next UTC Date at which a departure alert should fire, in Spain timezone.
 *
 * Iterates forward day by day (up to 30) from [from], finding the first day where:
 * 1. The day type matches (weekday, saturday, etc.) — if specified
 * 2. The seasonal availability applies — if specified
 * 3. The computed fire time (departure − leadMins) is strictly after [from]
 *
 * All date arithmetic uses Europe/Madrid timezone, handling DST automatically.
 */
export function nextOccurrence(
  hour: number,
  minute: number,
  leadMins: number,
  dayType: DayType | null,
  seasonal: SeasonalAvailability | null,
  from: Date = new Date(),
): Date | null {
  const totalMins = hour * 60 + minute - leadMins;
  if (totalMins < 0) return null;
  const fireHour = Math.floor(totalMins / 60);
  const fireMinute = totalMins % 60;

  const fromDT = DateTime.fromJSDate(from, { zone: SPAIN_TZ });

  for (let daysAhead = 0; daysAhead < 30; daysAhead++) {
    const baseDay = fromDT.plus({ days: daysAhead });
    const candidate = DateTime.fromObject(
      { year: baseDay.year, month: baseDay.month, day: baseDay.day, hour: fireHour, minute: fireMinute, second: 0 },
      { zone: SPAIN_TZ },
    );

    if (candidate.toJSDate() <= from) continue;
    if (dayType && !dayTypeMatches(dayType, candidate.weekday)) continue;
    if (seasonal && !runsIn(seasonal, candidate.month, candidate.weekday)) continue;

    return candidate.toJSDate();
  }
  return null;
}
