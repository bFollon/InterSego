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

/**
 * A single boarding event submitted by an app user.
 *
 * The server stores these as-is and never interprets them. All trip matching
 * and ETA computation happens on the client side using the local timetable.
 */
export interface BoardingEvent {
  /** UUID assigned by the server at ingestion time. */
  id: string;

  /** Canonical stop ID from BusStopRegistry (e.g. "azoguejo"). */
  stopId: string;

  /** Route identifier (e.g. "M4"). */
  routeId: string;

  /**
   * Direction label as it appears in the timetable (e.g. "Lastrilla → Sotillo").
   * Must match the direction string used in BusTimetable on both platforms.
   */
  direction: string;

  /**
   * Synthetic trip key computed by the app.
   * Format: "{routeId}|{direction}|{dayType}|{HH:MM}"
   * where HH:MM is the scheduled departure time at the boarding stop.
   * Example: "M4|Lastrilla → Sotillo|WEEKDAY|07:30"
   *
   * Other devices use this key to locate the matching entry in their local
   * timetable and derive an updated ETA for their own stop.
   */
  tripKey: string;

  /**
   * ISO 8601 timestamp of when the user boarded (device local time in UTC).
   * Compared to the scheduled departure to infer punctuality.
   */
  boardedAt: string;

  /**
   * ISO 8601 timestamp of the scheduled departure at the boarding stop.
   * Optional but strongly recommended: allows downstream users to calculate
   * how early or late the bus is running relative to the timetable.
   */
  scheduledDepartureTime?: string;

  /** ISO 8601 timestamp after which this event is considered expired. */
  expiresAt: string;
}

export interface RegisteredDevice {
  token: string;
  platform: 'ios' | 'android';
  minSeverity: 'none' | 'info' | 'warning' | 'critical';
  updatedAt: string;
}

export interface DbSchema {
  boardings: BoardingEvent[];
  reminders: DeviceReminder[];
  alerts: ServiceAlert[];
  devices: RegisteredDevice[];
}

export interface ServiceAlert {
  id: string;
  title: string;
  message: string;
  severity: 'info' | 'warning' | 'critical';
  affectedRoutes?: string[];
  startsAt: string;
  endsAt: string;
  broadcastSent: boolean;
}

export interface PostAlertBody {
  title: string;
  message: string;
  severity: 'info' | 'warning' | 'critical';
  affectedRoutes?: string[];
  startsAt: string;
  endsAt: string;
}

export type DayType =
  | 'weekday' | 'saturday' | 'sunday' | 'weekend' | 'holiday';

export type SeasonalAvailability =
  | 'YEAR_ROUND' | 'SCHOOL_ONLY' | 'SUMMER_ONLY'
  | 'JUNE_TO_SEPT_ONLY' | 'MON_FRI_ONLY' | 'FRI_ONLY';

export interface DeviceReminder {
  id: string;
  deviceToken: string;
  platform: 'ios' | 'android';
  routeId: string;
  routeNumber: string;
  stopId: string;
  stopName: string;
  direction: string;
  departureHour: number;
  departureMinute: number;
  leadMinutes: number;
  isDaily: boolean;
  dayType: DayType | null;
  seasonalAvailability: SeasonalAvailability | null;
  createdAt: string;
  nextFireAt: string | null;
}

export interface PostReminderBody {
  deviceToken: string;
  platform: 'ios' | 'android';
  routeId: string;
  routeNumber: string;
  stopId: string;
  stopName: string;
  direction: string;
  departureHour: number;
  departureMinute: number;
  leadMinutes: number;
  isDaily: boolean;
  dayType: DayType | null;
  seasonalAvailability: SeasonalAvailability | null;
}

export interface PostBoardingBody {
  stopId: string;
  routeId: string;
  direction: string;
  tripKey: string;
  boardedAt: string;
  scheduledDepartureTime?: string;
}
