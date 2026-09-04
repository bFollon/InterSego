/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import XCTest

/// Behavioral mirror of Android's `JourneyPlannerServiceTest.kt` — same 8 scenarios, same
/// inputs, same expected outputs, so the two platforms are checked against the same truths.
/// See `docs/JOURNEY_PLANNER.md`.
final class JourneyPlannerServiceTests: XCTestCase {

    private func stop(_ id: String, physicalStopId: String? = nil, isEstimated: Bool = false) -> JourneyStop {
        JourneyStop(id: id, physicalStopId: physicalStopId ?? id, isEstimated: isEstimated)
    }

    private func dep(_ minutesOfDay: Int, season: SeasonalAvailability = .yearRound) -> JourneyDeparture {
        JourneyDeparture(minutesOfDay: minutesOfDay, season: season)
    }

    private func singleVariantRoute(
        routeId: String,
        stops: [JourneyStop],
        stopSequenceIds: [String],
        dayType: DayType,
        trips: [[JourneyDeparture?]],
        variantId: String = "out"
    ) -> JourneyRouteData {
        JourneyRouteData(
            routeId: routeId,
            stops: stops,
            variants: [JourneyVariant(id: variantId, stopSequence: stopSequenceIds)],
            timetables: [JourneyTimetableSection(variantId: variantId, dayType: dayType, trips: trips.map { JourneyTrip(departures: $0) })]
        )
    }

    private func query(_ origin: String, _ destination: String, departAfterMin: Int = 0, dayTypes: Set<DayType> = [.weekday], arriveBeforeMin: Int? = nil) -> JourneyQuery {
        JourneyQuery(origin: origin, destination: destination, dayTypes: dayTypes, month: 3, weekday: 3, departAfterMin: departAfterMin, arriveBeforeMin: arriveBeforeMin)
    }

