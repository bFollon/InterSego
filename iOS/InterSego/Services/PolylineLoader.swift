/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import MapKit

struct PolylineLoader {
    private struct PolylineFile: Decodable {
        let version: String
        let coordinates: [[Double]]
    }

    func load(routeId: String, viewId: String) -> [CLLocationCoordinate2D] {
        guard let url = Bundle.main.url(
            forResource: "\(routeId)-\(viewId)",
            withExtension: "json",
            subdirectory: "RoutePolylines"
        ),
        let data = try? Data(contentsOf: url),
        let file = try? JSONDecoder().decode(PolylineFile.self, from: data)
        else { return [] }

        return file.coordinates.compactMap { pair in
            guard pair.count >= 2 else { return nil }
            return CLLocationCoordinate2D(latitude: pair[0], longitude: pair[1])
        }
    }
}
