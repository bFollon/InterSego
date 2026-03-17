#!/usr/bin/env bash
# generate_route_polylines.sh
#
# Fetches road-following polylines from OSRM for each known route view and
# writes them as JSON arrays of [lat, lon] pairs into resources/route_polylines/.
#
# Run from anywhere — paths are resolved relative to this script's location.
#
# Prerequisites: curl, jq
# Usage: bash resources/scripts/generate_route_polylines.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../route_polylines"
OSRM_BASE="https://router.project-osrm.org/route/v1/driving"

mkdir -p "$OUTPUT_DIR"

# ---------------------------------------------------------------------------
# fetch_polyline <waypoints_string>
#   waypoints_string: semicolon-separated "lon,lat" pairs (OSRM order)
#   Outputs a JSON array of [lat, lon] pairs (app order), or exits non-zero.
# ---------------------------------------------------------------------------
fetch_polyline() {
    local waypoints="$1"
    local url="$OSRM_BASE/$waypoints?overview=full&geometries=geojson"
    local response

    response=$(curl -sf -m 30 "$url") || {
        echo "  ERROR: curl failed for $url" >&2
        return 1
    }

    # OSRM returns [lon, lat]; swap to [lat, lon] to match app convention
    echo "$response" | jq '.routes[0].geometry.coordinates | map([.[1], .[0]])'
}

# ---------------------------------------------------------------------------
# save_polyline <view_file_stem> <waypoints_string>
#   view_file_stem: e.g. "M4-regular"  (produces M4-regular.json)
# ---------------------------------------------------------------------------
save_polyline() {
    local stem="$1"
    local waypoints="$2"
    local out="$OUTPUT_DIR/$stem.json"

    echo "→ $stem"
    local coords
    if coords=$(fetch_polyline "$waypoints"); then
        echo "$coords" > "$out"
        local count
        count=$(echo "$coords" | jq 'length')
        echo "  ✓ $count points saved to $(basename "$out")"
    else
        echo "  ✗ Skipped (request failed)" >&2
    fi

    # Be polite to the public OSRM demo server
    sleep 1
}

# ---------------------------------------------------------------------------
# Stop coordinates — OSRM format: "lon,lat"
#
# Each stop has two variables:
#   STOP_NAME          physical stop location (shown as a map marker)
#   STOP_NAME_ROUTING  waypoint sent to OSRM (defaults to the physical location)
#
# Override _ROUTING when the bus travels through a different point than the
# physical stop — for example when the stop is just past a turn the bus
# doesn't make, or the bus stops slightly before the junction to let passengers
# walk. Set the matching BusStop.routingCoordinates field in the parser too.
# ---------------------------------------------------------------------------

# --- M4 ---
M4_AZOGUEJO="-4.116411,40.948406"
M4_DELICIAS="-4.108889,40.954500"
M4_GASOLINERA="-4.106072,40.965944"
M4_PENSION="-4.107552,40.969289"
M4_POLIGONO="-4.108356,40.972010"
M4_CTRA_VALLADOLID="-4.104010,40.970464"
M4_LEOPOLDO_MORENO="-4.102850,40.967679"
M4_COLEGIO="-4.102033,40.966693"
M4_HOTEL_AV_SOTILLO="-4.097825,40.965769"
M4_MASPALOMAS="-4.094892,40.965714"
M4_CENTRO_BOAL="-4.091377,40.967592"
M4_PASEO_CABANILLAS="-4.092689,40.962806"
M4_PARROQ_SOTILLO="-4.095073,40.963449"
M4_RAFAEL_HERAS="-4.096711,40.961939"
M4_VENTA_MAGULLO="-4.100906,40.960876"

# Routing overrides for M4 (set to physical coord unless a routingCoordinates is defined in the parser)
M4_AZOGUEJO_ROUTING="$M4_AZOGUEJO"
M4_DELICIAS_ROUTING="$M4_DELICIAS"
M4_GASOLINERA_ROUTING="-4.106276,40.965897"
M4_PENSION_ROUTING="$M4_PENSION"
M4_POLIGONO_ROUTING="$M4_POLIGONO"
M4_CTRA_VALLADOLID_ROUTING="$M4_CTRA_VALLADOLID"
M4_LEOPOLDO_MORENO_ROUTING="$M4_LEOPOLDO_MORENO"
M4_COLEGIO_ROUTING="$M4_COLEGIO"
M4_HOTEL_AV_SOTILLO_ROUTING="$M4_HOTEL_AV_SOTILLO"
M4_MASPALOMAS_ROUTING="$M4_MASPALOMAS"
M4_CENTRO_BOAL_ROUTING="$M4_CENTRO_BOAL"       # override if routingCoordinates is set in M4Parser
M4_PASEO_CABANILLAS_ROUTING="$M4_PASEO_CABANILLAS"
M4_PARROQ_SOTILLO_ROUTING="$M4_PARROQ_SOTILLO"
M4_RAFAEL_HERAS_ROUTING="$M4_RAFAEL_HERAS"
M4_VENTA_MAGULLO_ROUTING="$M4_VENTA_MAGULLO"

