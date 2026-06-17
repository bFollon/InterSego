/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val prefs = getSharedPreferences("fcm_prefs", MODE_PRIVATE)
        val oldToken = prefs.getString("fcm_token", null)
        prefs.edit().putString("fcm_token", token).apply()
        CoroutineScope(Dispatchers.IO).launch {
            ReminderService(applicationContext).syncTokenToServer(oldToken, token)
            DeviceTokenService.register(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // FCM shows the notification automatically when the app is in background.
        // When the app is in the foreground, we receive it here but don't need
        // to do anything — reminders are not expected to arrive while the user
        // is actively using the app.
    }
}
