#!/usr/bin/env python3
"""
generate_route_polylines.py

Reads every timetable JSON under resources/timetables/, extracts each
variant's stop sequence, fetches a road-following polyline from OSRM for each
variant, writes the result to resources/route_polylines/, and syncs to both
platform asset directories.

Replaces generate_route_polylines.sh + sync_polylines.sh.

Usage:
    python3 resources/scripts/generate_route_polylines.py            # all routes
    python3 resources/scripts/generate_route_polylines.py M4        # one route only
    python3 resources/scripts/generate_route_polylines.py --bump    # also bump versions
    python3 resources/scripts/generate_route_polylines.py --sync-only  # skip OSRM, just sync

Stop coordinates come from each timetable JSON's "stops" array (the "lat" /
"lon" fields). To use a different waypoint for OSRM without changing the
displayed marker position, add "routingLat" / "routingLon" to the stop
object — those take precedence when building the OSRM request.

File naming: resources/route_polylines/{ROUTEID}-{variantId}.json
  e.g. M4-regular.json, M6-outbound.json, M7-ext-outbound.json

Version field: preserved from the existing file on re-generation.
  Pass --bump to auto-increment the minor version on every written file.
  New files start at "1.0".
"""

import json
import pathlib
import shutil
import sys
import time
import urllib.request

OSRM_BASE = "https://router.project-osrm.org/route/v1/driving"
OSRM_DELAY = 1.2  # seconds between requests — be polite to the demo server


# ---------------------------------------------------------------------------
# OSRM
# ---------------------------------------------------------------------------

def fetch_polyline(waypoints: list[tuple[float, float]]) -> list[list[float]] | None:
    """
    waypoints: list of (lon, lat) tuples — OSRM order
    Returns [[lat, lon], ...] — app order — or None on failure.
    """
    coords_str = ";".join(f"{lon},{lat}" for lon, lat in waypoints)
    url = f"{OSRM_BASE}/{coords_str}?overview=full&geometries=geojson"
    try:
        req = urllib.request.Request(
            url, headers={"User-Agent": "InterSego-polyline-script/1.0"}
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read())
        raw = data["routes"][0]["geometry"]["coordinates"]
        return [[c[1], c[0]] for c in raw]  # swap lon,lat → lat,lon
    except Exception as exc:
        print(f"    ERROR: {exc}", file=sys.stderr)
        return None


# ---------------------------------------------------------------------------
# Versioning
# ---------------------------------------------------------------------------

def read_version(path: pathlib.Path) -> str:
    if path.exists():
        try:
            return json.loads(path.read_text()).get("version", "1.0")
        except Exception:
            pass
    return "1.0"


def bump_version(v: str) -> str:
    """'1.0' → '1.1', '2.9' → '2.10'"""
    parts = v.split(".")
    try:
        return f"{parts[0]}.{int(parts[-1]) + 1}"
    except (IndexError, ValueError):
        return f"{v}.1"


# ---------------------------------------------------------------------------
# Per-route generation
# ---------------------------------------------------------------------------