# --- M6 ---
M6_AZOGUEJO="-4.115979,40.948502"
M6_DELICIAS="-4.108889,40.954500"
M6_MONTECORREDORES="-4.097278,40.952000"
M6_SANCRIS="-4.081139,40.952056"
M6_SANCRIS_IGLESIA="-4.077499,40.951733"
M6_SANCRIS_ROTONDA="-4.073449,40.951224"
M6_SONSOTO="-4.040524,40.954774"
M6_SONSOTO_2="-4.039154,40.957470"
M6_TRESCASAS="-4.037367,40.961834"
M6_TRESCASAS_2="-4.034776,40.963899"
M6_CABANILLAS="-4.028241,40.974402"
M6_TORRECABALLEROS="-4.022848,40.991880"
M6_TORRECABALLEROS_2="-4.021688,40.995364"
M6_TORRECABALLEROS_3="-4.020855,40.999144"
M6_ANDRES_LAGUNA="-4.115582,40.939106"
M6_LA_PISTA="-4.111411,40.937354"
M6_HERMANITAS="-4.110012,40.944234"
M6_ESTACION_BUS="-4.121823,40.944768"
M6_PLAZA_TOROS="-4.107603,40.942093"
M6_PALAZUELOS="-4.064340,40.931068"
M6_PALAZUELOS_COLEGIO="-4.063495,40.933921"
M6_TABANERA="-4.067014,40.934336"
M6_TABANERA_2="-4.065818,40.937491"
M6_JARDINILLOS="-4.120831,40.944361"

# Routing overrides for M6 (same pattern — override if routingCoordinates is set in M6Parser)
M6_AZOGUEJO_ROUTING="$M6_AZOGUEJO"
M6_DELICIAS_ROUTING="$M6_DELICIAS"
M6_MONTECORREDORES_ROUTING="$M6_MONTECORREDORES"
M6_SANCRIS_ROUTING="$M6_SANCRIS"
M6_SANCRIS_IGLESIA_ROUTING="$M6_SANCRIS_IGLESIA"
M6_SANCRIS_ROTONDA_ROUTING="$M6_SANCRIS_ROTONDA"
M6_SONSOTO_ROUTING="$M6_SONSOTO"
M6_SONSOTO_2_ROUTING="$M6_SONSOTO_2"
M6_TRESCASAS_ROUTING="$M6_TRESCASAS"
M6_TRESCASAS_2_ROUTING="$M6_TRESCASAS_2"
M6_CABANILLAS_ROUTING="$M6_CABANILLAS"
M6_TORRECABALLEROS_ROUTING="$M6_TORRECABALLEROS"
M6_TORRECABALLEROS_2_ROUTING="$M6_TORRECABALLEROS_2"
M6_TORRECABALLEROS_3_ROUTING="$M6_TORRECABALLEROS_3"
M6_ANDRES_LAGUNA_ROUTING="$M6_ANDRES_LAGUNA"
M6_LA_PISTA_ROUTING="$M6_LA_PISTA"
M6_HERMANITAS_ROUTING="$M6_HERMANITAS"
M6_ESTACION_BUS_ROUTING="$M6_ESTACION_BUS"
M6_PLAZA_TOROS_ROUTING="$M6_PLAZA_TOROS"
M6_PALAZUELOS_ROUTING="$M6_PALAZUELOS"
M6_PALAZUELOS_COLEGIO_ROUTING="$M6_PALAZUELOS_COLEGIO"
M6_TABANERA_ROUTING="$M6_TABANERA"
M6_TABANERA_2_ROUTING="$M6_TABANERA_2"
M6_JARDINILLOS_ROUTING="$M6_JARDINILLOS"

# ---------------------------------------------------------------------------
# M4 — view IDs: "regular", "reverse"
# ---------------------------------------------------------------------------
echo ""
echo "=== M4 ==="

