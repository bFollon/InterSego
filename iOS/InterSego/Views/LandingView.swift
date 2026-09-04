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
    let onMainCardTap: () -> Void
    var mainAction: MainLandingAction = .routes
    let onFindClosestStop: () -> Void
    let onShowAbout: () -> Void
    let onShowSettings: () -> Void
    let onShowOtrasOpciones: () -> Void
    let onBoardBus: () -> Void
    let isSearchingClosestStop: Bool
    let closestStopError: String?
    let isBoardingBus: Bool
    let boardingBusConfirmed: Bool
    let boardingBusError: String?
    let activeAlerts: [ServiceAlert]
    let onShowAlertDetail: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                if let primary = sortedAlerts.first {
                    Button(action: onShowAlertDetail) {
                        HStack(spacing: 4) {
                            Image(systemName: alertIconName(primary.severity))
                                .font(.caption.weight(.semibold))
                            Text(alertPillLabel(primary))
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(alertColor(primary.severity))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(alertColor(primary.severity).opacity(0.12))
                    .clipShape(Capsule())
                } else {
                    Text(" ")
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .opacity(0)
                }
                Spacer()
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)

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
                    Button(action: onMainCardTap) {
                        SquareCard(label: mainAction.label) {
                            MainLandingActionIcon(action: mainAction)
                        }
                    }
                    .buttonStyle(.plain)
                    .aspectRatio(1, contentMode: .fit)

                    Button(action: onShowOtrasOpciones) {
                        SquareCard(label: "Más opciones") {
                            Image(systemName: "square.grid.2x2")
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

    private var sortedAlerts: [ServiceAlert] {
        let order: [String: Int] = ["critical": 0, "warning": 1, "info": 2]
        return activeAlerts.sorted { (order[$0.severity] ?? 3) < (order[$1.severity] ?? 3) }
    }

    private func alertPillLabel(_ alert: ServiceAlert) -> String {
        let extra = activeAlerts.count - 1
        return extra > 0 ? "\(alert.title) (+\(extra))" : alert.title
    }

    private func alertColor(_ severity: String) -> Color {
        switch severity {
        case "critical": return .red
        case "warning":  return .orange
        default:         return .blue
        }
    }

    private func alertIconName(_ severity: String) -> String {
        switch severity {
        case "critical": return "exclamationmark.triangle.fill"
        case "warning":  return "exclamationmark.circle.fill"
        default:         return "info.circle.fill"
        }
    }
}

struct HorizontalCard<Icon: View>: View {
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

struct SquareCard<Icon: View>: View {
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

struct MainLandingActionIcon: View {
    let action: MainLandingAction
    var size: CGFloat = 40

    var body: some View {
        switch action {
        case .routes:
            BusLineIcon(size: size)
        case .routePlanner:
            Image(systemName: "point.topleft.down.curvedto.point.bottomright.up")
                .font(.system(size: 24))
                .foregroundColor(.accentColor)
                .frame(width: size, height: size)
        case .reminders:
            Image(systemName: "bell.fill")
                .font(.system(size: 24))
                .foregroundColor(.accentColor)
                .frame(width: size, height: size)
        case .anotherDay:
            Image(systemName: "calendar")
                .font(.system(size: 24))
                .foregroundColor(.accentColor)
                .frame(width: size, height: size)
        }
    }
}

struct BusLineIcon: View {
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
