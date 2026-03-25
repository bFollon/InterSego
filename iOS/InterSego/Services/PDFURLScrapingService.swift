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

struct ScrapedPDFData {
    let routeId: String
    let pdfUrl: String
    let lastUpdated: String?
}

actor PDFURLScrapingService {
    static let shared = PDFURLScrapingService()

    private static let baseURL = "https://www.linecar.es/metropolitano/segovia/"
    private let tag = "PDFURLScrapingService"

    private var scrapedURLs: [String: String] = [:]
    private var scrapingCompleted = false

    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 10
        return URLSession(configuration: config)
    }()

    func getScrapedURL(routeId: String) -> String? {
        guard scrapingCompleted else { return nil }
        return scrapedURLs[routeId]
    }

    func isScrapingComplete() -> Bool {
        scrapingCompleted
    }

    func scrapePDFURLs() async -> [ScrapedPDFData] {
        do {
            DebugConfig.debugPrint("\(tag): Starting PDF URL scraping from \(Self.baseURL)")

            guard let url = URL(string: Self.baseURL) else { return [] }

            var request = URLRequest(url: url)
            request.setValue(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
                forHTTPHeaderField: "User-Agent"
            )

            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200
            else {
                DebugConfig.debugError("\(tag): HTTP request failed")
                return []
            }

            guard let htmlContent = String(data: data, encoding: .utf8) else {
                DebugConfig.debugError("\(tag): Could not decode HTML content")
                return []
            }

            DebugConfig.debugPrint("\(tag): Successfully fetched HTML content (\(htmlContent.count) chars)")

            let scrapedData = extractPDFDataFromHTML(htmlContent)
            DebugConfig.debugPrint("\(tag): Successfully scraped \(scrapedData.count) PDF URLs")

            for item in scrapedData {
                scrapedURLs[item.routeId] = item.pdfUrl
                DebugConfig.debugPrint("\(tag): Found PDF for \(item.routeId): \(item.pdfUrl)")
            }

            scrapingCompleted = true
            DebugConfig.debugPrint("\(tag): Scraping completed, \(scrapedURLs.count) URLs cached")

            return scrapedData

        } catch {
            DebugConfig.debugError("\(tag): Error scraping PDF URLs", error: error)
            return []
        }
    }

    private func extractPDFDataFromHTML(_ htmlContent: String) -> [ScrapedPDFData] {
        var scrapedData: [ScrapedPDFData] = []

        guard let pdfPattern = try? NSRegularExpression(
            pattern: #"href="([^"]*\.pdf)""#,
            options: .caseInsensitive
        ) else { return [] }

        let range = NSRange(htmlContent.startIndex..., in: htmlContent)
        let matches = pdfPattern.matches(in: htmlContent, range: range)
        DebugConfig.debugPrint("\(tag): Found \(matches.count) PDF links in HTML")

        var uniquePdfUrls = Set<String>()
        for match in matches {
            if let urlRange = Range(match.range(at: 1), in: htmlContent) {
                uniquePdfUrls.insert(String(htmlContent[urlRange]))
            }
        }

        DebugConfig.debugPrint("\(tag): After removing duplicates: \(uniquePdfUrls.count) unique PDF URLs")

        for pdfUrl in uniquePdfUrls {
            let absoluteUrl: String
            if pdfUrl.hasPrefix("http") {
                absoluteUrl = pdfUrl
            } else if pdfUrl.hasPrefix("/") {
                absoluteUrl = "https://www.linecar.es\(pdfUrl)"
            } else {
                absoluteUrl = "https://www.linecar.es/metropolitano/segovia/\(pdfUrl)"
            }

            let routeId = extractRouteIdFromLinecarURL(absoluteUrl)

            if !routeId.isEmpty {
                scrapedData.append(ScrapedPDFData(
                    routeId: routeId,
                    pdfUrl: absoluteUrl,
                    lastUpdated: extractLastUpdatedDateFromHTML(htmlContent)
                ))
                DebugConfig.debugPrint("\(tag): Extracted route \(routeId) from \(absoluteUrl)")
            } else {
                DebugConfig.debugWarn("\(tag): Could not extract route ID from: \(absoluteUrl)")
            }
        }

        return scrapedData
    }

    private func extractRouteIdFromLinecarURL(_ url: String) -> String {
        guard let filename = url.split(separator: "/").last.map(String.init) else { return "" }

        // Pattern 1: SEGOVIA-{ROUTE_ID}
        if let segoviaMatch = filename.range(of: #"SEGOVIA-([A-Z0-9]+)"#, options: .regularExpression) {
            let matched = String(filename[segoviaMatch])
            let routeId = matched.replacingOccurrences(of: "SEGOVIA-", with: "")
            return routeId.uppercased()
        }

        // Pattern 2: M{number}.pdf
        if let simpleMatch = filename.range(of: #"^(M[0-9]+)\.pdf$"#, options: [.regularExpression, .caseInsensitive]) {
            let matched = String(filename[simpleMatch])
            return matched.replacingOccurrences(of: ".pdf", with: "", options: .caseInsensitive).uppercased()
        }

        // Pattern 3: M{number}-{anything}.pdf
        if let suffixMatch = filename.range(of: #"^(M[0-9]+)-"#, options: [.regularExpression, .caseInsensitive]) {
            let matched = String(filename[suffixMatch]).dropLast() // remove trailing "-"
            return String(matched).uppercased()
        }

        DebugConfig.debugWarn("\(tag): Could not match any route pattern in filename: \(filename)")
        return ""
    }

    private func extractLastUpdatedDateFromHTML(_ htmlContent: String) -> String? {
        let patterns = [
            "actualización", "última actualización",
            "fecha de actualización", "actualizado", "updated",
        ]

        for pattern in patterns {
            let regex = try? NSRegularExpression(
                pattern: "\(pattern)[^\\d]*(\\d{1,2}[^\\d]*\\d{4})",
                options: .caseInsensitive
            )
            let range = NSRange(htmlContent.startIndex..., in: htmlContent)
            if let match = regex?.firstMatch(in: htmlContent, range: range),
               let dateRange = Range(match.range(at: 1), in: htmlContent)
            {
                return String(htmlContent[dateRange]).trimmingCharacters(in: .whitespaces)
            }
        }

        return nil
    }
}
