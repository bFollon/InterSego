/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation
import UserNotifications

/// Manages bus departure reminders (one-off and daily).
///
/// Delivery is handled exclusively by the push server (APNs). No local
/// UNNotificationRequests are scheduled — that avoids the 64-notification iOS cap,
/// prevents duplicate notifications, and removes the 7-day expiry limitation.
///
/// This service owns:
///   - The in-memory + persisted reminder list (source of truth for the bell UI)
///   - APNs device token storage and rotation
///   - Server registration (POST/DELETE/PUT /reminders)
actor ReminderService {
    static let shared = ReminderService()

    private let session: URLSession
    private let remindersKey = "busReminders_v1"
    private let deviceTokenKey = "apnsDeviceToken"
    private let leadTimeKey = "reminderLeadTimeMinutes"
    private let dailyLeadTimeKey = "reminderDailyLeadTimeMinutes"
    private var _reminders: [BusReminder] = []
    private var _initialized = false
    private var _deviceToken: String?

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 15
        session = URLSession(configuration: config)
    }

    // MARK: - Initialization

    func initialize() {
        guard !_initialized else { return }
        _initialized = true
        guard let data = UserDefaults.standard.data(forKey: remindersKey),
              let decoded = try? JSONDecoder().decode([BusReminder].self, from: data)
        else { return }
        _reminders = decoded
    }

    private func ensureInitialized() {
        if !_initialized { initialize() }
    }

    // MARK: - Lead time preferences

    func getDefaultLeadMinutes() -> Int {
        let stored = UserDefaults.standard.integer(forKey: leadTimeKey)
        return stored == 0 ? 10 : stored
    }

    func setDefaultLeadMinutes(_ minutes: Int) {
        UserDefaults.standard.set(minutes, forKey: leadTimeKey)
    }

    func getDailyLeadMinutes() -> Int {
        let stored = UserDefaults.standard.integer(forKey: dailyLeadTimeKey)
        return stored == 0 ? 15 : stored
    }

    func setDailyLeadMinutes(_ minutes: Int) {
        UserDefaults.standard.set(minutes, forKey: dailyLeadTimeKey)
    }

    // MARK: - Reading reminders

    func getReminders() -> [BusReminder] {
        ensureInitialized()
        return _reminders.sorted { $0.fireDate < $1.fireDate }
    }

    func activeMatchKeys() -> Set<String> {
        ensureInitialized()
        return Set(_reminders.map { $0.matchKey })
    }

    func dailyMatchKeys() -> Set<String> {
        ensureInitialized()
        return Set(_reminders.filter { $0.isDaily }.map { $0.matchKey })
    }

    // MARK: - Pruning

    func pruneExpired() {
        ensureInitialized()
        let now = Date()
        guard _reminders.contains(where: { !$0.isDaily && $0.fireDate < now }) else { return }
        _reminders.removeAll { !$0.isDaily && $0.fireDate < now }
        persist()
    }

    // MARK: - Scheduling

    func scheduleReminder(
        departure: DepartureTime, stop: BusStop, route: BusRoute, direction: String,
        isDaily: Bool = false, dayType: DayType? = nil
    ) async throws {
        ensureInitialized()
        let leadMins = isDaily ? getDailyLeadMinutes() : getDefaultLeadMinutes()

        guard let fireDate = nextOccurrence(
            dayType: dayType,
            seasonalAvailability: departure.seasonalAvailability,
            hour: departure.hour, minute: departure.minute, leadMins: leadMins
        ) else {
            throw ReminderError.noUpcomingOccurrence
        }

        // Permission is required for APNs push delivery
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .notDetermined:
            let granted = try await center.requestAuthorization(options: [.alert, .sound])
            if !granted { throw ReminderError.permissionDenied }
        case .denied:
            throw ReminderError.permissionDenied
        default:
            break
        }

        var reminder = BusReminder(
            id: UUID().uuidString,
            routeId: route.id,
            routeNumber: route.number,
            stopId: stop.id,
            stopName: stop.name,
            direction: direction,
            departureHour: departure.hour,
            departureMinute: departure.minute,
            leadMinutes: leadMins,
            fireDate: fireDate,
            seasonalNote: departure.seasonalAvailability.displayLabel,
            isDaily: isDaily,
            seasonalAvailability: departure.seasonalAvailability,
            dayType: dayType
        )

        guard let token = currentDeviceToken(),
              let serverId = await postReminderToServer(reminder, deviceToken: token) else {
            throw ReminderError.serverUnavailable
        }
        reminder.serverId = serverId
        _reminders.append(reminder)
        persist()
    }

    // MARK: - Cancellation

    func cancelReminder(routeId: String, stopId: String, direction: String, hour: Int, minute: Int) {
        ensureInitialized()
        let key = BusReminder.matchKey(routeId: routeId, stopId: stopId, direction: direction, hour: hour, minute: minute)
        let matching = _reminders.filter { $0.matchKey == key }
        guard !matching.isEmpty else { return }

        let serverIds = matching.compactMap { $0.serverId }
        _reminders.removeAll { $0.matchKey == key }
        persist()

        Task { for sid in serverIds { await self.deleteReminderFromServer(sid) } }
    }

    func cancelReminder(id: String) {
        ensureInitialized()
        guard let reminder = _reminders.first(where: { $0.id == id }) else { return }
        let serverId = reminder.serverId
        _reminders.removeAll { $0.id == id }
        persist()

        if let sid = serverId { Task { await self.deleteReminderFromServer(sid) } }
    }

    // MARK: - Rescheduling

    /// Reschedules all one-off reminders to use [newLeadMinutes].
    /// Deletes the old server registration and posts a new one.
    /// Reminders with no upcoming occurrence within 30 days are left unchanged.
    func rescheduleOneOff(newLeadMinutes: Int) async {
        ensureInitialized()

        for i in _reminders.indices where !_reminders[i].isDaily {
            let reminder = _reminders[i]
            guard let newFireDate = nextOccurrence(
                dayType: reminder.dayType,
                seasonalAvailability: reminder.seasonalAvailability,
                hour: reminder.departureHour, minute: reminder.departureMinute,
                leadMins: newLeadMinutes
            ) else { continue }

            if let sid = reminder.serverId { await deleteReminderFromServer(sid) }

            let updated = BusReminder(
                id: UUID().uuidString,
                routeId: reminder.routeId, routeNumber: reminder.routeNumber,
                stopId: reminder.stopId, stopName: reminder.stopName,
                direction: reminder.direction,
                departureHour: reminder.departureHour, departureMinute: reminder.departureMinute,
                leadMinutes: newLeadMinutes, fireDate: newFireDate,
                seasonalNote: reminder.seasonalNote, isDaily: false,
                seasonalAvailability: reminder.seasonalAvailability, dayType: reminder.dayType
            )
            _reminders[i] = updated

            if let token = currentDeviceToken(),
               let serverId = await postReminderToServer(updated, deviceToken: token) {
                _reminders[i].serverId = serverId
            }
        }
        persist()
    }

    /// Reschedules all daily reminders to use [newLeadMinutes].
    /// Deletes the old server registration and posts a new one.
    func rescheduleDaily(newLeadMinutes: Int) async {
        ensureInitialized()

        for i in _reminders.indices where _reminders[i].isDaily {
            let reminder = _reminders[i]

            if let sid = reminder.serverId { await deleteReminderFromServer(sid) }

            let newFireDate = nextOccurrence(
                dayType: reminder.dayType,
                seasonalAvailability: reminder.seasonalAvailability,
                hour: reminder.departureHour, minute: reminder.departureMinute,
                leadMins: newLeadMinutes
            ) ?? reminder.fireDate.addingTimeInterval(Double(reminder.leadMinutes - newLeadMinutes) * 60)

            let updated = BusReminder(
                id: reminder.id,
                routeId: reminder.routeId, routeNumber: reminder.routeNumber,
                stopId: reminder.stopId, stopName: reminder.stopName,
                direction: reminder.direction,
                departureHour: reminder.departureHour, departureMinute: reminder.departureMinute,
                leadMinutes: newLeadMinutes, fireDate: newFireDate,
                seasonalNote: reminder.seasonalNote, isDaily: true,
                seasonalAvailability: reminder.seasonalAvailability, dayType: reminder.dayType
            )
            _reminders[i] = updated

            if let token = currentDeviceToken(),
               let serverId = await postReminderToServer(updated, deviceToken: token) {
                _reminders[i].serverId = serverId
            }
        }
        persist()
    }

    // MARK: - Next occurrence

    /// Iterates forward up to 30 days to find the first future fire time where:
    /// 1. The calendar weekday matches [dayType] (if provided)
    /// 2. The seasonal availability applies
    /// 3. The computed fire time (departure − lead) is still in the future
    private func nextOccurrence(
        dayType: DayType?,
        seasonalAvailability: SeasonalAvailability?,
        hour: Int, minute: Int, leadMins: Int
    ) -> Date? {
        let totalMins = hour * 60 + minute - leadMins
        guard totalMins >= 0 else { return nil }

        let cal = Calendar.current
        let now = Date()

        for daysAhead in 0 ..< 30 {
            guard let targetDay = cal.date(byAdding: .day, value: daysAhead, to: now) else { continue }
            let weekday = cal.component(.weekday, from: targetDay)
            let month = cal.component(.month, from: targetDay)

            if let dt = dayType, !dayTypeMatches(dt, weekday: weekday) { continue }
            if let seasonal = seasonalAvailability, !seasonal.runsIn(month: month, weekday: weekday) { continue }

            var comps = cal.dateComponents([.year, .month, .day], from: targetDay)
            comps.hour = totalMins / 60
            comps.minute = totalMins % 60
            comps.second = 0
            guard let fireDate = cal.date(from: comps), fireDate > now else { continue }

            return fireDate
        }
        return nil
    }

    /// Returns true if the given DayType applies on the given Calendar weekday (1=Sun, 7=Sat).
    private func dayTypeMatches(_ dayType: DayType, weekday: Int) -> Bool {
        switch dayType {
        case .weekday: return (2...6).contains(weekday)
        case .saturday: return weekday == 7
        case .sunday: return weekday == 1
        case .weekend: return weekday == 1 || weekday == 7
        case .holiday: return weekday == 1
        }
    }

    // MARK: - Persistence

    private func persist() {
        guard let data = try? JSONEncoder().encode(_reminders) else { return }
        UserDefaults.standard.set(data, forKey: remindersKey)
    }

    // MARK: - APNs token management

    func updateDeviceToken(_ newToken: String) async {
        let oldToken = UserDefaults.standard.string(forKey: deviceTokenKey)
        UserDefaults.standard.set(newToken, forKey: deviceTokenKey)
        _deviceToken = newToken

        if let oldToken, oldToken != newToken {
            await putTokenUpdate(from: oldToken, to: newToken)
        }
        // Pull server state first so orphaned/restored reminders are adopted locally,
        // then push any local reminders that never made it to the server.
        await mergeFromServer(token: newToken)
        await syncRemindersToServer()
    }

    // MARK: - Server sync

    private func currentDeviceToken() -> String? {
        if _deviceToken == nil { _deviceToken = UserDefaults.standard.string(forKey: deviceTokenKey) }
        return _deviceToken
    }

    private func syncRemindersToServer() async {
        guard let token = currentDeviceToken() else { return }
        ensureInitialized()
        var dirty = false
        for i in _reminders.indices where _reminders[i].serverId == nil {
            if let id = await postReminderToServer(_reminders[i], deviceToken: token) {
                _reminders[i].serverId = id
                dirty = true
            }
        }
        if dirty { persist() }
    }

    /// Syncs the local reminder list against the server for the current device token.
    /// Safe to call any time (e.g. on reminders screen open).
    func refreshFromServer() async {
        guard let token = currentDeviceToken() else { return }
        await mergeFromServer(token: token)
    }

    /// Fetches the server's list for [token] and reconciles it with the local list:
    /// - If a server reminder matches a local one by matchKey but has no serverId → links it.
    /// - If a server reminder has no local counterpart at all → adopts it (reinstall recovery).
    /// - If a server reminder matches a local one that already has a serverId → skips it.
    private func mergeFromServer(token: String) async {
        guard let serverReminders = await fetchRemindersFromServer(token: token) else { return }
        ensureInitialized()
        let knownServerIds = Set(_reminders.compactMap { $0.serverId })
        var dirty = false
        for sr in serverReminders {
            guard !knownServerIds.contains(sr.id) else { continue }
            let key = BusReminder.matchKey(
                routeId: sr.routeId, stopId: sr.stopId, direction: sr.direction,
                hour: sr.departureHour, minute: sr.departureMinute
            )
            if let idx = _reminders.firstIndex(where: { $0.matchKey == key && $0.serverId == nil }) {
                _reminders[idx].serverId = sr.id
                dirty = true
            } else if !_reminders.contains(where: { $0.matchKey == key }) {
                if let reminder = toLocalReminder(sr) {
                    _reminders.append(reminder)
                    dirty = true
                }
            }
        }
        if dirty { persist() }
    }

    private func fetchRemindersFromServer(token: String) async -> [ServerDeviceReminder]? {
        do {
            let (data, http) = try await serverRequest(method: "GET", path: "/reminders/device/\(token)", body: nil)
            guard (200 ... 299).contains(http.statusCode) else { return nil }
            return try JSONDecoder().decode([ServerDeviceReminder].self, from: data)
        } catch {
            DebugConfig.debugWarn("ReminderService: failed to fetch reminders from server: \(error)")
            return nil
        }
    }

    private func toLocalReminder(_ s: ServerDeviceReminder) -> BusReminder? {
        let dayType = s.dayType.flatMap { DayType(rawValue: $0.uppercased()) }
        let seasonal = s.seasonalAvailability.flatMap { SeasonalAvailability(rawValue: $0) }

        let fireDate: Date
        if let str = s.nextFireAt, let date = ISO8601DateFormatter().date(from: str) {
            fireDate = date
        } else if let computed = nextOccurrence(
            dayType: dayType, seasonalAvailability: seasonal,
            hour: s.departureHour, minute: s.departureMinute, leadMins: s.leadMinutes
        ) {
            fireDate = computed
        } else {
            return nil
        }

        return BusReminder(
            id: UUID().uuidString,
            routeId: s.routeId, routeNumber: s.routeNumber,
            stopId: s.stopId, stopName: s.stopName, direction: s.direction,
            departureHour: s.departureHour, departureMinute: s.departureMinute,
            leadMinutes: s.leadMinutes, fireDate: fireDate,
            seasonalNote: seasonal.flatMap { $0.displayLabel },
            isDaily: s.isDaily, seasonalAvailability: seasonal,
            dayType: dayType, serverId: s.id
        )
    }

    private func postReminderToServer(_ reminder: BusReminder, deviceToken: String) async -> String? {
        let payload = ServerReminderPayload(
            deviceToken: deviceToken,
            routeId: reminder.routeId,
            routeNumber: reminder.routeNumber,
            stopId: reminder.stopId,
            stopName: reminder.stopName,
            direction: reminder.direction,
            departureHour: reminder.departureHour,
            departureMinute: reminder.departureMinute,
            leadMinutes: reminder.leadMinutes,
            isDaily: reminder.isDaily,
            dayType: reminder.dayType?.rawValue.lowercased(),
            seasonalAvailability: reminder.seasonalAvailability?.rawValue
        )
        guard let body = try? JSONEncoder().encode(payload) else { return nil }
        do {
            let (data, _) = try await serverRequest(method: "POST", path: "/reminders", body: body)
            return try JSONDecoder().decode(ServerReminderResponse.self, from: data).id
        } catch {
            DebugConfig.debugWarn("ReminderService: failed to post reminder to server: \(error)")
            return nil
        }
    }

    private func deleteReminderFromServer(_ serverId: String) async {
        do {
            _ = try await serverRequest(method: "DELETE", path: "/reminders/\(serverId)", body: nil)
        } catch {
            DebugConfig.debugWarn("ReminderService: failed to delete reminder \(serverId) from server: \(error)")
        }
    }

    private func putTokenUpdate(from oldToken: String, to newToken: String) async {
        guard let body = try? JSONEncoder().encode(["oldToken": oldToken, "newToken": newToken]) else { return }
        do {
            _ = try await serverRequest(method: "PUT", path: "/reminders/token", body: body)
        } catch {
            DebugConfig.debugWarn("ReminderService: failed to update APNs token on server: \(error)")
        }
    }

    private func serverRequest(method: String, path: String, body: Data?) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: "\(AppConfig.boardingServerURL)\(path)") else {
            throw ReminderServerError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(AppConfig.serverAPIKey)", forHTTPHeaderField: "Authorization")
        request.setValue("InterSego-iOS/1.0", forHTTPHeaderField: "User-Agent")
        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw ReminderServerError.invalidResponse }
        return (data, http)
    }

    // MARK: - Errors

    enum ReminderError: LocalizedError {
        case invalidDate
        case alreadyPassed
        case permissionDenied
        case noUpcomingOccurrence
        case serverUnavailable

        var errorDescription: String? {
            switch self {
            case .invalidDate: return "No se puede calcular la hora del recordatorio."
            case .alreadyPassed: return "Este autobús ya ha salido."
            case .permissionDenied: return "Activa las notificaciones en Ajustes para usar esta función."
            case .noUpcomingOccurrence: return "No hay próxima salida disponible en los próximos 30 días."
            case .serverUnavailable: return "No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo."
            }
        }
    }
}

// MARK: - Server types (private)

private struct ServerDeviceReminder: Decodable {
    let id: String
    let routeId: String
    let routeNumber: String
    let stopId: String
    let stopName: String
    let direction: String
    let departureHour: Int
    let departureMinute: Int
    let leadMinutes: Int
    let isDaily: Bool
    let dayType: String?
    let seasonalAvailability: String?
    let nextFireAt: String?
}

private struct ServerReminderPayload: Encodable {
    let deviceToken: String
    let routeId: String
    let routeNumber: String
    let stopId: String
    let stopName: String
    let direction: String
    let departureHour: Int
    let departureMinute: Int
    let leadMinutes: Int
    let isDaily: Bool
    let dayType: String?
    let seasonalAvailability: String?
}

private struct ServerReminderResponse: Decodable {
    let id: String
}

private enum ReminderServerError: Error {
    case invalidURL
    case invalidResponse
}
