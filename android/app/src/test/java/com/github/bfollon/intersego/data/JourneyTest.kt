/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class JourneyTest : FunSpec({

    fun ride(routeId: String, from: String, to: String, depMin: Int, arrMin: Int) =
        Leg.Ride(routeId, "out", from, to, depMin, arrMin, isEstimated = false)

    test("a mid-journey transfer gap at exactly the threshold does not surface a Wait step") {
        val journey = Journey(
            legs = listOf(ride("M1", "a", "b", 600, 610), ride("M2", "b", "c", 615, 625)),
            departureMin = 600, arrivalMin = 625, transferCount = 1,
        )
        journey.stepsWithWaits() shouldBe listOf(
            JourneyStep.LegStep(journey.legs[0]),
            JourneyStep.LegStep(journey.legs[1]),
        )
    }

    test("a mid-journey transfer gap just above the threshold surfaces a Wait step") {
        val journey = Journey(
            legs = listOf(ride("M1", "a", "b", 600, 610), ride("M2", "b", "c", 616, 626)),
            departureMin = 600, arrivalMin = 626, transferCount = 1,
        )
        journey.stepsWithWaits() shouldBe listOf(
            JourneyStep.LegStep(journey.legs[0]),
            JourneyStep.Wait(6),
            JourneyStep.LegStep(journey.legs[1]),
        )
    }

    test("a gap before the first leg is never surfaced as a Wait step, however large") {
        // departureMin (600) precedes the first ride's own depMin (630) - this represents when the
        // journey starts, not a transbordo, so it must not be surfaced however large the gap is.
        val journey = Journey(
            legs = listOf(ride("M1", "a", "b", 630, 640)),
            departureMin = 600, arrivalMin = 640, transferCount = 0,
        )
        journey.stepsWithWaits() shouldBe listOf(JourneyStep.LegStep(journey.legs[0]))
    }

    test("a walking transfer with a long wait after it surfaces a Wait step before the next ride") {
        val walk = Leg.Walk("azoguejo", "estacion-autobuses", meters = 643, minutes = 12)
        // clock after walk = 620 (arrival) + 12 (walk) = 632; next ride departs 645 -> gap = 13
        val journey = Journey(
            legs = listOf(ride("M4", "x", "azoguejo", 600, 620), walk, ride("M7", "estacion-autobuses", "y", 645, 660)),
            departureMin = 600, arrivalMin = 660, transferCount = 1,
        )
        journey.stepsWithWaits() shouldBe listOf(
            JourneyStep.LegStep(journey.legs[0]),
            JourneyStep.LegStep(journey.legs[1]),
            JourneyStep.Wait(13),
            JourneyStep.LegStep(journey.legs[2]),
        )
    }
})
