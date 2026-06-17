/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

struct ServiceAlert: Identifiable, Codable {
    let id: String
    let title: String
    let message: String
    let severity: String          // "info" | "warning" | "critical"
    let affectedRoutes: [String]?
    let startsAt: String
    let endsAt: String
}

actor AlertService {
    static let shared = AlertService()
    private init() {}

    func fetchActiveAlerts() async -> [ServiceAlert] {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/alerts") else { return [] }
        guard let (data, _) = try? await URLSession.shared.data(from: url) else { return [] }
        return (try? JSONDecoder().decode([ServiceAlert].self, from: data)) ?? []
    }
}
