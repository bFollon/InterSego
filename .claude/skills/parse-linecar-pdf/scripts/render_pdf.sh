#!/usr/bin/env bash
# Downloads a Linecar timetable PDF and rasterizes page 1 to a high-res PNG
# for close reading. macOS-only (uses Quick Look's qlmanage).
#
# Usage: render_pdf.sh <pdf-url> <output-dir> [route-id]
#
# Produces <output-dir>/<route-id-or-basename>.pdf and a .pdf.png thumbnail
# next to it (qlmanage's naming convention: <input>.png).
#
# TODO (scaffold): not yet verified against every route's PDF — only
# exercised on M4 and M1 this session. If a route's PDF is multi-page,
# qlmanage's -t thumbnail mode only renders page 1; you'll need a different
# approach (e.g. Preview.app export, or check page count first — see the
# python regex trick in lessons-learned.md era commits) to get later pages.

set -euo pipefail

if [ $# -lt 2 ]; then
  echo "Usage: $0 <pdf-url> <output-dir> [route-id]" >&2
  exit 1
fi

URL="$1"
OUT_DIR="$2"
ROUTE_ID="${3:-$(basename "$URL" .pdf)}"

mkdir -p "$OUT_DIR"
PDF_PATH="$OUT_DIR/${ROUTE_ID}.pdf"

echo "Downloading $URL -> $PDF_PATH"
curl -sL "$URL" -o "$PDF_PATH" -w "HTTP %{http_code}, size %{size_download} bytes\n"

echo "Rendering page 1 at high resolution..."
qlmanage -t -s 2400 -o "$OUT_DIR" "$PDF_PATH" > /dev/null

echo "Done: ${PDF_PATH}.png"
echo "Next: crop tightly with Python PIL before reading any cells (see SKILL.md step 3)."
