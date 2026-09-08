/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Manages the "Favorite stops" list. Uses UserDefaults for storage, mirroring GuidedModePrefs.
/// Ordered most-recently-added first; no cap on the number of favorites.
struct FavoriteStopsPrefs {
    private static let key = "favoriteStops_v1"

    static func getAll() -> [FavoriteStop] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([FavoriteStop].self, from: data)
        else { return [] }
        return decoded
    }

    static func isFavorite(stopId: String, routeId: String, viewId: String) -> Bool {
        let key = FavoriteStop.matchKey(stopId: stopId, routeId: routeId, viewId: viewId)
        return getAll().contains { $0.matchKey == key }
    }

    /// Adds the favorite (at the front) if not already present, otherwise removes it.
    /// Returns the resulting favorited state.
    @discardableResult
    static func toggle(_ favorite: FavoriteStop) -> Bool {
        var favorites = getAll()
        if let index = favorites.firstIndex(where: { $0.matchKey == favorite.matchKey }) {
            favorites.remove(at: index)
            persist(favorites)
            return false
        } else {
            favorites.insert(favorite, at: 0)
            persist(favorites)
            return true
        }
    }

    static func remove(matchKey: String) {
        var favorites = getAll()
        favorites.removeAll { $0.matchKey == matchKey }
        persist(favorites)
    }

    private static func persist(_ favorites: [FavoriteStop]) {
        guard let data = try? JSONEncoder().encode(favorites) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
}
