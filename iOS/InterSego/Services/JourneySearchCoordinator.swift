/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import Foundation

/// Loads route/transfer data and hands it to the pure `JourneyPlannerService`, resolving
/// day-type/season against the query date via the same `TimetableQuery` logic every other
/// date-aware screen uses. Mirrors Android's JourneySearchCoordinator.
enum JourneySearchCoordinator {

    static func search(
        originPhysicalStopId: String,
        destinationPhysicalStopId: String,
        date: Date,
        departAfterMin: Int,
        arriveBeforeMin: Int? = nil,
    ) async -> [Journey] {
        let loader = TimetableLoader()
        let supportedRouteIds = await RouteDataService.shared.getSupportedRoutes()
        let routes: [JourneyRouteData] = supportedRouteIds.compactMap { routeId in
            try? loader.loadJourneyRouteData(routeId)
        }
        let transfers = (try? TransfersLoader().load()) ?? []

        let dayTypes = TimetableQuery.dayTypesForDate(date)
        let calendar = Calendar.current
        let month = calendar.component(.month, from: date)
        let weekday = calendar.component(.weekday, from: date)
        let nowMin: Int? = calendar.isDateInToday(date) ? {
            let now = calendar.dateComponents([.hour, .minute], from: Date())
            return (now.hour ?? 0) * 60 + (now.minute ?? 0)
        }() : nil

        let query = JourneyQuery(
            origin: originPhysicalStopId,
            destination: destinationPhysicalStopId,
            dayTypes: dayTypes,
            month: month,
            weekday: weekday,
            departAfterMin: departAfterMin,
            arriveBeforeMin: arriveBeforeMin,
            nowMin: nowMin,
        )
        return JourneyPlannerService.findJourneys(
            routes: routes, transfers: transfers, query: query,
            maxWaitMin: TripPlannerPrefs.getMaxWaitMin(),
            bufferSameStopTranscribed: TripPlannerPrefs.getBufferSameStopTranscribed(),
            bufferSameStopEstimated: TripPlannerPrefs.getBufferSameStopEstimated(),
            bufferWalkTranscribed: TripPlannerPrefs.getBufferWalkTranscribed(),
            bufferWalkEstimated: TripPlannerPrefs.getBufferWalkEstimated()
        )
    }
}
