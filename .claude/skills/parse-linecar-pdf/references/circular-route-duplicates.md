# Circular routes: avoiding duplicate trips across direction variants

## The trap

Some routes are circular (`"isCircular": true` on `route`) and modeled with two direction variants that the app lets the user swap between — e.g. M1's `circularA` (Segovia → Garcillán) and `circularB` (Garcillán → Segovia), linked via `"swapTargetId"` in each variant's definition.

The Linecar PDF for these routes typically prints **two tables side by side**, one per direction. For a bus that does the *full loop* (out via the villages, back direct — or vice versa), the PDF often prints that same physical bus's stop times in **both** tables — once in each direction's reading order (so the right-hand table is essentially the left-hand table's row, reversed). These are the same trip. If you transcribe both tables literally, cell by cell, you will duplicate every full-loop trip into both variants.

This is exactly what happened fixing M1: three trips (12:30, 15:40, 21:20) were added to `circularB` that turned out to be exact duplicates — every populated stop value, including season tags — of existing `circularA` rows. The user caught it in review; it should have been caught before ever writing the JSON.

## The established convention in this codebase

Checking all 8 originally-present `circularB` trips against `circularA` (before any of the bug-fix edits) showed **zero overlap** — every single one was genuinely unique data not derivable from `circularA`. That's the convention to preserve: a second-direction variant's trip list should contain *only* trips that aren't already fully represented in the other variant. Don't defensively duplicate "just in case" — if you're unsure whether the app needs the redundancy for some display case, ask the user rather than guessing (this was explicitly not resolved with certainty during the M1 investigation — see `lessons-learned.md`).

## How to check before adding a trip

For any trip you're about to add to a circular route's variant, map its departures onto stop IDs via that variant's `stopSequence`, then do the same for every trip in the *other* variant, and compare.

```python
import json

def flatten(dep):
    return dep.get("hhmm") if isinstance(dep, dict) else dep

d = json.load(open("resources/timetables/m1.json"))
variants = {v["id"]: v["stopSequence"] for v in d["variants"]}

for tt in d["timetables"]:
    if tt["dayType"] != "weekday":
        continue
    seq = variants[tt["variantId"]]
    print(f"--- {tt['variantId']} ---")
    for t in tt["trips"]:
        vals = [flatten(x) for x in t["departures"]]
        print(dict(zip(seq, vals)))
```

(`scripts/diff_variants.py` wraps this into a reusable form — pass a route ID and day type, get both variants dumped as stopId→time dicts, ready to eyeball for overlaps.)

A trip counts as a duplicate if **every populated stop** (non-null value) in the candidate trip matches the same stop's value in some trip from the other variant — including any `season` tag. Partial overlap (e.g. the trip shares one stop's clock time with another variant's trip by coincidence, but the rest of the stops differ) is *not* a duplicate — that happened during the M1 check too (a `15:15` end time in one row coincidentally matched a different trip's *start* time in the other variant; the rest of the values didn't match, so it wasn't a dupe). Check the whole trip, not just one cell.

## Open question, not yet resolved

It's not fully settled *why* the app wants each direction-variant to be self-contained rather than always deriving a stop's departures by scanning both variants (a rider standing at "Garcillán" wanting the next bus to Segovia arguably needs to see full-loop buses regardless of which variant's row happens to record them). The empirical convention (zero overlap) was verified from the data as it exists, not derived from reading the app's stop-lookup code end to end. If you're doing a deeper transcription pass, it's worth actually tracing `ClosestStopFinderService` / `DeparturesService` (Android) or the iOS equivalent to see whether cross-variant merging ever happens for a given stop ID — that would settle the question definitively instead of relying on "the existing data happens to look this way."
