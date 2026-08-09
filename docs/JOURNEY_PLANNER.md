# Journey Planner — Algorithm Spec

Normative spec for Epic 2 (A → B journey planning with transfers). Both platforms implement
this document, not each other's code. If Android and iOS disagree, this doc is wrong or stale —
fix it here first, then both implementations.

Depends on Epic 0: `physicalStopId` (stop identity), `resources/transfers.json` (walking edges),
`timeIsEstimated` (soft-time flag). See root `CLAUDE.md` feature tracker for what's implemented.

---

## Key decision: compute at runtime, do not precompute journeys

Rejected precomputing route-to-route connections — see the Epic 2 epic card for the full
reasoning (scale doesn't justify it, the cross-product of day-type × season × time exceeds the
source data, and it fights the server hot-swap update pipeline). The only precomputed artifact
is the transfer graph (Epic 0's `transfers.json`), which is static geometry with no time
dimension.

---

## Data model

```
Journey {
  legs: [Leg]
  departureMin: Int      // = legs.first.depMin
  arrivalMin: Int        // = legs.last.arrMin
  transferCount: Int     // = count(legs where legs[i] is Ride) - 1, minimum 0
}

Leg = Ride { routeId, variantId, fromStop, toStop, depMin, arrMin, isEstimated: Bool }
    | Walk { fromStop, toStop, meters, minutes }
```

- `fromStop` / `toStop` are `physicalStopId` values (Epic 0), not raw JSON `id`s.
- `depMin` / `arrMin` are minutes-of-day (0–1439), same encoding as the JSON `hhmm` field
  converted: `minutesOfDay = (hhmm / 100) * 60 + (hhmm % 100)`.
- `Ride.isEstimated` is true if **either** the `fromStop` or `toStop` physical stop has
  `timeIsEstimated: true` in that route's JSON. It is a property of this specific ride, not
  copied verbatim from the stop record — a leg touching one estimated endpoint and one real
  endpoint is still estimated overall, since either time could be off.
- `Walk.minutes` comes directly from `transfers.json`'s `walkMinutes` for that pair (looked up
  in whichever direction it's stored — recall `transfers.json` entries are bidirectional, see
  Epic 0 `physicalStopId` / `transfers.json` cards).
- Mirrored on both platforms as a sum type: Kotlin `sealed class Leg` with `Ride`/`Walk` data
  classes; Swift `enum Leg` with associated `Ride`/`Walk` structs. See
  `android/.../data/Journey.kt` and `iOS/InterSego/Models/Journey.swift`.

---

## Connection extraction

A trip's `departures` array (see `docs/TIMETABLE_JSON_REFACTOR.md` for the JSON schema) becomes
zero or more `(fromStop, toStop, depMin, arrMin, routeId, variantId)` connection tuples:

1. Resolve each stop index in `stopSequence` to its `physicalStopId` (default to raw `id` when
   absent, per Epic 0).
2. Within one trip, take the **populated** (non-null) departure values in array order —
   `null` entries mark stops this specific trip skips (partial trips: M5 weekday, M7 Sat/Sun,
   M8 Saturday) and must not produce a connection touching them.
3. For each **adjacent pair** of populated departures `(depMin at position i, arrMin at
   position i+1)`, in array order:
   - If `arrMin < depMin`, **skip this pair** — do not emit a connection. See "Non-monotonic
     departures" below for why this happens and why skipping is the correct default.
   - Otherwise emit a connection `fromStop=physicalStopId[i]`, `toStop=physicalStopId[i+1]`,
     `depMin`, `arrMin`, this trip's `routeId`/`variantId`.
4. A trip with only 0 or 1 populated departures produces no connections.

Each connection's `isEstimated` (for building a `Ride` leg later) is
`timeIsEstimated(fromStop) || timeIsEstimated(toStop)` per the stop flags added in Epic 0.

### Non-monotonic departures

14 adjacent-pair cases across the current data (M2, M6, M7, M8) have `arrMin < depMin` — the
next stop's recorded time is *earlier* than the previous stop's. Two different causes produce
this, and the extraction rule (skip the pair) is deliberately the same for both:

