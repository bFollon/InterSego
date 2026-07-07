# Seasonal availability: JSON schema and PDF footnote conventions

## JSON schema

A trip object in `resources/timetables/{routeId}.json` (inside `timetables[].trips[]`) carries an optional `"season"` string field. If absent, the trip is treated as always-running.

Source of truth for the mapping: `android/app/src/main/java/com/github/bfollon/intersego/services/TimetableLoader.kt`, function `parseSeason` (iOS `TimetableLoader.swift` has the equivalent). Re-check that function if this table ever looks stale — don't trust this file blindly, it's a snapshot.

| JSON `"season"` value | Enum | Meaning |
|---|---|---|
| absent, or `"yearRound"` | YEAR_ROUND | Always runs |
| `"schoolOnly"` | SCHOOL_ONLY | Runs only **outside** July/August — does NOT run in summer |
| `"summerOnly"` | SUMMER_ONLY | Runs **only** in July/August |
| `"juneToSept"` | JUNE_TO_SEPT_ONLY | Runs only 13 June – 13 September |
| `"monFriOnly"` | MON_FRI_ONLY | Runs only Monday + Friday |
| `"friOnly"` | FRI_ONLY | Runs only Friday |

A separate, unrelated field exists for alternate boarding locations: `"alternateId"` (or `"alternateLocationId"` depending on context) on a departure. This is a **stop-location** detail, not a season restriction — don't confuse the two even when the PDF marks both with similar-looking symbols (see the M1 case below).

## PDF footnote conventions seen so far

Every PDF can use different symbols for the same underlying concept, and you must read each PDF's own legend text rather than assume a pattern from a different route carries over. That said, here's what's been observed, to speed up recognition:

### Pattern A — "JULIO Y AGOSTO" labeled rows (seen on M4)

Legend text: *"Los Horarios en JULIO Y AGOSTO circularán sólo los nombrados en lateral izquierdo."*

Trips with "JULIO Y AGOSTO" printed in the left margin run **year-round** (including summer) → `yearRound` (no tag needed). Trips with **no label** run only during the school term → `schoolOnly`.

This is easy to get backwards — "JULIO Y AGOSTO" sounds like it should mean "summer only," but it actually means "this trip is one of the ones that keeps running in July/August" (i.e. it's the unrestricted one). The unlabeled rows are the restricted ones. This exact inversion was the root cause of the M4 bug.

### Pattern B — bare asterisk with a date range (seen on M1)

Legend text: *"\*DEL 13 DE JUNIO AL 13 DE SEPTIEMBRE."*

A bare `*` (not in parentheses) on a cell → `juneToSept`.

**Watch out**: the same M1 PDF also has a *different*, parenthetical `(*)` symbol with its own legend line — *"(*) se coge en la gasolinera"* (picked up at the gas station). That's an alternate-boarding-location detail (`alternateId`), completely unrelated to season, despite looking almost identical to the bare `*`. Read both legend lines carefully and check whether the mark on a given cell has parentheses or not.

M1 also has day-of-week footnote marks on the same PDF: a shaded cell reading `L Y V` → `monFriOnly`, and `#` with a footnote like *"19:30 A GARCILLAN SOLO LOS VIERNES"* → `friOnly`.

### Pattern C — "PERIODO LECTIVO" / "VACACIONES ESCOLARES" (seen on M6/M7 Sunday tables)

Legend text along the lines of *"...DURANTE PERIODO LECTIVO"* / *"...DURANTE VACACIONES ESCOLARES (ESTIVALES)"* attached via a `***` mark.

- "periodo lectivo" (school term) → `schoolOnly`
- "vacaciones escolares" / "estivales" (summer break) → `summerOnly`

### Pattern D — no seasonal restriction at all (seen on M2, M3, M5, M8)

Some PDFs have zero seasonal footnotes — every trip just runs every day the table covers. This is a completely valid finding; don't force a season tag onto data that doesn't need one. Confirm by reading the *entire* page including all footnote text at the bottom before concluding "no restrictions" — a restriction you didn't notice is a much easier mistake to make than the reverse.

## Reading checklist before tagging anything

1. Read every line of footnote/legend text at the bottom of the page, in full, before touching any cell.
2. List every distinct symbol used (asterisks with/without parens, letters, daggers, colored/shaded cells) and match each to its legend line.
3. For each symbol, decide: season restriction, or something else (alternate stop, partial-route truncation, approximate-time marker)? Only season restrictions get a `"season"` field.
4. Only after you know what every symbol means, go cell by cell and transcribe.
