/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follón
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

package com.github.bfollon.intersego.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.github.bfollon.intersego.data.BusReminder
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DepartureTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.UUID

/**
 * Manages today-only bus departure reminders.
 *
 * Reminders are persisted to SharedPreferences and backed by AlarmManager exact alarms.
 * On day rollover, expired entries are pruned on the next call to [pruneExpired].
 * Call [initialize] before any other method.
 */
class ReminderService(private val context: Context) {

    private val prefs = context.getSharedPreferences("reminder_service", Context.MODE_PRIVATE)
    private val _reminders: MutableList<BusReminder> = mutableListOf()
    private var initialized = false

    companion object {
        const val CHANNEL_ID = "bus_reminders"
        const val KEY_REMINDERS = "bus_reminders"
        const val KEY_LEAD_TIME = "reminder_lead_time_minutes"
        const val KEY_ALARM_COUNTER = "alarm_request_counter"

        /** Creates the notification channel. Call from MainActivity.onCreate(). */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios de autobús",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos antes de la salida del autobús"
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }
    }

    // MARK: - Initialization

    fun initialize() {
        if (initialized) return
        initialized = true
        val json = prefs.getString(KEY_REMINDERS, null) ?: return
        runCatching {
            Json.decodeFromString<List<BusReminder>>(json)
        }.getOrNull()?.let {
            _reminders.addAll(it)
        }
    }

    // MARK: - Lead time preference

    fun getDefaultLeadMinutes(): Int {
        val v = prefs.getInt(KEY_LEAD_TIME, 0)
        return if (v == 0) 10 else v
    }

    fun setDefaultLeadMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_LEAD_TIME, minutes).apply()
    }

    // MARK: - Reading

    fun getReminders(): List<BusReminder> = _reminders.sortedBy { it.fireDateMillis }

    fun activeMatchKeys(): Set<String> = _reminders.map { it.matchKey }.toSet()

    fun isSet(routeId: String, stopId: String, direction: String, hour: Int, minute: Int): Boolean {
        val key = BusReminder.matchKey(routeId, stopId, direction, hour, minute)
        return _reminders.any { it.matchKey == key }
    }

    // MARK: - Pruning

    /** Removes reminders whose fire time has passed (day rollover cleanup). Call on app launch. */
    fun pruneExpired() {
        val now = System.currentTimeMillis()
        val expired = _reminders.filter { it.fireDateMillis < now }
        expired.forEach { cancelAlarm(it.id) }
        _reminders.removeAll { it.fireDateMillis < now }
        persist()
    }

    // MARK: - Scheduling

    sealed class ScheduleResult {
        data class Success(val reminder: BusReminder) : ScheduleResult()
        data class Failure(val message: String) : ScheduleResult()
    }

    fun scheduleReminder(
        departure: DepartureTime,
        stop: BusStop,
        route: BusRoute,
        direction: String
    ): ScheduleResult {
        // Check POST_NOTIFICATIONS runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return ScheduleResult.Failure("Activa las notificaciones en Ajustes > Aplicaciones > InterSego para usar esta función.")
            }
        }

        val leadMins = getDefaultLeadMinutes()

        // Compute fire time: today at (departure time − lead minutes)
        val cal = Calendar.getInstance()
        val totalMins = departure.hour * 60 + departure.minute - leadMins
        if (totalMins < 0) return ScheduleResult.Failure("Este autobús ya ha salido.")

        cal.set(Calendar.HOUR_OF_DAY, totalMins / 60)
        cal.set(Calendar.MINUTE, totalMins % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val fireMillis = cal.timeInMillis

        if (fireMillis <= System.currentTimeMillis()) {
            return ScheduleResult.Failure("Este autobús ya ha salido.")
        }

        val id = UUID.randomUUID().toString()
        val requestCode = nextAlarmRequestCode()
        val reminder = BusReminder(
            id = id,
            routeId = route.id,
            routeNumber = route.number,
            stopId = stop.id,
            stopName = stop.name,
            direction = direction,
            departureHour = departure.hour,
            departureMinute = departure.minute,
            leadMinutes = leadMins,
            fireDateMillis = fireMillis,
            alarmRequestCode = requestCode,
            seasonalNote = departure.seasonalAvailability.displayLabel
        )

        scheduleAlarm(reminder)
        _reminders.add(reminder)
        persist()
        return ScheduleResult.Success(reminder)
    }

    // MARK: - Cancellation

    fun cancelReminder(routeId: String, stopId: String, direction: String, hour: Int, minute: Int) {
        val key = BusReminder.matchKey(routeId, stopId, direction, hour, minute)
        _reminders.filter { it.matchKey == key }.forEach { cancelAlarm(it.id) }
        _reminders.removeAll { it.matchKey == key }
        persist()
    }

    fun cancelReminder(id: String) {
        cancelAlarm(id)
        _reminders.removeAll { it.id == id }
        persist()
    }

    // MARK: - AlarmManager internals

    private fun scheduleAlarm(reminder: BusReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(reminder, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireDateMillis, pendingIntent)
            } else {
                // Inexact fallback: fires within a few minutes of the target time
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireDateMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireDateMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(reminderId: String) {
        val requestCode = _reminders.find { it.id == reminderId }?.alarmRequestCode ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(reminder: BusReminder, flags: Int): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderBroadcastReceiver.EXTRA_ROUTE_NUMBER, reminder.routeNumber)
            putExtra(ReminderBroadcastReceiver.EXTRA_STOP_NAME, reminder.stopName)
            putExtra(ReminderBroadcastReceiver.EXTRA_DEPARTURE_DISPLAY, reminder.departureDisplayString)
            putExtra(ReminderBroadcastReceiver.EXTRA_LEAD_MINUTES, reminder.leadMinutes)
            putExtra(ReminderBroadcastReceiver.EXTRA_SEASONAL_NOTE, reminder.seasonalNote)
        }
        return PendingIntent.getBroadcast(context, reminder.alarmRequestCode, intent, flags)
    }

    private fun nextAlarmRequestCode(): Int {
        val next = prefs.getInt(KEY_ALARM_COUNTER, 0) + 1
        prefs.edit().putInt(KEY_ALARM_COUNTER, next).apply()
        return next
    }

    // MARK: - Persistence

    private fun persist() {
        val json = Json.encodeToString(_reminders.toList())
        prefs.edit().putString(KEY_REMINDERS, json).apply()
    }

}
