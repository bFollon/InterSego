/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Reports each pill's natural (unconstrained) label height so the pill row can equalize on
/// the tallest one — safer than approximating a fixed height from font metrics, which risks
/// under-reserving space and clipping real 2-line labels into an ellipsis.
private struct PillLabelHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// Keyframe values for `BouncingBallLoader`'s ball: horizontal position plus a squash-and-stretch
/// scale pulse timed to hit as the ball touches each end of the track.
private struct BouncingBallKeyframes {
    var x: CGFloat = 0
    var scaleX: CGFloat = 1
    var scaleY: CGFloat = 1
}

/// Small indeterminate indicator shown while the app is contacting the server: a dot bouncing back
/// and forth along a track, with a brief squash at each end. When `hasError` is true, the ball is
/// replaced with a blinking red X centered on the track, so a fetch timeout/failure reads as
/// distinct from "still loading".
struct BouncingBallLoader: View {
    var color: Color = .accentColor
    var hasError: Bool = false

    @State private var errorBlinkVisible = false

    private let trackWidth: CGFloat = 64
    private let trackHeight: CGFloat = 16
    private let ballDiameter: CGFloat = 8
    private let legDuration: TimeInterval = 0.7

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "iphone")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(color)

            trackView

            Image(systemName: "cloud.fill")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(color)
        }
    }

    private var trackView: some View {
        ZStack(alignment: .leading) {
            Capsule()
                .fill(color.opacity(0.25))
                .frame(width: trackWidth - ballDiameter, height: 2)
                .frame(width: trackWidth, height: trackHeight)

            Circle()
                .fill(color)
                .frame(width: ballDiameter, height: ballDiameter)
                .opacity(hasError ? 0 : 1)
                .keyframeAnimator(
                    initialValue: BouncingBallKeyframes(),
                    repeating: true
                ) { content, value in
                    content
                        .scaleEffect(x: value.scaleX, y: value.scaleY)
                        .offset(x: value.x)
                } keyframes: { _ in
                    KeyframeTrack(\.x) {
                        CubicKeyframe(0, duration: 0)
                        CubicKeyframe(trackWidth - ballDiameter, duration: legDuration)
                        CubicKeyframe(0, duration: legDuration)
                    }
                    // Flatten along the direction of travel (horizontal) and bulge perpendicular
                    // (vertical) — matches a ball bouncing off a wall it's moving into, not one
                    // dropping onto a floor.
                    KeyframeTrack(\.scaleX) {
                        CubicKeyframe(0.78, duration: 0)
                        CubicKeyframe(1, duration: 0.08)
                        CubicKeyframe(1, duration: legDuration - 0.16)
                        CubicKeyframe(0.78, duration: 0.08)
                        CubicKeyframe(1, duration: 0.08)
                        CubicKeyframe(1, duration: legDuration - 0.16)
                        CubicKeyframe(0.78, duration: 0.08)
                    }
                    KeyframeTrack(\.scaleY) {
                        CubicKeyframe(1.22, duration: 0)
                        CubicKeyframe(1, duration: 0.08)
                        CubicKeyframe(1, duration: legDuration - 0.16)
                        CubicKeyframe(1.22, duration: 0.08)
                        CubicKeyframe(1, duration: 0.08)
                        CubicKeyframe(1, duration: legDuration - 0.16)
                        CubicKeyframe(1.22, duration: 0.08)
                    }
                }

            if hasError {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .heavy))
                    .foregroundStyle(.red)
                    .opacity(errorBlinkVisible ? 1 : 0.25)
                    .frame(width: trackWidth, alignment: .center)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true)) {
                            errorBlinkVisible = true
                        }
                    }
            }
        }
        .frame(width: trackWidth, height: trackHeight)
    }
}

/// Resolves each pool action to its navigation callback. Shared with the "Más opciones" hub.
struct LandingActionCallbacks {
    let onNavigateToRouteList: () -> Void
    let onPlanJourney: () -> Void
    let onShowReminders: () -> Void
    let onOpenAnotherDay: () -> Void
    let onShowFavorites: () -> Void
    let onBoardBus: () -> Void

