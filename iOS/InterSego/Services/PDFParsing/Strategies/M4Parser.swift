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

/// Parser for M4 route (La Lastrilla - El Sotillo)
///
/// M4 is a circular route operating weekdays and Saturdays (July/August only).
/// The bus travels from Azoguejo through La Lastrilla to El Sotillo and back.
///
/// Timetable data loaded from Timetables/m4.json (migrated from PDF 2026-04-15).
class M4Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M4"]),
        mode: .debug,
        version: "3.4",
    )

    // MARK: - Directions

    private static let directionRegular = "Lastrilla → Sotillo"
    private static let directionReverse = "Sotillo → Lastrilla"

    // MARK: - Stops

    private enum Stops {
        static let azoguejo = BusStopRegistry.azoguejo
        static let azoguejoEndStop = BusStopRegistry.azoguejoEndStop
        static let delicias = BusStopRegistry.delicias
        static let gasolinera = BusStopRegistry.gasolineraLastrilla
        static let pension = BusStopRegistry.pension
        static let poligono = BusStopRegistry.poligonoLastrilla
        static let ctraValladolid33 = BusStopRegistry.ctraValladolid
        static let leopoldoMoreno = BusStopRegistry.leopoldoMoreno
        static let colegio = BusStopRegistry.colegioLastrilla
        static let parroqSotillo = BusStopRegistry.parroquiaSotillo
        static let hotelAvSotillo = BusStopRegistry.hotelAvSotillo
        static let maspalomas = BusStopRegistry.maspalomas
        static let centroBoal = BusStopRegistry.centroBoal
        static let paseoCabanillas = BusStopRegistry.paseoCabanillasSotillo
        static let rafaelDeLasHeras = BusStopRegistry.rafaelLasHeras
        static let ventaMagullo = BusStopRegistry.ventaMagullo
    }

    static let m4RegularRoute: [BusStop] = [
        Stops.azoguejo,
        Stops.delicias,
        Stops.gasolinera,
        Stops.pension,
        Stops.poligono,
        Stops.ctraValladolid33,
        Stops.leopoldoMoreno,
        Stops.colegio,
        Stops.hotelAvSotillo,
        Stops.maspalomas,
        Stops.centroBoal,
        Stops.paseoCabanillas,
        Stops.parroqSotillo,
        Stops.rafaelDeLasHeras,
        Stops.ventaMagullo,
        Stops.azoguejoEndStop,
    ]

    static let m4ReverseRoute: [BusStop] = [
        Stops.azoguejo,
        Stops.delicias,
        Stops.hotelAvSotillo,
        Stops.maspalomas,
        Stops.centroBoal,
        Stops.paseoCabanillas,
        Stops.parroqSotillo,
        Stops.rafaelDeLasHeras,
        Stops.ventaMagullo,
        Stops.gasolinera,
        Stops.pension,
        Stops.poligono,
        Stops.ctraValladolid33,
        Stops.leopoldoMoreno,
        Stops.colegio,
        Stops.parroqSotillo,
        Stops.azoguejoEndStop,
    ]

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains {
            $0.caseInsensitiveCompare(routeId) == .orderedSame
        }
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else {
            return []
        }
        return [
            Array(Self.m4RegularRoute),
            Array(Self.m4ReverseRoute),
        ]
    }

    func getRouteVariants(_ routeId: String, dayType _: DayType) -> [RouteVariant] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else {
            return []
        }
        return [
            RouteVariant(
                id: "regular",
                label: Self.directionRegular,
                stops: Array(Self.m4RegularRoute),
                direction: Self.directionRegular,
            ),
            RouteVariant(
                id: "reverse",
                label: Self.directionReverse,
                stops: Array(Self.m4ReverseRoute),
                direction: Self.directionReverse,
            ),
        ]
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else {
            return nil
        }
        let variants = getRouteVariants(routeId, dayType: dayType)
        guard !variants.isEmpty else { return nil }
        let tabs = [
            RouteTab(label: "La Lastrilla", viewId: "regular"),
            RouteTab(label: "El Sotillo", viewId: "reverse"),
        ]
        return variants.map { variant in
            RouteView(
                id: variant.id,
                label: variant.label,
                stops: variant.stops.map { RouteViewStop(stop: $0) },
                direction: variant.direction,
                departureLabel: variant.departureLabel,
                swapAction: nil,
                tabs: tabs,
                tabsLabel: "Pasa primero por",
                mergedDirectionLabel: "La Lastrilla · El Sotillo",
            )
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M4") == .orderedSame else {
            return []
        }
        let dow = Calendar.current.component(.weekday, from: today)
        let isWeekday = dow != 1 && dow != 7
        let isSaturday = dow == 7
        guard let weekdayViews = getRouteViews(routeId, dayType: .weekday),
              let saturdayViews = getRouteViews(routeId, dayType: .saturday)
        else { return [] }
        return [
            RouteSelectorEntry(
                id: "entry-lv-lastrilla",
                label: "L-V La Lastrilla primero",
                views: weekdayViews,
                initialViewId: "regular",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-lv-sotillo",
                label: "L-V El Sotillo primero",
                views: weekdayViews,
                initialViewId: "reverse",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-sabado",
                label: "Sábados",
                views: saturdayViews,
                initialViewId: "regular",
                timetableDayType: .saturday,
                isActiveToday: isSaturday,
            ),
        ]
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M4Parser: loading timetable from JSON asset")
        return try TimetableLoader().load("M4")
    }
}
