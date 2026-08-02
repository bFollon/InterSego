/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Loads and caches the festivo calendar (see `docs/HOLIDAY_CALENDAR.md`).
///
/// Offline-first: `initialize()` loads whatever is available (disk cache, then bundled
/// resource) synchronously into memory with no network involved, so a device that has
/// never been online still resolves plain weekday/Saturday/Sunday correctly — a missing
/// calendar is not an error, `isHoliday(_:)` just returns false for every date.
/// `refresh()` is the network half, mirroring `TimetableCacheService`/`PolylineCacheService`:
/// GET /api/holidays, ETag/If-None-Match, disk cache at `Caches/Holidays/all.json`.
///
/// Unlike timetables there's no per-screen in-memory cache to evict — `refresh()` simply
/// replaces the in-memory date set when a fetch lands, and every future `isHoliday(_:)`
/// call sees the update immediately.
///
/// Bundles a copy of the current year's calendar (`Holidays/2026.json`) as a cold-start
/// floor: it will go stale year over year, but it means a fresh install with no
/// connectivity yet isn't wrong about this year's festivos. Out of scope here: wiring
/// this into actual day-type resolution (`TimetableQuery.dayTypesForDate`) — that's a
/// separate card.
actor HolidayService {
    static let shared = HolidayService()

    private let session: URLSession
    private var holidayDates: Set<String> = []

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 15
        session = URLSession(configuration: config)
    }

    // MARK: - Cache file URL

    nonisolated static func cacheDirectory() -> URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("Holidays")
    }

    nonisolated static func cacheURL() -> URL? {
        cacheDirectory()?.appendingPathComponent("all.json")
    }

    // MARK: - ETag persistence

    private func storedEtag() -> String? {
        UserDefaults.standard.string(forKey: "holiday_etag_all")
    }

    private func saveEtag(_ etag: String) {
        UserDefaults.standard.set(etag, forKey: "holiday_etag_all")
    }

    // MARK: - Parsing

    private struct HolidayEntry: Decodable {
        let date: String
    }

    /// Server responds with a JSON array, one entry per known year.
    private struct HolidayYearFile: Decodable {
        let holidays: [HolidayEntry]
    }

    private func dates(fromYearArray data: Data) throws -> Set<String> {
        let years = try JSONDecoder().decode([HolidayYearFile].self, from: data)
        return Set(years.flatMap { $0.holidays.map(\.date) })
    }

    /// Bundled `Holidays/{year}.json` resources are a single year object each.
    private func dates(fromBundleResource data: Data) throws -> Set<String> {
        let year = try JSONDecoder().decode(HolidayYearFile.self, from: data)
        return Set(year.holidays.map(\.date))
    }

    private func loadFromDisk() -> Set<String>? {
        guard let fileURL = Self.cacheURL(),
              let data = try? Data(contentsOf: fileURL) else {
            return nil
        }
        do {
            let dates = try self.dates(fromYearArray: data)
            return dates.isEmpty ? nil : dates
        } catch {
            DebugConfig.debugError("HolidayService: failed to parse disk cache", error: error)
            return nil
        }
    }

    private func loadFromBundle() -> Set<String> {
        guard let urls = Bundle.main.urls(forResourcesWithExtension: "json", subdirectory: "Holidays") else {
            return []
        }
        var result: Set<String> = []
        for url in urls {
            do {
                let data = try Data(contentsOf: url)
                result.formUnion(try dates(fromBundleResource: data))
            } catch {
                DebugConfig.debugError("HolidayService: failed to parse bundled resource \(url.lastPathComponent)", error: error)
            }
        }
        return result
    }

    // MARK: - Public API

    /// Loads whatever holiday data is available (disk cache, else bundled resource) into
    /// memory. No network access — safe to call unconditionally at startup, offline or not.
    func initialize() {
        holidayDates = loadFromDisk() ?? loadFromBundle()
        DebugConfig.debugPrint("HolidayService: loaded \(holidayDates.count) holiday date(s) into memory")
    }

    /// Fetches the latest calendar from the server and, on success, replaces the
    /// in-memory date set and disk cache. Call only when online; failures are logged
    /// and otherwise silent — `isHoliday(_:)` keeps serving whatever `initialize()` loaded.
    @discardableResult
    func refresh() async -> Bool {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)/api/holidays") else {
            return false
        }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(AppConfig.serverAPIKey)", forHTTPHeaderField: "Authorization")
        if let etag = storedEtag() {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { return false }
            DebugConfig.debugPrint("HolidayService: GET /api/holidays → HTTP \(http.statusCode)")
            switch http.statusCode {
            case 200:
                let dates = try self.dates(fromYearArray: data)
                guard let fileURL = Self.cacheURL() else { return false }
                try FileManager.default.createDirectory(
                    at: fileURL.deletingLastPathComponent(),
                    withIntermediateDirectories: true
                )
                try data.write(to: fileURL)
                if let etag = http.value(forHTTPHeaderField: "ETag") {
                    saveEtag(etag)
                }
                holidayDates = dates
                DebugConfig.debugPrint("HolidayService: updated, \(dates.count) holiday date(s)")
                return true
            case 304:
                DebugConfig.debugPrint("HolidayService: unchanged (304)")
                return false
            default:
                DebugConfig.debugError("HolidayService: fetch failed HTTP \(http.statusCode)")
                return false
            }
        } catch {
            DebugConfig.debugError("HolidayService: fetch error", error: error)
            return false
        }
    }

    private static let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    /// True if `date` is a known festivo. Never throws; false if no calendar is loaded.
    func isHoliday(_ date: Date = Date()) -> Bool {
        holidayDates.contains(Self.isoFormatter.string(from: date))
    }
}