def generate_route(
    route_id: str,
    timetable_path: pathlib.Path,
    output_dir: pathlib.Path,
    do_bump: bool,
    sync_only: bool,
) -> list[str]:
    """Returns list of output file stems produced (e.g. ['M4-regular', 'M4-reverse'])."""
    data = json.loads(timetable_path.read_text())

    # Build stop → (lon, lat) lookup; prefer routingLat/routingLon when present
    stop_coords: dict[str, tuple[float, float]] = {}
    for s in data.get("stops", []):
        sid = s["id"]
        lat = s.get("routingLat", s.get("lat"))
        lon = s.get("routingLon", s.get("lon"))
        if lat is not None and lon is not None:
            stop_coords[sid] = (float(lon), float(lat))

    variants = data.get("variants", [])
    if not variants:
        print("  No variants — skipping.")
        return []

    produced: list[str] = []

    for variant in variants:
        variant_id = variant["id"]
        stem = f"{route_id.upper()}-{variant_id}"
        out_path = output_dir / f"{stem}.json"
        produced.append(stem)

        if sync_only:
            continue

        sequence = variant.get("stopSequence", [])
        waypoints: list[tuple[float, float]] = []
        for sid in sequence:
            if sid in stop_coords:
                waypoints.append(stop_coords[sid])
            else:
                print(f"  WARNING: stop '{sid}' has no coordinates — omitted from {stem}")

        if len(waypoints) < 2:
            print(f"  → {stem}: only {len(waypoints)} waypoint(s), skipping")
            continue

        print(f"  → {stem} ({len(waypoints)} waypoints)...", end=" ", flush=True)
        coords = fetch_polyline(waypoints)
        if coords is None:
            print("✗ failed")
            continue

        version = read_version(out_path)
        if do_bump:
            version = bump_version(version)

        out_path.write_text(
            json.dumps({"version": version, "coordinates": coords}, indent=2) + "\n"
        )
        print(f"✓ {len(coords)} pts  v{version}")
        time.sleep(OSRM_DELAY)

    return produced


# ---------------------------------------------------------------------------
# Sync
# ---------------------------------------------------------------------------

def sync(source_dir: pathlib.Path, android_dir: pathlib.Path, ios_dir: pathlib.Path) -> None:
    android_dir.mkdir(parents=True, exist_ok=True)
    ios_dir.mkdir(parents=True, exist_ok=True)

    files = sorted(source_dir.glob("*.json"))
    for f in files:
        shutil.copy2(f, android_dir / f.name)
        shutil.copy2(f, ios_dir / f.name)

    print(f"  ✓ {len(files)} files → Android assets")
    print(f"  ✓ {len(files)} files → iOS bundle")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    raw_args = sys.argv[1:]
    flags = {a for a in raw_args if a.startswith("--")}
    positional = [a for a in raw_args if not a.startswith("--")]

    do_bump = "--bump" in flags
    sync_only = "--sync-only" in flags
    route_filter = positional[0].upper() if positional else None

    script_dir = pathlib.Path(__file__).parent.resolve()
    repo_root = script_dir.parent.parent
    timetables_dir = repo_root / "resources" / "timetables"
    output_dir = repo_root / "resources" / "route_polylines"
    android_dir = repo_root / "android" / "app" / "src" / "main" / "assets" / "route_polylines"
    ios_dir = repo_root / "iOS" / "InterSego" / "RoutePolylines"

    output_dir.mkdir(parents=True, exist_ok=True)

    timetable_files = sorted(timetables_dir.glob("*.json"))
    if not timetable_files:
        print("No timetable JSON files found under resources/timetables/.", file=sys.stderr)
        sys.exit(1)

    if sync_only:
        print("--sync-only: skipping OSRM, syncing existing files to both platforms.\n")

    all_produced_stems: set[str] = set()

    for timetable_path in timetable_files:
        route_id = timetable_path.stem.upper()
        if route_filter and route_id != route_filter:
            continue

        print(f"\n=== {route_id} ===")
        produced = generate_route(
            route_id, timetable_path, output_dir, do_bump, sync_only
        )
        all_produced_stems.update(produced)

    print("\nSyncing to platforms...")
    sync(output_dir, android_dir, ios_dir)

    # Report stale files — those in the source dir not produced by any variant
    if not route_filter and not sync_only:
        stale = [
            f for f in output_dir.glob("*.json")
            if f.stem not in all_produced_stems
        ]
        if stale:
            print("\nStale files in resources/route_polylines/ (no longer match any variant):")
            for f in sorted(stale):
                print(f"  {f.name}")
            print("Delete these manually if they are no longer needed,")
            print("then re-run with --sync-only to push the deletion to both platforms.")

    print("\nDone.")
    print("Note: if you added NEW polyline files on iOS, verify they appear in Xcode")
    print("      (RoutePolylines group — should auto-include if it's a folder reference).")


if __name__ == "__main__":
    main()