    func testDirectJourneyOnASingleRoute() {
        let route = singleVariantRoute(
            routeId: "M9", stops: [stop("a"), stop("b"), stop("c")],
            stopSequenceIds: ["a", "b", "c"], dayType: .weekday,
            trips: [[dep(600), dep(610), dep(620)]]
        )
        let result = JourneyPlannerService.findJourneys(routes: [route], transfers: [], query: query("a", "c"))

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].transferCount, 0)
        XCTAssertEqual(result[0].departureMin, 600)
        XCTAssertEqual(result[0].arrivalMin, 620)
        XCTAssertEqual(result[0].legs, [.ride(Leg.Ride(routeId: "M9", variantId: "out", fromStop: "a", toStop: "c", depMin: 600, arrMin: 620, isEstimated: false))])
    }

    func testOneTransferAtASameStopInterchange() {
        let routeA = singleVariantRoute(
            routeId: "M1", stops: [stop("x"), stop("estacion-autobuses")],
            stopSequenceIds: ["x", "estacion-autobuses"], dayType: .weekday,
            trips: [[dep(600), dep(620)]]
        )
        // second trip departs too soon after arrival (620 + 3min buffer = 623) to be usable
        let routeB = singleVariantRoute(
            routeId: "M6", stops: [stop("estacion-autobuses"), stop("y")],
            stopSequenceIds: ["estacion-autobuses", "y"], dayType: .weekday,
            trips: [[dep(621), dep(640)], [dep(630), dep(650)]]
        )
        let result = JourneyPlannerService.findJourneys(routes: [routeA, routeB], transfers: [], query: query("x", "y"), bufferSameStopTranscribed: 3)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].transferCount, 1)
        XCTAssertEqual(result[0].arrivalMin, 650)
        guard case .ride(let secondLeg) = result[0].legs[1] else { return XCTFail("expected a Ride leg") }
        XCTAssertEqual(secondLeg.depMin, 630) // the too-tight 621 trip must not have been used
    }

    func testWalkingTransferAzoguejoToEstacion() {
        let routeA = singleVariantRoute(
            routeId: "M4", stops: [stop("x"), stop("azoguejo")],
            stopSequenceIds: ["x", "azoguejo"], dayType: .weekday,
            trips: [[dep(600), dep(620)]]
        )
        let routeB = singleVariantRoute(
            routeId: "M7", stops: [stop("estacion-autobuses"), stop("y")],
            stopSequenceIds: ["estacion-autobuses", "y"], dayType: .weekday,
            trips: [[dep(634), dep(650)], [dep(635), dep(651)]]
        )
        let transfers = [TransferEdge(from: "azoguejo", to: "estacion-autobuses", meters: 643, walkMinutes: 12)]
        // buffer = walkMinutes(12) + 3 = 15; ready to board at 620 + 15 = 635 -> the 634 trip must be rejected
        let result = JourneyPlannerService.findJourneys(routes: [routeA, routeB], transfers: transfers, query: query("x", "y"), bufferWalkTranscribed: 3)

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].legs, [
            .ride(Leg.Ride(routeId: "M4", variantId: "out", fromStop: "x", toStop: "azoguejo", depMin: 600, arrMin: 620, isEstimated: false)),
            .walk(Leg.Walk(fromStop: "azoguejo", toStop: "estacion-autobuses", meters: 643, minutes: 12)),
            .ride(Leg.Ride(routeId: "M7", variantId: "out", fromStop: "estacion-autobuses", toStop: "y", depMin: 635, arrMin: 651, isEstimated: false)),
        ])
        XCTAssertEqual(result[0].transferCount, 1)
    }

    func testBoardingTheSameTripFurtherUpstreamViaAnUnnecessaryWalkBackDetourIsDroppedWhenItTiesTheDirectBoardingOnEveryOtherAxis() {
        // Regression for a live-testing report: two candidates both end up on the exact same M4
        // trip and reach the destination at the exact same time (arrival, transfers, and
        // departure all tie) - one boards it sensibly at b, the other rides the first bus (M6)
        // two stops further to c, walks back to d (upstream of b on the M4 trip), and boards the
        // same M4 vehicle there instead. Same outcome, strictly more walking for no benefit.
        let routeM6 = singleVariantRoute(
            routeId: "M6", stops: [stop("a"), stop("b"), stop("c")],
            stopSequenceIds: ["a", "b", "c"], dayType: .weekday,
            trips: [[dep(0), dep(8), dep(12)]]
        )
        let routeM4 = singleVariantRoute(
            routeId: "M4", stops: [stop("d"), stop("b"), stop("dest")],
            stopSequenceIds: ["d", "b", "dest"], dayType: .weekday,
            trips: [[dep(70), dep(73), dep(81)]]
        )
        let transfers = [TransferEdge(from: "c", to: "d", meters: 300, walkMinutes: 4)]
        let result = JourneyPlannerService.findJourneys(routes: [routeM6, routeM4], transfers: transfers, query: query("a", "dest"))

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].legs, [
            .ride(Leg.Ride(routeId: "M6", variantId: "out", fromStop: "a", toStop: "b", depMin: 0, arrMin: 8, isEstimated: false)),
            .ride(Leg.Ride(routeId: "M4", variantId: "out", fromStop: "b", toStop: "dest", depMin: 73, arrMin: 81, isEstimated: false)),
        ])
    }

    func testUnreachableOriginDestinationReturnsEmptyNotACrash() {
        let routeA = singleVariantRoute(routeId: "M1", stops: [stop("a"), stop("b")], stopSequenceIds: ["a", "b"], dayType: .weekday, trips: [[dep(600), dep(610)]])
        let routeB = singleVariantRoute(routeId: "M2", stops: [stop("c"), stop("d")], stopSequenceIds: ["c", "d"], dayType: .weekday, trips: [[dep(600), dep(610)]])

        XCTAssertEqual(JourneyPlannerService.findJourneys(routes: [routeA, routeB], transfers: [], query: query("a", "d")), [])
    }

    func testCircularRouteDoesNotTeleportBackwardWithinTheSameTrip() {
        // hub -> q -> r -> s -> hub (same physicalStopId as the start, via an alias id), one loop only.
        let stops = [stop("hub"), stop("q"), stop("r"), stop("s"), stop("hub-ret", physicalStopId: "hub")]
        let route = singleVariantRoute(
            routeId: "M7", stops: stops, stopSequenceIds: ["hub", "q", "r", "s", "hub-ret"], dayType: .weekday,
            trips: [[dep(700), dep(705), dep(710), dep(715), dep(720)]]
        )

        // s (position 3, t=715) -> q (position 1, t=705): q comes BEFORE s in this trip's sequence.
        // A naive same-physical-stop index (ignoring position/order) could wrongly treat the loop's
        // closing "hub" as identical to its opening "hub" and fabricate a backward/instant hop; the
        // real answer is that this specific trip cannot serve it at all (only one loop in the fixture).
        XCTAssertEqual(JourneyPlannerService.findJourneys(routes: [route], transfers: [], query: query("s", "q", departAfterMin: 0)), [])

        // Sanity check the fixture is otherwise wired correctly: forward within the same trip works.
        let forward = JourneyPlannerService.findJourneys(routes: [route], transfers: [], query: query("q", "s", departAfterMin: 0))
        XCTAssertEqual(forward.count, 1)
        XCTAssertEqual(forward[0].legs, [.ride(Leg.Ride(routeId: "M7", variantId: "out", fromStop: "q", toStop: "s", depMin: 705, arrMin: 715, isEstimated: false))])
    }

    func testDateWhereOriginRouteDoesNotRunYieldsNoJourneysForAWeekdayQuery() {
        let route = singleVariantRoute(
            routeId: "M3", stops: [stop("a"), stop("b")], stopSequenceIds: ["a", "b"],
            dayType: .saturday, trips: [[dep(600), dep(610)]]
        )
        XCTAssertEqual(JourneyPlannerService.findJourneys(routes: [route], transfers: [], query: query("a", "b", dayTypes: [.weekday])), [])

        // and confirms it *does* run when queried on the right day type
        let onSaturday = JourneyPlannerService.findJourneys(routes: [route], transfers: [], query: query("a", "b", dayTypes: [.saturday, .weekend]))
        XCTAssertEqual(onSaturday.count, 1)
    }

    func testDominatedWalkDetourAlternativeIsDroppedNotJustOutranked() {
        // Direct: board M9 at a, ride straight to c, arriving 620.
        let direct = singleVariantRoute(
            routeId: "M9", stops: [stop("a"), stop("b"), stop("c")],
            stopSequenceIds: ["a", "b", "c"], dayType: .weekday,
            trips: [[dep(600), dep(610), dep(620)]]
        )
        // Alternative: walk from a to a nearby stop d, then ride M10 to c - but it departs no
        // later and arrives later (630) with no other advantage, so it must never surface in
        // results, not merely rank below the direct journey (regression for the "pointless
        // walking detour" bug: real-world reports showed alternatives that walk to a nearby stop
        // the direct bus already serves, or get off one stop early, without any time/transfer
        // benefit).
        let detour = singleVariantRoute(
            routeId: "M10", stops: [stop("d"), stop("c")],
            stopSequenceIds: ["d", "c"], dayType: .weekday,
            trips: [[dep(590), dep(630)]]
        )
        let transfers = [TransferEdge(from: "a", to: "d", meters: 300, walkMinutes: 5)]
        let result = JourneyPlannerService.findJourneys(routes: [direct, detour], transfers: transfers, query: query("a", "c"))

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].legs, [.ride(Leg.Ride(routeId: "M9", variantId: "out", fromStop: "a", toStop: "c", depMin: 600, arrMin: 620, isEstimated: false))])
    }

    func testArriveBeforeModePicksTheJourneyClosestToTheDeadlineIgnoringDepartAfterMin() {
        let route = singleVariantRoute(
            routeId: "M9", stops: [stop("a"), stop("b"), stop("c")],
            stopSequenceIds: ["a", "b", "c"], dayType: .weekday,
            trips: [
                [dep(600), dep(610), dep(620)],
                [dep(630), dep(640), dep(650)],
                [dep(700), dep(710), dep(720)],
            ]
        )
        // departAfterMin is a nonsense value (800, after every trip) to prove it's ignored in this mode
        let result = JourneyPlannerService.findJourneys(
            routes: [route], transfers: [],
            query: query("a", "c", departAfterMin: 800, arriveBeforeMin: 655)
        )

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].arrivalMin, 650) // closest to 655 - not the earliest (620) or the excluded, past-deadline one (720)
    }

    func testArriveBeforeModePrefersAlightingAtTheDestinationDirectlyOverRidingFurtherAndWalkingBack() {
        // Regression for a live-testing report: a single bus's own route already serves the
        // destination (b) two stops before c: riding to c then walking back to b arrives later
        // (960) than just getting off at b (951), but 960 is numerically CLOSER to a distant
        // deadline (1000) than 951 is - closeness alone would wrongly prefer the walk-back detour.
        let route = singleVariantRoute(
            routeId: "M4", stops: [stop("a"), stop("b"), stop("c")],
            stopSequenceIds: ["a", "b", "c"], dayType: .weekday,
            trips: [[dep(940), dep(951), dep(953)]]
        )
        let transfers = [TransferEdge(from: "c", to: "b", meters: 450, walkMinutes: 7)]
        let result = JourneyPlannerService.findJourneys(
            routes: [route], transfers: transfers,
            query: query("a", "b", arriveBeforeMin: 1000)
        )

        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].legs, [.ride(Leg.Ride(routeId: "M4", variantId: "out", fromStop: "a", toStop: "b", depMin: 940, arrMin: 951, isEstimated: false))])
    }

    func testArriveBeforeModeExcludesJourneysThatArriveAfterTheDeadlineEntirelyNotJustRanksThemLast() {
        let route = singleVariantRoute(
            routeId: "M9", stops: [stop("a"), stop("b")], stopSequenceIds: ["a", "b"], dayType: .weekday,
            trips: [[dep(600), dep(620)]]
        )
        let result = JourneyPlannerService.findJourneys(
            routes: [route], transfers: [],
            query: query("a", "b", arriveBeforeMin: 500)
        )
        XCTAssertEqual(result, [])
    }

    func testSeasonalAvailabilityIsResolvedAgainstTheQueryMonthNotUnconditionallyTrue() {
        let route = singleVariantRoute(
            routeId: "M4", stops: [stop("a"), stop("b")], stopSequenceIds: ["a", "b"], dayType: .weekday,
            trips: [[dep(600, season: .summerOnly), dep(610, season: .summerOnly)]]
        )
        // query() fixture uses month=3 (March) by default - not summer
        XCTAssertEqual(JourneyPlannerService.findJourneys(routes: [route], transfers: [], query: query("a", "b")), [])

        let augustQuery = JourneyQuery(origin: "a", destination: "b", dayTypes: [.weekday], month: 8, weekday: 3, departAfterMin: 0)
        XCTAssertEqual(JourneyPlannerService.findJourneys(routes: [route], transfers: [], query: augustQuery).count, 1)
    }
}
