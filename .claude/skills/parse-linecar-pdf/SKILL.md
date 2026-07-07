---
name: parse-linecar-pdf
description: Parse or audit Linecar bus timetable PDFs (linecar.es) against this repo's JSON timetable schema (resources/timetables/{routeId}.json). Use this whenever the user wants to check a route's timetable against the official PDF, transcribe a new/changed PDF into JSON, investigate a reported wrong departure time or missing/incorrect seasonal availability, or audit one or more routes (M1-M8+) for data drift versus the live Linecar source. Also trigger on requests like "check this route against the PDF", "does this timetable match Linecar", "add the new schedule", or "why is this trip showing/not showing" even if the user doesn't say "PDF" explicitly — a wrong-departure-time report on this app is very often a PDF transcription bug, not a code bug. STATUS: scaffold / work in progress, still being fleshed out — see "Status" section below before treating this as a finished, battle-tested skill.
---

# Parsing Linecar PDFs into InterSego's timetable JSON

> **Status: scaffold.** This skill captures the hard-won knowledge from a real debugging session (fixing bugs on routes M4 and M1) but hasn't been iterated on with test cases/evals yet. Treat the workflow below as a strong starting point, not gospel — if something doesn't fit a new route's PDF layout, adapt it and consider updating this file afterward.

## Why this skill exists

InterSego's timetable data (`resources/timetables/{routeId}.json`) is hand-transcribed from PDFs that Linecar publishes at linecar.es. Transcription is error-prone in ways that are easy to miss on a casual read: dense small-font tables, footnote symbols that carry real semantic meaning (which trips run when), and — for circular routes — a PDF layout that can make one physical bus look like two separate trips. Two real, shipped bugs came from exactly these traps (see `references/lessons-learned.md`). This skill exists to make future PDF-to-JSON work (new routes, schedule updates, audits) systematic instead of ad hoc.

## When you're auditing vs. transcribing

- **Audit** (most common): a route's JSON already exists; check it against the *current* live PDF to catch drift (PDF changed, or the original transcription had errors).
- **Transcribe**: a brand-new route, or Linecar published a materially different PDF (new trips, new stops) and the JSON needs a fuller rewrite.

Both use the same reading discipline (see below); transcription just has more surface area to get right the first time.

## Workflow

1. **Get the PDF URL.** It's in the route's own JSON at `route.pdfUrl`. Don't guess a URL — read it from the file.
2. **Download and render.** PDFs from Linecar are single-page dense tables. Use `scripts/render_pdf.sh <url> <output-dir>` (downloads + rasterizes via macOS Quick Look). This gives you a full-page PNG — do **not** try to read it directly at full-page scale; the font is too small and you *will* misread footnote symbols and shading. This is exactly what happened during the M4 fix (initially misread which trips were "JULIO Y AGOSTO"-labeled).
3. **Crop and zoom before reading.** Use Python PIL (see `scripts/crop_region.py` or just inline `Image.open(...).crop((...)).resize((...))`) to zoom into specific regions — one table at a time, footnote text separately. Read the footnote legend text FIRST, in full, before reading any cells — you need to know what the symbols mean before you can transcribe them correctly.
4. **Cross-check every ambiguous cell with a second, tighter crop.** If a symbol, shading, or number is even slightly unclear at first read, don't guess — crop tighter and re-read. This was the single biggest source of error in the M4 investigation.
5. **Understand the seasonal/footnote conventions for THIS PDF.** Don't assume — every route's PDF can use different symbols for the same underlying concept, and some PDFs have no seasonal restrictions at all. Read `references/season-schema.md` for the JSON schema and the footnote patterns seen so far (M4, M1, M6/M7 style legends) before interpreting a new PDF's symbols.
6. **If the route is circular (`isCircular: true` with two direction variants, e.g. `circularA`/`circularB` linked by `swapTargetId`), check for duplicate trips before adding anything.** The PDF very often prints the same physical bus's full loop twice — once per direction table, in reverse column order. Read `references/circular-route-duplicates.md` and use `scripts/diff_variants.py` before writing a new trip into a second-direction variant.
7. **Apply the JSON edit**, then sync and finish per `references/workflow-checklist.md` (byte-identical sync to Android/iOS bundles, version bump, CLAUDE.md tracker update, commit discipline).

## Quick reference

- Season JSON field + string keys: `references/season-schema.md`
- Circular-route duplicate detection: `references/circular-route-duplicates.md`
- Sync / version / tracker / commit mechanics: `references/workflow-checklist.md`
- What went wrong before (concrete case studies): `references/lessons-learned.md`

## To finish this skill (for the next session)

This scaffold is missing the eval/iteration loop the skill-creator process normally does:
- No test cases yet (`evals/evals.json`) — good candidates: "audit M3 against its PDF" (should find nothing, exercises the negative case), "audit M1 circularB for missing trips" (should reproduce catching the missing-trip-but-not-duplicating bug), a synthetic new-route transcription.
- No description-triggering optimization pass yet.
- `scripts/render_pdf.sh` and `scripts/diff_variants.py` are drafted below but not yet battle-tested across all 8 routes — only exercised on M4 and M1 this session.
- Worth deciding whether this skill should also cover stop/polyline data drift (out of scope for now — this scaffold is season-tag and trip-completeness focused, since that's what the two real bugs were).
