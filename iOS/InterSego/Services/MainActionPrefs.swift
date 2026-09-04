/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// The action bound to Landing's configurable main card (replaces the "Líneas de bus" slot).
enum MainLandingAction: String, CaseIterable {
    case routes
    case routePlanner
    case reminders
    case anotherDay

    var label: String {
        switch self {
        case .routes: return "Líneas de bus"
        case .routePlanner: return "Planificar viaje"
        case .reminders: return "Mis recordatorios"
        case .anotherDay: return "Consultar otro día"
        }
    }
}

/// Manages the user's choice of Landing's configurable main action.
/// Uses UserDefaults for storage. Mirrors Android MainActionPrefs API.
struct MainActionPrefs {
    private static let keyAction = "mainLandingAction"

    static func getMainAction() -> MainLandingAction {
        guard let raw = UserDefaults.standard.string(forKey: keyAction),
              let action = MainLandingAction(rawValue: raw) else {
            return .routes
        }
        return action
    }

    static func setMainAction(_ action: MainLandingAction) {
        UserDefaults.standard.set(action.rawValue, forKey: keyAction)
    }
}
