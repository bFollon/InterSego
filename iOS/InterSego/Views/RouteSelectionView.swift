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

    private var sortedRoutes: [BusRoute] {
        routes.sorted { a, b in
            let aAvailable = supportedRoutes.contains(a.id)
            let bAvailable = supportedRoutes.contains(b.id)
            if aAvailable != bAvailable { return aAvailable }
            return a.number < b.number
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("Selecciona una línea")
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 4)

                LazyVStack(spacing: 12) {
                    ForEach(sortedRoutes) { route in
                        let isAvailable = supportedRoutes.contains(route.id)
                        RouteCardView(route: route, isAvailable: isAvailable)
                            .contentShape(RoundedRectangle(cornerRadius: 12))
                            .onTapGesture {
                                if isAvailable {
                                    AnalyticsService.shared.track("route_selected", with: ["route": route.id])
                                    onRouteSelected(route)
                                }
                            }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 16)
            }
        }
        .background(Color(.systemGroupedBackground))
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
                    .foregroundColor(isAvailable ? .accentColor : .secondary)

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
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.secondary)
                    Text("Próximamente")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.secondarySystemGroupedBackground))
                .opacity(isAvailable ? 1.0 : 0.5),
        )
        .shadow(color: .black.opacity(isAvailable ? 0.1 : 0), radius: 4, y: 2)
        .opacity(isAvailable ? 1.0 : 0.6)
    }
}
