/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

struct LandingView: View {
    let onShowRouteList: () -> Void
    let onFindClosestStop: () -> Void
    let onShowAbout: () -> Void
    let onShowReminders: () -> Void
    let onShowSettings: () -> Void
    let onBoardBus: () -> Void
    let isSearchingClosestStop: Bool
    let closestStopError: String?
    let isBoardingBus: Bool
    let boardingBusConfirmed: Bool
    let boardingBusError: String?

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

                Text("Metropolitanos de Segovia")
                    .font(.title2)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)
            }

            Spacer()

            VStack(spacing: 16) {
                Button(action: onFindClosestStop) {
                    HorizontalCard(
                        label: "Parada más cercana",
                        showChevron: true,
                        isLoading: isSearchingClosestStop,
                    ) {
                        Image(systemName: "location.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.accentColor)
                    }
                }
                .buttonStyle(.plain)
                .disabled(isSearchingClosestStop)

                Button(action: onBoardBus) {
                    HorizontalCard(
                        label: boardingBusConfirmed ? "¡Gracias por confirmar!" : "Estoy en el autobús",
                        labelColor: boardingBusConfirmed ? Color(UIColor.systemGreen) : .accentColor,
                        showChevron: false,
                        isLoading: isBoardingBus,
                    ) {
                        Image(systemName: boardingBusConfirmed ? "checkmark.circle.fill" : "bus")
                            .font(.system(size: 24))
                            .foregroundColor(boardingBusConfirmed ? Color(UIColor.systemGreen) : .accentColor)
                    }
                }
                .buttonStyle(.plain)
                .disabled(isBoardingBus || boardingBusConfirmed)

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                    Button(action: onShowRouteList) {
                        SquareCard(label: "Líneas de bus") {
                            BusLineIcon(size: 40)
                        }
                    }
                    .buttonStyle(.plain)
                    .aspectRatio(1, contentMode: .fit)

                    Button(action: onShowReminders) {
                        SquareCard(label: "Mis recordatorios") {
                            Image(systemName: "bell.fill")
                                .font(.system(size: 24))
                                .foregroundColor(.accentColor)
                                .frame(width: 40, height: 40)
                        }
                    }
                    .buttonStyle(.plain)
                    .aspectRatio(1, contentMode: .fit)
                }

                if let error = closestStopError {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
                }
                if let error = boardingBusError {
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
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: onShowSettings) {
                    Image(systemName: "gear")
                        .foregroundColor(.secondary)
                }
            }
        }
    }
}

private struct HorizontalCard<Icon: View>: View {
    let label: String
    var labelColor: Color = .accentColor
    let showChevron: Bool
    let isLoading: Bool
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        HStack(spacing: 16) {
            Group {
                if isLoading {
                    ProgressView()
                } else {
                    icon()
                }
            }
            .frame(width: 32, height: 32)

            Text(label)
                .font(.headline)
                .foregroundColor(labelColor)

            Spacer()

            if showChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 20)
        .frame(maxWidth: .infinity)
        .background(Color.green.opacity(0.1))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.green.opacity(0.3), lineWidth: 1)
        )
    }
}

private struct SquareCard<Icon: View>: View {
    let label: String
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        VStack(spacing: 12) {
            icon()
            Text(label)
                .font(.headline)
                .foregroundColor(.accentColor)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(16)
        .background(Color(UIColor.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.1), radius: 4, y: 2)
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
