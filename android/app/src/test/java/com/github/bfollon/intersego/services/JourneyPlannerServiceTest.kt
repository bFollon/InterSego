/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follon
 *
 * Source available for transparency and audit purposes only.
 * Redistribution and commercial use are prohibited without explicit written permission.
 * Inquiries: bfollon.dev@icloud.com
 */

package com.github.bfollon.intersego.services

import com.github.bfollon.intersego.data.DayType
import com.github.bfollon.intersego.data.JourneyDeparture
import com.github.bfollon.intersego.data.JourneyRouteData
import com.github.bfollon.intersego.data.JourneyStop
import com.github.bfollon.intersego.data.JourneyTimetableSection
import com.github.bfollon.intersego.data.JourneyTrip
import com.github.bfollon.intersego.data.JourneyVariant
import com.github.bfollon.intersego.data.Leg
import com.github.bfollon.intersego.data.SeasonalAvailability
import com.github.bfollon.intersego.data.TransferEdge
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.Month
import java.util.Calendar

class JourneyPlannerServiceTest : FunSpec({

    fun stop(id: String, physicalStopId: String = id, isEstimated: Boolean = false) =
        JourneyStop(id, physicalStopId, isEstimated)

    fun dep(minutesOfDay: Int, season: SeasonalAvailability = SeasonalAvailability.YEAR_ROUND) =
        JourneyDeparture(minutesOfDay, season)

    /** One route, one variant, one day-type section, with the given trips (each a list of nullable JourneyDeparture). */
    fun singleVariantRoute(
        routeId: String,
        stops: List<JourneyStop>,
        stopSequenceIds: List<String>,
        dayType: DayType,
        trips: List<List<JourneyDeparture?>>,
        variantId: String = "out",
    ) = JourneyRouteData(
        routeId = routeId,
        stops = stops,
        variants = listOf(JourneyVariant(variantId, stopSequenceIds)),
        timetables = listOf(JourneyTimetableSection(variantId, dayType, trips.map { JourneyTrip(it) })),
    )

    fun query(origin: String, destination: String, departAfterMin: Int = 0, dayTypes: Set<DayType> = setOf(DayType.WEEKDAY)) =
        JourneyQuery(origin, destination, dayTypes, Month.MARCH, Calendar.TUESDAY, departAfterMin)

    test("direct journey on a single route") {
        val route = singleVariantRoute(
            "M9", listOf(stop("a"), stop("b"), stop("c")),
            listOf("a", "b", "c"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(600), dep(610), dep(620))),
        )
        val result = JourneyPlannerService.findJourneys(listOf(route), emptyList(), query("a", "c"))

        result.size shouldBe 1
        result[0].transferCount shouldBe 0
        result[0].departureMin shouldBe 600
        result[0].arrivalMin shouldBe 620
        result[0].legs shouldBe listOf(Leg.Ride("M9", "out", "a", "c", 600, 620, isEstimated = false))
    }

    test("one transfer at a same-physical-stop interchange (Estacion de Autobuses)") {
        val routeA = singleVariantRoute(
            "M1", listOf(stop("x"), stop("estacion-autobuses")),
            listOf("x", "estacion-autobuses"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(600), dep(620))),
        )
        // second trip departs too soon after arrival (620 + 3min buffer = 623) to be usable
        val routeB = singleVariantRoute(
            "M6", listOf(stop("estacion-autobuses"), stop("y")),
            listOf("estacion-autobuses", "y"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(621), dep(640)), listOf(dep(630), dep(650))),
        )
        val result = JourneyPlannerService.findJourneys(listOf(routeA, routeB), emptyList(), query("x", "y"))

        result.size shouldBe 1
        result[0].transferCount shouldBe 1
        result[0].arrivalMin shouldBe 650
        (result[0].legs[1] as Leg.Ride).depMin shouldBe 630 // the too-tight 621 trip must not have been used
    }

    test("walking transfer Azoguejo <-> Estacion de Autobuses") {
        val routeA = singleVariantRoute(
            "M4", listOf(stop("x"), stop("azoguejo")),
            listOf("x", "azoguejo"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(600), dep(620))),
        )
        val routeB = singleVariantRoute(
            "M7", listOf(stop("estacion-autobuses"), stop("y")),
            listOf("estacion-autobuses", "y"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(634), dep(650)), listOf(dep(635), dep(651))),
        )
        val transfers = listOf(TransferEdge("azoguejo", "estacion-autobuses", meters = 643, walkMinutes = 12))
        // buffer = walkMinutes(12) + 3 = 15; ready to board at 620 + 15 = 635 -> the 634 trip must be rejected
        val result = JourneyPlannerService.findJourneys(listOf(routeA, routeB), transfers, query("x", "y"))

        result.size shouldBe 1
        result[0].legs shouldBe listOf(
            Leg.Ride("M4", "out", "x", "azoguejo", 600, 620, isEstimated = false),
            Leg.Walk("azoguejo", "estacion-autobuses", 643, 12),
            Leg.Ride("M7", "out", "estacion-autobuses", "y", 635, 651, isEstimated = false),
        )
        result[0].transferCount shouldBe 1
    }

    test("unreachable origin/destination returns empty, not a crash") {
        val routeA = singleVariantRoute("M1", listOf(stop("a"), stop("b")), listOf("a", "b"), DayType.WEEKDAY, listOf(listOf(dep(600), dep(610))))
        val routeB = singleVariantRoute("M2", listOf(stop("c"), stop("d")), listOf("c", "d"), DayType.WEEKDAY, listOf(listOf(dep(600), dep(610))))

        JourneyPlannerService.findJourneys(listOf(routeA, routeB), emptyList(), query("a", "d")).shouldBeEmpty()
    }

    test("circular route does not let a search teleport backward within the same trip") {
        // hub -> q -> r -> s -> hub (same physicalStopId as the start, via an alias id), one loop only.
        val stops = listOf(stop("hub"), stop("q"), stop("r"), stop("s"), stop("hub-ret", physicalStopId = "hub"))
        val route = singleVariantRoute(
            "M7", stops, listOf("hub", "q", "r", "s", "hub-ret"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(700), dep(705), dep(710), dep(715), dep(720))),
        )

        // s (position 3, t=715) -> q (position 1, t=705): q comes BEFORE s in this trip's sequence.
        // A naive same-physical-stop index (ignoring position/order) could wrongly treat the loop's
        // closing "hub" as identical to its opening "hub" and fabricate a backward/instant hop; the
        // real answer is that this specific trip cannot serve it at all (only one loop in the fixture).
        JourneyPlannerService.findJourneys(listOf(route), emptyList(), query("s", "q", departAfterMin = 0)).shouldBeEmpty()

        // Sanity check the fixture is otherwise wired correctly: forward within the same trip works.
        val forward = JourneyPlannerService.findJourneys(listOf(route), emptyList(), query("q", "s", departAfterMin = 0))
        forward.size shouldBe 1
        forward[0].legs shouldBe listOf(Leg.Ride("M7", "out", "q", "s", 705, 715, isEstimated = false))
    }

    test("a date where the origin route doesn't run (Saturday-only) yields no journeys for a weekday query") {
        val route = singleVariantRoute(
            "M3", listOf(stop("a"), stop("b")), listOf("a", "b"),
            DayType.SATURDAY, trips = listOf(listOf(dep(600), dep(610))),
        )
        JourneyPlannerService.findJourneys(listOf(route), emptyList(), query("a", "b", dayTypes = setOf(DayType.WEEKDAY))).shouldBeEmpty()

        // and confirms it *does* run when queried on the right day type
        val onSaturday = JourneyPlannerService.findJourneys(listOf(route), emptyList(), query("a", "b", dayTypes = setOf(DayType.SATURDAY, DayType.WEEKEND)))
        onSaturday.size shouldBe 1
    }

    test("a dominated walk-detour alternative is dropped, not just outranked") {
        // Direct: board M9 at a, ride straight to c, arriving 620.
        val direct = singleVariantRoute(
            "M9", listOf(stop("a"), stop("b"), stop("c")),
            listOf("a", "b", "c"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(600), dep(610), dep(620))),
        )
        // Alternative: walk from a to a nearby stop d, then ride M10 to c - but it arrives later
        // (630) with no other advantage, so it must never surface in results, not merely rank below
        // the direct journey (regression for the "pointless walking detour" bug: real-world reports
        // showed alternatives that walk to a nearby stop the direct bus already serves, or get off
        // one stop early, without any time/transfer benefit).
        val detour = singleVariantRoute(
            "M10", listOf(stop("d"), stop("c")),
            listOf("d", "c"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(590), dep(630))),
        )
        val transfers = listOf(TransferEdge("a", "d", meters = 300, walkMinutes = 5))
        val result = JourneyPlannerService.findJourneys(listOf(direct, detour), transfers, query("a", "c"))

        result.size shouldBe 1
        result[0].legs shouldBe listOf(Leg.Ride("M9", "out", "a", "c", 600, 620, isEstimated = false))
    }

    test("seasonal availability is resolved against the query month, not unconditionally true") {
        val route = singleVariantRoute(
            "M4", listOf(stop("a"), stop("b")), listOf("a", "b"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(600, SeasonalAvailability.SUMMER_ONLY), dep(610, SeasonalAvailability.SUMMER_ONLY))),
        )
        // query() fixture uses Month.MARCH by default - not summer
        JourneyPlannerService.findJourneys(listOf(route), emptyList(), query("a", "b")).shouldBeEmpty()

        val augustQuery = JourneyQuery("a", "b", setOf(DayType.WEEKDAY), Month.AUGUST, Calendar.TUESDAY, 0)
        JourneyPlannerService.findJourneys(listOf(route), emptyList(), augustQuery).size shouldBe 1
    }
})
