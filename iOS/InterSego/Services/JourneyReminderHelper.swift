/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Resolves the reminder identity for a planned journey.
///
/// A journey can start with a walk to a better-connected stop before boarding (see
/// `JourneyPlannerService`), so the reminder fires against the journey's own `departureMin`
/// (when to leave) rather than the first ride's own `depMin` — that keeps the walk time covered.
/// It's still identified against that first ride's route/variant so `ReminderService`'s existing
/// day-type smart-skip logic applies. Seasonal availability isn't preserved through the CSA
/// search, so it's treated as year-round here — acceptable since the journey was already
/// validated against the searched date's day type and season at search time.
enum JourneyReminderHelper {
    struct Context {
        let route: BusRoute
        let stop: BusStop
        let direction: String
        let departure: DepartureTime
        let dayType: DayType
        let journeyLabel: String
    }

    /// Nil when the journey has no bus ride at all (shouldn't happen in practice) or the first
    /// ride's route/origin stop can't be resolved from the caller's lookups.
    static func build(
        journey: Journey, date: Date, originName: String, destinationName: String,
        routesById: [String: BusRoute], stopsById: [String: BusStop]
    ) async -> Context? {
        guard let firstRide = journey.legs.lazy.compactMap({ leg -> Leg.Ride? in
            if case .ride(let ride) = leg { return ride }
            return nil
        }).first else { return nil }
        guard let route = routesById[firstRide.routeId] else { return nil }
        guard let originStopId = journey.legs.first?.fromStop, let stop = stopsById[originStopId] else { return nil }

        let dayType = TimetableQuery.primaryDayType(date)
        let direction = await RouteDataService.shared.getRouteViews(routeId: firstRide.routeId, dayType: dayType)
            .first(where: { $0.id == firstRide.variantId })?.direction ?? firstRide.routeId

        let departure = DepartureTime(
            hour: (journey.departureMin / 60) % 24,
            minute: journey.departureMin % 60,
            seasonalAvailability: .yearRound
        )

        return Context(
            route: route, stop: stop, direction: direction, departure: departure,
            dayType: dayType, journeyLabel: "\(originName) → \(destinationName)"
        )
    }

    static func matchKey(_ context: Context) -> String {
        BusReminder.matchKey(
            routeId: context.route.id, stopId: context.stop.id, direction: context.direction,
            hour: context.departure.hour, minute: context.departure.minute
        )
    }
}
