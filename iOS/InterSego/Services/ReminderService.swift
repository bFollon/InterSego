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
/// One-off reminders are scheduled for the next future occurrence of the departure,
/// considering day type and seasonal availability — not just today.
/// Daily reminders persist until cancelled and are backed by a rolling 7-day batch of
/// individual UNNotificationRequests with deterministic IDs. Smart-skip: days where the
/// departure doesn't run (wrong day type or seasonal) are simply not scheduled.
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
        let expiredIds = _reminders.filter { !$0.isDaily && $0.fireDate < now }.map { $0.id }
        guard !expiredIds.isEmpty else { return }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: expiredIds)
        _reminders.removeAll { !$0.isDaily && $0.fireDate < now }
        persist()
    }

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
        isDaily: Bool = false, dayType: DayType? = nil
    ) async throws {
        ensureInitialized()
        let leadMins = isDaily ? getDailyLeadMinutes() : getDefaultLeadMinutes()

        // Find the next future occurrence of this departure (respects day type + seasonal)
        guard let fireDate = nextOccurrence(
            dayType: dayType,
            seasonalAvailability: departure.seasonalAvailability,
            hour: departure.hour, minute: departure.minute, leadMins: leadMins
        ) else {
            throw ReminderError.noUpcomingOccurrence
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
            seasonalAvailability: departure.seasonalAvailability,
            dayType: dayType
        )

        if isDaily {
            try await scheduleDailyBatch(for: reminder, center: center)
        } else {
            let cal = Calendar.current
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

    // MARK: - Rescheduling

    /// Reschedules all one-off reminders to use [newLeadMinutes].
    /// For each reminder:
    ///   1. If a valid future fire date exists with the new lead → reschedule normally.
    ///   2. If the bus still runs but the lead time can't fit (fire time already passed) → fire immediately (~10s).
    ///   3. If no upcoming occurrence at all → leave the reminder unchanged.
    func rescheduleOneOff(newLeadMinutes: Int) async {
        ensureInitialized()
        let center = UNUserNotificationCenter.current()

        for i in _reminders.indices where !_reminders[i].isDaily {
            let reminder = _reminders[i]

            if let newFireDate = nextOccurrence(
                dayType: reminder.dayType,
                seasonalAvailability: reminder.seasonalAvailability,
                hour: reminder.departureHour, minute: reminder.departureMinute,
                leadMins: newLeadMinutes
            ) {
                // Normal reschedule: cancel old, schedule at new time
                center.removePendingNotificationRequests(withIdentifiers: [reminder.id])
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
                let cal = Calendar.current
                let content = buildNotificationContent(reminder: updated)
                let triggerComponents = cal.dateComponents([.year, .month, .day, .hour, .minute], from: newFireDate)
                let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComponents, repeats: false)
                try? await center.add(UNNotificationRequest(identifier: updated.id, content: content, trigger: trigger))
                _reminders[i] = updated

            } else if nextOccurrence(
                dayType: reminder.dayType,
                seasonalAvailability: reminder.seasonalAvailability,
                hour: reminder.departureHour, minute: reminder.departureMinute,
                leadMins: 0
            ) != nil {
                // Bus still runs but the lead-adjusted fire time has passed — notify immediately
                center.removePendingNotificationRequests(withIdentifiers: [reminder.id])
                let immediateFireDate = Date().addingTimeInterval(10)
                let updated = BusReminder(
                    id: UUID().uuidString,
                    routeId: reminder.routeId, routeNumber: reminder.routeNumber,
                    stopId: reminder.stopId, stopName: reminder.stopName,
                    direction: reminder.direction,
                    departureHour: reminder.departureHour, departureMinute: reminder.departureMinute,
                    leadMinutes: newLeadMinutes, fireDate: immediateFireDate,
                    seasonalNote: reminder.seasonalNote, isDaily: false,
                    seasonalAvailability: reminder.seasonalAvailability, dayType: reminder.dayType
                )
                let content = buildNotificationContent(reminder: updated)
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 10, repeats: false)
                try? await center.add(UNNotificationRequest(identifier: updated.id, content: content, trigger: trigger))
                _reminders[i] = updated
            }
            // else: no upcoming occurrence — leave the reminder untouched
        }
        persist()
    }

    /// Reschedules all daily reminders to use [newLeadMinutes].
    /// Cancels the existing 7-day batch and replaces it with a fresh one at the new lead time.
    func rescheduleDaily(newLeadMinutes: Int) async {
        ensureInitialized()
        let center = UNUserNotificationCenter.current()

        for i in _reminders.indices where _reminders[i].isDaily {
            let reminder = _reminders[i]
            cancelDailyBatch(matchKey: reminder.matchKey)

            // Compute a representative fire date (used for display in the list)
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
            try? await scheduleDailyBatch(for: updated, center: center)
            _reminders[i] = updated
        }
        persist()
    }

    // MARK: - Daily batch helpers

    private func scheduleDailyBatch(for reminder: BusReminder, center: UNUserNotificationCenter) async throws {
        let pendingRequests = await center.pendingNotificationRequests()
        let pendingIds = Set(pendingRequests.map { $0.identifier })
        try await scheduleMissingBatchDays(for: reminder, pendingIds: pendingIds, center: center, throwing: true)
    }

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

            let weekday = cal.component(.weekday, from: targetDay)
            let month = cal.component(.month, from: targetDay)

            // Smart-skip: check day type and seasonal availability
            if let dt = reminder.dayType, !dayTypeMatches(dt, weekday: weekday) { continue }
            if let seasonal = reminder.seasonalAvailability,
               !seasonal.runsIn(month: month, weekday: weekday) { continue }

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
            try? await center.add(request)
        }
    }

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
        case noUpcomingOccurrence

        var errorDescription: String? {
            switch self {
            case .invalidDate: return "No se puede calcular la hora del recordatorio."
            case .alreadyPassed: return "Este autobús ya ha salido."
            case .permissionDenied: return "Activa las notificaciones en Ajustes para usar esta función."
            case .noUpcomingOccurrence: return "No hay próxima salida disponible en los próximos 30 días."
            }
        }
    }
}
