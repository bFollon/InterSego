/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

actor DeviceTokenService {
    static let shared = DeviceTokenService()
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 15
        session = URLSession(configuration: config)
    }

    func registerToken(_ token: String, platform: String) async {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/device-tokens") else { return }
        guard let body = try? JSONSerialization.data(withJSONObject: ["token": token, "platform": platform]) else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(AppConfig.serverAPIKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("InterSego-iOS/1.0", forHTTPHeaderField: "User-Agent")
        request.httpBody = body
        _ = try? await session.data(for: request)
    }
}
