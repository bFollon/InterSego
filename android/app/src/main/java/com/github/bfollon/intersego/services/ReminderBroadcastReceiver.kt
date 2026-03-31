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
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.bfollon.intersego.R
import com.github.bfollon.intersego.data.BusReminder
import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.SeasonalAvailability
import com.github.bfollon.intersego.data.matchesCalendarDay
import java.time.Month
import java.util.Calendar

/**
 * BroadcastReceiver that fires when an AlarmManager alarm goes off for a bus reminder.
 *
 * For one-off reminders: displays the notification and stops.
 * For daily reminders: checks day type + SeasonalAvailability (smart-skip), optionally shows
 * the notification, then re-schedules for the same time tomorrow.
 */
class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routeNumber = intent.getStringExtra(EXTRA_ROUTE_NUMBER) ?: return
        val stopName = intent.getStringExtra(EXTRA_STOP_NAME) ?: return
        val departureDisplay = intent.getStringExtra(EXTRA_DEPARTURE_DISPLAY) ?: return
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 10)
        val seasonalNote = intent.getStringExtra(EXTRA_SEASONAL_NOTE)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val isDaily = intent.getBooleanExtra(EXTRA_IS_DAILY, false)
        val departureHour = intent.getIntExtra(EXTRA_DEPARTURE_HOUR, -1)
        val departureMinute = intent.getIntExtra(EXTRA_DEPARTURE_MINUTE, -1)
        val requestCode = intent.getIntExtra(EXTRA_ALARM_REQUEST_CODE, -1)
        val seasonalAvailability = intent.getStringExtra(EXTRA_SEASONAL_AVAILABILITY)
            ?.let { runCatching { SeasonalAvailability.valueOf(it) }.getOrNull() }
        val dayType = intent.getStringExtra(EXTRA_DAY_TYPE)
            ?.let { runCatching { DayType.valueOf(it) }.getOrNull() }

        // Smart-skip: for daily reminders check if the bus runs today
        val today = Calendar.getInstance()
        val todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK)
        val todayMonth = Month.of(today.get(Calendar.MONTH) + 1)

        val dayTypeMatches = dayType == null || dayType.matchesCalendarDay(todayDayOfWeek)
        val seasonalMatches = seasonalAvailability?.runsIn(todayMonth, todayDayOfWeek) != false
        val runsToday = dayTypeMatches && seasonalMatches

        if (runsToday) {
            val title = "Línea $routeNumber · $stopName"
            var body = "Sale en $leadMinutes min — $departureDisplay"
            if (seasonalNote != null) body += " ($seasonalNote)"

            val notification = NotificationCompat.Builder(context, ReminderService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_bus)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
        }

        // For daily reminders, re-schedule for the same time tomorrow
        if (isDaily && departureHour >= 0 && departureMinute >= 0 && requestCode >= 0) {
            val totalMins = departureHour * 60 + departureMinute - leadMinutes
            val nextCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, totalMins / 60)
                set(Calendar.MINUTE, totalMins % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val nextIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_ROUTE_NUMBER, routeNumber)
                putExtra(EXTRA_STOP_NAME, stopName)
                putExtra(EXTRA_DEPARTURE_DISPLAY, departureDisplay)
                putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
                putExtra(EXTRA_SEASONAL_NOTE, seasonalNote)
                putExtra(EXTRA_IS_DAILY, true)
                putExtra(EXTRA_DEPARTURE_HOUR, departureHour)
                putExtra(EXTRA_DEPARTURE_MINUTE, departureMinute)
                putExtra(EXTRA_ALARM_REQUEST_CODE, requestCode)
                putExtra(EXTRA_SEASONAL_AVAILABILITY, seasonalAvailability?.name)
                putExtra(EXTRA_DAY_TYPE, dayType?.name)
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode, nextIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextCal.timeInMillis, pi)
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_ROUTE_NUMBER = "route_number"
        const val EXTRA_STOP_NAME = "stop_name"
        const val EXTRA_DEPARTURE_DISPLAY = "departure_display"
        const val EXTRA_LEAD_MINUTES = "lead_minutes"
        const val EXTRA_SEASONAL_NOTE = "seasonal_note"
        const val EXTRA_IS_DAILY = "is_daily"
        const val EXTRA_DEPARTURE_HOUR = "departure_hour"
        const val EXTRA_DEPARTURE_MINUTE = "departure_minute"
        const val EXTRA_ALARM_REQUEST_CODE = "alarm_request_code"
        const val EXTRA_SEASONAL_AVAILABILITY = "seasonal_availability"
        const val EXTRA_DAY_TYPE = "day_type"

        /** Builds the broadcast intent for a BusReminder. Used by ReminderService and BootReceiver. */
        fun buildIntent(context: Context, reminder: BusReminder): Intent =
            Intent(context, ReminderBroadcastReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_ROUTE_NUMBER, reminder.routeNumber)
                putExtra(EXTRA_STOP_NAME, reminder.stopName)
                putExtra(EXTRA_DEPARTURE_DISPLAY, reminder.departureDisplayString)
                putExtra(EXTRA_LEAD_MINUTES, reminder.leadMinutes)
                putExtra(EXTRA_SEASONAL_NOTE, reminder.seasonalNote)
                putExtra(EXTRA_IS_DAILY, reminder.isDaily)
                putExtra(EXTRA_DEPARTURE_HOUR, reminder.departureHour)
                putExtra(EXTRA_DEPARTURE_MINUTE, reminder.departureMinute)
                putExtra(EXTRA_ALARM_REQUEST_CODE, reminder.alarmRequestCode)
                putExtra(EXTRA_SEASONAL_AVAILABILITY, reminder.seasonalAvailability?.name)
                putExtra(EXTRA_DAY_TYPE, reminder.dayType?.name)
            }
    }
}
