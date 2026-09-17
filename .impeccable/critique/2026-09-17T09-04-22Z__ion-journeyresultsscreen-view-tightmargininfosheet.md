---
target: Tight-transfer results section additions since e60501a
total_score: 26
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/Users/brunofollon/Documents/dev/apps/InterSego/Tight-transfer results section (JourneyResultsScreen/View + TightMarginInfoSheet)"
timestamp: 2026-09-17T09-04-22Z
slug: ion-journeyresultsscreen-view-tightmargininfosheet
---
Method: dual-agent (A: design-review fork · B: technical-audit general-purpose agent)

## Design Health Score (Assessment A)
26/40 Acceptable

## Audit Health Score (Assessment B, revised)
14/20 Good

## Priority Issues (introduced this session)
- [P1] iOS silently drops tight-transfer section when normal results empty (JourneyResultsView.swift else-branch nesting)
- [P1] Per-card tight-margin banner not actually fully tappable (card layered on top absorbs touches, ~30pt/dp usable strip vs 44/48 min)
- [P1] Info sheet always cites fixed 15min threshold even when opened from section banner, whose real trigger is the user's configured buffer
- [P2] Android empty-state copy contradicts tight section shown below it
- [P2] New Compose clickables missing explicit role/onClickLabel for TalkBack
- [P2] iOS auto-sizing sheet has confirmed first-frame .medium flash before snapping to measured height

## Adjacent pre-existing bugs (not introduced this session)
- Settings TightMarginWarningPill hardcodes RouteOrange instead of warningColor() - won't adapt to dark mode
- iOS TightTransferPeekBanner.visibleHeight assumes fixed 1-line title, risks wrap clipping at large Dynamic Type
- Settings pill's own touch target under 48dp/44pt

## Persona Red Flags
Casey (mobile, distracted): under-target per-card tap zone causes mis-taps onto the card underneath.
Sam (accessibility): same touch-target issue plus missing explicit button role/label.

## What's Working
Shared deduplicated TightMarginInfoSheet across both platforms; careful corner-radius contract between peek banner and card; capped tight-result count (3).

## Questions to Consider
Should the fixed-15min "recomendado" concept be retired in favor of one number (the user's configured buffer) everywhere, since the two thresholds now render identically without ever being distinguished to the rider?
