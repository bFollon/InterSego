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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.bfollon.intersego.R

/**
 * BroadcastReceiver that fires when an AlarmManager alarm goes off for a bus reminder.
 * Displays a high-priority notification with the departure details.
 */
class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routeNumber = intent.getStringExtra(EXTRA_ROUTE_NUMBER) ?: return
        val stopName = intent.getStringExtra(EXTRA_STOP_NAME) ?: return
        val departureDisplay = intent.getStringExtra(EXTRA_DEPARTURE_DISPLAY) ?: return
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 10)
        val seasonalNote = intent.getStringExtra(EXTRA_SEASONAL_NOTE)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return

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

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_ROUTE_NUMBER = "route_number"
        const val EXTRA_STOP_NAME = "stop_name"
        const val EXTRA_DEPARTURE_DISPLAY = "departure_display"
        const val EXTRA_LEAD_MINUTES = "lead_minutes"
        const val EXTRA_SEASONAL_NOTE = "seasonal_note"
    }
}
