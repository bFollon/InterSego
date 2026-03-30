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

/// Manages bus departure reminders (one-off and daily).
///
/// One-off reminders fire once and are pruned after their fire date.
/// Daily reminders persist until cancelled and are backed by a rolling 7-day batch of
/// individual UNNotificationRequests with deterministic IDs. The batch is replenished on
/// each app launch via `replenishDailyReminders()`. Smart-skip: days where the departure
/// doesn't run (per SeasonalAvailability) are simply not scheduled.
actor ReminderService {
    static let shared = ReminderService()

    private let remindersKey = "busReminders_v1"
    private let leadTimeKey = "reminderLeadTimeMinutes"
    private let dailyLeadTimeKey = "reminderDailyLeadTimeMinutes"
    /// Prefix for daily batch notification identifiers: "daily|<matchKey>|<YYYY-MM-DD>"
    private let dailyPrefix = "daily|"
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

    /// Returns the set of match keys for all active reminders (fast bell-state lookup).
    func activeMatchKeys() -> Set<String> {
        ensureInitialized()
        return Set(_reminders.map { $0.matchKey })
    }

    /// Returns the set of match keys for active DAILY reminders (for bell icon differentiation).
    func dailyMatchKeys() -> Set<String> {
        ensureInitialized()
        return Set(_reminders.filter { $0.isDaily }.map { $0.matchKey })
    }

    // MARK: - Pruning

    /// Removes expired one-off reminders. Daily reminders are never pruned here.
    /// Call on app launch before `replenishDailyReminders()`.
    func pruneExpired() {
        ensureInitialized()
        let now = Date()
        let expiredIds = _reminders.filter { !$0.isDaily && $0.fireDate < now }.map { $0.id }
        guard !expiredIds.isEmpty else { return }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: expiredIds)
        _reminders.removeAll { !$0.isDaily && $0.fireDate < now }
        persist()
    }

    /// Replenishes daily reminder batches to cover the next 7 days.
    /// Call on app launch after `pruneExpired()`.
    func replenishDailyReminders() async {
        ensureInitialized()
        let dailyReminders = _reminders.filter { $0.isDaily }
        guard !dailyReminders.isEmpty else { return }

        let center = UNUserNotificationCenter.current()
        let pendingRequests = await center.pendingNotificationRequests()
        let pendingIds = Set(pendingRequests.map { $0.identifier })

        for reminder in dailyReminders {
            await scheduleMissingBatchDays(for: reminder, pendingIds: pendingIds, center: center)
        }
    }

    // MARK: - Scheduling

    func scheduleReminder(
        departure: DepartureTime, stop: BusStop, route: BusRoute, direction: String,
        isDaily: Bool = false
    ) async throws {
        ensureInitialized()
        let leadMins = isDaily ? getDailyLeadMinutes() : getDefaultLeadMinutes()

        // Compute fire date (time-of-day component used for display and one-off scheduling)
        let cal = Calendar.current
        let now = Date()
        var components = cal.dateComponents([.year, .month, .day], from: now)
        let totalMins = departure.hour * 60 + departure.minute - leadMins
        guard totalMins >= 0 else { throw ReminderError.alreadyPassed }
        components.hour = totalMins / 60
        components.minute = totalMins % 60
        components.second = 0
        guard let fireDate = cal.date(from: components) else { throw ReminderError.invalidDate }

        // For one-off, fire date must still be in the future
        if !isDaily {
            guard fireDate > now else { throw ReminderError.alreadyPassed }
        }

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

        let reminder = BusReminder(
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
            seasonalAvailability: departure.seasonalAvailability
        )

        if isDaily {
            try await scheduleDailyBatch(for: reminder, center: center)
        } else {
            // One-off: single UNCalendarNotificationTrigger
            let content = buildNotificationContent(reminder: reminder)
            let triggerComponents = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComponents, repeats: false)
            try await center.add(UNNotificationRequest(identifier: reminder.id, content: content, trigger: trigger))
        }

        _reminders.append(reminder)
        persist()
    }

    // MARK: - Cancellation

    func cancelReminder(routeId: String, stopId: String, direction: String, hour: Int, minute: Int) {
        ensureInitialized()
        let key = BusReminder.matchKey(routeId: routeId, stopId: stopId, direction: direction, hour: hour, minute: minute)
        let matching = _reminders.filter { $0.matchKey == key }
        guard !matching.isEmpty else { return }

        let isDaily = matching.first?.isDaily ?? false
        if isDaily {
            cancelDailyBatch(matchKey: key)
        } else {
            let ids = matching.map { $0.id }
            UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
        }
        _reminders.removeAll { $0.matchKey == key }
        persist()
    }

    func cancelReminder(id: String) {
        ensureInitialized()
        guard let reminder = _reminders.first(where: { $0.id == id }) else { return }
        if reminder.isDaily {
            cancelDailyBatch(matchKey: reminder.matchKey)
        } else {
            UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
        }
        _reminders.removeAll { $0.id == id }
        persist()
    }

    // MARK: - Daily batch helpers

    /// Schedules one UNNotificationRequest per day in the next 7 days where the bus runs.
    private func scheduleDailyBatch(for reminder: BusReminder, center: UNUserNotificationCenter) async throws {
        let pendingRequests = await center.pendingNotificationRequests()
        let pendingIds = Set(pendingRequests.map { $0.identifier })
        try await scheduleMissingBatchDays(for: reminder, pendingIds: pendingIds, center: center, throwing: true)
    }

    /// Fills in missing days (not yet scheduled) for a daily reminder up to 7 days from now.
    private func scheduleMissingBatchDays(
        for reminder: BusReminder,
        pendingIds: Set<String>,
        center: UNUserNotificationCenter,
        throwing: Bool = false
    ) async {
        let cal = Calendar.current
        let now = Date()
        let totalMins = reminder.departureHour * 60 + reminder.departureMinute - reminder.leadMinutes
        let formatter = dailyDateFormatter()

        for daysAhead in 0 ..< 7 {
            guard let targetDay = cal.date(byAdding: .day, value: daysAhead, to: now) else { continue }

            // Smart-skip: check seasonal availability for this day
            if let seasonal = reminder.seasonalAvailability {
                let month = cal.component(.month, from: targetDay)
                let weekday = cal.component(.weekday, from: targetDay)
                guard seasonal.runsIn(month: month, weekday: weekday) else { continue }
            }

            // Compute fire date for this specific day
            var comps = cal.dateComponents([.year, .month, .day], from: targetDay)
            comps.hour = totalMins / 60
            comps.minute = totalMins % 60
            comps.second = 0
            guard let fireDate = cal.date(from: comps), fireDate > now else { continue }

            let dateString = formatter.string(from: targetDay)
            let notifId = "\(dailyPrefix)\(reminder.matchKey)|\(dateString)"
            guard !pendingIds.contains(notifId) else { continue }

            let content = buildNotificationContent(reminder: reminder)
            let triggerComps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComps, repeats: false)
            let request = UNNotificationRequest(identifier: notifId, content: content, trigger: trigger)

            if throwing {
                try? await center.add(request)
            } else {
                try? await center.add(request)
            }
        }
    }

    /// Cancels all pending batch notifications for a daily reminder by generating their
    /// deterministic IDs for a window around today (past 2 days + next 30 days).
    private func cancelDailyBatch(matchKey: String) {
        let cal = Calendar.current
        let now = Date()
        let formatter = dailyDateFormatter()
        var ids: [String] = []
        for offset in -2 ... 30 {
            guard let date = cal.date(byAdding: .day, value: offset, to: now) else { continue }
            ids.append("\(dailyPrefix)\(matchKey)|\(formatter.string(from: date))")
        }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
    }

    // MARK: - Shared helpers

    private func buildNotificationContent(reminder: BusReminder) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = "Línea \(reminder.routeNumber) · \(reminder.stopName)"
        var body = "Sale en \(reminder.leadMinutes) min — \(reminder.departureDisplayString)"
        if let note = reminder.seasonalNote { body += " (\(note))" }
        content.body = body
        content.sound = .default
        return content
    }

    private func dailyDateFormatter() -> DateFormatter {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
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
