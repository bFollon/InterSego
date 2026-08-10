#!/usr/bin/env python3
"""
generate_transfers.py

Reads every timetable JSON under resources/timetables/, collects the
distinct *physical* stops (deduplicated via "physicalStopId", falling back
to "id" when absent), and emits resources/transfers.json: walking edges
between distinct physical stops within a radius threshold.

Distance is straight-line (haversine) over the bundled lat/lon. Walk time
is distance / 4.5 km/h, rounded up to the nearest minute.

Directionality: each pair is emitted ONCE, with "from" < "to" alphabetically
by physicalStopId. Consumers MUST treat every entry as bidirectional (walking
A→B takes the same listed time as B→A) — we do not model direction-dependent
walk times (e.g. uphill vs downhill) even though real walking speed can
differ by direction. Entries computed purely from straight-line distance are
a lower bound; review WALK_MINUTES_OVERRIDES below for manually-adjusted
pairs where straight-line distance is known to understate real walking time
(e.g. crossing the Segovia old town, or a steep climb).

Usage:
    python3 resources/scripts/generate_transfers.py               # radius 700m
    python3 resources/scripts/generate_transfers.py --radius 500  # custom radius
    python3 resources/scripts/generate_transfers.py --bump        # bump version
"""

import json
import math
import pathlib
import shutil
import sys

WALK_SPEED_M_PER_MIN = 4500 / 60  # 4.5 km/h

# Manual overrides for walkMinutes, keyed by frozenset({from, to}).
# Straight-line distance underestimates real walking time for these pairs —
# see card notes / commit message for the reasoning behind each one.
WALK_MINUTES_OVERRIDES: dict[frozenset, int] = {
    # Azoguejo <-> Estación de Autobuses: 643m straight-line but a steep
    # climb in the Azoguejo->Estación direction through the old town;
    # straight-line/4.5kmh (~9 min) is optimistic for that direction.
    frozenset({"azoguejo", "estacion-autobuses"}): 12,
}


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlambda / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def read_version(path: pathlib.Path) -> str:
    if path.exists():
        try:
            return json.loads(path.read_text()).get("version", "1.0")
        except Exception:
            pass
    return "1.0"


def bump_version(v: str) -> str:
    parts = v.split(".")
    try:
        return f"{parts[0]}.{int(parts[-1]) + 1}"
    except (IndexError, ValueError):
        return f"{v}.1"


def collect_physical_stops(timetables_dir: pathlib.Path) -> dict[str, dict]:
    """physicalStopId -> {name, lat, lon}. Warns on conflicting coords for the same id."""
    stops: dict[str, dict] = {}
    for path in sorted(timetables_dir.glob("*.json")):
        data = json.loads(path.read_text())
        for s in data.get("stops", []):
            pid = s.get("physicalStopId", s["id"])
            entry = {"name": s["name"], "lat": s["lat"], "lon": s["lon"]}
            if pid in stops:
                prev = stops[pid]
                if abs(prev["lat"] - entry["lat"]) > 1e-4 or abs(prev["lon"] - entry["lon"]) > 1e-4:
                    print(
                        f"  WARNING: physicalStopId '{pid}' has conflicting coordinates "
                        f"({prev} vs {entry} in {path.name}) — keeping first seen",
                        file=sys.stderr,
                    )
            else:
                stops[pid] = entry
    return stops


def build_transfers(stops: dict[str, dict], radius_m: float) -> list[dict]:
    ids = sorted(stops.keys())
    transfers = []
    for i, a in enumerate(ids):
        for b in ids[i + 1:]:
            sa, sb = stops[a], stops[b]
            meters = haversine_meters(sa["lat"], sa["lon"], sb["lat"], sb["lon"])
            if meters <= radius_m:
                walk_minutes = WALK_MINUTES_OVERRIDES.get(
                    frozenset({a, b}), math.ceil(meters / WALK_SPEED_M_PER_MIN)
                )
                transfers.append({
                    "from": a,
                    "to": b,
                    "meters": round(meters),
                    "walkMinutes": walk_minutes,
                })
    return transfers


def sync(source: pathlib.Path, android_path: pathlib.Path, ios_path: pathlib.Path) -> None:
    android_path.parent.mkdir(parents=True, exist_ok=True)
    ios_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, android_path)
    shutil.copy2(source, ios_path)
    print(f"  ✓ synced to {android_path}")
    print(f"  ✓ synced to {ios_path}")


def main() -> None:
    args = sys.argv[1:]
    do_bump = "--bump" in args
    radius_m = 700.0
    if "--radius" in args:
        radius_m = float(args[args.index("--radius") + 1])

    script_dir = pathlib.Path(__file__).parent.resolve()
    repo_root = script_dir.parent.parent
    timetables_dir = repo_root / "resources" / "timetables"
    out_path = repo_root / "resources" / "transfers.json"
    android_path = repo_root / "android" / "app" / "src" / "main" / "assets" / "transfers.json"
    ios_path = repo_root / "iOS" / "InterSego" / "transfers.json"

    stops = collect_physical_stops(timetables_dir)
    print(f"{len(stops)} distinct physical stops across {len(list(timetables_dir.glob('*.json')))} route files")

    transfers = build_transfers(stops, radius_m)
    transfers.sort(key=lambda t: t["meters"])
    print(f"{len(transfers)} transfer edges within {radius_m:.0f}m")
    for t in transfers:
        note = " (override)" if frozenset({t["from"], t["to"]}) in WALK_MINUTES_OVERRIDES else ""
        print(f"  {t['from']:35s} <-> {t['to']:35s} {t['meters']:5d}m  {t['walkMinutes']:3d}min{note}")

    version = bump_version(read_version(out_path)) if do_bump else read_version(out_path)
    out_path.write_text(
        json.dumps({"version": version, "transfers": transfers}, indent=2) + "\n"
    )
    print(f"\nWrote {out_path} (v{version})")

    sync(out_path, android_path, ios_path)


if __name__ == "__main__":
    main()
