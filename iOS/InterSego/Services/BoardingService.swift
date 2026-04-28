/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Handles all network communication with the InterSego boarding notification server.
///
/// The server is a dumb repository — it stores raw boarding events and returns them.
/// All trip matching and ETA computation happen on the client using local timetable data.
actor BoardingService {
    static let shared = BoardingService()

    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 15
        session = URLSession(configuration: config)
    }

    // MARK: - POST /boardings

    /// Submit a boarding event to the server.
    ///
    /// - Throws: `BoardingServiceError` on HTTP or network failure.
    func postBoarding(_ request: BoardingRequest) async throws {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/boardings") else {
            throw BoardingServiceError.invalidURL
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("Bearer \(AppConfig.boardingAPIKey)", forHTTPHeaderField: "Authorization")
        urlRequest.setValue("InterSego-iOS/1.0", forHTTPHeaderField: "User-Agent")

        do {
            urlRequest.httpBody = try JSONEncoder().encode(request)
        } catch {
            throw BoardingServiceError.encodingFailed
        }

        DebugConfig.debugPrint("BoardingService: posting boarding for tripKey \(request.tripKey)")

        let (_, response) = try await session.data(for: urlRequest)

        guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            DebugConfig.debugError("BoardingService: postBoarding failed with HTTP \(code)")
            throw BoardingServiceError.httpError(statusCode: code)
        }

        DebugConfig.debugPrint("BoardingService: postBoarding succeeded")
    }

    // MARK: - GET /boardings

    /// Fetch all active (non-expired) boarding events.
    ///
    /// Never throws — returns an empty array on any error and logs the failure.
    func fetchActiveBoardings() async -> [BoardingEvent] {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/boardings") else {
            DebugConfig.debugError("BoardingService: invalid server URL")
            return []
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.setValue("Bearer \(AppConfig.boardingAPIKey)", forHTTPHeaderField: "Authorization")
        urlRequest.setValue("InterSego-iOS/1.0", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await session.data(for: urlRequest)
            guard let http = response as? HTTPURLResponse, (200 ... 299).contains(http.statusCode) else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? -1
                DebugConfig.debugWarn("BoardingService: fetchActiveBoardings HTTP \(code)")
                return []
            }
            let events = (try? JSONDecoder().decode([BoardingEvent].self, from: data)) ?? []
            DebugConfig.debugPrint("BoardingService: fetched \(events.count) active boardings")
            return events
        } catch {
            DebugConfig.debugWarn("BoardingService: fetchActiveBoardings error: \(error.localizedDescription)")
            return []
        }
    }
}

// MARK: - Error

enum BoardingServiceError: LocalizedError {
    case invalidURL
    case encodingFailed
    case httpError(statusCode: Int)

    var errorDescription: String? {
        switch self {
        case .invalidURL: "URL del servidor no válida."
        case .encodingFailed: "No se pudo preparar la solicitud."
        case .httpError(let code): "Error del servidor (HTTP \(code))."
        }
    }
}
