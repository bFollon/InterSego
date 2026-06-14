/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import MapKit

struct PolylineLoader {
    private struct PolylineFile: Decodable {
        let version: String
        let coordinates: [[Double]]
    }

    func load(routeId: String, viewId: String) -> [CLLocationCoordinate2D] {
        let id = "\(routeId)-\(viewId)"

        let data: Data?
        if let cacheURL = PolylineCacheService.cacheURL(for: id),
           FileManager.default.fileExists(atPath: cacheURL.path) {
            data = try? Data(contentsOf: cacheURL)
        } else if let url = Bundle.main.url(forResource: id, withExtension: "json", subdirectory: "RoutePolylines") {
            data = try? Data(contentsOf: url)
        } else {
            data = nil
        }

        guard let data, let file = try? JSONDecoder().decode(PolylineFile.self, from: data) else { return [] }

        return file.coordinates.compactMap { pair in
            guard pair.count >= 2 else { return nil }
            return CLLocationCoordinate2D(latitude: pair[0], longitude: pair[1])
        }
    }
}
