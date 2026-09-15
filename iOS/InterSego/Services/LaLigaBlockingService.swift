/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2026 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation
#if canImport(Darwin)
import Darwin
#endif

/// Detects whether our server is unreachable because of LaLiga's court-authorized IP
/// blocking (used against Cloudflare-hosted piracy sites during football matches, which
/// collaterally blocks unrelated sites sharing the same IPs — including our own tunnel).
///
/// This is deliberately separate from `NetworkMonitor`: the device can have perfectly good
/// internet while our own server is unreachable, and that combination is what this service
/// distinguishes from a generic offline state.
///
/// `onServerReachable()`/`onServerUnreachable()` are called by
/// `TimetableCacheService.fetchManifest` — our most frequent call to our own server —
/// whenever it observes a request success or failure, since this service has no server
/// calls of its own to hook a failure into.
actor LaLigaBlockingService {
    static let shared = LaLigaBlockingService()

    private let session: URLSession
    private let statusURL = URL(string: "https://hayahora.futbol/estado/blocked-any.txt")!
    private let minRecheckInterval: TimeInterval = 2 * 60

    private var lastCheckAt: Date?
    private(set) var isLikelyBlocked = false

    private init() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 5
        config.timeoutIntervalForResource = 5
        session = URLSession(configuration: config)
    }

    /// Call when a request to our own server succeeds — clears any stale blocked state.
    func onServerReachable() {
        isLikelyBlocked = false
    }

    /// Call when a request to our own server fails. If the device is online, this checks
    /// hayahora.futbol's live list of blocked IPs (rate-limited to once per
    /// `minRecheckInterval`) to see whether a LaLiga blocking wave is the likely cause.
    func onServerUnreachable() async {
        guard NetworkMonitor.shared.isOnline else {
            isLikelyBlocked = false
            return
        }
        let now = Date()
        if let last = lastCheckAt, now.timeIntervalSince(last) < minRecheckInterval { return }
        lastCheckAt = now
        isLikelyBlocked = await checkHayAhoraFutbol()
    }

    /// Checks whether our own server's IP is among those currently blocked, rather than just
    /// whether *some* blocking is active — a resolved-and-matched IP is strong evidence,
    /// while an empty/unreachable blocklist is strong evidence against.
    ///
    /// If our own hostname can't be resolved at all, we have no way to confirm a specific
    /// match, so we fall back to correlation (blocklist non-empty) as the best available signal.
    private func checkHayAhoraFutbol() async -> Bool {
        guard let blockedIPs = await fetchBlockedIPs(), !blockedIPs.isEmpty else { return false }

        guard let host = URL(string: AppConfig.boardingServerURL)?.host,
              let resolvedIPs = Self.resolveHostIPs(host), !resolvedIPs.isEmpty
        else {
            DebugConfig.debugPrint("LaLigaBlockingService: could not resolve our own server IP, falling back to correlation")
            return true
        }
        return !resolvedIPs.isDisjoint(with: blockedIPs)
    }

    private func fetchBlockedIPs() async -> Set<String>? {
        do {
            let (data, response) = try await session.data(from: statusURL)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            guard let body = String(data: data, encoding: .utf8) else { return nil }
            let ips = body
                .split(whereSeparator: \.isNewline)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
            return Set(ips)
        } catch {
            DebugConfig.debugError("LaLigaBlockingService: hayahora.futbol check failed", error: error)
            return nil
        }
    }

    /// Resolves `host` to its numeric IP addresses via a plain DNS lookup (`getaddrinfo`),
    /// so we can compare against hayahora.futbol's list of currently-blocked IPs.
    private static func resolveHostIPs(_ host: String) -> Set<String>? {
        var hints = addrinfo(
            ai_flags: 0,
            ai_family: AF_UNSPEC,
            ai_socktype: SOCK_STREAM,
            ai_protocol: 0,
            ai_addrlen: 0,
            ai_canonname: nil,
            ai_addr: nil,
            ai_next: nil
        )
        var resultPointer: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &resultPointer) == 0, let firstResult = resultPointer else {
            return nil
        }
        defer { freeaddrinfo(resultPointer) }

        var ips = Set<String>()
        var current: UnsafeMutablePointer<addrinfo>? = firstResult
        while let entry = current {
            var hostBuffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let status = getnameinfo(
                entry.pointee.ai_addr,
                entry.pointee.ai_addrlen,
                &hostBuffer,
                socklen_t(hostBuffer.count),
                nil,
                0,
                NI_NUMERICHOST
            )
            if status == 0 {
                ips.insert(String(cString: hostBuffer))
            }
            current = entry.pointee.ai_next
        }
        return ips.isEmpty ? nil : ips
    }
}
