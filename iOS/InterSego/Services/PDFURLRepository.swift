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

enum URLValidationResult {
    case valid(String)
    case invalid(String, Int)
    case error(String, String)
}

enum URLResolutionResult {
    case success(String)
    case updated(oldUrl: String, newUrl: String)
    case failed(String)
}

actor PDFURLRepository {
    static let shared = PDFURLRepository()

    private static let prefsKey = "pdf_url_repository_scraped_urls"
    private static let lastScrapeKey = "pdf_url_repository_last_scrape"

    static let fallbackURLs: [String: String] = [
        "M1": "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M1.pdf",
        "M2": "https://www.linecar.es/wp-content/uploads/2024/09/M2-septiembre-2024.pdf",
        "M3": "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M3.pdf",
        "M4": "https://www.linecar.es/wp-content/uploads/2025/10/M4.pdf",
        "M5": "https://www.linecar.es/wp-content/uploads/2024/09/M5-septiembre-2024.pdf",
        "M6": "https://www.linecar.es/wp-content/uploads/2025/12/M6.pdf",
        "M7": "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M7.pdf",
        "M8": "https://www.linecar.es/wp-content/uploads/2024/07/SEGOVIA-M8.pdf"
    ]

    private var validationCache: [String: URLValidationResult] = [:]
    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 10
        return URLSession(configuration: config)
    }()

    // MARK: - Persistence

    private struct ScrapedURLData: Codable {
        let urls: [String: String]
        let timestamp: TimeInterval
    }

    private func loadPersistedURLs() -> [String: String] {
        guard let data = UserDefaults.standard.data(forKey: Self.prefsKey) else { return [:] }
        do {
            let decoded = try JSONDecoder().decode(ScrapedURLData.self, from: data)
            DebugConfig.debugPrint("PDFURLRepository: Loaded \(decoded.urls.count) persisted URLs")
            return decoded.urls
        } catch {
            DebugConfig.debugError("Failed to load persisted URLs", error: error)
            return [:]
        }
    }

    private func persistURLs(_ urls: [String: String]) {
        do {
            let data = ScrapedURLData(urls: urls, timestamp: Date().timeIntervalSince1970)
            let encoded = try JSONEncoder().encode(data)
            UserDefaults.standard.set(encoded, forKey: Self.prefsKey)
            UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: Self.lastScrapeKey)
            DebugConfig.debugPrint("PDFURLRepository: Persisted \(urls.count) URLs to storage")
        } catch {
            DebugConfig.debugError("Failed to persist URLs", error: error)
        }
    }

    // MARK: - URL Validation

    func validateURL(_ url: String, useCache: Bool = true) async -> URLValidationResult {
        if useCache, let cached = validationCache[url] {
            DebugConfig.debugPrint("PDFURLRepository: Using cached validation for \(url)")
            return cached
        }

        do {
            DebugConfig.debugPrint("PDFURLRepository: Validating URL with HEAD request: \(url)")
            guard let requestURL = URL(string: url) else {
                return .error(url, "Invalid URL")
            }

            var request = URLRequest(url: requestURL)
            request.httpMethod = "HEAD"
            request.setValue("InterSego-iOS/1.0", forHTTPHeaderField: "User-Agent")

            let (_, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                let result = URLValidationResult.error(url, "Not an HTTP response")
                validationCache[url] = result
                return result
            }

            let result: URLValidationResult
            if (200...299).contains(httpResponse.statusCode) {
                DebugConfig.debugPrint("PDFURLRepository: URL is valid (\(httpResponse.statusCode))")
                result = .valid(url)
            } else {
                DebugConfig.debugWarn("PDFURLRepository: URL returned \(httpResponse.statusCode)")
                result = .invalid(url, httpResponse.statusCode)
            }

            validationCache[url] = result
            return result

        } catch {
            DebugConfig.debugError("PDFURLRepository: Error validating URL", error: error)
            let result = URLValidationResult.error(url, error.localizedDescription)
            validationCache[url] = result
            return result
        }
    }

    // MARK: - Scraping

    func scrapeURLs() async -> [String: String] {
        DebugConfig.debugPrint("PDFURLRepository: Starting fresh URL scraping...")

        let scrapedData = await PDFURLScrapingService.shared.scrapePDFURLs()

        guard !scrapedData.isEmpty else {
            DebugConfig.debugWarn("PDFURLRepository: Scraping returned no results")
            return [:]
        }

        var urlMap: [String: String] = [:]
        for item in scrapedData {
            urlMap[item.routeId] = item.pdfUrl
        }

        persistURLs(urlMap)
        DebugConfig.debugPrint("PDFURLRepository: Scraped and persisted \(urlMap.count) URLs")
        return urlMap
    }

    func validatePersistedURLs() async -> [String: String] {
        let persistedURLs = loadPersistedURLs()

        guard !persistedURLs.isEmpty else {
            DebugConfig.debugPrint("PDFURLRepository: No persisted URLs to validate")
            return [:]
        }

        DebugConfig.debugPrint("PDFURLRepository: Validating \(persistedURLs.count) persisted URLs...")

        var validURLs: [String: String] = [:]

        for (routeId, url) in persistedURLs {
            let result = await validateURL(url, useCache: false)
            switch result {
            case .valid:
                validURLs[routeId] = url
                DebugConfig.debugPrint("\(routeId): Valid")
            case .invalid(_, let statusCode):
                DebugConfig.debugWarn("\(routeId): Invalid (\(statusCode))")
            case .error(_, let message):
                DebugConfig.debugWarn("\(routeId): Error (\(message))")
            }
        }

        DebugConfig.debugPrint("PDFURLRepository: \(validURLs.count)/\(persistedURLs.count) URLs are valid")
        return validURLs
    }

    // MARK: - URL Resolution

    func getURL(routeId: String) -> String? {
        let normalizedId = routeId.uppercased()
        let persistedURLs = loadPersistedURLs()
        if let url = persistedURLs[normalizedId] ?? Self.fallbackURLs[normalizedId] {
            return url
        }
        DebugConfig.debugError("No URL found for route: \(routeId)")
        return nil
    }

    func resolveURLWithHealing(routeId: String) async -> URLResolutionResult {
        let normalizedId = routeId.uppercased()

        guard NetworkMonitor.shared.isOnline else {
            guard let url = getURL(routeId: normalizedId) else {
                return .failed("No URL available for route \(normalizedId)")
            }
            DebugConfig.debugPrint("PDFURLRepository: Offline, using stored URL for \(normalizedId)")
            return .success(url)
        }

        guard let currentURL = getURL(routeId: normalizedId) else {
            return .failed("No URL available for route \(normalizedId)")
        }
        DebugConfig.debugPrint("PDFURLRepository: Resolving URL for \(normalizedId) with self-healing")

        let validation = await validateURL(currentURL)
        switch validation {
        case .valid:
            DebugConfig.debugPrint("PDFURLRepository: Current URL is valid")
            return .success(currentURL)
        case .invalid(_, let statusCode) where statusCode == 404:
            DebugConfig.debugWarn("PDFURLRepository: URL returned 404, attempting self-healing...")
            let freshURLs = await scrapeURLs()
            if let newURL = freshURLs[normalizedId], newURL != currentURL {
                DebugConfig.debugPrint("PDFURLRepository: Found new URL: \(newURL)")
                return .updated(oldUrl: currentURL, newUrl: newURL)
            }
            return .failed("No se puede acceder al horario en este momento. Inténtalo más tarde.")
        case .invalid(_, let statusCode):
            return .failed("No se puede acceder al horario (Error \(statusCode))")
        case .error:
            return .failed("Error de red. Inténtalo más tarde.")
        }
    }

    // MARK: - Initialization

    func initializeURLs() async -> Bool {
        DebugConfig.debugPrint("PDFURLRepository: Initializing URLs...")

        guard NetworkMonitor.shared.isOnline else {
            DebugConfig.debugPrint("PDFURLRepository: Offline, skipping initialization")
            return true
        }

        let validPersistedURLs = await validatePersistedURLs()
        let allRoutes = Set(Self.fallbackURLs.keys)

        if allRoutes.isSubset(of: Set(validPersistedURLs.keys)) {
            DebugConfig.debugPrint("PDFURLRepository: All persisted URLs are valid, no scraping needed")
            return true
        }

        DebugConfig.debugPrint("PDFURLRepository: Some URLs are invalid, scraping fresh URLs...")
        let scrapedURLs = await scrapeURLs()

        if scrapedURLs.isEmpty {
            DebugConfig.debugWarn("PDFURLRepository: Scraping failed, will use fallback URLs")
            return false
        }

        DebugConfig.debugPrint("PDFURLRepository: Initialization complete")
        return true
    }

    func clearCache() {
        UserDefaults.standard.removeObject(forKey: Self.prefsKey)
        UserDefaults.standard.removeObject(forKey: Self.lastScrapeKey)
        validationCache.removeAll()
        DebugConfig.debugPrint("PDFURLRepository: Cleared all cached data")
    }
}
