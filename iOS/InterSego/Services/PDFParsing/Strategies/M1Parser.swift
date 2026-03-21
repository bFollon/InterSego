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
/// M1 is a circular route with two main directions:
///   Direction A (circularA): Segovia → Polígono → Polígono2 → Casino → Valverde → Abades
///                             → Martín Miguel → Garcillán → Segovia (full outbound, direct return)
///   Direction B (circularB): Segovia → Polígono → Polígono2 → Garcillán → Martín Miguel
///                             → Abades → Valverde → Casino → Polígono2 → Polígono → Segovia
///                             (direct outbound, return via all villages)
///
/// The M1 PDF uses non-standard font encodings. Timetable data is hardcoded from the
/// official Linecar schedule (screenshot dated 2026-03-18).
///
/// Annotations:
///   ★  = Jun 13–Sep 13 only (juneToSeptOnly)
///   (*) = different pickup location (gasolinera), NOT summer-restricted (yearRound)
///   L Y V = Lunes y Viernes only (monFriOnly)
///   #  = Fridays only (friOnly)
///
/// No Sunday service. Saturday runs a shorter Segovia ↔ Abades variant.
class M1Parser: CapableParser, RouteStopsProvider {

    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M1"]),
        mode: .production,
        version: "1.6"
    )

    // MARK: - Directions

    // Direction A: full outbound via villages + direct return to Segovia
    private static let directionCircularA = "Segovia → Garcillán"
    // Direction B: direct outbound to Garcillán + return via all villages
    private static let directionCircularB = "Garcillán → Segovia"

    // MARK: - Stops

    private enum Stops {
        static let segovia        = BusStop(id: "m1-segovia",         name: "Segovia",              coordinates: "40.944973, -4.122431")
        static let poligono       = BusStop(id: "m1-poligono",        name: "Polígono Industrial",   coordinates: "40.957976, -4.198156")
        static let poligono2      = BusStop(id: "m1-poligono-2",      name: "Polígono Industrial 2", coordinates: "40.957554, -4.206457")
        static let casino         = BusStop(id: "m1-casino",          name: "Casino",                coordinates: "40.965154, -4.209251")
        static let valverde       = BusStop(id: "m1-valverde",        name: "Valverde de Majano",    coordinates: "40.956274, -4.235343")
        static let abades         = BusStop(id: "m1-abades",          name: "Abades",                coordinates: "40.915804, -4.267038")
        static let martinMiguel   = BusStop(id: "m1-martin-miguel",   name: "Martín Miguel",         coordinates: "40.951889, -4.268660")
        static let garcillan      = BusStop(id: "m1-garcillan",       name: "Garcillán",             coordinates: "40.976809, -4.264724")
        // Circular return stops — same physical locations, distinct IDs for route positioning
        static let segoviaReturn  = BusStop(id: "m1-segovia-return",  name: "Segovia",              coordinates: "40.944973, -4.122431")
        static let poligonoBIn    = BusStop(id: "m1-poligono-b-in",   name: "Polígono Industrial",   coordinates: "40.957976, -4.198156")
        static let poligono2BIn   = BusStop(id: "m1-poligono-2-b-in", name: "Polígono Industrial 2", coordinates: "40.957554, -4.206457")
    }

    // Direction A: Segovia → (all villages) → Garcillán → Segovia (return)
    static let m1CircularAWeekday: [BusStop] = [
        Stops.segovia, Stops.poligono, Stops.poligono2,
        Stops.casino, Stops.valverde, Stops.abades, Stops.martinMiguel,
        Stops.garcillan, Stops.segoviaReturn
    ]

    // Direction B: Segovia → Polígono → Polígono2 → Garcillán → (all villages) → Segovia (return)
    static let m1CircularBWeekday: [BusStop] = [
        Stops.segovia, Stops.poligono, Stops.poligono2,
        Stops.garcillan, Stops.martinMiguel, Stops.abades, Stops.valverde,
        Stops.casino, Stops.poligono2BIn, Stops.poligonoBIn, Stops.segoviaReturn
    ]

    // Saturday: shorter Segovia ↔ Abades route (SG-Labajos variant)
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
        return [Self.m1CircularAWeekday, Self.m1CircularBWeekday]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else { return [] }
        switch dayType {
        case .saturday:
            return [
                RouteVariant(id: "circularA", label: Self.directionCircularA,
                             stops: Self.m1SaturdayOutbound, direction: Self.directionCircularA),
                RouteVariant(id: "circularB", label: Self.directionCircularB,
                             stops: Self.m1SaturdayInbound,  direction: Self.directionCircularB)
            ]
        case .sunday:
            return []
        default:
            return [
                RouteVariant(id: "circularA", label: Self.directionCircularA,
                             stops: Self.m1CircularAWeekday, direction: Self.directionCircularA),
                RouteVariant(id: "circularB", label: Self.directionCircularB,
                             stops: Self.m1CircularBWeekday, direction: Self.directionCircularB)
            ]
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else { return nil }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        return variants.enumerated().map { (index, variant) in
            let swapTargetId = variants.count == 2 ? variants[1 - index].id : nil
            return RouteView(
                id: variant.id,
                label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction,
                departureLabel: variant.departureLabel,
                swapAction: swapTargetId.map { SwapAction(targetViewId: $0) }
            )
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M1") == .orderedSame else { return [] }
        let dow = Calendar.current.component(.weekday, from: today) // 1=Sun, 7=Sat
        let isWeekday  = dow != 7 && dow != 1
        let isSaturday = dow == 7
        guard let weekdayViews  = getRouteViews(routeId, dayType: .weekday),
              let saturdayViews = getRouteViews(routeId, dayType: .saturday) else { return [] }
        return [
            RouteSelectorEntry(id: "entry-lv-a",  label: "L-V - \(Self.directionCircularA)",  views: weekdayViews,  initialViewId: "circularA", timetableDayType: .weekday,  isActiveToday: isWeekday),
            RouteSelectorEntry(id: "entry-lv-b",  label: "L-V - \(Self.directionCircularB)",  views: weekdayViews,  initialViewId: "circularB", timetableDayType: .weekday,  isActiveToday: isWeekday),
            RouteSelectorEntry(id: "entry-sab-a", label: "Sáb - \(Self.directionCircularA)", views: saturdayViews, initialViewId: "circularA", timetableDayType: .saturday, isActiveToday: isSaturday),
            RouteSelectorEntry(id: "entry-sab-b", label: "Sáb - \(Self.directionCircularB)", views: saturdayViews, initialViewId: "circularB", timetableDayType: .saturday, isActiveToday: isSaturday)
        ]
    }

    func parse(pdfPath: String, routeId: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M1Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // MARK: - Static Timetable
    //
    // Source: Linecar M1 PDF screenshot, 2026-03-18.
    // Dashes in the PDF = stop omitted from that row's departure list.
    // Polígono is a two-stop cluster; times are estimated +2 min between stops.

    private func buildStaticTimetables() -> [BusTimetable] {
        let jun = SeasonalAvailability.juneToSeptOnly
        let fri = SeasonalAvailability.friOnly
        let lyv = SeasonalAvailability.monFriOnly

        // ── Weekday Direction A (circularA): full outbound via all villages + direct return ──────
        //
        // Buses that go Segovia → Casino → Valverde → Abades → Martín Miguel → Garcillán
        // then return directly to Segovia (rows 5, 8, 11 reach Garcillán and return).
        // Note: 15:15 bus (row 8) skips Casino due to a PDF dash.
        let circADeps: [[DepartureTime]] = [
            // SEGOVIA (11 departures)
            [t(6,40), t(7,25), t(8,25), t(10,0), t(12,0), t(13,0), t(14,40), t(15,15), t(18,0), t(19,30), t(20,50)],
            // POLIGONO (9 — 13:00 and 19:30 rows are dashes)
            [t(6,50), t(7,40), t(8,35), t(10,10), t(12,5), t(14,45), t(15,20), t(18,5), t(20,55)],
            // POLIGONO_2 — cluster, +2 min estimate from stop 1
            [t(6,52), t(7,42), t(8,37), t(10,12), t(12,7), t(14,47), t(15,22), t(18,7), t(20,57)],
            // CASINO (6 — rows 1-3 and 8=15:15 are dashes; ★ = Jun–Sep only)
            [t(10,15,jun), t(12,10,jun), t(13,7), t(14,50,jun), t(18,10,jun), t(21,0,jun)],
            // VALVERDE (8 — rows 1,2,4 dashes)
            [t(8,40), t(12,15), t(13,10), t(14,55), t(15,25), t(18,15), t(19,40), t(21,5)],
            // ABADES (8 — rows 1,2,4 dashes)
            [t(8,45), t(12,20), t(13,15), t(15,0), t(15,30), t(18,20), t(19,45), t(21,10)],
            // MARTIN_MIGUEL (3 — rows 5, 8, 11 only)
            [t(12,25), t(15,35), t(21,15)],
            // GARCILLAN (4 — rows 5, 8, 10, 11; row 10 = # Fridays only)
            [t(12,30), t(15,40), t(19,50,fri), t(21,20)],
            // SEGOVIA_RETURN — direct return from Garcillán (rows 5, 8, 11)
            [t(12,45), t(16,0), t(21,35)],
        ]

        // ── Weekday Direction B (circularB): direct outbound to Garcillán + return via villages ──
        //
        // Outbound leg: Segovia → Polígono → Polígono2 → Garcillán (only 6:40 bus documented).
        // Return leg: Garcillán → Martín Miguel → Abades → Valverde → Casino → Polígono2 → Polígono → Segovia.
        // (*) at Garcillán 8:40 and 10:40 = different pickup location (gasolinera), NOT summer-only.
        // Backward pass times from Direction A outbound buses have been removed.
        let circBDeps: [[DepartureTime]] = [
            // SEGOVIA outbound — only the 6:40 direct bus is documented in the PDF
            [t(6,40)],
            // POLIGONO first pass
            [t(6,50)],
            // POLIGONO_2 first pass
            [t(6,52)],
            // GARCILLAN turning point; 8:40 and 10:40 are (*) = gasolinera pickup = year-round
            [t(6,55), t(8,40), t(10,40), t(16,25)],
            // MARTIN_MIGUEL return leg (9:40 = L Y V Mondays & Fridays only)
            [t(7,0), t(9,40,lyv)],
            // ABADES return leg
            [t(7,5), t(7,40), t(10,45), t(16,0), t(18,20)],
            // VALVERDE return leg (9:40 = L Y V Mondays & Fridays only)
            [t(7,10), t(7,50), t(9,40,lyv), t(10,50), t(15,5), t(16,5), t(18,25)],
            // CASINO return leg
            [t(16,10)],
            // POLIGONO_2_B_IN return leg
            [t(7,15), t(10,55), t(15,10)],
            // POLIGONO_B_IN return leg
            [t(7,17), t(10,57), t(15,12)],
            // SEGOVIA_RETURN arrival
            [t(7,25), t(8,0), t(8,55), t(10,0), t(11,0), t(15,15), t(16,20), t(16,50), t(18,35)],
        ]

        // ── Saturday Direction A: Segovia → Casino → Valverde → Abades ───────────────────────
        let satADeps: [[DepartureTime]] = [
            [t(13,30)], // SEGOVIA
            [t(13,40)], // CASINO
            [t(13,45)], // VALVERDE
            [t(13,50)], // ABADES
        ]

        // ── Saturday Direction B: Abades → Valverde → Segovia ────────────────────────────────
        let satBDeps: [[DepartureTime]] = [
            [t(10,45)], // ABADES
            [t(10,50)], // VALVERDE
            [t(11,0)],  // SEGOVIA
        ]

        return buildTimetables(stops: Self.m1CircularAWeekday, dayType: .weekday,  direction: Self.directionCircularA, deps: circADeps)
             + buildTimetables(stops: Self.m1CircularBWeekday, dayType: .weekday,  direction: Self.directionCircularB, deps: circBDeps)
             + buildTimetables(stops: Self.m1SaturdayOutbound, dayType: .saturday, direction: Self.directionCircularA, deps: satADeps)
             + buildTimetables(stops: Self.m1SaturdayInbound,  dayType: .saturday, direction: Self.directionCircularB, deps: satBDeps)
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
                stopId: stop.id,
                dayType: dayType,
                departures: deps[i],
                direction: direction
            )
        }
    }
}
