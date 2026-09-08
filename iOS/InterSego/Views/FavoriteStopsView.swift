/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Lists the user's favorited stop+route+direction combinations (most-recently-added first).
/// Reached from "Más opciones" → "Favoritos". Tapping a row opens NextDeparture directly for
/// that stop+route+direction; swiping removes it (the same toggle also lives as a star in
/// NextDepartureView's toolbar).
struct FavoriteStopsView: View {
    let onSelectFavorite: (FavoriteStop) -> Void

    @State private var favorites: [FavoriteStop] = []

    var body: some View {
        List {
            if favorites.isEmpty {
                emptyState
            } else {
                Section {
                    ForEach(favorites) { favorite in
                        Button {
                            onSelectFavorite(favorite)
                        } label: {
                            FavoriteStopRow(favorite: favorite)
                        }
                        .buttonStyle(.plain)
                        .swipeActions {
                            Button(role: .destructive) {
                                remove(favorite)
                            } label: {
                                Label("Eliminar", systemImage: "trash")
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Favoritos")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { favorites = FavoriteStopsPrefs.getAll() }
    }

    private var emptyState: some View {
        Section {
            HStack {
                Spacer()
                VStack(spacing: 10) {
                    Image(systemName: "star.slash")
                        .font(.system(size: 36))
                        .foregroundColor(.secondary)
                    Text("Aún no tienes paradas favoritas")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Text("Marca una parada con la estrella desde la pantalla de horarios para acceder rápido a ella.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                Spacer()
            }
            .padding(.vertical, 24)
        }
    }

    private func remove(_ favorite: FavoriteStop) {
        FavoriteStopsPrefs.remove(matchKey: favorite.matchKey)
        favorites = FavoriteStopsPrefs.getAll()
    }
}

private struct FavoriteStopRow: View {
    let favorite: FavoriteStop

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    Text("Línea \(favorite.routeNumber)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.accentColor)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    Text(favorite.stopName)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.primary)
                }
                Text(favorite.directionLabel)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 2)
    }
}
