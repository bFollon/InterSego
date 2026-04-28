/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
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

    let tabsLabel: String?
    let mergedDirectionLabel: String?

    init(id: String, label: String, stops: [RouteViewStop], direction: String,
         departureLabel: String? = nil, swapAction: SwapAction? = nil,
         tabs: [RouteTab]? = nil, tabsLabel: String? = nil,
         extendedSectionLabel: String? = nil, mergedDirectionLabel: String? = nil)
    {
        self.id = id
        self.label = label
        self.stops = stops
        self.direction = direction
        self.departureLabel = departureLabel
        self.swapAction = swapAction
        self.tabs = tabs
        self.tabsLabel = tabsLabel
        self.extendedSectionLabel = extendedSectionLabel
        self.mergedDirectionLabel = mergedDirectionLabel
    }
}