- **Estimation noise** (13 of 14 cases, all 1–3 min reversals): both endpoints sit next to a
  cluster-estimated stop (e.g. `torrecaballeros` → `cabanillas`, `tabanera-2` → `san-cristobal`)
  where two independently-derived times happen to cross by a minute or two. Skipping the pair
  just means "no connection modeled for this specific hop on this trip" — the rest of the trip's
  connections (before and after the crossing) are unaffected and still extracted normally.
- **Trip originates mid-route** (1 of 14: `resources/timetables/m2.json`, `circularB`/weekday,
  trip 0: `hontanares(740) → valseca(725)`) — documented in `TIMETABLE_JSON_REFACTOR.md`
  ("Stop times do not need to be chronologically ordered"). This bus's revenue service actually
  starts at Valseca; `los-huertos`/`hontanares`'s printed times are unclear whether they're a
  real earlier call the bus makes or a leftover pattern-fill from the PDF transcription.
  **Open question, not resolved by this spec**: whether `los-huertos(735) → hontanares(740)`
  (which *is* monotonic and would be extracted as a normal connection) represents a real ride a
  passenger could take. Flagged here rather than guessed at — verify against the source PDF
  before the planner ships if M2's circularB start-of-service trip matters for real journeys.

---

## Day-type and seasonal filtering

Resolved against the query date using the existing `TimetableQueryUtils.dayTypesForDate`
(Android) / equivalent iOS logic — reuse it, don't reimplement:

1. `dayTypesForDate(date)` → a set of applicable `DayType`s (weekday → `{WEEKDAY}`; Saturday →
   `{SATURDAY, WEEKEND}`; Sunday or a festivo on any other weekday → `{SUNDAY, WEEKEND, HOLIDAY}`).
2. Only `timetables[]` entries whose `dayType` is in that set are eligible.
3. Within an eligible trip, a departure's effective `SeasonalAvailability` is
   `departure.season ?? trip.season ?? YEAR_ROUND`, evaluated with `runsIn(month, weekday)`
   against the query date's month and day-of-week. A connection is only valid if **both**
   endpoints' effective seasons run on the query date — a trip whose `fromStop` departure runs
   `MON_FRI_ONLY` but whose `toStop` departure (rare, but per-departure overrides exist) is
   `YEAR_ROUND` still needs both to pass for that specific hop to be usable that day.

Do this filtering once per query (date is fixed for the whole search), not per connection.

---

## The Connection Scan algorithm

Standard Connection Scan Algorithm (CSA), scoped to this network's fixed characteristics:

**Precondition (validated by the Epic 0 spike):** no O/D pair in this network needs more than
1 transfer, and every pair is reachable. This lets v1 use a **bounded** scan rather than
general multi-round CSA:

```
function findJourneys(origin, destination, queryDepartureMin, date):
  connections = extractAllConnections(date)          // per rules above, all routes/variants
                  .sorted(by: depMin)
  walkEdges = transfers.json entries, bidirectional

  # Round 0: direct connections + walk-only
  reachable[origin] = queryDepartureMin
  for each walkEdge (origin, x, minutes): reachable[x] = min(reachable[x], queryDepartureMin)
  earliestArrival = {}   // stop -> earliest arrival time reachable so far, this round
  journeys = []          // candidate (stop reached, via which connection chain)

  for each connection c in connections (in depMin order):
    if c.depMin < reachable[c.fromStop]: continue        // can't make it
    if c.arrMin < earliestArrival.get(c.toStop, +inf):
      earliestArrival[c.toStop] = c.arrMin
      record predecessor chain for c.toStop

  if destination in earliestArrival:
    journeys.add(reconstruct(destination))               // 0-transfer journeys

  # Round 1: from every stop reached in round 0 (± walking), scan again allowing one more ride
  # bounded by the transfer-buffer rules (see below) applied at the transfer point
  for each stop s reached in round 0:
    for each walkEdge(s, w, minutes): consider w as a round-1 origin at earliestArrival[s] + minutes
  repeat the connection scan from these round-1 origins, respecting each origin's
  buffer-adjusted earliest-departure-eligible time (not just earliestArrival[s])
  journeys.add(any destination arrivals found in round 1, with transferCount = 1)

  return topN(journeys, n=3, by: tie-breaking rules below)
```

