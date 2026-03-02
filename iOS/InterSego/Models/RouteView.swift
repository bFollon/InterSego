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

struct RouteViewStop: Hashable {
    let stop: BusStop
    let isExtendedOnly: Bool

    init(stop: BusStop, isExtendedOnly: Bool = false) {
        self.stop = stop
        self.isExtendedOnly = isExtendedOnly
    }
}

struct SwapAction: Hashable {
    let targetViewId: String
}

struct RouteTab: Hashable {
    let label: String
    let viewId: String
}

struct RouteView: Identifiable, Hashable {
    let id: String
    let label: String
    let stops: [RouteViewStop]
    let direction: String
    let departureLabel: String?
    let swapAction: SwapAction?
    let tabs: [RouteTab]?
    let extendedSectionLabel: String?

    init(id: String, label: String, stops: [RouteViewStop], direction: String,
         departureLabel: String? = nil, swapAction: SwapAction? = nil,
         tabs: [RouteTab]? = nil, extendedSectionLabel: String? = nil) {
        self.id = id
        self.label = label
        self.stops = stops
        self.direction = direction
        self.departureLabel = departureLabel
        self.swapAction = swapAction
        self.tabs = tabs
        self.extendedSectionLabel = extendedSectionLabel
    }
}
