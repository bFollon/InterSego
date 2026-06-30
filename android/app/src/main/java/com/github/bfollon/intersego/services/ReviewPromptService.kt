/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ReviewPromptService {

    private const val PREFS_NAME = "review_prompt_prefs"
    private const val KEY_LAUNCH_COUNT = "review_prompt_launch_count"
    private const val KEY_FIRST_LAUNCH_DATE = "review_prompt_first_launch_date"
    private const val KEY_LAST_PROMPT_DATE = "review_prompt_last_prompt_date"

    private const val MINIMUM_LAUNCH_COUNT = 3
    private const val MINIMUM_DAYS_SINCE_FIRST_LAUNCH = 3
    private const val MINIMUM_SESSION_DURATION_MS = 10_000L
    private const val DAYS_BETWEEN_PROMPTS = 90

    private var sessionJob: Job? = null

    fun recordAppLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        prefs.edit().putInt(KEY_LAUNCH_COUNT, current + 1).apply()

        if (!prefs.contains(KEY_FIRST_LAUNCH_DATE)) {
            prefs.edit().putLong(KEY_FIRST_LAUNCH_DATE, System.currentTimeMillis()).apply()
            DebugConfig.debugPrint("⭐️ First app launch recorded")
        }

        DebugConfig.debugPrint("⭐️ App launch count: ${current + 1}")
    }

    fun startSession(activity: Activity) {
        sessionJob?.cancel()

        DebugConfig.debugPrint("⭐️ Review prompt session started")

        sessionJob = CoroutineScope(Dispatchers.Main).launch {
            delay(MINIMUM_SESSION_DURATION_MS)
            checkAndRequestReviewIfNeeded(activity)
        }
    }

    fun cancelSession() {
        sessionJob?.cancel()
        sessionJob = null
        DebugConfig.debugPrint("⭐️ Review prompt session cancelled")
    }

    private fun checkAndRequestReviewIfNeeded(activity: Activity) {
        if (!shouldPromptForReview(activity)) return
        recordReviewPrompt(activity)

        DebugConfig.debugPrint("⭐️ All conditions met - requesting Play Store review")

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                manager.launchReviewFlow(activity, request.result)
                    .addOnCompleteListener {
                        DebugConfig.debugPrint("⭐️ Review flow complete")
                    }
            } else {
                DebugConfig.debugPrint("⭐️ Review flow request failed: ${request.exception?.message}")
            }
        }
    }

    private fun shouldPromptForReview(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        if (launchCount < MINIMUM_LAUNCH_COUNT) {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: Launch count $launchCount < $MINIMUM_LAUNCH_COUNT")
            return false
        }

        val firstLaunchMs = prefs.getLong(KEY_FIRST_LAUNCH_DATE, 0L)
        if (firstLaunchMs == 0L) {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: No first launch date recorded")
            return false
        }

        val daysSinceFirstLaunch = (System.currentTimeMillis() - firstLaunchMs) / (1000.0 * 60 * 60 * 24)

        if (daysSinceFirstLaunch < 0) {
            DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation detected (negative days)")
            return false
        }

        if (daysSinceFirstLaunch > 3650) {
            DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation suspected (${daysSinceFirstLaunch.toInt()} days)")
            return false
        }

        if (daysSinceFirstLaunch < MINIMUM_DAYS_SINCE_FIRST_LAUNCH) {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: Only ${daysSinceFirstLaunch.toInt()} days since first launch")
            return false
        }

        val lastPromptMs = prefs.getLong(KEY_LAST_PROMPT_DATE, 0L)
        if (lastPromptMs > 0L) {
            val daysSinceLastPrompt = (System.currentTimeMillis() - lastPromptMs) / (1000.0 * 60 * 60 * 24)

            if (daysSinceLastPrompt < 0) {
                DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation detected (negative days since last prompt)")
                return false
            }

            if (daysSinceLastPrompt < DAYS_BETWEEN_PROMPTS) {
                DebugConfig.debugPrint("⭐️ Review prompt skipped: Only ${daysSinceLastPrompt.toInt()} days since last prompt")
                return false
            }
        }

        return true
    }

    private fun recordReviewPrompt(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_PROMPT_DATE, System.currentTimeMillis()).apply()
        DebugConfig.debugPrint("⭐️ Review prompt recorded")
    }
}
