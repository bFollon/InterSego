/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation
import StoreKit
import UIKit

@MainActor
class ReviewPromptService {
    static let shared = ReviewPromptService()

    private init() {}

    private enum UserDefaultsKeys {
        static let appLaunchCount = "review_prompt_launch_count"
        static let firstLaunchDate = "review_prompt_first_launch_date"
        static let lastReviewPromptDate = "review_prompt_last_prompt_date"
        static let sessionStartTime = "review_prompt_session_start"
    }

    private static let userDefaults = UserDefaults.standard
    private var sessionTask: Task<Void, Never>?

    private var appLaunchCount: Int {
        Self.userDefaults.integer(forKey: UserDefaultsKeys.appLaunchCount)
    }

    private var firstLaunchDate: Date? {
        let timestamp = Self.userDefaults.double(forKey: UserDefaultsKeys.firstLaunchDate)
        return timestamp > 0 ? Date(timeIntervalSince1970: timestamp) : nil
    }

    private var lastReviewPromptDate: Date? {
        let timestamp = Self.userDefaults.double(forKey: UserDefaultsKeys.lastReviewPromptDate)
        return timestamp > 0 ? Date(timeIntervalSince1970: timestamp) : nil
    }

    func recordAppLaunch() {
        let currentCount = appLaunchCount
        Self.userDefaults.set(currentCount + 1, forKey: UserDefaultsKeys.appLaunchCount)

        if firstLaunchDate == nil {
            Self.userDefaults.set(Date().timeIntervalSince1970, forKey: UserDefaultsKeys.firstLaunchDate)
            DebugConfig.debugPrint("⭐️ First app launch recorded")
        }

        DebugConfig.debugPrint("⭐️ App launch count: \(currentCount + 1)")
    }

    func startSession() {
        let startTime = Date()
        Self.userDefaults.set(startTime.timeIntervalSince1970, forKey: UserDefaultsKeys.sessionStartTime)

        sessionTask?.cancel()

        DebugConfig.debugPrint("⭐️ Review prompt session started")

        sessionTask = Task {
            try? await Task.sleep(nanoseconds: UInt64(AppConfig.ReviewPrompt.minimumSessionDuration * 1_000_000_000))

            guard !Task.isCancelled else {
                DebugConfig.debugPrint("⭐️ Review prompt session task cancelled")
                return
            }

            let storedTime = Self.userDefaults.double(forKey: UserDefaultsKeys.sessionStartTime)
            guard storedTime == startTime.timeIntervalSince1970 else {
                DebugConfig.debugPrint("⭐️ Review prompt session invalidated (app backgrounded)")
                return
            }

            checkAndRequestReviewIfNeeded(in: getCurrentWindowScene())
        }
    }

    func cancelSession() {
        sessionTask?.cancel()
        sessionTask = nil
        DebugConfig.debugPrint("⭐️ Review prompt session cancelled")
    }

    private func checkAndRequestReviewIfNeeded(in windowScene: UIWindowScene?) {
        guard shouldPromptForReview() else { return }

        recordReviewPrompt()

        guard let scene = windowScene else {
            DebugConfig.debugPrint("⚠️ Cannot request review: no window scene")
            return
        }

        DebugConfig.debugPrint("⭐️ All conditions met - requesting App Store review")
        if #available(iOS 18.0, *) {
            AppStore.requestReview(in: scene)
        } else {
            SKStoreReviewController.requestReview(in: scene)
        }
    }

    private func shouldPromptForReview() -> Bool {
        guard appLaunchCount >= AppConfig.ReviewPrompt.minimumLaunchCount else {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: Launch count \(appLaunchCount) < \(AppConfig.ReviewPrompt.minimumLaunchCount)")
            return false
        }

        guard let firstLaunch = firstLaunchDate else {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: No first launch date recorded")
            return false
        }

        let daysSinceFirstLaunch = Date().timeIntervalSince(firstLaunch) / (24 * 60 * 60)

        if daysSinceFirstLaunch < 0 {
            DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation detected (negative days)")
            return false
        }

        if daysSinceFirstLaunch > 3650 {
            DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation suspected (\(Int(daysSinceFirstLaunch)) days)")
            return false
        }

        guard daysSinceFirstLaunch >= Double(AppConfig.ReviewPrompt.minimumDaysSinceFirstLaunch) else {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: Only \(Int(daysSinceFirstLaunch)) days since first launch")
            return false
        }

        if let lastPrompt = lastReviewPromptDate {
            let daysSinceLastPrompt = Date().timeIntervalSince(lastPrompt) / (24 * 60 * 60)

            if daysSinceLastPrompt < 0 {
                DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation detected (negative days since last prompt)")
                return false
            }

            guard daysSinceLastPrompt >= Double(AppConfig.ReviewPrompt.daysBetweenPrompts) else {
                DebugConfig.debugPrint("⭐️ Review prompt skipped: Only \(Int(daysSinceLastPrompt)) days since last prompt")
                return false
            }
        }

        return true
    }

    private func recordReviewPrompt() {
        Self.userDefaults.set(Date().timeIntervalSince1970, forKey: UserDefaultsKeys.lastReviewPromptDate)
        DebugConfig.debugPrint("⭐️ Review prompt recorded")
    }

    private func getCurrentWindowScene() -> UIWindowScene? {
        return UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive })
            as? UIWindowScene
    }

    #if DEBUG
    func simulateConditionsForTesting() {
        Self.userDefaults.set(AppConfig.ReviewPrompt.minimumLaunchCount, forKey: UserDefaultsKeys.appLaunchCount)
        Self.userDefaults.set(
            Date().addingTimeInterval(-Double(AppConfig.ReviewPrompt.minimumDaysSinceFirstLaunch + 1) * 24 * 60 * 60).timeIntervalSince1970,
            forKey: UserDefaultsKeys.firstLaunchDate
        )
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.lastReviewPromptDate)
        DebugConfig.debugPrint("⭐️ TEST MODE: Simulated conditions for review prompt")
        DebugConfig.debugPrint("⭐️ \(getTrackingStatus())")
    }

    func getTrackingStatus() -> String {
        let count = appLaunchCount
        let firstLaunch = firstLaunchDate?.description ?? "nil"
        let lastPrompt = lastReviewPromptDate?.description ?? "nil"
        return "Launches: \(count), First: \(firstLaunch), Last prompt: \(lastPrompt)"
    }

    func resetAllTracking() {
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.appLaunchCount)
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.firstLaunchDate)
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.lastReviewPromptDate)
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.sessionStartTime)
        DebugConfig.debugPrint("⭐️ Reset all review prompt tracking")
    }
    #endif
}