This is a 2-round bounded CSA (round 0 = direct, round 1 = one transfer), not general
multi-round CSA — simpler and faster, and correct *for this network* per the spike. If a future
route addition breaks the "never more than 1 transfer" invariant, re-run the Epic 0 spike script
before assuming this bound still holds; don't silently extend to more rounds without re-checking.

**Termination:** connections are scanned once, in departure-time order; no early-exit is needed
beyond "stop scanning once past the latest useful departure time for the query" (an optimization,
not a correctness requirement — both platforms may implement it independently since it doesn't
affect output).

### Transfer buffer rules

At the transfer point (end of leg *i*, start of leg *i+1*), the connection scan must apply a
minimum buffer before considering the next connection eligible. This section is the single
source of truth for the buffer values — do not duplicate them elsewhere; if they change, change
them here.

| Case | Minimum buffer |
|---|---|
| Same physical stop, both times transcribed | 3 min |
| Same physical stop, either time estimated (`Leg.isEstimated` on either leg) | 8 min |
| Walking transfer, both times transcribed | `Walk.minutes` + 3 min |
| Walking transfer, either time estimated | `Walk.minutes` + 8 min |

A candidate connection at the transfer point is eligible only if
`nextConnection.depMin >= arrivalAtTransferStop + bufferFor(transferKind, isEstimated)`.

**Below-buffer transfers are hidden, not shown with a warning.** A warning users don't read is
worse than an option they don't see, and a below-buffer connection is exactly the kind of
suggestion that makes someone miss a bus and blame the app, not the estimate.

