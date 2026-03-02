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
import PDFKit

enum PDFTextExtractor {

    /// Extract text from a PDF file at the given path, handling broken font encodings.
    static func extractText(from pdfPath: String, tag: String = "PDFTextExtractor") -> String {
        guard let document = PDFDocument(url: URL(fileURLWithPath: pdfPath)) else {
            DebugConfig.debugError("\(tag): Could not open PDF at \(pdfPath)")
            return ""
        }

        var allText = ""
        for pageIndex in 0..<document.pageCount {
            guard let page = document.page(at: pageIndex) else { continue }
            let pageText = page.string ?? ""
            DebugConfig.debugPrint("\(tag): Page \(pageIndex + 1) extracted \(pageText.count) chars")

            if needsDecoding(pageText) {
                DebugConfig.debugPrint("\(tag): Detected broken encoding on page \(pageIndex + 1), using +29 offset decoder")
                allText += decodeWithCharacterOffset(pageText, offset: 29)
            } else {
                allText += pageText
            }

            if pageIndex < document.pageCount - 1 {
                allText += "\n"
            }
        }

        return allText
    }

    /// Decode text with a fixed character offset (for old Linecar PDFs with broken font encoding).
    static func decodeWithCharacterOffset(_ text: String, offset: Int = 29) -> String {
        var decoded = ""
        for scalar in text.unicodeScalars {
            let code = Int(scalar.value)
            if code == 0x00 { continue }

            let actualCode = code + offset
            if (0x20...0x7E).contains(actualCode) || actualCode == 0x0A || actualCode == 0x0D {
                decoded.append(Character(UnicodeScalar(actualCode)!))
            } else {
                decoded.append("?")
            }
        }
        return decoded
    }

    /// Detect if text needs decoding by checking for high ratio of replacement/control characters.
    static func needsDecoding(_ text: String) -> Bool {
        guard !text.isEmpty else { return false }

        var replacementChars = 0
        var controlChars = 0
        let totalChars = text.count

        for scalar in text.unicodeScalars {
            let code = Int(scalar.value)
            if scalar == "\u{FFFD}" {
                replacementChars += 1
            } else if code < 0x20 && code != 0x0A && code != 0x0D {
                controlChars += 1
            }
        }

        let problematicRatio = Double(replacementChars + controlChars) / Double(totalChars)
        return problematicRatio > 0.5
    }
}
