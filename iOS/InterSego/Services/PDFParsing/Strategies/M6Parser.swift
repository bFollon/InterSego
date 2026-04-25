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

/// Parser for M6 route (Segovia ↔ Torrecaballeros).
///
/// Route metadata (stop lists, RouteViews, RouteEntries) lives here.
/// Timetable data is loaded from Timetables/m6.json via TimetableLoader.
///
/// Weekday: regular (Azoguejo cluster → Torrecaballeros), extended (→ from Andrés Laguna),
/// and circular (21:20, Estación Bus loop via Palazuelos/Tabanera).
/// Saturday: circular route to Torrecaballeros (outbound); different urban return leg
/// (Plaza de Toros → La Pista → Andrés Laguna → Jardinillos).
/// Sunday: same rural leg as Saturday; urban return ends at Estación de Autobuses.
class M6Parser: CapableParser, RouteStopsProvider {
    let capabilities = ParserCapabilities(
        supportedRoutes: Set(["M6"]),
        mode: .production,
        version: "1.0",
    )

    // MARK: - Types

    struct StopCluster {
        let stops: [BusStop]

        init(_ stops: [BusStop]) {
            precondition(!stops.isEmpty)
            self.stops = stops
        }
    }

    enum ClusterAlignment {
        case fromStart
        case fromEnd
    }

    struct Route {
        let clusters: [StopCluster]
        let alignment: ClusterAlignment

        init(
            clusters: [StopCluster],
            alignment: ClusterAlignment = .fromStart,
        ) {
            self.clusters = clusters
            self.alignment = alignment
        }

        var stops: [BusStop] {
            clusters.flatMap(\.stops)
        }

        func reversed() -> Route {
            Route(
                clusters: clusters.reversed().map {
                    StopCluster($0.stops.reversed())
                },
                alignment: alignment == .fromStart ? .fromEnd : .fromStart,
            )
        }

        func copy(
            clusters: [StopCluster]? = nil,
            alignment: ClusterAlignment? = nil,
        ) -> Route {
            Route(
                clusters: clusters ?? self.clusters,
                alignment: alignment ?? self.alignment,
            )
        }
    }

    // MARK: - Constants

    private static let directionOutbound = "Segovia → Torrecaballeros"
    private static let directionInbound = "Torrecaballeros → Segovia"

    // MARK: - Stops

    private enum Stops {
        static let azoguejo = BusStopRegistry.azoguejo
        static let delicias = BusStopRegistry.delicias
        static let montecorredores = BusStopRegistry.montecorredores
        static let sanCris = BusStopRegistry.sanCristobal
        static let sanCrisIglesia = BusStopRegistry.sanCristobalIglesia
        static let sanCrisRotonda = BusStopRegistry.sanCristobalRotonda
        static let sonsoto = BusStopRegistry.sonsoto
        static let sonsoto2 = BusStopRegistry.sonsoto2
        static let trescasas = BusStopRegistry.trescasas
        static let trescasas2 = BusStopRegistry.trescasas2
        static let cabanillas = BusStopRegistry.cabanillas
        static let torrecaballeros = BusStopRegistry.torrecaballeros
        static let torrecaballeros2 = BusStopRegistry.torrecaballeros2
        static let torrecaballeros3 = BusStopRegistry.torrecaballeros3
        static let andresLaguna = BusStopRegistry.andresLaguna
        static let laPista = BusStopRegistry.laPista
        static let hermanitas = BusStopRegistry.hermanitas
        static let estacionBus = BusStopRegistry.estacionAutobuses
        static let plazaToros = BusStopRegistry.plazaDeToros
        static let palazuelos = BusStopRegistry.palazuelos
        static let palazuelosColegio = BusStopRegistry.palazuelosColegio
        static let tabanera = BusStopRegistry.tabanera
        static let tabanera2 = BusStopRegistry.tabanera2
        static let jardinillos = BusStopRegistry.jardinillos
    }

    // MARK: - Routes

    enum Routes {
        enum Weekday {
            static let regular = Route(
                clusters: [
                    StopCluster([
                        Stops.azoguejo, Stops.delicias, Stops.montecorredores,
                    ]),
                    StopCluster([
                        Stops.sanCris, Stops.sanCrisIglesia,
                        Stops.sanCrisRotonda,
                    ]),
                    StopCluster([Stops.sonsoto, Stops.sonsoto2]),
                    StopCluster([Stops.trescasas, Stops.trescasas2]),
                    StopCluster([Stops.cabanillas]),
                    StopCluster([
                        Stops.torrecaballeros, Stops.torrecaballeros2,
                        Stops.torrecaballeros3,
                    ]),
                ],
            )
            static let reversed = regular.reversed()

