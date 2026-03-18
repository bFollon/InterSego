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

import CoreGraphics
import Foundation
import PDFKit

/// Accumulates raw PDF glyph bytes during a CGPDFScanner pass.
private class M1GlyphCollector {
    var rawBytes: [UInt8] = []
}

/// Parser for M1 route (Segovia – Garcillán via Polígono, Casino, Valverde, Abades, Martín Miguel)
///
/// PDF Structure:
/// - Two tables rendered side by side: outbound (left, 8 columns) and inbound (right, 7 columns).
/// - Each decoded row = one departure: 15 times total (8 outbound stops + 7 inbound stops).
///   Index 7 in each outbound row is the return-to-Segovia terminal and is not stored.
/// - Saturday section is appended inline in TEXT font after the last weekday row.
///   Saturday times are 'H'-delimited: first 4 = outbound (Segovia→Abades),
///   next 3 = inbound (Abades→Segovia).
/// - Two font types in the PDF:
///     Time font: raw codes 0xEC–0xF5 = digits 0–9 | 0x72 = row separator ('\n') |
///                0x57 = colon placeholder ('t') | 0x8E/0xC1/0xC9 = '*' | 0xB7 = '#'
///     Text font: standard +29 character-offset encoding (shared with M4/M6)
///
/// PDFKit's page.string returns an empty string for M1 (no ToUnicode tables in the fonts).
/// This parser uses CGPDFScanner to extract raw glyph bytes directly from the content stream,
/// mirroring Android's RawGlyphExtractionStrategy / PdfCanvasProcessor approach.
///
/// '*' on a time = summer only (13 Jun – 13 Sep → SeasonalAvailability.summerOnly).
/// No Sunday service.
///
/// Coordinates are placeholders (0.0, 0.0) pending real GPS data.
class M1Parser: CapableParser, RouteStopsProvider {

    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M1"]),
        mode: .debug,
        version: "1.0"
    )

    // MARK: - Directions

    private static let directionOutbound = "Segovia → Garcillán"
    private static let directionInbound  = "Garcillán → Segovia"

    // MARK: - Stops

    private enum Stops {
        static let segovia      = BusStop(name: "Segovia",             coordinates: "0.0, 0.0")
        static let poligono     = BusStop(name: "Polígono Industrial", coordinates: "0.0, 0.0")
        static let casino       = BusStop(name: "Casino",              coordinates: "0.0, 0.0")
        static let valverde     = BusStop(name: "Valverde de Majano",  coordinates: "0.0, 0.0")
        static let abades       = BusStop(name: "Abades",              coordinates: "0.0, 0.0")
        static let martinMiguel = BusStop(name: "Martín Miguel",       coordinates: "0.0, 0.0")
        static let garcillan    = BusStop(name: "Garcillán",           coordinates: "0.0, 0.0")
    }

    // Weekday outbound: 7 named stops (PDF has 8 columns; index 7 = return terminal, skipped)
    static let m1WeekdayOutbound: [BusStop] = [
        Stops.segovia, Stops.poligono, Stops.casino, Stops.valverde,
        Stops.abades, Stops.martinMiguel, Stops.garcillan
    ]

    static let m1WeekdayInbound: [BusStop] = [
        Stops.garcillan, Stops.martinMiguel, Stops.abades, Stops.valverde,
        Stops.casino, Stops.poligono, Stops.segovia
    ]

    // Saturday runs a shorter variant: Segovia ↔ Abades only
    static let m1SaturdayOutbound: [BusStop] = [
        Stops.segovia, Stops.casino, Stops.valverde, Stops.abades
    ]

    static let m1SaturdayInbound: [BusStop] = [
        Stops.abades, Stops.valverde, Stops.segovia
    ]

    // MARK: - Patterns

    // Time token with optional suffix marker (* # I)
    private static let timeWithMarkerPattern = try! NSRegularExpression(
        pattern: #"(\d{1,2}:\d{2})([*#I]?)"#
    )

    // Saturday times are separated by 'H' in the decoded text section.
    private static let satTimePattern = try! NSRegularExpression(
        pattern: #"(\d{1,2}:\d{2})([*]?)H"#
    )

    private static let digitSpaceDigitPattern = try! NSRegularExpression(
        pattern: #"(\d) (\d)"#
    )

    private static let multiSpacePattern = try! NSRegularExpression(
        pattern: #" +"#
    )

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains { $0.caseInsensitiveCompare(routeId) == .orderedSame }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else { return [] }
        return [Self.m1WeekdayOutbound, Self.m1WeekdayInbound]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else { return [] }
        switch dayType {
        case .saturday:
            return [
                RouteVariant(id: "outbound", label: Self.directionOutbound,
                             stops: Self.m1SaturdayOutbound, direction: Self.directionOutbound),
                RouteVariant(id: "inbound",  label: Self.directionInbound,
                             stops: Self.m1SaturdayInbound,  direction: Self.directionInbound)
            ]
        case .sunday:
            return []
        default:
            return [
                RouteVariant(id: "outbound", label: Self.directionOutbound,
                             stops: Self.m1WeekdayOutbound, direction: Self.directionOutbound),
                RouteVariant(id: "inbound",  label: Self.directionInbound,
                             stops: Self.m1WeekdayInbound,  direction: Self.directionInbound)
            ]
        }
    }

    func parse(pdfPath: String, routeId: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M1Parser: Starting PDF parsing for \(pdfPath)")

        guard FileManager.default.fileExists(atPath: pdfPath) else {
            throw PDFParsingError("PDF file not found: \(pdfPath)")
        }

        guard let document = PDFDocument(url: URL(fileURLWithPath: pdfPath)) else {
            throw PDFParsingError("Could not open PDF at \(pdfPath)")
        }

        var allLines: [String] = []

        for pageIndex in 0..<document.pageCount {
            guard let page = document.page(at: pageIndex),
                  let cgPage = page.pageRef else { continue }

            // M1 uses non-standard font encodings that PDFKit cannot decode via page.string
            // (returns empty string). Use CGPDFScanner to extract raw glyph bytes directly,
            // then apply the M1-specific decoder — mirrors Android's RawGlyphExtractionStrategy.
            let rawText = extractRawGlyphs(from: cgPage)

            let rawSample = rawText.prefix(80)
            let rawCodepoints = rawSample.unicodeScalars.map { "U+\(String(format: "%04X", $0.value))" }.joined(separator: " ")
            DebugConfig.debugPrint("M1Parser[p\(pageIndex)] raw codepoints (first 80): \(rawCodepoints)")

            let decoded = Self.decodeM1Text(rawText)
            let decodedSample = decoded.prefix(80)
            DebugConfig.debugPrint("M1Parser[p\(pageIndex)] after decodeM1Text (first 80): \(String(decodedSample).debugDescription)")

            // Normalise: colon placeholder → ':', collapse spaces.
            var normalised = decoded.replacingOccurrences(of: " t ", with: ":")
            normalised = Self.digitSpaceDigitPattern.stringByReplacingMatches(
                in: normalised, range: NSRange(normalised.startIndex..., in: normalised), withTemplate: "$1$2")
            normalised = Self.digitSpaceDigitPattern.stringByReplacingMatches(
                in: normalised, range: NSRange(normalised.startIndex..., in: normalised), withTemplate: "$1$2")
            normalised = Self.multiSpacePattern.stringByReplacingMatches(
                in: normalised, range: NSRange(normalised.startIndex..., in: normalised), withTemplate: " ")

            let pageLines = normalised.components(separatedBy: "\n")
            DebugConfig.debugPrint("M1Parser[p\(pageIndex)] lines after normalisation: \(pageLines.count)")
            pageLines.prefix(10).enumerated().forEach { i, line in
                if !line.trimmingCharacters(in: .whitespaces).isEmpty {
                    DebugConfig.debugPrint("M1Parser[p\(pageIndex)]   line[\(i)]: \(line.debugDescription)")
                }
            }
            allLines += pageLines
        }

        let timetables = parseTimeTable(allLines)
        DebugConfig.debugPrint("M1Parser: Parsed \(timetables.count) timetables")
        return timetables
    }

    /// Extracts raw PDF glyph bytes using CGPDFScanner, bypassing PDFKit's font decoding.
    /// Returns a String where each character's Unicode value equals the raw byte value (U+0000–U+00FF).
    private func extractRawGlyphs(from cgPage: CGPDFPage) -> String {
        let collector = M1GlyphCollector()

        let stream = CGPDFContentStreamCreateWithPage(cgPage)
        defer { CGPDFContentStreamRelease(stream) }

        let table = CGPDFOperatorTableCreate()!
        defer { CGPDFOperatorTableRelease(table) }

        let info = Unmanaged.passUnretained(collector).toOpaque()

        // Tj: show string — collect raw bytes
        CGPDFOperatorTableSetCallback(table, "Tj") { scanner, info in
            guard let info = info else { return }
            let c = Unmanaged<M1GlyphCollector>.fromOpaque(info).takeUnretainedValue()
            var str: CGPDFStringRef?
            guard CGPDFScannerPopString(scanner, &str), let s = str else { return }
            let len = CGPDFStringGetLength(s)
            if let bytes = CGPDFStringGetBytePtr(s) {
                for i in 0..<len { c.rawBytes.append(bytes[i]) }
            }
        }

        // TJ: show array (with kerning) — collect raw bytes from string elements only
        CGPDFOperatorTableSetCallback(table, "TJ") { scanner, info in
            guard let info = info else { return }
            let c = Unmanaged<M1GlyphCollector>.fromOpaque(info).takeUnretainedValue()
            var arr: CGPDFArrayRef?
            guard CGPDFScannerPopArray(scanner, &arr), let array = arr else { return }
            let count = CGPDFArrayGetCount(array)
            for i in 0..<count {
                var str: CGPDFStringRef?
                if CGPDFArrayGetString(array, i, &str), let s = str {
                    let len = CGPDFStringGetLength(s)
                    if let bytes = CGPDFStringGetBytePtr(s) {
                        for j in 0..<len { c.rawBytes.append(bytes[j]) }
                    }
                }
            }
        }

        let scanner = CGPDFScannerCreate(stream, table, info)
        defer { CGPDFScannerRelease(scanner) }
        CGPDFScannerScan(scanner)

        return String(collector.rawBytes.map { Character(UnicodeScalar($0)) })
    }

    // MARK: - M1 Font Decoder

    /// Decode M1 raw PDF glyph bytes (extracted via CGPDFScanner).
    ///
    /// The M1 PDF uses two font encodings — each raw byte is treated as its glyph code:
    ///
    ///   Time font codes:
    ///     0xEC–0xF5 → digits '0'–'9'
    ///     0x72      → '\n' (row separator)
    ///     0x57      → 't'  (colon placeholder, normalised later to ':')
    ///     0x8E, 0xC1, 0xC9 → '*' (seasonal marker)
    ///     0xB7      → '#'  (special marker)
    ///
    ///   Text font codes (standard +29 offset encoding, shared with M4/M6):
    ///     Apply +29 offset to recover printable ASCII.
    private static func decodeM1Text(_ raw: String) -> String {
        var result = ""
        result.reserveCapacity(raw.unicodeScalars.count)
        for scalar in raw.unicodeScalars {
            let code = Int(scalar.value)
            switch code {
            case 0x00:
                continue  // null byte (2-byte CID encoding artefact)
            case 0xEC...0xF5:
                // Time font digits 0–9
                result.append(Character(UnicodeScalar(Int(("0" as UnicodeScalar).value) + code - 0xEC)!))
            case 0x72:
                // Time font row separator
                result.append("\n")
            case 0x57:
                // Time font colon placeholder (also text font 'W' raw = 0x3A → ':' after +29,
                // but that path goes through the +29 branch below). Here we're seeing the time
                // font's raw 0x57 mapped to 'W' by PDFKit, so convert to 't'.
                result.append("t")
            case 0x8E, 0xC1, 0xC9:
                result.append("*")
            case 0xB7:
                result.append("#")
            default:
                // Text font: apply +29 offset
                let shifted = code + 29
                if (0x20...0x7E).contains(shifted) || shifted == 0x0A || shifted == 0x0D {
                    result.append(Character(UnicodeScalar(shifted)!))
                }
                // Codes that map outside printable ASCII are discarded.
            }
        }
        return result
    }

    // MARK: - Parsing

    private func parseTimeTable(_ lines: [String]) -> [BusTimetable] {
        // Departure accumulators per stop index.
        var outDepsWk  = Array(repeating: [DepartureTime](), count: Self.m1WeekdayOutbound.count)
        var inDepsWk   = Array(repeating: [DepartureTime](), count: Self.m1WeekdayInbound.count)
        var outDepsSat = Array(repeating: [DepartureTime](), count: Self.m1SaturdayOutbound.count)
        var inDepsSat  = Array(repeating: [DepartureTime](), count: Self.m1SaturdayInbound.count)

        // Buffer accumulates (time, seasonal) pairs until we have a full row (15 = 8 + 7).
        var buffer: [(hour: Int, minute: Int, seasonal: SeasonalAvailability)] = []

        for line in lines {
            // Split off the Saturday section (H-delimited times in TEXT font) if present.
            let satRange = Self.satTimePattern.rangeOfFirstMatch(
                in: line, range: NSRange(line.startIndex..., in: line))
            let weekdayPart: String
            let satPart: String
            if satRange.location != NSNotFound, let swiftRange = Range(satRange, in: line) {
                weekdayPart = String(line[line.startIndex..<swiftRange.lowerBound])
                satPart     = String(line[swiftRange.lowerBound...])
            } else {
                weekdayPart = line
                satPart     = ""
            }

            // --- Weekday times ---
            buffer += extractTimesWithSeasonal(from: weekdayPart)

            while buffer.count >= 15 {
                let row = Array(buffer.prefix(15))
                buffer.removeFirst(15)

                // Outbound: indices 0–6 (stop 0–6); index 7 = return terminal (skipped)
                for i in 0..<Self.m1WeekdayOutbound.count {
                    outDepsWk[i].append(DepartureTime(hour: row[i].hour, minute: row[i].minute,
                                                      seasonalAvailability: row[i].seasonal))
                }
                // Inbound: PDF indices 8–14
                for i in 0..<Self.m1WeekdayInbound.count {
                    let r = row[i + 8]
                    inDepsWk[i].append(DepartureTime(hour: r.hour, minute: r.minute,
                                                     seasonalAvailability: r.seasonal))
                }
            }

            // --- Saturday times (H-delimited) ---
            if !satPart.isEmpty {
                let satTimes = extractSaturdayTimes(from: satPart)
                for i in 0..<Self.m1SaturdayOutbound.count {
                    if i < satTimes.count {
                        outDepsSat[i].append(DepartureTime(hour: satTimes[i].hour,
                                                           minute: satTimes[i].minute,
                                                           seasonalAvailability: satTimes[i].seasonal))
                    }
                }
                for i in 0..<Self.m1SaturdayInbound.count {
                    let idx = i + Self.m1SaturdayOutbound.count
                    if idx < satTimes.count {
                        inDepsSat[i].append(DepartureTime(hour: satTimes[idx].hour,
                                                          minute: satTimes[idx].minute,
                                                          seasonalAvailability: satTimes[idx].seasonal))
                    }
                }
            }
        }

        // Drain remaining buffer: 8 = outbound-only, 7 = inbound-only.
        if buffer.count == 8 {
            for i in 0..<Self.m1WeekdayOutbound.count {
                outDepsWk[i].append(DepartureTime(hour: buffer[i].hour, minute: buffer[i].minute,
                                                  seasonalAvailability: buffer[i].seasonal))
            }
        } else if buffer.count == 7 {
            for i in 0..<Self.m1WeekdayInbound.count {
                inDepsWk[i].append(DepartureTime(hour: buffer[i].hour, minute: buffer[i].minute,
                                                 seasonalAvailability: buffer[i].seasonal))
            }
        } else if !buffer.isEmpty {
            DebugConfig.debugPrint("M1Parser: \(buffer.count) leftover times after parsing — discarding")
        }

        let wkOutTotal = outDepsWk.map(\.count).reduce(0, +)
        let wkInTotal  = inDepsWk.map(\.count).reduce(0, +)
        let satOutTotal = outDepsSat.map(\.count).reduce(0, +)
        let satInTotal  = inDepsSat.map(\.count).reduce(0, +)
        DebugConfig.debugPrint("M1Parser: departure counts — wkOut=\(wkOutTotal) wkIn=\(wkInTotal) satOut=\(satOutTotal) satIn=\(satInTotal)")

        return buildTimetables(stops: Self.m1WeekdayOutbound,  dayType: .weekday,   direction: Self.directionOutbound, deps: outDepsWk)
             + buildTimetables(stops: Self.m1WeekdayInbound,   dayType: .weekday,   direction: Self.directionInbound,  deps: inDepsWk)
             + buildTimetables(stops: Self.m1SaturdayOutbound, dayType: .saturday,  direction: Self.directionOutbound, deps: outDepsSat)
             + buildTimetables(stops: Self.m1SaturdayInbound,  dayType: .saturday,  direction: Self.directionInbound,  deps: inDepsSat)
    }

    private func extractTimesWithSeasonal(
        from text: String
    ) -> [(hour: Int, minute: Int, seasonal: SeasonalAvailability)] {
        let range = NSRange(text.startIndex..., in: text)
        return Self.timeWithMarkerPattern.matches(in: text, range: range).compactMap { match in
            guard let timeRange   = Range(match.range(at: 1), in: text),
                  let markerRange = Range(match.range(at: 2), in: text) else { return nil }
            let timeStr   = String(text[timeRange])
            let marker    = String(text[markerRange])
            guard let (h, m) = parseTime(timeStr) else { return nil }
            let seasonal: SeasonalAvailability = marker == "*" ? .summerOnly : .yearRound
            return (hour: h, minute: m, seasonal: seasonal)
        }
    }

    private func extractSaturdayTimes(
        from text: String
    ) -> [(hour: Int, minute: Int, seasonal: SeasonalAvailability)] {
        let range = NSRange(text.startIndex..., in: text)
        return Self.satTimePattern.matches(in: text, range: range).compactMap { match in
            guard let timeRange   = Range(match.range(at: 1), in: text),
                  let markerRange = Range(match.range(at: 2), in: text) else { return nil }
            let timeStr = String(text[timeRange])
            let marker  = String(text[markerRange])
            guard let (h, m) = parseTime(timeStr) else { return nil }
            let seasonal: SeasonalAvailability = marker == "*" ? .summerOnly : .yearRound
            return (hour: h, minute: m, seasonal: seasonal)
        }
    }

    private func parseTime(_ timeStr: String) -> (Int, Int)? {
        let parts = timeStr.split(separator: ":")
        guard parts.count == 2,
              let h = Int(parts[0]),
              let m = Int(parts[1]) else { return nil }
        return (h, m)
    }

    private func buildTimetables(
        stops: [BusStop],
        dayType: DayType,
        direction: String,
        deps: [[DepartureTime]]
    ) -> [BusTimetable] {
        stops.enumerated().map { (i, stop) in
            BusTimetable(
                routeId: "M1",
                stopId: stop.name,
                dayType: dayType,
                departures: deps[i],
                direction: direction
            )
        }
    }
}