save_polyline "M4-regular" \
    "$M4_AZOGUEJO_ROUTING;$M4_DELICIAS_ROUTING;$M4_GASOLINERA_ROUTING;$M4_PENSION_ROUTING;$M4_POLIGONO_ROUTING;\
$M4_CTRA_VALLADOLID_ROUTING;$M4_LEOPOLDO_MORENO_ROUTING;$M4_COLEGIO_ROUTING;\
$M4_HOTEL_AV_SOTILLO_ROUTING;$M4_MASPALOMAS_ROUTING;$M4_CENTRO_BOAL_ROUTING;\
$M4_PASEO_CABANILLAS_ROUTING;$M4_PARROQ_SOTILLO_ROUTING;$M4_RAFAEL_HERAS_ROUTING;$M4_VENTA_MAGULLO_ROUTING"

save_polyline "M4-reverse" \
    "$M4_AZOGUEJO_ROUTING;$M4_DELICIAS_ROUTING;\
$M4_HOTEL_AV_SOTILLO_ROUTING;$M4_MASPALOMAS_ROUTING;$M4_CENTRO_BOAL_ROUTING;\
$M4_PASEO_CABANILLAS_ROUTING;$M4_PARROQ_SOTILLO_ROUTING;$M4_RAFAEL_HERAS_ROUTING;$M4_VENTA_MAGULLO_ROUTING;\
$M4_GASOLINERA_ROUTING;$M4_PENSION_ROUTING;$M4_POLIGONO_ROUTING;\
$M4_CTRA_VALLADOLID_ROUTING;$M4_LEOPOLDO_MORENO_ROUTING;$M4_COLEGIO_ROUTING;$M4_PARROQ_SOTILLO_ROUTING"

# ---------------------------------------------------------------------------
# M6 — view IDs match what getRouteViews() returns
# ---------------------------------------------------------------------------
echo ""
echo "=== M6 ==="

# weekday-unified: extended route forward
# (Andres Laguna → La Pista → Hermanitas → Azoguejo → … → Torrecaballeros 3)
save_polyline "M6-weekday-unified" \
    "$M6_ANDRES_LAGUNA_ROUTING;$M6_LA_PISTA_ROUTING;$M6_HERMANITAS_ROUTING;\
$M6_AZOGUEJO_ROUTING;$M6_DELICIAS_ROUTING;$M6_MONTECORREDORES_ROUTING;\
$M6_SANCRIS_ROUTING;$M6_SANCRIS_IGLESIA_ROUTING;$M6_SANCRIS_ROTONDA_ROUTING;\
$M6_SONSOTO_ROUTING;$M6_SONSOTO_2_ROUTING;\
$M6_TRESCASAS_ROUTING;$M6_TRESCASAS_2_ROUTING;\
$M6_CABANILLAS_ROUTING;\
$M6_TORRECABALLEROS_ROUTING;$M6_TORRECABALLEROS_2_ROUTING;$M6_TORRECABALLEROS_3_ROUTING"

# weekday-unified-reversed: extended route reversed
save_polyline "M6-weekday-unified-reversed" \
    "$M6_TORRECABALLEROS_3_ROUTING;$M6_TORRECABALLEROS_2_ROUTING;$M6_TORRECABALLEROS_ROUTING;\
$M6_CABANILLAS_ROUTING;\
$M6_TRESCASAS_2_ROUTING;$M6_TRESCASAS_ROUTING;\
$M6_SONSOTO_2_ROUTING;$M6_SONSOTO_ROUTING;\
$M6_SANCRIS_ROTONDA_ROUTING;$M6_SANCRIS_IGLESIA_ROUTING;$M6_SANCRIS_ROUTING;\
$M6_MONTECORREDORES_ROUTING;$M6_DELICIAS_ROUTING;$M6_AZOGUEJO_ROUTING;\
$M6_HERMANITAS_ROUTING;$M6_LA_PISTA_ROUTING;$M6_ANDRES_LAGUNA_ROUTING"

# weekday-circular: circular weekday route
# (Estacion Bus → Andres Laguna → … → Torrecaballeros 3 → Delicias → Azoguejo)
save_polyline "M6-weekday-circular" \
    "$M6_ESTACION_BUS_ROUTING;$M6_ANDRES_LAGUNA_ROUTING;$M6_LA_PISTA_ROUTING;$M6_PLAZA_TOROS_ROUTING;\
$M6_PALAZUELOS_ROUTING;$M6_PALAZUELOS_COLEGIO_ROUTING;\
$M6_TABANERA_ROUTING;$M6_TABANERA_2_ROUTING;\
$M6_SANCRIS_IGLESIA_ROUTING;$M6_SANCRIS_ROTONDA_ROUTING;\
$M6_SONSOTO_ROUTING;$M6_SONSOTO_2_ROUTING;\
$M6_TRESCASAS_ROUTING;$M6_TRESCASAS_2_ROUTING;\
$M6_CABANILLAS_ROUTING;\
$M6_TORRECABALLEROS_ROUTING;$M6_TORRECABALLEROS_2_ROUTING;$M6_TORRECABALLEROS_3_ROUTING;\
$M6_DELICIAS_ROUTING;$M6_AZOGUEJO_ROUTING"

