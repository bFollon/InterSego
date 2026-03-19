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

/// Parser for M1 route (Segovia – Garcillán via Polígono, Casino, Valverde, Abades, Martín Miguel)
///
/// The M1 PDF uses non-standard font encodings (no ToUnicode tables) and a sparse table layout
/// where most rows have dashes in many columns. Reliable PDF extraction is not feasible, so
/// timetable data is hardcoded from the official Linecar schedule.
///
/// Source: Linecar M1 PDF, verified from screenshot dated 2026-03-18.
///
/// Markers:
///   * = summer only (13 Jun – 13 Sep, SeasonalAvailability.summerOnly)
///   # = Garcillán only on Fridays (treated as yearRound for simplicity)
///   H = highlighted cell in PDF (no semantic meaning, ignored)
///   L Y V = Lunes y Viernes (Mon & Fri only, treated as yearRound for simplicity)
///
/// No Sunday service. Saturday runs a shorter Segovia ↔ Abades variant (SG-Labajos route).
///
/// Coordinates are placeholders (0.0, 0.0) pending real GPS data.
class M1Parser: CapableParser, RouteStopsProvider {

    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M1"]),
        mode: .production,
        version: "1.2"
    )

    // MARK: - Directions

    private static let directionOutbound = "Segovia → Garcillán"
    private static let directionInbound  = "Garcillán → Segovia"

    // MARK: - Stops

    private enum Stops {
        static let segovia      = BusStop(name: "Segovia",             coordinates: "40.944973, -4.122431")
        static let poligono     = BusStop(name: "Polígono Industrial", coordinates: "40.957976, -4.198156")
        static let poligono2    = BusStop(name: "Polígono Industrial", coordinates: "40.957554, -4.206457")
        static let casino       = BusStop(name: "Casino",              coordinates: "40.965154, -4.209251")
        static let valverde     = BusStop(name: "Valverde de Majano",  coordinates: "40.956274, -4.235343")
        static let abades       = BusStop(name: "Abades",              coordinates: "40.915804, -4.267038")
        static let martinMiguel = BusStop(name: "Martín Miguel",       coordinates: "40.951889, -4.268660")
        static let garcillan    = BusStop(name: "Garcillán",           coordinates: "40.976809, -4.264724")
    }

    // Weekday outbound: 8 named stops (PDF has 8 columns; index 7 = return terminal, skipped)
    // Polígono is a two-stop cluster: poligono → poligono2 on the way out.
    static let m1WeekdayOutbound: [BusStop] = [
        Stops.segovia, Stops.poligono, Stops.poligono2, Stops.casino, Stops.valverde,
        Stops.abades, Stops.martinMiguel, Stops.garcillan
    ]

    static let m1WeekdayInbound: [BusStop] = [
        Stops.garcillan, Stops.martinMiguel, Stops.abades, Stops.valverde,
        Stops.casino, Stops.poligono2, Stops.poligono, Stops.segovia
    ]

    // Saturday runs a shorter variant: Segovia ↔ Abades only (SG-Labajos route)
    static let m1SaturdayOutbound: [BusStop] = [
        Stops.segovia, Stops.casino, Stops.valverde, Stops.abades
    ]

    static let m1SaturdayInbound: [BusStop] = [
        Stops.abades, Stops.valverde, Stops.segovia
    ]

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
        DebugConfig.debugPrint("M1Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // MARK: - Static Timetable (weekday outbound, 11 rows × 8 stops)
    //
    // Rows match the PDF table top-to-bottom. Dashes in the PDF = stop omitted from that array.
    // Index 7 in the PDF outbound table (return-to-Segovia terminal) is not stored.
    // Polígono is a two-stop cluster; both stops share the same scheduled times.

    private func buildStaticTimetables() -> [BusTimetable] {
        let su = SeasonalAvailability.summerOnly

        // ── Weekday outbound ──────────────────────────────────────────────────────────────────
        let wkOut: [[DepartureTime]] = [
            // Segovia (11 departures)
            [t(6,40), t(7,25), t(8,25), t(10,0), t(12,0), t(13,0), t(14,40), t(15,15), t(18,0), t(19,30), t(20,50)],
            // Polígono IND. stop 1 (9 departures — rows 6 and 10 are dashes)
            [t(6,50), t(7,40), t(8,35), t(10,10), t(12,5), t(14,45), t(15,20), t(18,5), t(20,55)],
            // Polígono IND. stop 2 — cluster, +2 min estimate from stop 1
            [t(6,52), t(7,42), t(8,37), t(10,12), t(12,7), t(14,47), t(15,22), t(18,7), t(20,57)],
            // Casino (6 departures — rows 1-3 and 8 are dashes; rows 4,5,7,9,11 are summer-only)
            [t(10,15,su), t(12,10,su), t(13,7), t(14,50,su), t(18,10,su), t(21,0,su)],
            // Valverde (8 departures — rows 1,2,4 are dashes)
            [t(8,40), t(12,15), t(13,10), t(14,55), t(15,25), t(18,15), t(19,40), t(21,5)],
            // Abades (8 departures — rows 1,2,4 are dashes)
            [t(8,45), t(12,20), t(13,15), t(15,0), t(15,30), t(18,20), t(19,45), t(21,10)],
            // Martín Miguel (3 departures — only rows 5,8,11)
            [t(12,25), t(15,35), t(21,15)],
            // Garcillán (4 departures — only rows 5,8,10,11; row 10 is #=Fridays-only, kept as yearRound)
            [t(12,30), t(15,40), t(19,50), t(21,20)],
        ]

        // ── Weekday inbound ───────────────────────────────────────────────────────────────────
        let wkIn: [[DepartureTime]] = [
            // Garcillán (7 departures)
            [t(6,55), t(8,40,su), t(10,40,su), t(12,30), t(15,40), t(16,25), t(21,20)],
            // Martín Miguel (5 departures — rows 2,3,5,7,9-11 are dashes)
            [t(7,0), t(9,40), t(12,25), t(15,35), t(21,15)],
            // Abades (9 departures)
            [t(7,5), t(7,40), t(10,45), t(12,20), t(15,0), t(15,30), t(16,0), t(18,20), t(21,10)],
            // Valverde (10 departures)
            [t(7,10), t(7,50), t(9,40), t(10,50), t(12,15), t(15,5), t(15,25), t(16,5), t(18,25), t(21,5)],
            // Casino (3 departures — only rows 6,9,12; rows 6,12 are summer-only)
            [t(12,10,su), t(16,10), t(21,0,su)],
            // Polígono IND. stop 2 — cluster, PDF anchor time (stop 1 is +2 min)
            [t(7,15), t(10,55), t(12,5), t(15,10), t(15,20), t(18,5), t(20,55)],
            // Polígono IND. stop 1 — +2 min from stop 2
            [t(7,17), t(10,57), t(12,7), t(15,12), t(15,22), t(18,7), t(20,57)],
            // Segovia (12 departures)
            [t(7,25), t(8,0), t(8,55), t(10,0), t(11,0), t(12,45), t(15,15), t(16,0), t(16,20), t(16,50), t(18,35), t(21,35)],
        ]

        // ── Saturday outbound: Segovia → Abades ───────────────────────────────────────────────
        let satOut: [[DepartureTime]] = [
            [t(13,30)], // Segovia
            [t(13,40)], // Casino
            [t(13,45)], // Valverde
            [t(13,50)], // Abades
        ]

        // ── Saturday inbound: Abades → Segovia ────────────────────────────────────────────────
        let satIn: [[DepartureTime]] = [
            [t(10,45)], // Abades
            [t(10,50)], // Valverde
            [t(11,0)],  // Segovia
        ]

        return buildTimetables(stops: Self.m1WeekdayOutbound,  dayType: .weekday,   direction: Self.directionOutbound, deps: wkOut)
             + buildTimetables(stops: Self.m1WeekdayInbound,   dayType: .weekday,   direction: Self.directionInbound,  deps: wkIn)
             + buildTimetables(stops: Self.m1SaturdayOutbound, dayType: .saturday,  direction: Self.directionOutbound, deps: satOut)
             + buildTimetables(stops: Self.m1SaturdayInbound,  dayType: .saturday,  direction: Self.directionInbound,  deps: satIn)
    }

    private func t(_ h: Int, _ m: Int, _ s: SeasonalAvailability = .yearRound) -> DepartureTime {
        DepartureTime(hour: h, minute: m, seasonalAvailability: s)
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