    func forAction(_ action: MainLandingAction) -> () -> Void {
        switch action {
        case .routes: return onNavigateToRouteList
        case .routePlanner: return onPlanJourney
        case .reminders: return onShowReminders
        case .anotherDay: return onOpenAnotherDay
        case .favorites: return onShowFavorites
        case .boardBus: return onBoardBus
        }
    }
}

struct LandingView: View {
    let landingSlots: [LandingSlot: MainLandingAction]
    let onFindClosestStop: () -> Void
    let onShowAbout: () -> Void
    let onShowSettings: () -> Void
    let onShowOtrasOpciones: () -> Void
    let onNavigateToRouteList: () -> Void
    let onPlanJourney: () -> Void
    let onShowReminders: () -> Void
    let onOpenAnotherDay: () -> Void
    let onShowFavorites: () -> Void
    let onBoardBus: () -> Void
    let isSearchingClosestStop: Bool
    let closestStopError: String?
    let isBoardingBus: Bool
    let boardingBusConfirmed: Bool
    let boardingBusError: String?
    let activeAlerts: [ServiceAlert]
    let onShowAlertDetail: () -> Void
    let laLigaBlockingSuspected: Bool
    let onShowLaLigaDetail: () -> Void
    var serverUnreachable: Bool = false
    var isFetchingManifest: Bool = false
    var manifestFetchFailed: Bool = false

    @State private var pillLabelHeight: CGFloat? = nil

    private var callbacks: LandingActionCallbacks {
        LandingActionCallbacks(
            onNavigateToRouteList: onNavigateToRouteList,
            onPlanJourney: onPlanJourney,
            onShowReminders: onShowReminders,
            onOpenAnotherDay: onOpenAnotherDay,
            onShowFavorites: onShowFavorites,
            onBoardBus: onBoardBus,
        )
    }