            static let extended = Route(
                clusters: [
                    StopCluster([
                        Stops.andresLaguna, Stops.laPista, Stops.hermanitas,
                        Stops.azoguejo, Stops.delicias, Stops.montecorredores,
                    ]),
                ] + Array(regular.clusters.dropFirst()),
            )
            static let extendedReversed = extended.reversed()

            static let circular = Route(
                clusters: [
                    StopCluster([
                        Stops.estacionBus, Stops.andresLaguna, Stops.laPista,
                        Stops.plazaToros,
                    ]),
                    StopCluster([Stops.palazuelos, Stops.palazuelosColegio]),
                    StopCluster([Stops.tabanera, Stops.tabanera2]),
                    StopCluster([Stops.sanCrisIglesia, Stops.sanCrisRotonda]),
                    StopCluster([Stops.sonsoto, Stops.sonsoto2]),
                    StopCluster([Stops.trescasas, Stops.trescasas2]),
                    StopCluster([Stops.cabanillas]),
                    StopCluster([
                        Stops.torrecaballeros, Stops.torrecaballeros2,
                        Stops.torrecaballeros3,
                    ]),
                    StopCluster([Stops.delicias, Stops.azoguejo]),
                ],
            )
        }

        enum Saturday {
            static let regular = Weekday.circular.copy(
                clusters: Array(Weekday.circular.clusters.dropLast()),
            )

            // Return leg: proper reversal of regular, but the Segovia urban terminus
            // is Plaza de Toros → La Pista → Andres Laguna → Jardinillos
            // (not Estacion Bus as in outbound). See PDF footnote:
            // "RECORRIDO URBANO: PLAZA DE TOROS-LA PISTA-ANDRES LAGUNA-JARDINILLOS"
            static let reversed: Route = {
                let base = regular.reversed()
                return Route(
                    clusters: Array(base.clusters.dropLast()) + [
                        StopCluster([Stops.plazaToros, Stops.laPista, Stops.andresLaguna, Stops.jardinillos]),
                    ],
                    alignment: base.alignment,
                )
            }()
        }

        enum Sunday {
            static let regular = Weekday.circular.copy(
                clusters: Array(Weekday.circular.clusters.dropLast()),
            )

            static let reversed = regular.reversed()
        }
    }

    // MARK: - Protocol Conformance

    func canParse(routeId: String) -> Bool {
        capabilities.supportedRoutes.contains {
            $0.caseInsensitiveCompare(routeId) == .orderedSame
        }
    }

    func parse(pdfPath _: String, routeId _: String) throws -> [BusTimetable] {
        DebugConfig.debugPrint("M6Parser: loading timetable from JSON asset")
        return try TimetableLoader().load("M6")
    }

    func getRoutesForId(_ routeId: String) -> [[BusStop]] {
        guard routeId.caseInsensitiveCompare("M6") == .orderedSame else {
            return []
        }
        return getRouteVariants(routeId, dayType: .weekday).map(\.stops)
    }

    func getRouteVariants(_ routeId: String, dayType: DayType) -> [RouteVariant] {
        guard let views = getRouteViews(routeId, dayType: dayType) else {
            return []
        }
        return views.map { view in
            RouteVariant(
                id: view.id,
                label: view.label,
                stops: view.stops.map(\.stop),
                direction: view.direction,
                departureLabel: view.departureLabel,
            )
        }
    }

