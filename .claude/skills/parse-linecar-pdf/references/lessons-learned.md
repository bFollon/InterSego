# Case studies: two real bugs, what caused them, how they were caught

These are concrete enough to be worth a full read the first time you use this skill — both were real, shipped-then-fixed bugs, not hypotheticals.

## Case 1: M4 — inverted/incomplete season tags (July 2026)

**Symptom reported by the user**: on the M4 route in July, the app showed departure times that "shouldn't apply" — i.e. trips that should have been filtered out as school-term-only were showing up in summer.

**What actually happened**: the initial investigation (code review of the filtering logic on both Android and iOS, plus checking that the deployed server was serving the same JSON as the repo) found *nothing wrong* — the season-filtering code was correct, and the server's copy matched the repo's copy. The bug was in the data itself: `m4.json`'s `"season": "schoolOnly"` tags didn't match the live PDF at all. Comparing row by row against a freshly-downloaded PDF render showed the tagging was essentially inverted — trips that should have been `schoolOnly` had no tag, and one trip that should have been year-round was wrongly tagged `schoolOnly`.

**Root cause of the transcription error**: misreading the PDF's own legend. *"Los Horarios en JULIO Y AGOSTO circularán sólo los nombrados en lateral izquierdo"* means labeled trips run in summer too (year-round); it's easy to misread this as "these are the summer-only ones." A low-resolution first pass also visually confused zebra-striping (alternating grey/white rows, purely cosmetic) with the actual season-restriction signal (the "JULIO Y AGOSTO" text label) — the real signal is the *text*, not the shading.

**How it was actually caught**: user reported the specific symptom (wrong times showing in July) with a screenshot of the app. Verifying required downloading the live PDF, rendering it at high resolution, and doing several progressively tighter crops — the first low-res read of the whole page gave an answer that was later found to be wrong on a closer second pass of the same rows.

**Takeaway**: when a code review finds the logic and the deployed data agree with each other but the user's report says something's still wrong, the last place left to check is whether the *data itself* is simply incorrect relative to its real-world source — the code being "correct" only means it's faithfully executing on bad input.

## Case 2: M1 — missing trips, then over-corrected into duplicates (same session, later)

**Symptom**: as part of a broader audit across all routes prompted by the M4 fix, an initial pass flagged that M1's `circularB` (Garcillán → Segovia) variant seemed to be missing a trip visible in the PDF (a `16:00/16:05/16:10 → 16:20` row with no JSON counterpart).

**First fix (wrong)**: transcribed *every* row visible in the PDF's right-hand table that didn't already have a JSON entry, adding 4 new trips. Committed.

**What was actually wrong with that fix**: the user flagged it directly — *"Some trips in the B column are actually mirrors of the first column, but backwards. I fear you might have added duplicates."* Checking confirmed it: 3 of the 4 added trips were exact duplicates of existing `circularA` rows (same physical bus, PDF just re-prints the loop in reverse column order for the other direction's table). Only 1 of the 4 was genuinely new data. A 4th edit (adding a Polígono stop value to an already-existing trip) was *also* a duplicate of data already present in `circularA`.

**Root cause**: treating "this cell has a value in the PDF but not in the JSON" as sufficient evidence that data is missing, without checking whether that value was *already captured elsewhere* (in the other direction's variant, representing the same physical bus).

**How it was actually caught**: not by re-reading the PDF more carefully — by the user's domain knowledge of how the circular-route data is structured, followed by a systematic cross-check (dumping both variants as stopId→time maps and diffing) that confirmed the suspicion precisely.

**Takeaway**: for circular routes specifically, "missing from the JSON" is not the same claim as "missing from the data model." Always check the other direction's variant before concluding a trip needs to be added. See `circular-route-duplicates.md` for the concrete method.

## Case 3: M7 — partial-trip stop mis-anchored one physical stop late (found during this skill's first eval round)

**Symptom reported (synthetic eval prompt, phrased like a real user report)**: "M7 Sunday afternoon departure times look off — some trips that should only run in summer seem to be showing year-round."

**What happened**: two independent investigations ran the same task — one following this skill's workflow, one with no skill at all. Both correctly downloaded the live PDF, read the Sunday footnote legend in full, and confirmed the season tags (`schoolOnly`/`summerOnly`) matched the PDF's "PERIODO LECTIVO"/"VACACIONES ESCOLARES ESTIVALES" pattern. The skill-following investigation stopped there and concluded no bug existed, stating the times "match the PDF's populated cells." The no-skill investigation kept going: it built a full stop-by-stop table of the PDF's inbound partial trip and compared every value to its exact JSON index, and found that `resources/timetables/m7.json`'s `ext-inbound` Sunday `schoolOnly`/`summerOnly` trip had its first real value on the wrong stop — `trescasas` (the trip's real starting stop per the PDF) was null, and the value was instead sitting one stop later, on `sonsoto-2`.

**Root cause**: the JSON models `trescasas`/`sonsoto` as cluster pairs (`trescasas-2`+`trescasas`, `sonsoto-2`+`sonsoto`) to give finer-grained estimated times than the PDF's single named column per stop. A transcription error placed this partial trip's null/value boundary one cluster too late. The result read as entirely plausible — correct season tag, a headline time in the right ballpark, numbers climbing steadily — which is exactly why "does it have a season tag and does the first time look right" isn't a sufficient check.

**How it was actually caught**: not by re-reading the legend more carefully (both investigations read it identically and correctly) — by building a complete PDF-column-to-JSON-index table for the *entire* trip and noticing that two unrelated, non-clustered anchors later in the same trip (`tabanera`, `palazuelos`) matched their JSON indices exactly, which made the mismatch at `trescasas`/`sonsoto` impossible to explain away as approximation noise.

**Takeaway**: for partial trips (trips with a run of nulls at either end) and for any trip touching a cluster-modeled stop, verify every populated value against its exact named PDF column — not just whether a season tag exists and whether the trip's overall shape looks right. See `partial-trip-anchors.md` for the concrete method this skill now requires.

## Common thread across all three cases

In all three cases, the *first* instinct (trust a single read of the PDF, transcribe/verify at a surface level) produced a plausible-looking but wrong result, and the actual bug was only caught through a second, more granular pass — a closer re-read (Case 1), an explicit cross-check against related data (Case 2), or a full index-by-index verification (Case 3). Build that second pass into the workflow by default rather than treating it as optional extra diligence.
