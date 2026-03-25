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

/// Parser for M5 route (Segovia – Sto. Domingo de Pirón)
///
/// M5 is a linear route operating weekdays and Saturdays. The bus travels from Segovia
/// to Santo Domingo de Pirón via Tizneros, Espirdo, La Higuera, Brieva, and Basardilla.
///
/// The PDF shows four tables:
///   Weekday outbound:  Segovia → Sto. Domingo de Pirón (3 trips, 2 partial)
///   Weekday inbound:   Sto. Domingo de Pirón → Segovia (3 trips, 2 partial)
///   Saturday outbound:  Segovia → Sto. Domingo de Pirón (1 trip)
///   Saturday inbound:   Sto. Domingo de Pirón → Segovia (1 trip)
///
/// Partial trips: some weekday services only run Segovia–La Higuera (outbound) or
/// start from Brieva/La Higuera (inbound). Stops not served simply have fewer departures.
///
/// Footnotes:
///   * = departure from Via Roma (weekday outbound Segovia)
///   *** = departure from Estación de Autobuses (Saturday outbound Segovia)
///
/// No Sunday service. No seasonal restrictions.
///
/// Timetable data hardcoded from official Linecar M5 PDF screenshot (2026-03-25).
class M5Parser: CapableParser, RouteStopsProvider {

    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M5"]),
        mode: .production,
        version: "1.0"
    )

    // MARK: - Directions

    private static let directionOutbound = "Segovia → Sto. Domingo de Pirón"
    private static let directionInbound = "Sto. Domingo de Pirón → Segovia"

    // MARK: - Stops

    private enum Stops {
        static let segovia           = BusStop(id: "m5-segovia",            name: "Segovia",               area: "Segovia",               coordinates: "40.944768, -4.121823")
        static let tizneros          = BusStop(id: "m5-tizneros",           name: "Tizneros",              area: "Tizneros",              coordinates: "0.0, 0.0")
        static let espirdo           = BusStop(id: "m5-espirdo",            name: "Espirdo",               area: "Espirdo",               coordinates: "0.0, 0.0")
        static let laHiguera         = BusStop(id: "m5-la-higuera",         name: "La Higuera",            area: "La Higuera",            coordinates: "0.0, 0.0")
        static let brieva            = BusStop(id: "m5-brieva",             name: "Brieva",                area: "Brieva",                coordinates: "0.0, 0.0")
        static let basardilla        = BusStop(id: "m5-basardilla",         name: "Basardilla",            area: "Basardilla",            coordinates: "0.0, 0.0")
        static let stoDomingoPiron   = BusStop(id: "m5-sto-domingo-piron",  name: "Sto. Domingo de Pirón", area: "Sto. Domingo de Pirón", coordinates: "0.0, 0.0")

        // Inbound copies with -in suffix
        static let segoviaIn           = BusStop(id: "m5-segovia-in",            name: "Segovia",               area: "Segovia",               coordinates: "40.944768, -4.121823")
        static let tiznerosIn          = BusStop(id: "m5-tizneros-in",           name: "Tizneros",              area: "Tizneros",              coordinates: "0.0, 0.0")
        static let espirdoIn           = BusStop(id: "m5-espirdo-in",            name: "Espirdo",               area: "Espirdo",               coordinates: "0.0, 0.0")
        static let laHigueraIn         = BusStop(id: "m5-la-higuera-in",         name: "La Higuera",            area: "La Higuera",            coordinates: "0.0, 0.0")
        static let brievaIn            = BusStop(id: "m5-brieva-in",             name: "Brieva",                area: "Brieva",                coordinates: "0.0, 0.0")
        static let basardillaIn        = BusStop(id: "m5-basardilla-in",         name: "Basardilla",            area: "Basardilla",            coordinates: "0.0, 0.0")
        static let stoDomingoPironIn   = BusStop(id: "m5-sto-domingo-piron-in",  name: "Sto. Domingo de Pirón", area: "Sto. Domingo de Pirón", coordinates: "0.0, 0.0")
    }

    // Outbound: Segovia → Sto. Domingo de Pirón
    static let m5Outbound: [BusStop] = [
        Stops.segovia, Stops.tizneros, Stops.espirdo, Stops.laHiguera,
        Stops.brieva, Stops.basardilla, Stops.stoDomingoPiron
    ]

    // Inbound: Sto. Domingo de Pirón → Segovia
    static let m5Inbound: [BusStop] = [
        Stops.stoDomingoPironIn, Stops.basardillaIn, Stops.brievaIn,
        Stops.laHigueraIn, Stops.espirdoIn, Stops.tiznerosIn, Stops.segoviaIn
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains { $0.caseInsensitiveCompare(routeId) == .orderedSame }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M5") == .orderedSame else { return [] }
        return [Self.m5Outbound, Self.m5Inbound]
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M5") == .orderedSame else { return [] }
        switch dayType {
        case .weekday, .saturday:
            return [
                RouteVariant(id: "regular", label: Self.directionOutbound,
                             stops: Self.m5Outbound, direction: Self.directionOutbound),
                RouteVariant(id: "reverse", label: Self.directionInbound,
                             stops: Self.m5Inbound, direction: Self.directionInbound)
            ]
        default:
            return []
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M5") == .orderedSame else { return nil }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        return variants.enumerated().map { index, variant in
            let swapTargetId = variants.count == 2 ? variants[1 - index].id : nil
            return RouteView(
                id: variant.id, label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction, departureLabel: variant.departureLabel,
                swapAction: swapTargetId.map { SwapAction(targetViewId: $0) }
            )
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M5") == .orderedSame else { return [] }
        let dow = Calendar.current.component(.weekday, from: today)
        let isWeekday = dow != 1 && dow != 7  // not Sunday (1) and not Saturday (7)
        let isSaturday = dow == 7
        guard let weekdayViews = getRouteViews(routeId, dayType: .weekday),
              let saturdayViews = getRouteViews(routeId, dayType: .saturday) else { return [] }
        return [
            RouteSelectorEntry(id: "entry-lv", label: "Lunes a Viernes", views: weekdayViews, initialViewId: "regular", timetableDayType: .weekday, isActiveToday: isWeekday),
            RouteSelectorEntry(id: "entry-sabado", label: "Sábados", views: saturdayViews, initialViewId: "regular", timetableDayType: .saturday, isActiveToday: isSaturday)
        ]
    }

    func parse(pdfPath: String, routeId: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M5Parser: returning hardcoded timetable (PDF parsing bypassed)")
        return buildStaticTimetables()
    }

    // MARK: - Static Timetable
    //
    // Source: Linecar M5 PDF screenshot, 2026-03-25.
    // Weekday + Saturday service. No Sunday service. No seasonal restrictions.
    //
    // Partial trips:
    //   Outbound: 15:15 and 20:30 only go to La Higuera (Brieva/Basardilla/StoDomingo = dash)
    //   Inbound:  07:25 starts from Brieva (StoDomingo/Basardilla = dash)
    //             15:45 starts from La Higuera (StoDomingo/Basardilla/Brieva = dash)
    //
    // Footnotes:
    //   * weekday outbound Segovia = departure from Via Roma
    //   *** Saturday outbound Segovia = departure from Estación de Autobuses

    private func buildStaticTimetables() -> [BusTimetable] {

        // ── Weekday outbound: Segovia → Sto. Domingo de Pirón ──────────────────────────────────
        // PDF row 1: 13:45  14:00  14:10  14:15  14:25  14:30  14:35  (full trip)
        // PDF row 2: 15:15  15:30  15:40  15:45   —      —      —    (partial: ends La Higuera)
        // PDF row 3: 20:30  20:45  20:55  21:00   —      —      —    (partial: ends La Higuera)
        let wkOutDeps: [[DepartureTime]] = [
            [t(13,45), t(15,15), t(20,30)],  // SEGOVIA
            [t(14,0),  t(15,30), t(20,45)],  // TIZNEROS
            [t(14,10), t(15,40), t(20,55)],  // ESPIRDO
            [t(14,15), t(15,45), t(21,0)],   // LA_HIGUERA
            [t(14,25)],                       // BRIEVA (row 1 only)
            [t(14,30)],                       // BASARDILLA (row 1 only)
            [t(14,35)],                       // STO_DOMINGO_PIRON (row 1 only)
        ]

        // ── Weekday inbound: Sto. Domingo de Pirón → Segovia ──────────────────────────────────
        // PDF row 1:  —      —     07:25  07:30  07:35  07:40  07:50  (partial: starts Brieva)
        // PDF row 2: 10:00  10:05  10:10  10:20  10:25  10:30  10:40  (full trip)
        // PDF row 3:  —      —      —     15:45  15:50  15:55  16:05  (partial: starts La Higuera)
        let wkInDeps: [[DepartureTime]] = [
            [t(10,0)],                         // STO_DOMINGO_PIRON_IN (row 2 only)
            [t(10,5)],                         // BASARDILLA_IN (row 2 only)
            [t(7,25),  t(10,10)],              // BRIEVA_IN (rows 1,2)
            [t(7,30),  t(10,20), t(15,45)],   // LA_HIGUERA_IN
            [t(7,35),  t(10,25), t(15,50)],   // ESPIRDO_IN
            [t(7,40),  t(10,30), t(15,55)],   // TIZNEROS_IN
            [t(7,50),  t(10,40), t(16,5)],    // SEGOVIA_IN
        ]

        // ── Saturday outbound: Segovia → Sto. Domingo de Pirón ─────────────────────────────────
        // PDF row 1: 12:45  13:00  13:05  13:10  13:20  13:25  13:30  (full trip)
        let satOutDeps: [[DepartureTime]] = [
            [t(12,45)],  // SEGOVIA
            [t(13,0)],   // TIZNEROS
            [t(13,5)],   // ESPIRDO
            [t(13,10)],  // LA_HIGUERA
            [t(13,20)],  // BRIEVA
            [t(13,25)],  // BASARDILLA
            [t(13,30)],  // STO_DOMINGO_PIRON
        ]

        // ── Saturday inbound: Sto. Domingo de Pirón → Segovia ──────────────────────────────────
        // PDF row 1: 10:00  10:05  10:10  10:20  10:25  10:30  10:40  (full trip)
        let satInDeps: [[DepartureTime]] = [
            [t(10,0)],   // STO_DOMINGO_PIRON_IN
            [t(10,5)],   // BASARDILLA_IN
            [t(10,10)],  // BRIEVA_IN
            [t(10,20)],  // LA_HIGUERA_IN
            [t(10,25)],  // ESPIRDO_IN
            [t(10,30)],  // TIZNEROS_IN
            [t(10,40)],  // SEGOVIA_IN
        ]

        return buildTimetables(stops: Self.m5Outbound, dayType: .weekday, direction: Self.directionOutbound, deps: wkOutDeps)
             + buildTimetables(stops: Self.m5Inbound, dayType: .weekday, direction: Self.directionInbound, deps: wkInDeps)
             + buildTimetables(stops: Self.m5Outbound, dayType: .saturday, direction: Self.directionOutbound, deps: satOutDeps)
             + buildTimetables(stops: Self.m5Inbound, dayType: .saturday, direction: Self.directionInbound, deps: satInDeps)
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
                routeId: "M5",
                stopId: stop.id,
                dayType: dayType,
                departures: deps[i],
                direction: direction
            )
        }
    }
}
