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
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.SeasonalAvailability
import com.github.bfollon.intersego.data.matchesCalendarDay
import java.time.Month
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.UUID

/**
 * Manages bus departure reminders (one-off and daily).
 *
 * One-off reminders are scheduled for the next future occurrence of the departure
 * (considering day type and seasonal availability). Daily reminders persist until
 * cancelled and smart-skip days when the bus doesn't run.
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
        const val KEY_DAILY_LEAD_TIME = "reminder_daily_lead_time_minutes"
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

    fun getDailyLeadMinutes(): Int {
        val v = prefs.getInt(KEY_DAILY_LEAD_TIME, 0)
        return if (v == 0) 15 else v
    }

    fun setDailyLeadMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_LEAD_TIME, minutes).apply()
    }

    // MARK: - Reading

    fun getReminders(): List<BusReminder> = _reminders.sortedBy { it.fireDateMillis }

    fun activeMatchKeys(): Set<String> = _reminders.map { it.matchKey }.toSet()

    /** Returns match keys for daily-only reminders (used to render the repeat badge on bell icons). */
    fun dailyMatchKeys(): Set<String> = _reminders.filter { it.isDaily }.map { it.matchKey }.toSet()

    fun isSet(routeId: String, stopId: String, direction: String, hour: Int, minute: Int): Boolean {
        val key = BusReminder.matchKey(routeId, stopId, direction, hour, minute)
        return _reminders.any { it.matchKey == key }
    }

    // MARK: - Pruning

    /** Removes one-off reminders whose fire time has passed. Daily reminders are not pruned. */
    fun pruneExpired() {
        val now = System.currentTimeMillis()
        val expired = _reminders.filter { !it.isDaily && it.fireDateMillis < now }
        expired.forEach { cancelAlarm(it.id) }
        _reminders.removeAll { !it.isDaily && it.fireDateMillis < now }
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
        direction: String,
        isDaily: Boolean = false,
        dayType: DayType? = null
    ): ScheduleResult {
        // Check POST_NOTIFICATIONS runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return ScheduleResult.Failure("Activa las notificaciones en Ajustes > Aplicaciones > InterSego para usar esta función.")
            }
        }

        val leadMins = if (isDaily) getDailyLeadMinutes() else getDefaultLeadMinutes()

        // Find the next future occurrence of this departure that matches day type and seasonal rules
        val fireMillis = nextOccurrenceMillis(
            dayType = dayType,
            seasonal = departure.seasonalAvailability,
            hour = departure.hour,
            minute = departure.minute,
            leadMins = leadMins
        ) ?: return ScheduleResult.Failure("No hay próxima salida disponible en los próximos 30 días.")

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
            seasonalNote = departure.seasonalAvailability.displayLabel,
            isDaily = isDaily,
            seasonalAvailability = departure.seasonalAvailability,
            dayType = dayType
        )

        scheduleAlarm(reminder)
        _reminders.add(reminder)
        persist()
        return ScheduleResult.Success(reminder)
    }

    // MARK: - Next occurrence

    /**
     * Iterates forward up to 30 days to find the first future fire time where:
     * 1. The calendar day matches [dayType] (if provided)
     * 2. The [seasonal] availability applies
     * 3. The computed fire time (departure − lead) is still in the future
     */
    fun nextOccurrenceMillis(
        dayType: DayType?,
        seasonal: SeasonalAvailability?,
        hour: Int,
        minute: Int,
        leadMins: Int
    ): Long? {
        val totalMins = hour * 60 + minute - leadMins
        if (totalMins < 0) return null
        val now = System.currentTimeMillis()
        val base = Calendar.getInstance()
        for (daysAhead in 0..29) {
            val cal = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, daysAhead)
                set(Calendar.HOUR_OF_DAY, totalMins / 60)
                set(Calendar.MINUTE, totalMins % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            if (dayType != null && !dayType.matchesCalendarDay(dayOfWeek)) continue
            if (seasonal != null) {
                val month = Month.of(cal.get(Calendar.MONTH) + 1)
                if (!seasonal.runsIn(month, dayOfWeek)) continue
            }
            if (cal.timeInMillis > now) return cal.timeInMillis
        }
        return null
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
        val intent = ReminderBroadcastReceiver.buildIntent(context, reminder)
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
