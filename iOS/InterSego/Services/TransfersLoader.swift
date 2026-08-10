/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Loads `transfers.json` (bundled at the top level, alongside `Timetables/`) — the
/// walking-transfer table between distinct physical stops.
struct TransfersLoader {

    private struct TransfersFile: Decodable {
        let version: String
        let transfers: [TransferEntry]
    }

    private struct TransferEntry: Decodable {
        let from: String
        let to: String
        let meters: Int
        let walkMinutes: Int
    }

    func load() throws -> [TransferEdge] {
        guard let url = Bundle.main.url(forResource: "transfers", withExtension: "json") else {
            throw TimetableLoaderError.fileNotFound("transfers")
        }
        let data = try Data(contentsOf: url)
        let file = try JSONDecoder().decode(TransfersFile.self, from: data)
        return file.transfers.map { TransferEdge(from: $0.from, to: $0.to, meters: $0.meters, walkMinutes: $0.walkMinutes) }
    }
}
