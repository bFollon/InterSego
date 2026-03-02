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

import SwiftUI

struct RouteSelectionView: View {
    let routes: [BusRoute]
    let supportedRoutes: Set<String>
    let onRouteSelected: (BusRoute) -> Void

    var body: some View {
        List {
            Section {
                Text("Selecciona una línea")
                    .font(.headline)
            }

            ForEach(routes) { route in
                let isAvailable = supportedRoutes.contains(route.id)
                RouteCardView(route: route, isAvailable: isAvailable)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if isAvailable {
                            onRouteSelected(route)
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
            }
        }
        .listStyle(.plain)
        .navigationTitle("InterSego")
    }
}

private struct RouteCardView: View {
    let route: BusRoute
    let isAvailable: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(route.number)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(isAvailable ? .primary : .secondary)

                Text(route.name)
                    .font(.body)
                    .foregroundColor(isAvailable ? .primary : .secondary)
            }

            Spacer()

            if isAvailable {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
            } else {
                HStack(spacing: 4) {
                    Image(systemName: "exclamationmark.triangle")
                        .foregroundColor(.secondary)
                    Text("Próximamente")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(.vertical, 8)
        .opacity(isAvailable ? 1.0 : 0.6)
    }
}
