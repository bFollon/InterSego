/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
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

import Foundation

actor PDFCacheManager {
    static let shared = PDFCacheManager()

    private static let versionStorageKey = "pdf_cache_manager_versions"
    private static let lastUpdateCheckKey = "pdf_cache_manager_last_update_check"

    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 10
        return URLSession(configuration: config)
    }()

    // MARK: - Version Management

    private func getStoredVersion(routeId: String) -> PDFVersion? {
        guard let data = UserDefaults.standard.data(forKey: Self.versionStorageKey) else { return nil }
        do {
            let versions = try JSONDecoder().decode([String: PDFVersion].self, from: data)
            return versions[routeId]
        } catch {
            DebugConfig.debugError("Failed to decode version data", error: error)
            return nil
        }
    }

    func getDownloadDate(routeId: String) -> TimeInterval? {
        getStoredVersion(routeId: routeId)?.downloadDate
    }

    private func storeVersion(_ version: PDFVersion, routeId: String) {
        do {
            var versions: [String: PDFVersion] = [:]
            if let data = UserDefaults.standard.data(forKey: Self.versionStorageKey) {
                versions = (try? JSONDecoder().decode([String: PDFVersion].self, from: data)) ?? [:]
            }
            versions[routeId] = version
            let encoded = try JSONEncoder().encode(versions)
            UserDefaults.standard.set(encoded, forKey: Self.versionStorageKey)
            DebugConfig.debugPrint("PDFCacheManager: Stored version info for \(routeId)")
        } catch {
            DebugConfig.debugError("Failed to store version data", error: error)
        }
    }

    // MARK: - File Management

    func cachedFileURL(routeId: String) -> URL? {
        let file = AppDirectories.pdfs.appendingPathComponent(cacheFileName(routeId: routeId))
        return FileManager.default.fileExists(atPath: file.path) ? file : nil
    }

    private func cacheFileName(routeId: String) -> String {
        "\(routeId.lowercased().replacingOccurrences(of: " ", with: "-")).pdf"
    }

    // MARK: - Version Checking

    func checkRemoteVersion(url: String) async -> PDFVersion? {
        do {
            guard let requestURL = URL(string: url) else { return nil }

            var request = URLRequest(url: requestURL)
            request.httpMethod = "HEAD"
            request.setValue("InterSego-iOS/1.0", forHTTPHeaderField: "User-Agent")

            let (_, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse,
                  (200...299).contains(httpResponse.statusCode) else {
                DebugConfig.debugError("Failed to get remote PDF info")
                return nil
            }

            let lastModifiedStr = httpResponse.value(forHTTPHeaderField: "Last-Modified")
            let contentLength = httpResponse.value(forHTTPHeaderField: "Content-Length").flatMap { Int64($0) }
            let etag = httpResponse.value(forHTTPHeaderField: "ETag")

            let lastModified = lastModifiedStr.flatMap { parseHttpDate($0) }

            return PDFVersion(
                url: url,
                lastModified: lastModified,
                contentLength: contentLength,
                etag: etag
            )
        } catch {
            DebugConfig.debugError("Failed to check remote version for \(url)", error: error)
            return nil
        }
    }

    func isCacheUpToDate(routeId: String, pdfUrl: String) async -> Bool {
        guard cachedFileURL(routeId: routeId) != nil,
              let cachedVersion = getStoredVersion(routeId: routeId) else {
            DebugConfig.debugPrint("PDFCacheManager: No cached file or version for \(routeId)")
            return false
        }

        guard let remoteVersion = await checkRemoteVersion(url: pdfUrl) else {
            DebugConfig.debugWarn("PDFCacheManager: Failed to check remote version, using cached file")
            return true
        }

        // 1. Last-Modified (most reliable)
        if let cachedLM = cachedVersion.lastModified, let remoteLM = remoteVersion.lastModified {
            return cachedLM == remoteLM
        }

        // 2. Content-Length as backup
        if let cachedCL = cachedVersion.contentLength, let remoteCL = remoteVersion.contentLength {
            return cachedCL == remoteCL
        }

        // 3. ETag
        if let cachedETag = cachedVersion.etag, let remoteETag = remoteVersion.etag {
            return cachedETag == remoteETag
        }

        DebugConfig.debugPrint("PDFCacheManager: No comparison criteria available, assuming outdated")
        return false
    }

    // MARK: - Download and Cache

    func downloadAndCache(routeId: String, pdfUrl: String) async -> URL? {
        let fileName = cacheFileName(routeId: routeId)

        guard let file = await PDFDownloadService.shared.downloadPDF(url: pdfUrl, fileName: fileName, forceDownload: true) else {
            return nil
        }

        if let remoteVersion = await checkRemoteVersion(url: pdfUrl) {
            storeVersion(remoteVersion, routeId: routeId)
        }

        DebugConfig.debugPrint("PDFCacheManager: Successfully cached PDF for \(routeId)")
        return file
    }

    func getEffectivePDFFile(routeId: String, pdfUrl: String) async -> URL? {
        guard NetworkMonitor.shared.isOnline else {
            if let cachedFile = cachedFileURL(routeId: routeId) {
                DebugConfig.debugPrint("PDFCacheManager: Offline - using cached file for \(routeId)")
                return cachedFile
            }
            DebugConfig.debugWarn("PDFCacheManager: Offline and no cache available for \(routeId)")
            return nil
        }

        let isCacheValid = await isCacheUpToDate(routeId: routeId, pdfUrl: pdfUrl)

        if isCacheValid, let cached = cachedFileURL(routeId: routeId) {
            return cached
        }
        return await downloadAndCache(routeId: routeId, pdfUrl: pdfUrl)
    }

    // MARK: - Cache Management

    func clearCache() {
        try? FileManager.default.removeItem(at: AppDirectories.pdfs)
        UserDefaults.standard.removeObject(forKey: Self.versionStorageKey)
        DebugConfig.debugPrint("PDFCacheManager: Cleared all version info and PDFs")
    }

    func hasCachedFile(routeId: String) -> Bool {
        cachedFileURL(routeId: routeId) != nil
    }

    func initialize() {
        DebugConfig.debugPrint("PDFCacheManager: Initialized")
    }

    // MARK: - Automatic Update Checking

    private func shouldCheckForUpdates() -> Bool {
        let lastCheck = UserDefaults.standard.double(forKey: Self.lastUpdateCheckKey)
        guard lastCheck > 0 else { return true }
        let oneDayAgo = Date().timeIntervalSince1970 - (24 * 60 * 60)
        return lastCheck < oneDayAgo
    }

    private func recordUpdateCheck() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: Self.lastUpdateCheckKey)
    }

    func checkForUpdatesIfNeeded(routes: [BusRoute]) async {
        guard shouldCheckForUpdates() else {
            DebugConfig.debugPrint("PDFCacheManager: Skipping update check - already checked today")
            return
        }

        DebugConfig.debugPrint("PDFCacheManager: Checking for PDF updates...")
        recordUpdateCheck()

        for route in routes {
            let isCacheValid = await isCacheUpToDate(routeId: route.id, pdfUrl: route.pdfURL)
            if !isCacheValid {
                if let _ = await downloadAndCache(routeId: route.id, pdfUrl: route.pdfURL) {
                    DebugConfig.debugPrint("PDFCacheManager: Updated PDF for \(route.id)")
                } else {
                    DebugConfig.debugPrint("PDFCacheManager: Failed to update PDF for \(route.id)")
                }
            } else {
                DebugConfig.debugPrint("PDFCacheManager: PDF for \(route.id) is up to date")
            }
        }

        DebugConfig.debugPrint("PDFCacheManager: Update check completed")
    }

    // MARK: - Helpers

    private func parseHttpDate(_ dateString: String) -> TimeInterval? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "GMT")

        let formats = [
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",     // RFC 1123
            "EEEE, dd-MMM-yy HH:mm:ss 'GMT'",      // RFC 850
            "EEE MMM d HH:mm:ss yyyy"               // ANSI C asctime()
        ]

        for format in formats {
            formatter.dateFormat = format
            if let date = formatter.date(from: dateString) {
                return date.timeIntervalSince1970
            }
        }

        return nil
    }
}
