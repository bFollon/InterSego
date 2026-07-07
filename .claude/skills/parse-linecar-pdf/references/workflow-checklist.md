# Applying a timetable fix: mechanics checklist

Once you know exactly what the JSON should say (season tags, missing/extra/wrong trips), applying it correctly involves more than just editing one file.

## 1. Edit the source of truth

`resources/timetables/{routeId}.json` is the human source of truth. Neither app reads it at runtime — it must be synced.

## 2. Sync to both app bundles, byte-identical

Targets:
- `android/app/src/main/assets/timetables/{routeId}.json`
- `iOS/InterSego/Timetables/{routeId}.json`

There's a `sync-timetables.sh` script at the repo root, but **it syncs every route file at once**. If `resources/timetables/` has pre-existing drift on routes you didn't mean to touch (this happened for real: M6/M7 had a stop-id rename — `torrecaballeros-3` → `aldehuela` — sitting unsynced in `resources/` from earlier work), running the blanket script will silently pull that unrelated change into your commit too.

Prefer, when only one route changed:
```bash
cp resources/timetables/{routeId}.json android/app/src/main/assets/timetables/{routeId}.json
cp resources/timetables/{routeId}.json iOS/InterSego/Timetables/{routeId}.json
diff resources/timetables/{routeId}.json android/app/src/main/assets/timetables/{routeId}.json
diff resources/timetables/{routeId}.json iOS/InterSego/Timetables/{routeId}.json
```
Then check `git status` / `git diff --stat` for the timetables directories before staging — if anything besides your route shows up, investigate it as a separate concern rather than bundling it in.

## 3. Validate the JSON

```bash
python3 -m json.tool resources/timetables/{routeId}.json > /dev/null && echo "valid"
```
Also worth a sanity dump of trip counts / departures per variant (see `scripts/diff_variants.py`) to visually confirm the diff did what you intended, especially after multi-trip edits.

## 4. Bump the version field

Top-level `"version"` field in the JSON, e.g. `"1.0"` → `"1.1"`. Bump on every data change, even a single-field fix. Note: the version scheme in this file is sometimes out of sync with what `CLAUDE.md`'s feature tracker claims (e.g. a tracker note said "v3.4" while the file itself said `"1.0"`) — don't let a stale tracker number talk you out of bumping the file's own version; just bump the file version and correct the tracker note to match afterward.

## 5. Update the CLAUDE.md feature tracker row

The root `CLAUDE.md` "Route Timetables" table has one row per route with a free-text notes field describing (among other things) which trips are seasonal and why. Future sessions — including future invocations of *this* skill — will read that note as a starting hypothesis to verify, not as unquestionable fact. A stale note actively caused confusion once already: it baked in the old, wrong M4 season tagging as if it had been verified. When you fix data, update the note to describe the *corrected* state, and it's worth adding a short "(fixed YYYY-MM-DD, verified against live PDF)" marker so a future reader knows this specific fact was actually checked, not just inherited.

## 6. Commit discipline (per this repo's own CLAUDE.md)

- No Claude co-authoring references or links in commit messages.
- Only commit after the user has had a chance to test — don't commit proactively unless told to.
- One focused commit per logical fix, with a message that explains *why* (what was wrong, what evidence confirmed it), not just what changed.
- Never push to the remote unless explicitly asked.

## 7. Don't scope-creep into other routes

If auditing multiple routes, keep each route's fix as its own commit and its own file diff. Resist the temptation to "fix while you're in there" — a broad `sync-timetables.sh` run or a batch edit across routes makes it much harder for the user to review and test each change independently before it ships.
