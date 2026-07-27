# Holiday Calendar Data

`resources/holidays/{year}.json` is the source of truth for the festivo calendar used to
resolve `DayType.holiday` (which maps to Sunday service — see the day-type resolution card).
The server serves these files as-is; there is no second place to edit them.

## Format

```json
{
  "version": "1.0",
  "year": 2026,
  "holidays": [
    { "date": "2026-01-01", "name": "Año Nuevo", "scope": "national" }
  ]
}
```

- `date` — ISO 8601 (`YYYY-MM-DD`), the **observed** non-working day. When a festivo falls on
  a Sunday and is legally moved to the following Monday (common for Todos los Santos and the
  Día de la Constitución), use the moved date, not the original — that's the day service is
  actually affected. Sundays don't need an entry regardless, since Sunday service already applies.
- `scope` — `national` | `regional` | `local`. All three resolve identically today (→ Sunday
  timetable); the field exists for UI labeling and in case Linecar ever treats them differently.
- Bump `version` on any edit to an existing year's file.

## Scope for Segovia

Three tiers stack to a fixed 14 festivos/year, per Estatuto de los Trabajadores:
- **National** (8, fixed by BOE resolution): Año Nuevo, Reyes, Viernes Santo, Fiesta del
  Trabajo, Asunción, Fiesta Nacional, Inmaculada, Navidad.
- **Regional** (4, chosen by each autonomous community from a menu of substitutable dates):
  Castilla y León's picks for 2026 are Jueves Santo, Día de Castilla y León (23 abril),
  Todos los Santos, and Día de la Constitución.
- **Local** (2, set per-municipality): Segovia capital's are San Pedro (29 junio) and San
  Frutos (25 octubre, patron saint of the city).

## 2026 sourcing

- National: BOE resolution [BOE-A-2025-21667](https://www.boe.es/diario_boe/txt.php?id=BOE-A-2025-21667)
  (Dirección General de Trabajo, 17 Oct 2025).
- Regional (Castilla y León): [Junta de Castilla y León announcement](https://comunicacion.jcyl.es/web/jcyl/Comunicacion/es/Plantilla100Detalle/1281372051501/AcuerdoGobierno/1285524661004/Comunicacion).
- Local (Segovia): [Iberley — Fiestas locales de la provincia de Segovia 2026](https://www.iberley.es/legislacion/fiestas-locales-provincia-segovia-ano-2026-27284527),
  cross-checked against [acueducto2.com coverage](https://www.acueducto2.com/los-lunes-29-de-junio-y-26-de-octubre-fiestas-locales-de-segovia-para-2026/177360).

Todos los Santos (1 nov, a Sunday in 2026) is observed 2 Nov; Día de la Constitución (6 dic,
also a Sunday) is observed 7 Dec; San Frutos (25 oct, also a Sunday) is observed 26 Oct —
verified with `date -j -f "%Y-%m-%d" "<date>" "+%A"`.

## Open question: does Linecar run reduced service on non-festivo dates like 24/31 Dec or 5 Jan?

No evidence either way — Linecar's site (linecar.es/metropolitano/segovia/) and the route PDFs
carry no notice about special schedules on these dates, and none of Dec 24, Dec 31, or Jan 5
are statutory festivos, so they're intentionally **not** in the calendar. If a user reports a
wrong schedule on one of these dates, that's a new finding, not a bug in this data — raise a
follow-up card rather than guessing at a special timetable now.

## Yearly update ritual

Before December each year:
1. Add `resources/holidays/{next-year}.json` following the format above, sourcing each tier
   from its official publication (BOE for national, Junta de Castilla y León for regional,
   Ayuntamiento de Segovia / BOCyL for local — search "fiestas locales Segovia {year}").
2. Bump `version` if correcting a prior year's file; new files start at `"1.0"`.
3. No app release needed — the server (`GET /api/holidays`, separate card) reads directly from
   this directory, and clients fetch it over the network with ETag/disk-cache fallback.
