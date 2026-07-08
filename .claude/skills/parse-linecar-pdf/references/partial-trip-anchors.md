# Partial trips and cluster-modeled stops: verify every value's index, not just the headline time

## The trap

Many routes model a single physical PDF stop as *multiple* consecutive `stopSequence`
entries — a "cluster" — to give the app finer-grained estimated times without the
PDF actually printing that many columns. You'll recognize this pattern by naming:
`trescasas-2` immediately followed by `trescasas`, or `sonsoto-2` followed by
`sonsoto`, or a run like `san-cristobal-rotonda` / `san-cristobal-iglesia` /
`san-cristobal`. The PDF prints exactly one named column for that physical place;
the JSON spreads it across 2-3 indices with small estimated offsets.

This is harmless for trips that run the *full* route — every index gets some
value, cluster estimates and all, and it's easy to eyeball that the trip "looks
right." It becomes a real trap for **partial trips**: ones that start or end
mid-route (very often exactly the seasonal `schoolOnly`/`summerOnly` trips this
skill spends the most time on, since Linecar frequently timetables a partial
evening service that only runs part of the route). A partial trip's departures
array has a run of `null`s before/after its real values. If you place that
null/value boundary one cluster too early or too late, you get a *plausible-looking*
result — real numbers, right order of magnitude, season tag present and correct —
that is nonetheless wrong, because the whole trip's values have shifted by one
physical stop.

## Why "does a season tag exist and is the headline time right" isn't enough

A real, shipped bug in `resources/timetables/m7.json` (`ext-inbound`, Sunday
`schoolOnly`/`summerOnly` trips, found and fixed after this skill's first eval
round) is the concrete case study. The trip's `stopSequence` around the affected
area: `... 4 trescasas-2, 5 trescasas, 6 sonsoto-2, 7 sonsoto, 8
san-cristobal-rotonda ...`. The JSON had:

```
[null, null, null, null, null, null, 2054, 2055, 2100, 2101, 2102, 2104, 2105, 2108, ...]
```

Six leading nulls — meaning index 5 (`trescasas`) was blank, and the first real
value (`2054`) landed on index 6 (`sonsoto-2`) instead. Superficially this looks
completely fine: there's a season tag, the departures start around a plausible
evening time, the numbers climb steadily. A check that only asks "is there a
season tag, and does the first real number look like the PDF's headline time"
would pass this — and one investigation genuinely did, concluding "times/nulls
... match the PDF's populated cells," which was wrong.

The way it was actually caught: the PDF's un-clustered anchors elsewhere in the
*same* trip were checked value-for-value against their exact matching index —
`tabanera` (index 12) = `2105`, matching the PDF's `TABANERA` column exactly;
`palazuelos` (index 13) = `2108`, matching exactly. Those exact matches are
strong, load-bearing evidence: they prove the *back half* of the trip is
correctly anchored, which means the front-half mismatch (PDF says
`TRESCASAS`=20:55, `SONSOTO`=20:58; neither value appears anywhere near the
right index) isn't noise or a PDF-reading error — it's a real one-stop shift.

## The check to actually run

For every trip — but especially partial trips, and *always* when the trip
touches a cluster-modeled stop — build a table with three columns: the PDF's
named column, its time, and the JSON's value at the index that name maps to.
Do this for **every populated (non-null) index**, not just the first and last.
`scripts/diff_variants.py` (or a quick inline `zip(stopSequence, departures)`)
gives you the JSON side; read the PDF table the same way, left to right.

If even one index in the middle doesn't line up with its named column, don't
wave it off as an estimate — cluster-estimated indices *should* sit close to
their neighbors' confirmed anchors, so a value that's clearly off (or a null
where the PDF plainly has a number) is a strong signal that the whole run of
values has shifted by one physical stop. Use any indices that *do* match
exactly (there's almost always at least one, usually near the trip's other
end) as your anchor to sanity-check where the shift starts.

This check costs a few extra minutes per trip. It's worth doing every time a
trip is partial or touches a cluster, because the failure mode it catches
produces an answer that reads as confident and correct right up until someone
checks the actual numbers.
