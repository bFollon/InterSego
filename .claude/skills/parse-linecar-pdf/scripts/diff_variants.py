#!/usr/bin/env python3
"""Dump a route's timetable variants as stopId -> time maps, for spotting
duplicate trips across direction variants on circular routes.

Before adding a trip to a circular route's second-direction variant (e.g.
M1's circularB), use this to check whether that trip's stop times already
appear, in full, as a row in the other direction's variant (circularA).
The PDF often prints the same physical bus's full loop in both direction
tables, in reverse column order — that's one trip, not two. See
references/circular-route-duplicates.md for the full explanation; this
script just automates the comparison so you don't have to eyeball raw
departures arrays against a stopSequence by hand.

Usage:
    python3 diff_variants.py resources/timetables/m1.json --day-type weekday

Prints every variant's trips as {stopId: hhmm-or-null, ...} dicts, in
route order. A trip in one variant that's an exact match (on every
populated stop, including any season tag) to a trip in another variant is
almost certainly a duplicate — skip adding it.

TODO (scaffold): this only prints the data; it doesn't yet auto-flag
matches. Worth adding a real diff pass if this gets used often.
"""
import argparse
import json


def flatten(dep):
    if isinstance(dep, dict):
        val = dep.get("hhmm")
        season = dep.get("season")
        alt = dep.get("alternateId") or dep.get("alternateLocationId")
        if season:
            return f"{val}({season})"
        if alt:
            return f"{val}[{alt}]"
        return val
    return dep


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("json_path", help="Path to resources/timetables/{routeId}.json")
    parser.add_argument("--day-type", default=None, help="Filter to a single dayType (e.g. weekday, saturday). Default: all.")
    args = parser.parse_args()

    d = json.load(open(args.json_path))
    variants = {v["id"]: v["stopSequence"] for v in d["variants"]}

    for tt in d["timetables"]:
        if args.day_type and tt["dayType"] != args.day_type:
            continue
        seq = variants.get(tt["variantId"])
        if not seq:
            print(f"WARNING: variant '{tt['variantId']}' not found in variants[]; skipping")
            continue
        print(f"--- {tt['variantId']} ({tt['dayType']}) — {len(tt['trips'])} trips ---")
        for t in tt["trips"]:
            vals = [flatten(x) for x in t["departures"]]
            print(dict(zip(seq, vals)))
        print()


if __name__ == "__main__":
    main()
