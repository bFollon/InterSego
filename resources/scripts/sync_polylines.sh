#!/usr/bin/env bash
# sync_polylines.sh
#
# Copies the generated route polyline JSON files from the canonical source
# (resources/route_polylines/) to both platform asset directories.
#
# Run this after generate_route_polylines.sh to keep the platforms in sync.
#
# Usage: bash resources/scripts/sync_polylines.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$SCRIPT_DIR/../route_polylines"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

ANDROID_DEST="$REPO_ROOT/android/app/src/main/assets/route_polylines"
IOS_DEST="$REPO_ROOT/iOS/InterSego/RoutePolylines"

echo "Source:  $SOURCE_DIR"
echo "Android: $ANDROID_DEST"
echo "iOS:     $IOS_DEST"
echo ""

# Ensure destination directories exist
mkdir -p "$ANDROID_DEST"
mkdir -p "$IOS_DEST"

# Copy all JSON files from source to both platforms
COUNT=$(find "$SOURCE_DIR" -name "*.json" | wc -l | tr -d ' ')

cp "$SOURCE_DIR"/*.json "$ANDROID_DEST/"
echo "✓ Copied $COUNT files to Android assets"

cp "$SOURCE_DIR"/*.json "$IOS_DEST/"
echo "✓ Copied $COUNT files to iOS bundle"

echo ""
echo "Done. Both platforms are now in sync with $SOURCE_DIR"
echo ""
echo "Next steps:"
echo "  1. On iOS: verify the copied files are included in the Xcode project"
echo "     (Resources group → RoutePolylines → check all .json files are listed)"
echo "  2. Commit all changed polyline files in resources/, android/, and iOS/"
