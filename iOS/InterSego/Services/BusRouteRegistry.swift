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

enum BusRouteRegistry {

    static func knownRoutes() -> [BusRoute] {
        [
            BusRoute(id: "M1", number: "M1", name: "Línea Metropolitana 1",
                     origin: "Segovia", destination: "Área Metropolitana",
                     pdfURL: "", routeType: .urban, isCircular: true),
            BusRoute(id: "M2", number: "M2", name: "Línea Metropolitana 2",
                     origin: "Segovia", destination: "Área Metropolitana",
                     pdfURL: "", routeType: .urban),
            BusRoute(id: "M3", number: "M3", name: "Línea Metropolitana 3",
                     origin: "Segovia", destination: "Área Metropolitana",
                     pdfURL: "", routeType: .urban),
            BusRoute(id: "M4", number: "M4", name: "La Lastrilla - El Sotillo",
                     origin: "La Lastrilla", destination: "El Sotillo",
                     pdfURL: "", routeType: .urban, isCircular: true),
            BusRoute(id: "M5", number: "M5", name: "Línea Metropolitana 5",
                     origin: "Segovia", destination: "Área Metropolitana",
                     pdfURL: "", routeType: .urban),
            BusRoute(id: "M6", number: "M6", name: "Segovia - Torrecaballeros",
                     origin: "Segovia", destination: "Torrecaballeros",
                     pdfURL: "", routeType: .urban),
            BusRoute(id: "M7", number: "M7", name: "Línea Metropolitana 7",
                     origin: "Segovia", destination: "Área Metropolitana",
                     pdfURL: "", routeType: .urban),
            BusRoute(id: "M8", number: "M8", name: "Línea Metropolitana 8",
                     origin: "Segovia", destination: "Área Metropolitana",
                     pdfURL: "", routeType: .urban),
        ]
    }
}
