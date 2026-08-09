/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon (@bFollon)
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

import XCTest

/// Behavioral mirror of Android's `JourneyTest.kt` — same 4 scenarios, same inputs, same
/// expected outputs. See `docs/JOURNEY_PLANNER.md`.
final class JourneyTests: XCTestCase {

    private func ride(_ routeId: String, _ from: String, _ to: String, _ depMin: Int, _ arrMin: Int) -> Leg {
        .ride(Leg.Ride(routeId: routeId, variantId: "out", fromStop: from, toStop: to, depMin: depMin, arrMin: arrMin, isEstimated: false))
    }

    func testMidJourneyTransferGapAtExactlyTheThresholdDoesNotSurfaceAWaitStep() {
        let legs = [ride("M1", "a", "b", 600, 610), ride("M2", "b", "c", 615, 625)]
        let journey = Journey(legs: legs, departureMin: 600, arrivalMin: 625, transferCount: 1)
        XCTAssertEqual(journey.stepsWithWaits(), [.leg(legs[0]), .leg(legs[1])])
    }

    func testMidJourneyTransferGapJustAboveTheThresholdSurfacesAWaitStep() {
        let legs = [ride("M1", "a", "b", 600, 610), ride("M2", "b", "c", 616, 626)]
        let journey = Journey(legs: legs, departureMin: 600, arrivalMin: 626, transferCount: 1)
        XCTAssertEqual(journey.stepsWithWaits(), [.leg(legs[0]), .wait(6), .leg(legs[1])])
    }

    func testGapBeforeTheFirstLegIsNeverSurfacedAsAWaitStepHoweverLarge() {
        // departureMin (600) precedes the first ride's own depMin (630) - this represents when the
        // journey starts, not a transbordo, so it must not be surfaced however large the gap is.
        let legs = [ride("M1", "a", "b", 630, 640)]
        let journey = Journey(legs: legs, departureMin: 600, arrivalMin: 640, transferCount: 0)
        XCTAssertEqual(journey.stepsWithWaits(), [.leg(legs[0])])
    }

    func testWalkingTransferWithALongWaitAfterItSurfacesAWaitStepBeforeTheNextRide() {
        let walk = Leg.walk(Leg.Walk(fromStop: "azoguejo", toStop: "estacion-autobuses", meters: 643, minutes: 12))
        // clock after walk = 620 (arrival) + 12 (walk) = 632; next ride departs 645 -> gap = 13
        let legs = [ride("M4", "x", "azoguejo", 600, 620), walk, ride("M7", "estacion-autobuses", "y", 645, 660)]
        let journey = Journey(legs: legs, departureMin: 600, arrivalMin: 660, transferCount: 1)
        XCTAssertEqual(journey.stepsWithWaits(), [.leg(legs[0]), .leg(legs[1]), .wait(13), .leg(legs[2])])
    }
}
