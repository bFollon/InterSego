#!/usr/bin/env bash
# Syncs timetable JSONs from resources/timetables/ to Android and iOS bundle directories.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$SCRIPT_DIR/resources/timetables"
ANDROID="$SCRIPT_DIR/android/app/src/main/assets/timetables"
IOS="$SCRIPT_DIR/iOS/InterSego/Timetables"

cp "$SRC"/*.json "$ANDROID"/
cp "$SRC"/*.json "$IOS"/

echo "Synced $(ls "$SRC"/*.json | wc -l | tr -d ' ') file(s) to Android and iOS."
