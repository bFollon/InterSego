/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import SwiftUI

/// Coordinates the "report error" / "send suggestion" flow:
/// if the user has opted into error reporting, present the in-app feedback
/// form (BugSink); otherwise offer to opt in or fall back to email.
final class FeedbackCoordinator: ObservableObject {
    @Published var presentedCategory: FeedbackCategory?
    @Published var noConsentCategory: FeedbackCategory?

    func trigger(_ category: FeedbackCategory) {
        if MonitoringPreferencesService.shared.hasUserOptedIn() {
            presentedCategory = category
        } else {
            noConsentCategory = category
        }
    }

    func enableAndPresent() {
        guard let category = noConsentCategory else { return }
        MonitoringPreferencesService.shared.setMonitoringEnabled(true)
        ErrorReportingService.shared.initialize()
        noConsentCategory = nil
        presentedCategory = category
    }
}

private struct FeedbackPresentationModifier: ViewModifier {
    @ObservedObject var coordinator: FeedbackCoordinator
    @Environment(\.openURL) private var openURL

    func body(content: Content) -> some View {
        content
            .sheet(item: $coordinator.presentedCategory) { category in
                FeedbackFormView(category: category)
            }
            .alert(
                "Informes de error desactivados",
                isPresented: Binding(
                    get: { coordinator.noConsentCategory != nil },
                    set: { isPresented in
                        if !isPresented { coordinator.noConsentCategory = nil }
                    }
                )
            ) {
                Button("Activar informes de error") {
                    coordinator.enableAndPresent()
                }
                Button("Enviar por email") {
                    if let category = coordinator.noConsentCategory,
                       let url = makeMailtoURL(subject: category.mailtoSubject, body: category.mailtoBody) {
                        openURL(url)
                    }
                    coordinator.noConsentCategory = nil
                }
                Button("Cancelar", role: .cancel) {
                    coordinator.noConsentCategory = nil
                }
            } message: {
                Text("Los informes de error están desactivados. Puedes activarlos para enviar el mensaje directamente, o contactar por email.")
            }
    }
}

extension View {
    func feedbackPresentation(_ coordinator: FeedbackCoordinator) -> some View {
        modifier(FeedbackPresentationModifier(coordinator: coordinator))
    }
}

/// Builds a `mailto:` URL to the developer's contact address.
func makeMailtoURL(subject: String, body: String) -> URL? {
    let encodedSubject = subject.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
    let encodedBody = body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
    return URL(string: "mailto:bfollon.dev@icloud.com?subject=\(encodedSubject)&body=\(encodedBody)")
}
