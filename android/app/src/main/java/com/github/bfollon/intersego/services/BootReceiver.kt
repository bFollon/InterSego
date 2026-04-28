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
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.bfollon.intersego.data.BusReminder
import kotlinx.serialization.json.Json
import java.util.Calendar

/**
 * Restores daily reminders after a device reboot.
 * AlarmManager alarms are cleared on reboot; this receiver re-schedules all persisted
 * daily reminders so they continue firing every day without requiring the app to open.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val prefs = context.getSharedPreferences("reminder_service", Context.MODE_PRIVATE)
        val json = prefs.getString(ReminderService.KEY_REMINDERS, null) ?: return
        val reminders = runCatching {
            Json.decodeFromString<List<BusReminder>>(json)
        }.getOrNull() ?: return

        val now = System.currentTimeMillis()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (reminder in reminders) {
            if (!reminder.isDaily) continue

            val totalMins = reminder.departureHour * 60 + reminder.departureMinute - reminder.leadMinutes
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, totalMins / 60)
                set(Calendar.MINUTE, totalMins % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If today's fire window already passed, schedule for tomorrow
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val broadcastIntent = ReminderBroadcastReceiver.buildIntent(context, reminder)
            val pi = PendingIntent.getBroadcast(
                context, reminder.alarmRequestCode, broadcastIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }
}
