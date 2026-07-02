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

    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 15
        session = URLSession(configuration: config)
    }

    func fetchActiveAlerts() async -> [ServiceAlert] {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/alerts") else { return [] }
        guard let (data, _) = try? await session.data(from: url) else { return [] }
        return (try? JSONDecoder().decode([ServiceAlert].self, from: data)) ?? []
    }
}
