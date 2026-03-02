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

actor PDFDownloadService {
    static let shared = PDFDownloadService()

    private let session: URLSession

    private init() {
        // URLSession with lenient SSL for linecar.es certificate issues
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 15
        let delegate = LenientSSLDelegate()
        session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
    }

    func downloadPDF(url: String, fileName: String, forceDownload: Bool = false) async -> URL? {
        let pdfDir = AppDirectories.pdfs
        let outputFile = pdfDir.appendingPathComponent(fileName)

        // Create directory if needed
        try? FileManager.default.createDirectory(at: pdfDir, withIntermediateDirectories: true)

        // Skip download if file exists and not forced
        if !forceDownload && FileManager.default.fileExists(atPath: outputFile.path) {
            DebugConfig.debugPrint("PDFDownloadService: Using existing file \(outputFile.lastPathComponent)")
            return outputFile
        }

        // Retry logic (3 attempts)
        for attempt in 0..<3 {
            do {
                DebugConfig.debugPrint("PDFDownloadService: Starting download from \(url) (attempt \(attempt + 1))")

                guard let requestURL = URL(string: url) else { return nil }

                var request = URLRequest(url: requestURL)
                request.setValue("InterSego-iOS/1.0", forHTTPHeaderField: "User-Agent")

                let (data, response) = try await session.data(for: request)

                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode) else {
                    let code = (response as? HTTPURLResponse)?.statusCode ?? -1
                    DebugConfig.debugError("PDFDownloadService: Download failed with code \(code)")
                    if attempt < 2 {
                        try await Task.sleep(for: .seconds(1))
                    }
                    continue
                }

                try data.write(to: outputFile)

                DebugConfig.debugPrint("PDFDownloadService: Successfully downloaded \(data.count) bytes to \(outputFile.lastPathComponent)")
                return outputFile

            } catch is URLError {
                DebugConfig.debugError("PDFDownloadService: Network error on attempt \(attempt + 1)")
                if attempt < 2 {
                    try? await Task.sleep(for: .seconds(1))
                }
            } catch {
                DebugConfig.debugError("PDFDownloadService: Unexpected error on attempt \(attempt + 1)", error: error)
                break
            }
        }

        DebugConfig.debugError("PDFDownloadService: All download attempts failed")
        return nil
    }

    func clearCache() {
        let pdfDir = AppDirectories.pdfs
        try? FileManager.default.removeItem(at: pdfDir)
        DebugConfig.debugPrint("PDFDownloadService: Cleared PDF cache")
    }

    func hasCachedFile(_ fileName: String) -> Bool {
        let file = AppDirectories.pdfs.appendingPathComponent(fileName)
        return FileManager.default.fileExists(atPath: file.path)
    }

    func getCachedFile(_ fileName: String) -> URL? {
        let file = AppDirectories.pdfs.appendingPathComponent(fileName)
        return FileManager.default.fileExists(atPath: file.path) ? file : nil
    }
}

/// Lenient SSL delegate for linecar.es certificate chain issues
private final class LenientSSLDelegate: NSObject, URLSessionDelegate, @unchecked Sendable {
    private let allowedHosts: Set<String> = ["linecar.es", "www.linecar.es", "avilabus.es", "www.avilabus.es"]

    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge) async
        -> (URLSession.AuthChallengeDisposition, URLCredential?) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            return (.performDefaultHandling, nil)
        }

        let host = challenge.protectionSpace.host

        guard allowedHosts.contains(host) else {
            return (.performDefaultHandling, nil)
        }

        // Accept the certificate only if standard trust evaluation passes
        if SecTrustEvaluateWithError(serverTrust, nil) {
            return (.useCredential, URLCredential(trust: serverTrust))
        }

        // Trust evaluation failed — do not bypass
        DebugConfig.debugWarn("PDFDownloadService: Certificate validation failed for \(host)")
        return (.performDefaultHandling, nil)
    }
}
