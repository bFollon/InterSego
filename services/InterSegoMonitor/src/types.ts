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

export interface RouteCheckState {
  routeId: string;
  url: string;
  sha256: string;
  lastChecked: string;
  lastChanged: string | null;
}

export interface ChangeDetail {
  routeId: string;
  previousUrl: string | null;
  currentUrl: string;
  urlChanged: boolean;
  previousSha256: string | null;
  currentSha256: string;
}

export interface DbSchema {
  routes: RouteCheckState[];
}