Validated against real weekday data before adopting (not just proposed and shipped): built the
full set of same-stop and walk-connected transfer opportunities across all 12 route files
(4,677 opportunities, arrival + next departure of a different route, ≤90 min wait). 14.7% fall
below these buffers — expected and correct, that's the buffers doing their job on tight
connections. Critically, **all 48 route pairs that have any transfer opportunity at all have at
least one that clears the buffer somewhere in the day** — hiding below-buffer transfers does not
produce a systemic dead end for any route combination in the current data. (A specific query at
a specific time can still come back empty if the only real-world option for that moment is
below-buffer — that's the buffer working as intended, not a bug.) Re-run this check if the route
network changes meaningfully.

**Surfacing why a transfer is generous**: when a shown journey's transfer buffer is driven by the
estimated-time penalty (i.e. it would have failed the transcribed-only buffer but clears the
estimated one), the results UI should say so (e.g. "margen amplio: horario aproximado") rather
than presenting an oddly generous-looking wait with no explanation. Implemented in the
results/detail-view cards, not here — this spec only requires that the buffer check preserve
*which* rule admitted the connection (transcribed vs. estimated threshold) so the UI has that
information to surface.

**Long-wait step.** The detail (leg-by-leg) view inserts an explicit "Espera X min" step before
any ride that starts more than **5 minutes** after the previous leg ends, so a long transbordo
wait reads as its own step instead of being buried in the two legs' times either side of it. This
is purely a display concern — it doesn't affect which journeys are found or how they're ranked —
but the 5-minute threshold is a single source of truth shared by both platforms:
`Journey.longWaitThresholdMin` (iOS) / `Journey.LONG_WAIT_THRESHOLD_MIN` (Android), both in
`Journey`'s file. Only mid-journey gaps count (never before the first leg — that's simply when the
journey starts, see `departureMin`'s doc comment); a gap after a walk counts the same as a gap
after a ride. See `Journey.stepsWithWaits()` / `Journey.kt`'s `stepsWithWaits()`.

---

## Defining "best" — tie-breaking

Earliest arrival alone produces bad suggestions (an earlier arrival via a stressful 4-minute
transfer beats a relaxed one it shouldn't). Ranking, in order:

1. **Primary key** — see "Arrive-before mode" below; earliest arrival (`arrivalMin`) in the
   default (depart-after) mode
2. **Fewest transfers** (`transferCount`)
3. **Latest departure** (`departureMin`, descending — less waiting at the origin)

Return the **top 3 distinct** journeys (distinct = different leg sequence, not just different
times on the same route/transfer pattern), not just the single best, so the results UI can offer
a trade-off instead of only the tightest option.

### Arrive-before mode

The query can ask for journeys that arrive by a deadline instead of departing after a given time
("¿A qué hora salgo si quiero llegar antes de las X?"). This is a second, mutually-exclusive mode
on the same query (`JourneyQuery.arriveBeforeMin` — when set, `departAfterMin` is ignored):

- The whole day is searched (round 0/1 both start from minute 0), since any bus, however early,
  is a candidate — the deadline is a filter, not a departure floor.
- Journeys arriving after the deadline are dropped entirely before ranking, not merely ranked
  last (`arrivalMin <= arriveBeforeMin`).
- **Ranking key: latest departure, then fewest transfers, then earliest arrival** — *not*
  closeness to the deadline (`arriveBeforeMin - arrivalMin`), which was tried first and produced
  a real bug: closeness alone rewards riding further than necessary on a trip that already
  passes the destination and then walking back to it, because arriving *later* (via the extra
  ride + walk) reads as "closer to the deadline" than getting off at the right stop, even though
  it's strictly worse (later arrival, extra walking, identical everything else). Latest-departure
  ranking sidesteps this: such a detour never departs later than simply alighting at the correct
  stop (both share the same boarding chain up to where they diverge), so it can only win on a
  later tie-break — and it never wins on "earliest arrival" either, since alighting correctly is
  by definition no later. See `JourneyPlannerServiceTest.kt` / `JourneyPlannerServiceTests.swift`'s
  "prefers alighting at the destination directly" test for the regression case.
- `rankAndDedupe` takes a **rank key of ordered ints, ascending = better** (callers negate
  fields where higher is naturally better, e.g. `-departureMin`) rather than a single primary
  key, so depart-after (`[arrivalMin, transferCount, -departureMin]`) and arrive-before
  (`[-departureMin, transferCount, arrivalMin]`) can use genuinely different orderings, not just
  different primary values, while still sharing one implementation. Dominance filtering compares
  the same key element-wise.

**Dominance filtering.** A different leg sequence is not automatically a genuine trade-off — a
walk to a nearby stop the direct bus already serves, or alighting one stop early, produces a
distinct pattern with no actual benefit. Before taking the top 3, drop any journey `b` for which
another candidate `a` is *never worse* and *strictly better* on at least one axis:

```
dominates(a, b) = (a.arrivalMin <= b.arrivalMin
                    && a.transferCount <= b.transferCount
                    && a.departureMin >= b.departureMin)
                  && (a.arrivalMin < b.arrivalMin
                      || a.transferCount < b.transferCount
                      || a.departureMin > b.departureMin)
```

Applied per-pattern, after collapsing each pattern to its best instance and before the final
sort/take(3) — so a dominated alternative never displaces or crowds out a real trade-off, and
never appears at all, not merely last. (Found via live testing: a real query surfaced "walk to a
different stop, then ride the same buses" alternatives that arrived no sooner and cost no fewer
transfers than the direct option — see `JourneyPlannerServiceTest.kt` /
`JourneyPlannerServiceTests.swift`'s "dominated walk-detour" test for the regression case.)

---

## What this spec does not cover

- Buffer values themselves (see the buffer-rules card).
- UI presentation of journeys / chains (results-list and detail-view cards).
- Origin/destination input (stop picker / "mi ubicación" — picker card; free-text geocoding is
  an explicit v1 non-goal, follow-up card).
- Whether a below-buffer journey is hidden or shown-with-warning (buffer-rules card decision).
