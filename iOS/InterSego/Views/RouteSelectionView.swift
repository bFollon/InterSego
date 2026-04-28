/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
