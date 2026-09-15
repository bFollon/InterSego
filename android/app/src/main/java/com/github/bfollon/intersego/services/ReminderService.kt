/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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
import com.github.bfollon.intersego.BuildConfig
import com.github.bfollon.intersego.data.BusReminder
import com.github.bfollon.intersego.data.BusRoute
import com.github.bfollon.intersego.data.BusStop
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.DepartureTime
import com.github.bfollon.intersego.data.SeasonalAvailability
import java.time.Month
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

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

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

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
        dayType: DayType? = null,
        journeyLabel: String? = null
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
            dayType = dayType,
            journeyLabel = journeyLabel
        )

        scheduleAlarm(reminder)
        _reminders.add(reminder)
        persist()
        val savedReminder = _reminders.last()
        CoroutineScope(Dispatchers.IO).launch { postReminderToServer(savedReminder) }
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
            if (dayType != null && !dayType.matchesDate(cal)) continue
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
        val serverIds = _reminders.filter { it.matchKey == key }.mapNotNull { it.serverId }
        _reminders.filter { it.matchKey == key }.forEach { cancelAlarm(it.id) }
        _reminders.removeAll { it.matchKey == key }
        persist()
        serverIds.forEach { sid ->
            CoroutineScope(Dispatchers.IO).launch { deleteReminderFromServer(sid) }
        }
    }

    fun cancelReminder(id: String) {
        val serverId = _reminders.find { it.id == id }?.serverId
        cancelAlarm(id)
        _reminders.removeAll { it.id == id }
        persist()
        serverId?.let { sid ->
            CoroutineScope(Dispatchers.IO).launch { deleteReminderFromServer(sid) }
        }
    }

    // MARK: - Rescheduling

    /**
     * Reschedules all one-off reminders to use [newLeadMinutes].
     * For each reminder:
     *   1. If a valid future fire time exists with the new lead → reschedule normally.
     *   2. If the bus still runs but the lead time can't fit (fire time already past) → fire immediately (~10s).
     *   3. If no upcoming occurrence at all → leave the reminder unchanged.
     */
    fun rescheduleOneOff(newLeadMinutes: Int) {
        val updated = mutableListOf<BusReminder>()
        for (reminder in _reminders.filter { !it.isDaily }) {
            val newFireMillis = nextOccurrenceMillis(
                reminder.dayType, reminder.seasonalAvailability,
                reminder.departureHour, reminder.departureMinute, newLeadMinutes
            )
            if (newFireMillis != null) {
                // Normal reschedule
                cancelAlarm(reminder.id)
                val updatedReminder = reminder.copy(leadMinutes = newLeadMinutes, fireDateMillis = newFireMillis)
                scheduleAlarm(updatedReminder)
                updated.add(updatedReminder)
            } else {
                val departureStillRuns = nextOccurrenceMillis(
                    reminder.dayType, reminder.seasonalAvailability,
                    reminder.departureHour, reminder.departureMinute, 0
                ) != null
                if (departureStillRuns) {
                    // Bus still runs but lead-adjusted fire time has passed — notify immediately (~10s)
                    cancelAlarm(reminder.id)
                    val immediateMillis = System.currentTimeMillis() + 10_000L
                    val updatedReminder = reminder.copy(leadMinutes = newLeadMinutes, fireDateMillis = immediateMillis)
                    scheduleAlarm(updatedReminder)
                    updated.add(updatedReminder)
                } else {
                    // No upcoming occurrence — leave unchanged
                    updated.add(reminder)
                }
            }
        }
        _reminders.removeAll { !it.isDaily }
        _reminders.addAll(updated)
        persist()
    }

    /**
     * Reschedules all daily reminders to use [newLeadMinutes].
     * Cancels each existing alarm and replaces it with one at the new lead time.
     * The self-rescheduling chain in [ReminderBroadcastReceiver] will also use the new value
     * because [scheduleAlarm] embeds [leadMinutes] in the PendingIntent extras.
     */
    fun rescheduleDaily(newLeadMinutes: Int) {
        for (i in _reminders.indices) {
            if (!_reminders[i].isDaily) continue
            val reminder = _reminders[i]
            cancelAlarm(reminder.id)
            val newFireMillis = nextOccurrenceMillis(
                reminder.dayType, reminder.seasonalAvailability,
                reminder.departureHour, reminder.departureMinute, newLeadMinutes
            ) ?: (System.currentTimeMillis() + 86_400_000L) // fallback: same time tomorrow
            val updatedReminder = reminder.copy(leadMinutes = newLeadMinutes, fireDateMillis = newFireMillis)
            scheduleAlarm(updatedReminder)
            _reminders[i] = updatedReminder
        }
        persist()
    }

    // MARK: - Server sync (best effort; AlarmManager fallback fires regardless)

    private fun getFcmToken(): String? =
        context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .getString("fcm_token", null)

    private suspend fun postReminderToServer(reminder: BusReminder) {
        val token = getFcmToken() ?: return
        val body = JSONObject().apply {
            put("deviceToken", token)
            put("platform", "android")
            put("routeId", reminder.routeId)
            put("routeNumber", reminder.routeNumber)
            put("stopId", reminder.stopId)
            put("stopName", reminder.stopName)
            put("direction", reminder.direction)
            put("departureHour", reminder.departureHour)
            put("departureMinute", reminder.departureMinute)
            put("leadMinutes", reminder.leadMinutes)
            put("isDaily", reminder.isDaily)
            put("dayType", reminder.dayType?.name?.lowercase())
            put("seasonalAvailability", reminder.seasonalAvailability?.name)
            put("journeyLabel", reminder.journeyLabel)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${BuildConfig.BOARDING_SERVER_URL}/reminders")
            .addHeader("Authorization", "Bearer ${BuildConfig.SERVER_API_KEY}")
            .post(body)
            .build()

        val serverId = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                JSONObject(response.body?.string() ?: return).optString("id").takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            return
        } ?: return

        withContext(Dispatchers.Main) {
            val idx = _reminders.indexOfFirst { it.id == reminder.id }
            if (idx >= 0) {
                _reminders[idx] = _reminders[idx].copy(serverId = serverId)
                persist()
            }
        }
    }

    private fun deleteReminderFromServer(serverId: String) {
        val request = Request.Builder()
            .url("${BuildConfig.BOARDING_SERVER_URL}/reminders/$serverId")
            .addHeader("Authorization", "Bearer ${BuildConfig.SERVER_API_KEY}")
            .delete()
            .build()
        try {
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) { }
    }

    fun syncTokenToServer(oldToken: String?, newToken: String) {
        if (oldToken == null || oldToken == newToken) return
        val body = JSONObject()
            .put("oldToken", oldToken)
            .put("newToken", newToken)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.BOARDING_SERVER_URL}/reminders/token")
            .addHeader("Authorization", "Bearer ${BuildConfig.SERVER_API_KEY}")
            .put(body)
            .build()
        try {
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) { }
    }

    // MARK: - AlarmManager internals

    private fun scheduleAlarm(reminder: BusReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(reminder, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireDateMillis, pendingIntent)
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
