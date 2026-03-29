/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import Foundation
import UserNotifications

/// Manages today-only bus departure reminders.
/// Reminders are persisted to UserDefaults and backed by UNUserNotificationCenter local notifications.
/// On day rollover, expired entries are pruned on the next call to `pruneExpired()`.
actor ReminderService {
    static let shared = ReminderService()

    private let remindersKey = "busReminders_v1"
    private let leadTimeKey = "reminderLeadTimeMinutes"
    private var _reminders: [BusReminder] = []
    private var _initialized = false

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

    // MARK: - Lead time preference

    func getDefaultLeadMinutes() -> Int {
        let stored = UserDefaults.standard.integer(forKey: leadTimeKey)
        return stored == 0 ? 10 : stored
    }

    func setDefaultLeadMinutes(_ minutes: Int) {
        UserDefaults.standard.set(minutes, forKey: leadTimeKey)
    }

    // MARK: - Reading reminders

    func getReminders() -> [BusReminder] {
        ensureInitialized()
        return _reminders.sorted { $0.fireDate < $1.fireDate }
    }

    /// Returns the set of match keys for all active reminders (fast bell-state lookup).
    func activeMatchKeys() -> Set<String> {
        ensureInitialized()
        return Set(_reminders.map { $0.matchKey })
    }

    // MARK: - Pruning

    /// Removes reminders whose fire date has already passed (day rollover cleanup).
    /// Call on app launch.
    func pruneExpired() {
        ensureInitialized()
        let now = Date()
        let expiredIds = _reminders.filter { $0.fireDate < now }.map { $0.id }
        guard !expiredIds.isEmpty else { return }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: expiredIds)
        _reminders.removeAll { $0.fireDate < now }
        persist()
    }

    // MARK: - Scheduling

    func scheduleReminder(departure: DepartureTime, stop: BusStop, route: BusRoute, direction: String) async throws {
        ensureInitialized()
        let leadMins = getDefaultLeadMinutes()

        // Compute fire date: today at (departure minutes − lead minutes)
        let cal = Calendar.current
        let now = Date()
        var components = cal.dateComponents([.year, .month, .day], from: now)
        let totalMins = departure.hour * 60 + departure.minute - leadMins
        guard totalMins >= 0 else { throw ReminderError.alreadyPassed }
        components.hour = totalMins / 60
        components.minute = totalMins % 60
        components.second = 0
        guard let fireDate = cal.date(from: components) else { throw ReminderError.invalidDate }
        guard fireDate > now else { throw ReminderError.alreadyPassed }

        // Request notification permission if needed
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

        // Build notification content
        let content = UNMutableNotificationContent()
        content.title = "Línea \(route.number) · \(stop.name)"
        var body = "Sale en \(leadMins) min — \(departure.displayString)"
        if let note = departure.seasonalAvailability.displayLabel {
            body += " (\(note))"
        }
        content.body = body
        content.sound = .default

        let triggerComponents = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComponents, repeats: false)
        let id = UUID().uuidString
        try await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))

        let reminder = BusReminder(
            id: id,
            routeId: route.id,
            routeNumber: route.number,
            stopId: stop.id,
            stopName: stop.name,
            direction: direction,
            departureHour: departure.hour,
            departureMinute: departure.minute,
            leadMinutes: leadMins,
            fireDate: fireDate,
            seasonalNote: departure.seasonalAvailability.displayLabel
        )
        _reminders.append(reminder)
        persist()
    }

    // MARK: - Cancellation

    func cancelReminder(routeId: String, stopId: String, direction: String, hour: Int, minute: Int) {
        ensureInitialized()
        let key = BusReminder.matchKey(routeId: routeId, stopId: stopId, direction: direction, hour: hour, minute: minute)
        let ids = _reminders.filter { $0.matchKey == key }.map { $0.id }
        guard !ids.isEmpty else { return }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
        _reminders.removeAll { $0.matchKey == key }
        persist()
    }

    func cancelReminder(id: String) {
        ensureInitialized()
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
        _reminders.removeAll { $0.id == id }
        persist()
    }

    // MARK: - Persistence

    private func persist() {
        guard let data = try? JSONEncoder().encode(_reminders) else { return }
        UserDefaults.standard.set(data, forKey: remindersKey)
    }

    // MARK: - Errors

    enum ReminderError: LocalizedError {
        case invalidDate
        case alreadyPassed
        case permissionDenied

        var errorDescription: String? {
            switch self {
            case .invalidDate: return "No se puede calcular la hora del recordatorio."
            case .alreadyPassed: return "Este autobús ya ha salido."
            case .permissionDenied: return "Activa las notificaciones en Ajustes para usar esta función."
            }
        }
    }
}