    func getRouteViews(_ routeId: String, dayType: DayType) -> [RouteView]? {
        guard routeId.caseInsensitiveCompare("M6") == .orderedSame else {
            return nil
        }

        let extendedOnlyIds: Set<String> = [
            Stops.andresLaguna.id, Stops.laPista.id, Stops.hermanitas.id,
        ]

        func extendedStops(_ route: Route) -> [RouteViewStop] {
            route.stops.map { stop in
                RouteViewStop(
                    stop: stop,
                    isExtendedOnly: extendedOnlyIds.contains(stop.id),
                )
            }
        }

        func plainStops(_ route: Route) -> [RouteViewStop] {
            route.stops.map { RouteViewStop(stop: $0) }
        }

        switch dayType {
        case .weekday:
            let tabs = [
                RouteTab(label: "Regular", viewId: "weekday-unified"),
                RouteTab(label: "Circular", viewId: "weekday-circular"),
            ]
            return [
                RouteView(
                    id: "weekday-unified",
                    label: "Segovia → Torrecaballeros",
                    stops: extendedStops(Routes.Weekday.extended),
                    direction: Self.directionOutbound,
                    swapAction: SwapAction(
                        targetViewId: "weekday-unified-reversed",
                    ),
                    tabs: tabs,
                    extendedSectionLabel: "Ruta extendida",
                ),
                RouteView(
                    id: "weekday-unified-reversed",
                    label: "Torrecaballeros → Segovia",
                    stops: extendedStops(Routes.Weekday.extendedReversed),
                    direction: Self.directionInbound,
                    swapAction: SwapAction(targetViewId: "weekday-unified"),
                    tabs: tabs,
                    extendedSectionLabel: "Ruta extendida",
                ),
                RouteView(
                    id: "weekday-circular",
                    label: "Circular",
                    stops: plainStops(Routes.Weekday.circular),
                    direction: Self.directionOutbound,
                    departureLabel: "Circular",
                    tabs: tabs,
                ),
            ]

        case .saturday:
            return [
                RouteView(
                    id: "saturday-regular",
                    label: "Segovia → Torrecaballeros",
                    stops: plainStops(Routes.Saturday.regular),
                    direction: Self.directionOutbound,
                    departureLabel: "Sábado",
                ),
                RouteView(
                    id: "saturday-reversed",
                    label: "Torrecaballeros → Segovia",
                    stops: plainStops(Routes.Saturday.reversed),
                    direction: Self.directionInbound,
                    departureLabel: "Sábado",
                ),
            ]

        case .sunday:
            return [
                RouteView(
                    id: "sunday-regular",
                    label: "Segovia → Torrecaballeros",
                    stops: plainStops(Routes.Sunday.regular),
                    direction: Self.directionOutbound,
                    departureLabel: "Domingo",
                    swapAction: SwapAction(targetViewId: "sunday-reversed"),
                ),
                RouteView(
                    id: "sunday-reversed",
                    label: "Torrecaballeros → Segovia",
                    stops: plainStops(Routes.Sunday.reversed),
                    direction: Self.directionInbound,
                    departureLabel: "Domingo",
                    swapAction: SwapAction(targetViewId: "sunday-regular"),
                ),
            ]

        default:
            return getRouteViews(routeId, dayType: .weekday)
        }
    }

    func getRouteEntries(_ routeId: String, today: Date) -> [RouteSelectorEntry] {
        guard routeId.caseInsensitiveCompare("M6") == .orderedSame else {
            return []
        }
        let dow = Calendar.current.component(.weekday, from: today) // 1=Sun, 7=Sat
        let isWeekday = dow != 7 && dow != 1
        let isSaturday = dow == 7
        let isSunday = dow == 1
        let weekdayViews = getRouteViews(routeId, dayType: .weekday)!
        let saturdayViews = getRouteViews(routeId, dayType: .saturday)!
        let sundayViews = getRouteViews(routeId, dayType: .sunday)!
        return [
            RouteSelectorEntry(
                id: "entry-lv-regular",
                label: "L-V - Regular",
                views: weekdayViews,
                initialViewId: "weekday-unified",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-lv-circular",
                label: "L-V - Circular",
                views: weekdayViews,
                initialViewId: "weekday-circular",
                timetableDayType: .weekday,
                isActiveToday: isWeekday,
            ),
            RouteSelectorEntry(
                id: "entry-sabado-ida",
                label: "Sáb - Ida",
                views: saturdayViews.filter { $0.id == "saturday-regular" },
                initialViewId: "saturday-regular",
                timetableDayType: .saturday,
                isActiveToday: isSaturday,
            ),
            RouteSelectorEntry(
                id: "entry-sabado-vuelta",
                label: "Sáb - Vuelta",
                views: saturdayViews.filter { $0.id == "saturday-reversed" },
                initialViewId: "saturday-reversed",
                timetableDayType: .saturday,
                isActiveToday: isSaturday,
            ),
            RouteSelectorEntry(
                id: "entry-domingo",
                label: "Domingo",
                views: sundayViews,
                initialViewId: "sunday-regular",
                timetableDayType: .sunday,
                isActiveToday: isSunday,
            ),
        ]
    }
}
