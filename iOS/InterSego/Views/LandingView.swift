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

struct LandingView: View {
    let onShowRouteList: () -> Void
    let onFindClosestStop: () -> Void
    let onShowAbout: () -> Void
    let isSearchingClosestStop: Bool
    let closestStopError: String?

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 8) {
                Text("InterSego")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.green, .blue],
                            startPoint: .leading,
                            endPoint: .trailing,
                        ),
                    )

                Text("Interurbanos de Segovia")
                    .font(.title2)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)
            }

            Spacer()

            VStack(spacing: 16) {
                Button(action: onFindClosestStop) {
                    VStack(spacing: 14) {
                        if isSearchingClosestStop {
                            ProgressView()
                                .frame(width: 60, height: 60)
                        } else {
                            Image(systemName: "location.fill")
                                .font(.system(size: 32))
                                .foregroundColor(.accentColor)
                                .frame(width: 60, height: 60)
                        }
                        Text("Parada más cercana")
                            .font(.headline)
                            .foregroundColor(.accentColor)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 28)
                    .background(Color(UIColor.secondarySystemGroupedBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.1), radius: 4, y: 2)
                }
                .buttonStyle(.plain)
                .disabled(isSearchingClosestStop)

                Button(action: onShowRouteList) {
                    VStack(spacing: 14) {
                        BusLineIcon(size: 60)
                        Text("Líneas de bus")
                            .font(.headline)
                            .foregroundColor(.accentColor)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 28)
                    .background(Color(UIColor.secondarySystemGroupedBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.1), radius: 4, y: 2)
                }
                .buttonStyle(.plain)

                if let error = closestStopError {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
                }
            }
            .padding(.horizontal, 24)

            Spacer()

            Button(action: onShowAbout) {
                Text("Acerca de")
                    .font(.footnote)
                    .foregroundColor(.accentColor)
            }
            .padding(.bottom, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .toolbar(.hidden, for: .navigationBar)
    }
}

private struct BusLineIcon: View {
    let size: CGFloat

    var body: some View {
        Canvas { context, canvasSize in
            let midY = canvasSize.height / 2
            let lineStart = CGPoint(x: canvasSize.width * 0.05, y: midY)
            let lineEnd = CGPoint(x: canvasSize.width * 0.95, y: midY)
            let lineWidth = canvasSize.width * 0.07
            let stopRadius = canvasSize.width * 0.07
            let stopColor = Color.accentColor

            // Horizontal line
            var linePath = Path()
            linePath.move(to: lineStart)
            linePath.addLine(to: lineEnd)
            context.stroke(
                linePath,
                with: .color(stopColor),
                style: StrokeStyle(lineWidth: lineWidth, lineCap: .round),
            )

            // 4 evenly distributed stops
            let stopXPositions: [CGFloat] = [0.05, 0.368, 0.632, 0.95]
            for xRatio in stopXPositions {
                let center = CGPoint(x: canvasSize.width * xRatio, y: midY)
                let rect = CGRect(
                    x: center.x - stopRadius,
                    y: center.y - stopRadius,
                    width: stopRadius * 2,
                    height: stopRadius * 2,
                )
                context.fill(Path(ellipseIn: rect), with: .color(stopColor))
            }
        }
        .frame(width: size, height: size)
    }
}
