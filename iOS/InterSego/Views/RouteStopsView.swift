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

struct RouteStopsView: View {
    let route: BusRoute
    let views: [RouteView]
    var initialViewId: String?
    var onAllRoutesSelected: (() -> Void)?
    let onStopSelected: (BusStop, String) -> Void
    let onMapSelected: (String) -> Void

    @State private var currentViewId: String = ""
    @Environment(\.dismiss) private var dismiss

    private var currentView: RouteView? {
        views.first { $0.id == currentViewId } ?? views.first
    }

    var body: some View {
        if let currentView {
            mainContent(currentView: currentView)
        } else {
            noServiceContent
        }
    }

    private var noServiceContent: some View {
        VStack(spacing: 0) {
            // "Ver todas las rutas" button
            if let onAllRoutes = onAllRoutesSelected {
                Button(action: onAllRoutes) {
                    HStack(spacing: 8) {
                        Text("Ver todas las rutas")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }

            Spacer()
            Text("Esta línea no ofrece servicio hoy")
                .font(.title3)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(32)
            Spacer()
        }
        .navigationTitle("Línea \(route.number)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                }
            }
        }
    }

    private func mainContent(currentView: RouteView) -> some View {
        VStack(spacing: 0) {
            // Tab chips (if available), with optional heading label above
            if let tabs = currentView.tabs, !tabs.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    if let heading = currentView.tabsLabel {
                        Text(heading)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 16)
                            .padding(.top, 8)
                            .padding(.bottom, 2)
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(tabs, id: \.viewId) { tab in
                                let tabView = views.first { $0.id == tab.viewId }
                                let isSelected = currentViewId == tab.viewId
                                    || tabView?.swapAction?.targetViewId == currentViewId
                                TabChip(label: tab.label, isSelected: isSelected) {
                                    if !isSelected {
                                        currentViewId = tab.viewId
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    }
                }
            }

            // "Ver todas las rutas" button
            if let onAllRoutes = onAllRoutesSelected {
                Button(action: onAllRoutes) {
                    HStack(spacing: 8) {
                        Text("Ver todas las rutas")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }

            // "Ruta de hoy" separator
            if onAllRoutesSelected != nil {
                HStack(spacing: 8) {
                    Text("Ruta de hoy")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(Color(.separator))
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 4)
            }

            // Stop list
            ScrollView {
                LazyVStack(spacing: 0) {
                    let stops = currentView.stops
                    let extendedLabel = currentView.extendedSectionLabel
                    let hasExtendedStops = extendedLabel != nil && stops.contains { $0.isExtendedOnly }

                    ForEach(Array(stops.enumerated()), id: \.offset) { index, viewStop in
                        // Extended section separator
                        if hasExtendedStops, index > 0 {
                            let prevIsExtended = stops[index - 1].isExtendedOnly
                            let currIsExtended = viewStop.isExtendedOnly
                            if prevIsExtended != currIsExtended {
                                ExtendedSectionSeparator(label: extendedLabel!)
                            }
                        }

                        StopRowView(
                            stop: viewStop.stop,
                            isExtended: viewStop.isExtendedOnly,
                            isFirst: index == 0,
                            isLast: index == stops.count - 1,
                        )
                        .contentShape(Rectangle())
                        .onTapGesture {
                            AnalyticsService.shared.track("stop_selected", with: ["stop": viewStop.stop.id, "route": route.id])
                            onStopSelected(viewStop.stop, currentView.id)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack {
                    Text("Línea \(route.number)")
                        .font(.headline)
                    Text(currentView.label)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    onMapSelected(currentView.id)
                } label: {
                    Image(systemName: "map")
                }
            }
            if currentView.swapAction != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        if let swap = currentView.swapAction {
                            currentViewId = swap.targetViewId
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                    }
                }
            }
        }
        .onAppear {
            if currentViewId.isEmpty {
                currentViewId = initialViewId ?? views.first?.id ?? ""
            }
        }
    }
}

// MARK: - Tab Chip

struct TabChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline)
                .fontWeight(isSelected ? .semibold : .regular)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(isSelected ? Color.accentColor.opacity(0.15) : Color(.systemGray6))
                .foregroundColor(isSelected ? .accentColor : .primary)
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(isSelected ? Color.accentColor : Color.clear, lineWidth: 1),
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Stop Row

struct StopRowView: View {
    let stop: BusStop
    let isExtended: Bool
    let isFirst: Bool
    let isLast: Bool

    private var lineColor: Color {
        isExtended ? .orange : .accentColor
    }

    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            // Route line indicator — fills full row height so lines connect between rows
            RouteLineIndicator(
                isFirst: isFirst,
                isLast: isLast,
                isExtended: isExtended,
                lineColor: lineColor,
            )
            .frame(width: 40)

            // Stop info — vertical padding here (not on HStack) keeps indicator edge-to-edge
            VStack(alignment: .leading, spacing: 2) {
                Text(stop.name)
                    .font(.body)
                    .fontWeight(.semibold)

                if let area = stop.area {
                    Text(area)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .padding(.vertical, 12)

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

// MARK: - Route Line Indicator

struct RouteLineIndicator: View {
    let isFirst: Bool
    let isLast: Bool
    let isExtended: Bool
    let lineColor: Color

    var body: some View {
        GeometryReader { geo in
            let midX = geo.size.width / 2
            let midY = geo.size.height / 2
            let dotSize: CGFloat = 12

            Canvas { context, size in
                // Line above dot
                if !isFirst {
                    let rect = CGRect(x: midX - 2, y: 0, width: 4, height: midY - dotSize / 2)
                    if isExtended {
                        drawDashedLine(context: context, rect: rect, color: lineColor)
                    } else {
                        context.fill(Path(rect), with: .color(lineColor))
                    }
                }

                // Line below dot
                if !isLast {
                    let rect = CGRect(x: midX - 2, y: midY + dotSize / 2, width: 4, height: size.height - midY - dotSize / 2)
                    if isExtended {
                        drawDashedLine(context: context, rect: rect, color: lineColor)
                    } else {
                        context.fill(Path(rect), with: .color(lineColor))
                    }
                }

                // Stop dot
                if isFirst {
                    // Chevron down for start
                    let chevronPath = Path { p in
                        p.move(to: CGPoint(x: midX - 8, y: midY - 4))
                        p.addLine(to: CGPoint(x: midX, y: midY + 4))
                        p.addLine(to: CGPoint(x: midX + 8, y: midY - 4))
                    }
                    context.stroke(chevronPath, with: .color(lineColor), lineWidth: 3)
                } else if isLast {
                    // Square for end
                    let rect = CGRect(x: midX - dotSize / 2, y: midY - dotSize / 2, width: dotSize, height: dotSize)
                    context.fill(Path(rect), with: .color(lineColor))
                } else if isExtended {
                    // Hollow circle for extended
                    let circle = Path(ellipseIn: CGRect(x: midX - dotSize / 2, y: midY - dotSize / 2, width: dotSize, height: dotSize))
                    context.fill(circle, with: .color(.white))
                    context.stroke(circle, with: .color(lineColor), lineWidth: 2.5)
                } else {
                    // Filled circle for regular
                    let circle = Path(ellipseIn: CGRect(x: midX - dotSize / 2, y: midY - dotSize / 2, width: dotSize, height: dotSize))
                    context.fill(circle, with: .color(lineColor))
                }
            }
        }
    }

    private func drawDashedLine(context: GraphicsContext, rect: CGRect, color: Color) {
        let path = Path { p in
            p.move(to: CGPoint(x: rect.midX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        }
        context.stroke(
            path,
            with: .color(color),
            style: StrokeStyle(lineWidth: 4, dash: [6, 4]),
        )
    }
}

// MARK: - Extended Section Separator

struct ExtendedSectionSeparator: View {
    let label: String

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.caption2)
                .fontWeight(.medium)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.orange.opacity(0.15))
                .foregroundColor(.orange)
                .clipShape(RoundedRectangle(cornerRadius: 4))

            Rectangle()
                .frame(height: 1)
                .foregroundColor(Color(.separator))
        }
        .padding(.leading, 56)
        .padding(.trailing, 16)
        .padding(.vertical, 4)
    }
}