    private var slot1: MainLandingAction { landingSlots[.main1] ?? LandingSlot.main1.defaultAction }
    private var slot2: MainLandingAction { landingSlots[.main2] ?? LandingSlot.main2.defaultAction }
    private var extra1: MainLandingAction { landingSlots[.extra1] ?? LandingSlot.extra1.defaultAction }
    private var extra2: MainLandingAction { landingSlots[.extra2] ?? LandingSlot.extra2.defaultAction }
    private var boardingBusVisible: Bool { landingSlots.values.contains(.boardBus) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                if isFetchingManifest {
                    BouncingBallLoader(hasError: manifestFetchFailed)
                }
                if let primary = isFetchingManifest ? nil : sortedAlerts.first {
                    Button(action: {
                        AnalyticsService.shared.track("alert_banner_tapped", with: ["severity": primary.severity])
                        onShowAlertDetail()
                    }) {
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

            // Reserved slot, always at least this tall regardless of which (if any) banner is
            // showing — otherwise the banner appearing/disappearing reflows every Spacer() below
            // it (title block + action cards both shift; only the "Acerca de" button at the very
            // bottom stays put, since it's the last fixed element in the stack).
            VStack(spacing: 0) {
                if laLigaBlockingSuspected {
                    Button(action: {
                        AnalyticsService.shared.track("laliga_blocking_banner_tapped")
                        onShowLaLigaDetail()
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "soccerball")
                                .font(.subheadline)
                            Text("Sin conexión al servidor: posible bloqueo de LaLiga en curso")
                                .font(.caption.weight(.medium))
                                .multilineTextAlignment(.leading)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(Color.orange.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .foregroundColor(.orange)
                    .padding(.horizontal, 24)
                    .padding(.top, 4)
                } else if serverUnreachable {
                    // Generic counterpart to the LaLiga banner above — shown when the server was
                    // unreachable at startup but there's no specific evidence of a LaLiga blocking
                    // wave (device fully offline, our server down for unrelated reasons, etc).
                    // Mutually exclusive with the LaLiga banner since both explain the same
                    // underlying symptom (stale/cached data) and only one cause is surfaced at a time.
                    HStack(spacing: 8) {
                        Image(systemName: "icloud.slash")
                            .font(.subheadline)
                        Text("Sin conexión con el servidor: los datos mostrados pueden no estar actualizados")
                            .font(.caption.weight(.medium))
                            .multilineTextAlignment(.leading)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color.red.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .foregroundColor(.red)
                    .padding(.horizontal, 24)
                    .padding(.top, 4)
                }
            }
            .frame(minHeight: 68, alignment: .top)

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

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                    LandingActionSquare(
                        action: slot1,
                        callbacks: callbacks,
                        isBoardingBus: isBoardingBus,
                        boardingBusConfirmed: boardingBusConfirmed,
                    )
                    LandingActionSquare(
                        action: slot2,
                        callbacks: callbacks,
                        isBoardingBus: isBoardingBus,
                        boardingBusConfirmed: boardingBusConfirmed,
                    )
                }

                HStack(spacing: 12) {
                    LandingActionPill(
                        action: extra1,
                        callbacks: callbacks,
                        isBoardingBus: isBoardingBus,
                        boardingBusConfirmed: boardingBusConfirmed,
                        labelHeight: pillLabelHeight,
                    )
                    LandingActionPill(
                        action: extra2,
                        callbacks: callbacks,
                        isBoardingBus: isBoardingBus,
                        boardingBusConfirmed: boardingBusConfirmed,
                        labelHeight: pillLabelHeight,
                    )

                    Button(action: {
                        AnalyticsService.shared.track("otras_opciones_opened")
                        onShowOtrasOpciones()
                    }) {
                        PillCard(
                            label: "Más opciones",
                            tint: Color(UIColor.secondarySystemGroupedBackground),
                            border: nil,
                            contentColor: .secondary,
                            elevated: true,
                            labelHeight: pillLabelHeight,
                        ) {
                            Image(systemName: "square.grid.2x2")
                                .font(.system(size: 24))
                                .foregroundColor(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
                .onPreferenceChange(PillLabelHeightKey.self) { pillLabelHeight = $0 }

                if let error = closestStopError {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
                }
                if boardingBusVisible, let error = boardingBusError {
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

/// One of Landing's 2 square "acción principal" cards — resolves label/subtitle/icon/tap
/// from the pool action, special-casing `.boardBus`'s live loading/confirmed state.
struct LandingActionSquare: View {
    let action: MainLandingAction
    let callbacks: LandingActionCallbacks
    let isBoardingBus: Bool
    let boardingBusConfirmed: Bool

    private var isBoard: Bool { action == .boardBus }
    private var confirmed: Bool { isBoard && boardingBusConfirmed }
    private var loading: Bool { isBoard && isBoardingBus }
    private var accent: Color { isBoard ? Color(UIColor.systemGreen) : .accentColor }
    private var tint: Color { isBoard ? Color.green.opacity(0.1) : Color.accentColor.opacity(0.08) }
    private var border: Color { isBoard ? Color.green.opacity(0.3) : Color.accentColor.opacity(0.18) }

    var body: some View {
        Button(action: {
            if !isBoard {
                AnalyticsService.shared.track("main_card_tapped", with: ["action": action.rawValue])
            }
            callbacks.forAction(action)()
        }) {
            SquareCard(
                label: confirmed ? "¡Gracias por confirmar!" : action.label,
                subtitle: confirmed ? "Viaje confirmado" : action.subtitle,
                tint: tint,
                border: border,
                accentColor: accent,
            ) {
                if loading {
                    ProgressView().frame(width: 32, height: 32)
                } else {
                    MainLandingActionIcon(action: action, size: 36, confirmed: confirmed)
                }
            }
        }
        .buttonStyle(.plain)
        .aspectRatio(1, contentMode: .fit)
        .disabled(loading || confirmed)
    }
}

/// One of Landing's 2 compact "acción adicional" pills — same resolution as `LandingActionSquare`.
struct LandingActionPill: View {
    let action: MainLandingAction
    let callbacks: LandingActionCallbacks
    let isBoardingBus: Bool
    let boardingBusConfirmed: Bool
    var labelHeight: CGFloat? = nil

    private var isBoard: Bool { action == .boardBus }
    private var confirmed: Bool { isBoard && boardingBusConfirmed }
    private var loading: Bool { isBoard && isBoardingBus }
    private var accent: Color { isBoard ? Color(UIColor.systemGreen) : .accentColor }
    private var tint: Color { isBoard ? Color.green.opacity(0.1) : Color.accentColor.opacity(0.08) }
    private var border: Color { isBoard ? Color.green.opacity(0.3) : Color.accentColor.opacity(0.18) }

    var body: some View {
        Button(action: callbacks.forAction(action)) {
            PillCard(
                label: confirmed ? "¡Confirmado!" : action.label,
                tint: tint,
                border: border,
                contentColor: accent,
                labelHeight: labelHeight,
            ) {
                if loading {
                    ProgressView().frame(width: 24, height: 24)
                } else {
                    MainLandingActionIcon(action: action, size: 28, confirmed: confirmed)
                }
            }
        }
        .buttonStyle(.plain)
        .disabled(loading || confirmed)
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
    var subtitle: String? = nil
    var tint: Color = Color(UIColor.secondarySystemGroupedBackground)
    var border: Color? = nil
    var accentColor: Color = .accentColor
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        Group {
            if let subtitle {
                VStack(alignment: .leading, spacing: 0) {
                    icon()
                        .frame(width: 36, height: 36, alignment: .topLeading)
                    Spacer(minLength: 8)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(label)
                            .font(.title3)
                            .fontWeight(.bold)
                            .foregroundColor(accentColor)
                            .multilineTextAlignment(.leading)
                        Text(subtitle)
                            .font(.subheadline)
                            .foregroundColor(accentColor.opacity(0.7))
                            .multilineTextAlignment(.leading)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(18)
            } else {
                VStack(spacing: 12) {
                    icon()
                    Text(label)
                        .font(.headline)
                        .foregroundColor(accentColor)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(16)
            }
        }
        .background(tint)
        .overlay(
            Group {
                if let border {
                    RoundedRectangle(cornerRadius: 20).stroke(border, lineWidth: 1)
                }
            }
        )
        .clipShape(RoundedRectangle(cornerRadius: subtitle != nil ? 20 : 16))
        .shadow(color: border == nil ? .black.opacity(0.1) : .clear, radius: 4, y: 2)
    }
}

struct PillCard<Icon: View>: View {
    let label: String
    let tint: Color
    var border: Color? = nil
    let contentColor: Color
    var elevated: Bool = false
    /// Shared height applied once known (see `PillLabelHeightKey`); nil on the first, measuring pass.
    var labelHeight: CGFloat? = nil
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        VStack(spacing: 8) {
            icon()
            Text(label)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(contentColor)
                .multilineTextAlignment(.center)
                .frame(height: labelHeight, alignment: .center)
                .background(
                    GeometryReader { geo in
                        Color.clear.preference(key: PillLabelHeightKey.self, value: geo.size.height)
                    }
                )
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .padding(.horizontal, 8)
        .background(tint)
        .overlay(
            Group {
                if let border {
                    RoundedRectangle(cornerRadius: 18).stroke(border, lineWidth: 1)
                }
            }
        )
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .shadow(color: elevated ? .black.opacity(0.08) : .clear, radius: elevated ? 4 : 0, y: elevated ? 2 : 0)
    }
}

struct MainLandingActionIcon: View {
    let action: MainLandingAction
    var size: CGFloat = 40
    var confirmed: Bool = false

    private var glyphSize: CGFloat { size * 0.7 }

    var body: some View {
        switch action {
        case .routes:
            BusLineIcon(size: size)
        case .routePlanner:
            Image(systemName: "point.topleft.down.curvedto.point.bottomright.up")
                .font(.system(size: glyphSize))
                .foregroundColor(.accentColor)
                .frame(width: size, height: size)
        case .reminders:
            Image(systemName: "bell.fill")
                .font(.system(size: glyphSize))
                .foregroundColor(.accentColor)
                .frame(width: size, height: size)
        case .anotherDay:
            Image(systemName: "calendar")
                .font(.system(size: glyphSize))
                .foregroundColor(.accentColor)
                .frame(width: size, height: size)
        case .boardBus:
            Image(systemName: confirmed ? "checkmark.circle.fill" : "bus")
                .font(.system(size: glyphSize))
                .foregroundColor(Color(UIColor.systemGreen))
                .frame(width: size, height: size)
        case .favorites:
            Image(systemName: "star.fill")
                .font(.system(size: glyphSize))
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
