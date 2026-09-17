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
        val result = JourneyPlannerService.findJourneys(listOf(routeA, routeB), emptyList(), query("x", "y"), bufferSameStopTranscribed = 3)

        result.size shouldBe 1
        result[0].transferCount shouldBe 1
        result[0].arrivalMin shouldBe 650
        (result[0].legs[1] as Leg.Ride).depMin shouldBe 630 // the too-tight 621 trip must not have been used
    }

    test("findJourneysWithTightTransfers surfaces the too-tight 621 connection in its own list") {
        val routeA = singleVariantRoute(
            "M1", listOf(stop("x"), stop("estacion-autobuses")),
            listOf("x", "estacion-autobuses"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(600), dep(620))),
        )
        val routeB = singleVariantRoute(
            "M6", listOf(stop("estacion-autobuses"), stop("y")),
            listOf("estacion-autobuses", "y"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(621), dep(640)), listOf(dep(630), dep(650))),
        )
        val result = JourneyPlannerService.findJourneysWithTightTransfers(
            listOf(routeA, routeB), emptyList(), query("x", "y"), bufferSameStopTranscribed = 3,
        )

        // Normal list is unchanged from the plain findJourneys behavior.
        result.journeys.size shouldBe 1
        result.journeys[0].arrivalMin shouldBe 650

        // The 621 connection (1min margin, below the 3min buffer) shows up as a tight option instead
        // of being silently dropped.
        result.tightTransferJourneys.size shouldBe 1
        result.tightTransferJourneys[0].arrivalMin shouldBe 640
        (result.tightTransferJourneys[0].legs[1] as Leg.Ride).depMin shouldBe 621
    }

    test("a comfortable-margin connection excluded from the normal top result must not leak into the tight-transfer section") {
        // Regression for a live-testing report: four connecting departures off the same M1 trip, all
        // with a margin well above the configured buffer (10-25min vs. a 3min buffer) - none of
        // these should ever be considered "tight". Dominance filtering correctly collapses them to
        // just the earliest-arriving one (650, since they all share the same overall departure time
        // and a later arrival on the same start is strictly worse) - the other three, though excluded
        // from the shown result, are still comfortable connections and must not show up as "tight"
        // options. (An earlier, buggy implementation diffed two independently-ranked/top-3-capped
        // result lists, which let exactly this kind of dropped-but-comfortable candidate leak in.)
        val routeA = singleVariantRoute(
            "M1", listOf(stop("x"), stop("estacion-autobuses")),
            listOf("x", "estacion-autobuses"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(600), dep(620))),
        )
        val routeB = singleVariantRoute(
            "M6", listOf(stop("estacion-autobuses"), stop("y")),
            listOf("estacion-autobuses", "y"), DayType.WEEKDAY,
            trips = listOf(
                listOf(dep(630), dep(650)),
                listOf(dep(635), dep(655)),
                listOf(dep(640), dep(660)),
                listOf(dep(645), dep(665)),
            ),
        )
        val result = JourneyPlannerService.findJourneysWithTightTransfers(
            listOf(routeA, routeB), emptyList(), query("x", "y"), bufferSameStopTranscribed = 3,
        )

        result.journeys.map { it.arrivalMin } shouldBe listOf(650)
        result.tightTransferJourneys.shouldBeEmpty()
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
        val result = JourneyPlannerService.findJourneys(listOf(routeA, routeB), transfers, query("x", "y"), bufferWalkTranscribed = 3)

        result.size shouldBe 1
        result[0].legs shouldBe listOf(
            Leg.Ride("M4", "out", "x", "azoguejo", 600, 620, isEstimated = false),
            Leg.Walk("azoguejo", "estacion-autobuses", 643, 12),
            Leg.Ride("M7", "out", "estacion-autobuses", "y", 635, 651, isEstimated = false),
        )
        result[0].transferCount shouldBe 1
    }

    test("boarding the same trip further upstream via an unnecessary walk-back detour is dropped when it ties the direct boarding on every other axis") {
        // Regression for a live-testing report: two candidates both end up on the exact same M4
        // trip and reach the destination at the exact same time (arrival, transfers, and
        // departure all tie) - one boards it sensibly at b, the other rides the first bus (M6)
        // two stops further to c, walks back to d (upstream of b on the M4 trip), and boards the
        // same M4 vehicle there instead. Same outcome, strictly more walking for no benefit.
        val routeM6 = singleVariantRoute(
            "M6", listOf(stop("a"), stop("b"), stop("c")),
            listOf("a", "b", "c"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(0), dep(8), dep(12))),
        )
        val routeM4 = singleVariantRoute(
            "M4", listOf(stop("d"), stop("b"), stop("dest")),
            listOf("d", "b", "dest"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(70), dep(73), dep(81))),
        )
        val transfers = listOf(TransferEdge("c", "d", meters = 300, walkMinutes = 4))
        val result = JourneyPlannerService.findJourneys(listOf(routeM6, routeM4), transfers, query("a", "dest"))

        result.size shouldBe 1
        result[0].legs shouldBe listOf(
            Leg.Ride("M6", "out", "a", "b", 0, 8, isEstimated = false),
            Leg.Ride("M4", "out", "b", "dest", 73, 81, isEstimated = false),
        )
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

    test("arrive-before mode picks the journey closest to the deadline, ignoring departAfterMin") {
        val route = singleVariantRoute(
            "M9", listOf(stop("a"), stop("b"), stop("c")),
            listOf("a", "b", "c"), DayType.WEEKDAY,
            trips = listOf(
                listOf(dep(600), dep(610), dep(620)),
                listOf(dep(630), dep(640), dep(650)),
                listOf(dep(700), dep(710), dep(720)),
            ),
        )
        // departAfterMin is a nonsense value (800, after every trip) to prove it's ignored in this mode
        val result = JourneyPlannerService.findJourneys(
            listOf(route), emptyList(),
            query("a", "c", departAfterMin = 800).copy(arriveBeforeMin = 655)
        )

        // Two distinct trips clear the deadline (620 and 650, on different physical buses) - both
        // are genuine options and neither dominates the other (650 departs later, 620 arrives
        // earlier), so both are returned, ranked with the closer/later one first. Only the
        // past-deadline trip (720) is excluded.
        result.size shouldBe 2
        result[0].arrivalMin shouldBe 650
        result[1].arrivalMin shouldBe 620
    }

    test("arrive-before mode prefers alighting at the destination directly over riding further and walking back, even though the latter is nominally closer to the deadline") {
        // Regression for a live-testing report: a single bus's own route already serves the
        // destination (b) two stops before c: riding to c then walking back to b arrives later
        // (960) than just getting off at b (951), but 960 is numerically CLOSER to a distant
        // deadline (1000) than 951 is - closeness alone would wrongly prefer the walk-back detour.
        val route = singleVariantRoute(
            "M4", listOf(stop("a"), stop("b"), stop("c")),
            listOf("a", "b", "c"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(940), dep(951), dep(953))),
        )
        val transfers = listOf(TransferEdge("c", "b", meters = 450, walkMinutes = 7))
        val result = JourneyPlannerService.findJourneys(
            listOf(route), transfers,
            query("a", "b").copy(arriveBeforeMin = 1000)
        )

        result.size shouldBe 1
        result[0].legs shouldBe listOf(Leg.Ride("M4", "out", "a", "b", 940, 951, isEstimated = false))
    }

    test("arrive-before mode excludes journeys that arrive after the deadline entirely, not just ranks them last") {
        val route = singleVariantRoute(
            "M9", listOf(stop("a"), stop("b")), listOf("a", "b"), DayType.WEEKDAY,
            trips = listOf(listOf(dep(600), dep(620))),
        )
        val result = JourneyPlannerService.findJourneys(
            listOf(route), emptyList(),
            query("a", "b").copy(arriveBeforeMin = 500)
        )
        result.shouldBeEmpty()
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