# saturday-regular: circular route without the return to Azoguejo
# (Estacion Bus → … → Torrecaballeros 3)
save_polyline "M6-saturday-regular" \
    "$M6_ESTACION_BUS_ROUTING;$M6_ANDRES_LAGUNA_ROUTING;$M6_LA_PISTA_ROUTING;$M6_PLAZA_TOROS_ROUTING;\
$M6_PALAZUELOS_ROUTING;$M6_PALAZUELOS_COLEGIO_ROUTING;\
$M6_TABANERA_ROUTING;$M6_TABANERA_2_ROUTING;\
$M6_SANCRIS_IGLESIA_ROUTING;$M6_SANCRIS_ROTONDA_ROUTING;\
$M6_SONSOTO_ROUTING;$M6_SONSOTO_2_ROUTING;\
$M6_TRESCASAS_ROUTING;$M6_TRESCASAS_2_ROUTING;\
$M6_CABANILLAS_ROUTING;\
$M6_TORRECABALLEROS_ROUTING;$M6_TORRECABALLEROS_2_ROUTING;$M6_TORRECABALLEROS_3_ROUTING"

# saturday-reversed: outbound leg cut short + return to Azoguejo + Jardinillos
save_polyline "M6-saturday-reversed" \
    "$M6_ESTACION_BUS_ROUTING;$M6_ANDRES_LAGUNA_ROUTING;$M6_LA_PISTA_ROUTING;$M6_PLAZA_TOROS_ROUTING;\
$M6_PALAZUELOS_ROUTING;$M6_PALAZUELOS_COLEGIO_ROUTING;\
$M6_TABANERA_ROUTING;$M6_TABANERA_2_ROUTING;\
$M6_SANCRIS_IGLESIA_ROUTING;$M6_SANCRIS_ROTONDA_ROUTING;\
$M6_SONSOTO_ROUTING;$M6_SONSOTO_2_ROUTING;\
$M6_TRESCASAS_ROUTING;$M6_TRESCASAS_2_ROUTING;\
$M6_CABANILLAS_ROUTING;\
$M6_AZOGUEJO_ROUTING;$M6_JARDINILLOS_ROUTING"

# sunday-regular: same stop sequence as saturday-regular
save_polyline "M6-sunday-regular" \
    "$M6_ESTACION_BUS_ROUTING;$M6_ANDRES_LAGUNA_ROUTING;$M6_LA_PISTA_ROUTING;$M6_PLAZA_TOROS_ROUTING;\
$M6_PALAZUELOS_ROUTING;$M6_PALAZUELOS_COLEGIO_ROUTING;\
$M6_TABANERA_ROUTING;$M6_TABANERA_2_ROUTING;\
$M6_SANCRIS_IGLESIA_ROUTING;$M6_SANCRIS_ROTONDA_ROUTING;\
$M6_SONSOTO_ROUTING;$M6_SONSOTO_2_ROUTING;\
$M6_TRESCASAS_ROUTING;$M6_TRESCASAS_2_ROUTING;\
$M6_CABANILLAS_ROUTING;\
$M6_TORRECABALLEROS_ROUTING;$M6_TORRECABALLEROS_2_ROUTING;$M6_TORRECABALLEROS_3_ROUTING"

# sunday-reversed: sunday-regular reversed
save_polyline "M6-sunday-reversed" \
    "$M6_TORRECABALLEROS_3_ROUTING;$M6_TORRECABALLEROS_2_ROUTING;$M6_TORRECABALLEROS_ROUTING;\
$M6_CABANILLAS_ROUTING;\
$M6_TRESCASAS_2_ROUTING;$M6_TRESCASAS_ROUTING;\
$M6_SONSOTO_2_ROUTING;$M6_SONSOTO_ROUTING;\
$M6_SANCRIS_ROTONDA_ROUTING;$M6_SANCRIS_IGLESIA_ROUTING;\
$M6_TABANERA_2_ROUTING;$M6_TABANERA_ROUTING;\
$M6_PALAZUELOS_COLEGIO_ROUTING;$M6_PALAZUELOS_ROUTING;\
$M6_PLAZA_TOROS_ROUTING;$M6_LA_PISTA_ROUTING;$M6_ANDRES_LAGUNA_ROUTING;$M6_ESTACION_BUS_ROUTING"

echo ""
echo "Done. Files written to: $OUTPUT_DIR"
