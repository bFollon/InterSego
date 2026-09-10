/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// The pool of actions that can be pinned to one of Landing's 4 configurable slots
/// (see `LandingSlot`) or, if not pinned anywhere, fall back to the "Más opciones" hub.
enum MainLandingAction: String, CaseIterable {
    case boardBus
    case routes
    case routePlanner
    case reminders
    case anotherDay
    case favorites

    var label: String {
        switch self {
        case .boardBus: return "Estoy en el autobús"
        case .routes: return "Líneas de bus"
        case .routePlanner: return "Planificar viaje"
        case .reminders: return "Mis recordatorios"
        case .anotherDay: return "Consultar otro día"
        case .favorites: return "Favoritos"
        }
    }

    var subtitle: String {
        switch self {
        case .boardBus: return "Confirma tu viaje"
        case .routes: return "Horarios y paradas"
        case .routePlanner: return "Encuentra tu ruta"
        case .reminders: return "Avisos programados"
        case .anotherDay: return "Horarios de otro día"
        case .favorites: return "Tus paradas guardadas"
        }
    }
}

/// Landing's 4 configurable slots: 2 square "main" cards + 2 compact "extra" pills.
/// ("Parada más cercana" and "Más opciones" are fixed and not part of this pool.)
enum LandingSlot: String, CaseIterable {
    case main1
    case main2
    case extra1
    case extra2

    var label: String {
        switch self {
        case .main1: return "Acción principal 1"
        case .main2: return "Acción principal 2"
        case .extra1: return "Acción adicional 1"
        case .extra2: return "Acción adicional 2"
        }
    }

    var defaultAction: MainLandingAction {
        switch self {
        case .main1: return .boardBus
        case .main2: return .routes
        case .extra1: return .favorites
        case .extra2: return .reminders
        }
    }
}

/// Manages the user's assignment of `MainLandingAction`s to Landing's 4 `LandingSlot`s.
/// Guarantees the 4 returned actions are always distinct — `getSlots()` self-heals any
/// duplicate/invalid/missing stored value by falling back to the next unused pool action.
/// Uses UserDefaults for storage. Mirrors Android LandingLayoutPrefs API.
struct LandingLayoutPrefs {
    private static let legacyKeyAction = "mainLandingAction"

    private static func key(for slot: LandingSlot) -> String {
        "landingLayout.\(slot.rawValue)"
    }

    /// The old single "Acción principal" setting occupied the square slot next to the
    /// (then hardcoded) "Estoy en el autobús" card — migrate it into `main2` once.
    private static func migrateLegacyIfNeeded() {
        let alreadyConfigured = LandingSlot.allCases.contains {
            UserDefaults.standard.string(forKey: key(for: $0)) != nil
        }
        guard !alreadyConfigured else { return }

        if let legacyRaw = UserDefaults.standard.string(forKey: legacyKeyAction),
           let legacyAction = MainLandingAction(rawValue: legacyRaw) {
            UserDefaults.standard.set(legacyAction.rawValue, forKey: key(for: .main2))
        }
    }

    /// Always returns 4 distinct actions, one per slot, self-healing bad/duplicate stored data.
    static func getSlots() -> [LandingSlot: MainLandingAction] {
        migrateLegacyIfNeeded()

        var used = Set<MainLandingAction>()
        var resolved: [LandingSlot: MainLandingAction] = [:]
        var needsPersist = false

        for slot in LandingSlot.allCases {
            let stored = UserDefaults.standard.string(forKey: key(for: slot)).flatMap { MainLandingAction(rawValue: $0) }
            let chosen: MainLandingAction
            if let stored, !used.contains(stored) {
                chosen = stored
            } else {
                needsPersist = true
                chosen = ([slot.defaultAction] + MainLandingAction.allCases).first { !used.contains($0) } ?? slot.defaultAction
            }
            used.insert(chosen)
            resolved[slot] = chosen
        }

        if needsPersist {
            for (slot, action) in resolved {
                UserDefaults.standard.set(action.rawValue, forKey: key(for: slot))
            }
        }

        return resolved
    }

    static func getAction(_ slot: LandingSlot) -> MainLandingAction {
        getSlots()[slot] ?? slot.defaultAction
    }

    /// Swaps whichever slot currently holds `action` with `slot`'s previous value, so no duplicates occur.
    static func setAction(_ slot: LandingSlot, _ action: MainLandingAction) {
        var current = getSlots()
        let previousOwner = current.first { $0.value == action && $0.key != slot }?.key
        let previousValue = current[slot]

        current[slot] = action
        if let previousOwner, let previousValue {
            current[previousOwner] = previousValue
        }

        for (s, a) in current {
            UserDefaults.standard.set(a.rawValue, forKey: key(for: s))
        }
    }
}
