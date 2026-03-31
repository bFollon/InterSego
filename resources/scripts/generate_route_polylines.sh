#!/usr/bin/env bash
# generate_route_polylines.sh
#
# Fetches road-following polylines from OSRM for each known route view and
# writes them as JSON arrays of [lat, lon] pairs into resources/route_polylines/.
#
# Run from anywhere — paths are resolved relative to this script's location.
# After running, copy results to both platforms with sync_polylines.sh.
#
# Prerequisites: curl, jq
# Usage: bash resources/scripts/generate_route_polylines.sh [ROUTE]
#   ROUTE: optional, e.g. "M4" or "M7" to regenerate only one route
#          omit to regenerate all routes

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../route_polylines"
OSRM_BASE="https://router.project-osrm.org/route/v1/driving"
FILTER="${1:-}"

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
# Source of truth: BusStopRegistry.swift / BusStopRegistry.kt
# Each stop has two variables:
#   STOP_NAME          physical stop location (shown as a map marker)
#   STOP_NAME_ROUTING  waypoint sent to OSRM (defaults to the physical location)
#
# Override _ROUTING when the bus travels through a different point than the
# physical stop — for example when the stop is just past a turn the bus
# doesn't make. Set the matching BusStop.routingCoordinates field in the
# parser too.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Shared — Segovia cluster stops (used across M1, M3, M7, M8)
# ---------------------------------------------------------------------------
SEG_ESTACION="-4.121823,40.944768"
SEG_IGLESIA_STO_TOMAS="-4.118070,40.941935"
SEG_FRENTE_BAR_NORTE="-4.113999,40.937206"
SEG_PLAZA_TOROS="-4.107603,40.942093"
SEG_HOSPITAL="-4.127405,40.944055"
SEG_JARDINILLOS="-4.120831,40.944361"
SEG_ANDRES_LAGUNA="-4.115582,40.939106"
SEG_LA_PISTA="-4.111411,40.937354"

# ---------------------------------------------------------------------------
# M4 stops
# ---------------------------------------------------------------------------
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

# Routing overrides for M4
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
M4_CENTRO_BOAL_ROUTING="$M4_CENTRO_BOAL"
M4_PASEO_CABANILLAS_ROUTING="$M4_PASEO_CABANILLAS"
M4_PARROQ_SOTILLO_ROUTING="$M4_PARROQ_SOTILLO"
M4_RAFAEL_HERAS_ROUTING="$M4_RAFAEL_HERAS"
M4_VENTA_MAGULLO_ROUTING="$M4_VENTA_MAGULLO"

# ---------------------------------------------------------------------------
# M6 stops
# ---------------------------------------------------------------------------
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

# Routing overrides for M6
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
# M1 stops (Segovia – Garcillán via Polígono, Casino, Valverde, Abades)
# ---------------------------------------------------------------------------
M1_ESTACION="$SEG_ESTACION"
M1_POLIGONO="-4.198156,40.957976"
M1_POLIGONO_2="-4.206457,40.957554"
M1_CASINO="-4.209251,40.965154"
M1_VALVERDE="-4.235343,40.956274"
M1_ABADES="-4.267038,40.915804"
M1_MARTIN_MIGUEL="-4.268660,40.951889"
M1_GARCILLAN="-4.264724,40.976809"

# Routing overrides for M1
M1_ESTACION_ROUTING="$M1_ESTACION"
M1_POLIGONO_ROUTING="$M1_POLIGONO"
M1_POLIGONO_2_ROUTING="$M1_POLIGONO_2"
M1_CASINO_ROUTING="$M1_CASINO"
M1_VALVERDE_ROUTING="$M1_VALVERDE"
M1_ABADES_ROUTING="$M1_ABADES"
M1_MARTIN_MIGUEL_ROUTING="$M1_MARTIN_MIGUEL"
M1_GARCILLAN_ROUTING="$M1_GARCILLAN"

# ---------------------------------------------------------------------------
# M2 stops (Segovia – Valseca via Casino, Hontanares, Los Huertos)
# ---------------------------------------------------------------------------
M2_SEGOVIA="-4.121823,40.944768"
M2_CASINO="-4.209251,40.965154"
M2_HONTANARES="-4.204160,40.983628"
M2_LOS_HUERTOS="-4.219216,41.009124"
M2_VALSECA="-4.174266,40.999306"

# Routing overrides for M2
M2_SEGOVIA_ROUTING="$M2_SEGOVIA"
M2_CASINO_ROUTING="$M2_CASINO"
M2_HONTANARES_ROUTING="$M2_HONTANARES"
M2_LOS_HUERTOS_ROUTING="$M2_LOS_HUERTOS"
M2_VALSECA_ROUTING="$M2_VALSECA"

# ---------------------------------------------------------------------------
# M3 stops (Segovia – La Granja – Valsaín – Navacerrada; Saturday only)
# ---------------------------------------------------------------------------
M3_ESTACION="$SEG_ESTACION"
M3_IGLESIA="$SEG_IGLESIA_STO_TOMAS"
M3_FRENTE_BAR="$SEG_FRENTE_BAR_NORTE"
M3_PLAZA_TOROS="$SEG_PLAZA_TOROS"
M3_CARRASCALEJO="-4.078270,40.922847"
M3_PARQUE_ROBLEDO="-4.058750,40.910111"
M3_LA_GRANJA="-4.003333,40.901389"
M3_VALSAIN="-4.019444,40.877500"
M3_BOCA_DEL_ASNO="-4.025668,40.844128"
M3_PUENTE_MOSQUITOS="-4.017340,40.823325"
M3_NAVACERRADA="-4.008056,40.780833"

# Routing overrides for M3
M3_ESTACION_ROUTING="$M3_ESTACION"
M3_IGLESIA_ROUTING="$M3_IGLESIA"
M3_FRENTE_BAR_ROUTING="$M3_FRENTE_BAR"
M3_PLAZA_TOROS_ROUTING="$M3_PLAZA_TOROS"
M3_CARRASCALEJO_ROUTING="$M3_CARRASCALEJO"
M3_PARQUE_ROBLEDO_ROUTING="$M3_PARQUE_ROBLEDO"
M3_LA_GRANJA_ROUTING="$M3_LA_GRANJA"
M3_VALSAIN_ROUTING="$M3_VALSAIN"
M3_BOCA_DEL_ASNO_ROUTING="$M3_BOCA_DEL_ASNO"
M3_PUENTE_MOSQUITOS_ROUTING="$M3_PUENTE_MOSQUITOS"
M3_NAVACERRADA_ROUTING="$M3_NAVACERRADA"

# ---------------------------------------------------------------------------
# M5 stops (Segovia – Sto. Domingo de Pirón via Tizneros, Espirdo, etc.)
# ---------------------------------------------------------------------------
M5_ESTACION="$SEG_ESTACION"
M5_TIZNEROS="-4.055240,40.991521"
M5_ESPIRDO="-4.073623,40.996957"
M5_LA_HIGUERA="-4.080770,41.016117"
M5_BRIEVA="-4.052387,41.035677"
M5_BASARDILLA="-4.025058,41.027220"
M5_STO_DOMINGO="-3.989562,41.041438"

# Routing overrides for M5
M5_ESTACION_ROUTING="$M5_ESTACION"
M5_TIZNEROS_ROUTING="$M5_TIZNEROS"
M5_ESPIRDO_ROUTING="$M5_ESPIRDO"
M5_LA_HIGUERA_ROUTING="$M5_LA_HIGUERA"
M5_BRIEVA_ROUTING="$M5_BRIEVA"
M5_BASARDILLA_ROUTING="$M5_BASARDILLA"
M5_STO_DOMINGO_ROUTING="$M5_STO_DOMINGO"

# ---------------------------------------------------------------------------
# M7 stops (Segovia – Tabanera – Palazuelos – Segovia / Torrecaballeros)
# Shares San Cristóbal, Sonsoto, Trescasas, Cabanillas, Torrecaballeros with M6
# ---------------------------------------------------------------------------
M7_ESTACION="$SEG_ESTACION"
M7_HOSPITAL="$SEG_HOSPITAL"
M7_ANDRES_LAGUNA="$SEG_ANDRES_LAGUNA"
M7_LA_PISTA="$SEG_LA_PISTA"
M7_PLAZA_TOROS="$SEG_PLAZA_TOROS"
M7_JARDINILLOS="$SEG_JARDINILLOS"
M7_PALAZUELOS="$M6_PALAZUELOS"
M7_PALAZUELOS_COLEGIO="$M6_PALAZUELOS_COLEGIO"
M7_TABANERA="$M6_TABANERA"
M7_TABANERA_2="$M6_TABANERA_2"
M7_SANCRIS="$M6_SANCRIS"
M7_SANCRIS_IGLESIA="$M6_SANCRIS_IGLESIA"
M7_SANCRIS_ROTONDA="$M6_SANCRIS_ROTONDA"
M7_SONSOTO="$M6_SONSOTO"
M7_SONSOTO_2="$M6_SONSOTO_2"
M7_TRESCASAS="$M6_TRESCASAS"
M7_TRESCASAS_2="$M6_TRESCASAS_2"
M7_CABANILLAS="$M6_CABANILLAS"
M7_TORRECABALLEROS="$M6_TORRECABALLEROS"
M7_TORRECABALLEROS_2="$M6_TORRECABALLEROS_2"
M7_TORRECABALLEROS_3="$M6_TORRECABALLEROS_3"

# Routing overrides for M7
M7_ESTACION_ROUTING="$M7_ESTACION"
M7_HOSPITAL_ROUTING="$M7_HOSPITAL"
M7_ANDRES_LAGUNA_ROUTING="$M7_ANDRES_LAGUNA"
M7_LA_PISTA_ROUTING="$M7_LA_PISTA"
M7_PLAZA_TOROS_ROUTING="$M7_PLAZA_TOROS"
M7_JARDINILLOS_ROUTING="$M7_JARDINILLOS"
M7_PALAZUELOS_ROUTING="$M7_PALAZUELOS"
M7_PALAZUELOS_COLEGIO_ROUTING="$M7_PALAZUELOS_COLEGIO"
M7_TABANERA_ROUTING="$M7_TABANERA"
M7_TABANERA_2_ROUTING="$M7_TABANERA_2"
M7_SANCRIS_ROUTING="$M7_SANCRIS"
M7_SANCRIS_IGLESIA_ROUTING="$M7_SANCRIS_IGLESIA"
M7_SANCRIS_ROTONDA_ROUTING="$M7_SANCRIS_ROTONDA"
M7_SONSOTO_ROUTING="$M7_SONSOTO"
M7_SONSOTO_2_ROUTING="$M7_SONSOTO_2"
M7_TRESCASAS_ROUTING="$M7_TRESCASAS"
M7_TRESCASAS_2_ROUTING="$M7_TRESCASAS_2"
M7_CABANILLAS_ROUTING="$M7_CABANILLAS"
M7_TORRECABALLEROS_ROUTING="$M7_TORRECABALLEROS"
M7_TORRECABALLEROS_2_ROUTING="$M7_TORRECABALLEROS_2"
M7_TORRECABALLEROS_3_ROUTING="$M7_TORRECABALLEROS_3"

# ---------------------------------------------------------------------------
# M8 stops (Segovia – La Granja – Valsaín)
# Shares Carrascalejo and Parque Robledo with M3
# ---------------------------------------------------------------------------
M8_ESTACION="$SEG_ESTACION"
M8_IGLESIA="$SEG_IGLESIA_STO_TOMAS"
M8_FRENTE_BAR="$SEG_FRENTE_BAR_NORTE"
M8_PLAZA_TOROS="$SEG_PLAZA_TOROS"
M8_CARRASCALEJO="-4.078270,40.922847"
M8_PENAS_DEL_ERIZO="-4.066063,40.914155"
M8_C_LA_FUENCISLA="-4.060857,40.917151"
M8_PARQUE_ROBLEDO="-4.058750,40.910111"
M8_FABRICA_CRISTAL="-4.006773,40.902789"
M8_PISCINAS="-4.009200,40.909126"
M8_PTAS_SEGOVIA="-4.009467,40.900325"
M8_LA_PRADERA="-4.018356,40.878114"
M8_FRONTON="-4.021955,40.875741"
M8_PLAZA_VALSAIN="-4.027028,40.878374"

# Routing overrides for M8
M8_ESTACION_ROUTING="$M8_ESTACION"
M8_IGLESIA_ROUTING="$M8_IGLESIA"
M8_FRENTE_BAR_ROUTING="$M8_FRENTE_BAR"
M8_PLAZA_TOROS_ROUTING="$M8_PLAZA_TOROS"
M8_CARRASCALEJO_ROUTING="$M8_CARRASCALEJO"
M8_PENAS_DEL_ERIZO_ROUTING="$M8_PENAS_DEL_ERIZO"
M8_C_LA_FUENCISLA_ROUTING="$M8_C_LA_FUENCISLA"
M8_PARQUE_ROBLEDO_ROUTING="$M8_PARQUE_ROBLEDO"
M8_FABRICA_CRISTAL_ROUTING="$M8_FABRICA_CRISTAL"
M8_PISCINAS_ROUTING="$M8_PISCINAS"
M8_PTAS_SEGOVIA_ROUTING="$M8_PTAS_SEGOVIA"
M8_LA_PRADERA_ROUTING="$M8_LA_PRADERA"
M8_FRONTON_ROUTING="$M8_FRONTON"
M8_PLAZA_VALSAIN_ROUTING="$M8_PLAZA_VALSAIN"

# ===========================================================================
# Route generation
# ===========================================================================

# ---------------------------------------------------------------------------
# M4 — view IDs: "regular", "reverse"
# ---------------------------------------------------------------------------
if [[ -z "$FILTER" || "$FILTER" == "M4" ]]; then
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
fi

# ---------------------------------------------------------------------------
# M6 — view IDs match what getRouteViews() returns
# ---------------------------------------------------------------------------
if [[ -z "$FILTER" || "$FILTER" == "M6" ]]; then
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
fi

# ---------------------------------------------------------------------------
# M1 — view IDs: "circularA", "circularB", "saturday-outbound", "saturday-inbound"
# ---------------------------------------------------------------------------
if [[ -z "$FILTER" || "$FILTER" == "M1" ]]; then
echo ""
echo "=== M1 ==="

# circularA: full outbound via all villages, direct return to Segovia
# Estación → Polígono → Polígono2 → Casino → Valverde → Abades → Martín Miguel → Garcillán → Estación
save_polyline "M1-circularA" \
    "$M1_ESTACION_ROUTING;$M1_POLIGONO_ROUTING;$M1_POLIGONO_2_ROUTING;$M1_CASINO_ROUTING;\
$M1_VALVERDE_ROUTING;$M1_ABADES_ROUTING;$M1_MARTIN_MIGUEL_ROUTING;$M1_GARCILLAN_ROUTING;\
$M1_ESTACION_ROUTING"

# circularB: direct outbound to Garcillán, return via all villages
# Estación → Polígono → Polígono2 → Garcillán → Martín Miguel → Abades → Valverde → Casino → Polígono2 → Polígono → Estación
save_polyline "M1-circularB" \
    "$M1_ESTACION_ROUTING;$M1_POLIGONO_ROUTING;$M1_POLIGONO_2_ROUTING;\
$M1_GARCILLAN_ROUTING;$M1_MARTIN_MIGUEL_ROUTING;$M1_ABADES_ROUTING;$M1_VALVERDE_ROUTING;\
$M1_CASINO_ROUTING;$M1_POLIGONO_2_ROUTING;$M1_POLIGONO_ROUTING;$M1_ESTACION_ROUTING"

# saturday-outbound: Estación → Casino → Valverde → Abades
save_polyline "M1-saturday-outbound" \
    "$M1_ESTACION_ROUTING;$M1_CASINO_ROUTING;$M1_VALVERDE_ROUTING;$M1_ABADES_ROUTING"

# saturday-inbound: Abades → Valverde → Estación
save_polyline "M1-saturday-inbound" \
    "$M1_ABADES_ROUTING;$M1_VALVERDE_ROUTING;$M1_ESTACION_ROUTING"
fi

# ---------------------------------------------------------------------------
# M2 — view IDs: "circularA", "circularB"
# ---------------------------------------------------------------------------
if [[ -z "$FILTER" || "$FILTER" == "M2" ]]; then
echo ""
echo "=== M2 ==="

# circularA (outbound view): Segovia → Casino → Hontanares → Los Huertos → Hontanares → Valseca
# The bus physically backtracks through Hontanares after Los Huertos to reach Valseca.
save_polyline "M2-circularA" \
    "$M2_SEGOVIA_ROUTING;$M2_CASINO_ROUTING;$M2_HONTANARES_ROUTING;$M2_LOS_HUERTOS_ROUTING;\
$M2_HONTANARES_ROUTING;$M2_VALSECA_ROUTING"

# circularB (return view): Los Huertos → Hontanares → Valseca → Casino → Segovia
save_polyline "M2-circularB" \
    "$M2_LOS_HUERTOS_ROUTING;$M2_HONTANARES_ROUTING;$M2_VALSECA_ROUTING;\
$M2_CASINO_ROUTING;$M2_SEGOVIA_ROUTING"
fi

# ---------------------------------------------------------------------------
# M3 — view IDs: "regular" (outbound), "reverse" (inbound); Saturday only
# ---------------------------------------------------------------------------
if [[ -z "$FILTER" || "$FILTER" == "M3" ]]; then
echo ""
echo "=== M3 ==="

# regular: Segovia cluster → Navacerrada
save_polyline "M3-regular" \
    "$M3_ESTACION_ROUTING;$M3_IGLESIA_ROUTING;$M3_FRENTE_BAR_ROUTING;$M3_PLAZA_TOROS_ROUTING;\
$M3_CARRASCALEJO_ROUTING;$M3_PARQUE_ROBLEDO_ROUTING;$M3_LA_GRANJA_ROUTING;\
$M3_VALSAIN_ROUTING;$M3_BOCA_DEL_ASNO_ROUTING;$M3_PUENTE_MOSQUITOS_ROUTING;\
$M3_NAVACERRADA_ROUTING"

# reverse: Navacerrada → Segovia cluster
save_polyline "M3-reverse" \
    "$M3_NAVACERRADA_ROUTING;$M3_PUENTE_MOSQUITOS_ROUTING;$M3_BOCA_DEL_ASNO_ROUTING;\
$M3_VALSAIN_ROUTING;$M3_LA_GRANJA_ROUTING;$M3_PARQUE_ROBLEDO_ROUTING;\
$M3_CARRASCALEJO_ROUTING;\
$M3_PLAZA_TOROS_ROUTING;$M3_FRENTE_BAR_ROUTING;$M3_IGLESIA_ROUTING;$M3_ESTACION_ROUTING"
fi

# ---------------------------------------------------------------------------
# M5 — view IDs: "regular" (outbound), "reverse" (inbound); weekday + Saturday
# ---------------------------------------------------------------------------
if [[ -z "$FILTER" || "$FILTER" == "M5" ]]; then
echo ""
echo "=== M5 ==="

# regular: Segovia → Sto. Domingo de Pirón
save_polyline "M5-regular" \
    "$M5_ESTACION_ROUTING;$M5_TIZNEROS_ROUTING;$M5_ESPIRDO_ROUTING;$M5_LA_HIGUERA_ROUTING;\
$M5_BRIEVA_ROUTING;$M5_BASARDILLA_ROUTING;$M5_STO_DOMINGO_ROUTING"

# reverse: Sto. Domingo de Pirón → Segovia
save_polyline "M5-reverse" \
    "$M5_STO_DOMINGO_ROUTING;$M5_BASARDILLA_ROUTING;$M5_BRIEVA_ROUTING;$M5_LA_HIGUERA_ROUTING;\
$M5_ESPIRDO_ROUTING;$M5_TIZNEROS_ROUTING;$M5_ESTACION_ROUTING"
fi

# ---------------------------------------------------------------------------
# M7 — view IDs: "weekday-circular", "saturday-outbound", "saturday-inbound",
#                "sunday-outbound", "sunday-inbound"
# ---------------------------------------------------------------------------
if [[ -z "$FILTER" || "$FILTER" == "M7" ]]; then
echo ""
echo "=== M7 ==="

# weekday-circular: Segovia → Tabanera → Palazuelos (cluster) → Segovia (return)
save_polyline "M7-weekday-circular" \
    "$M7_ESTACION_ROUTING;$M7_TABANERA_ROUTING;$M7_TABANERA_2_ROUTING;\
$M7_PALAZUELOS_ROUTING;$M7_PALAZUELOS_COLEGIO_ROUTING;$M7_ESTACION_ROUTING"

# saturday-outbound / sunday-outbound: extended route Segovia → Torrecaballeros
# Outbound Segovia cluster: Estación → Hospital → Andrés Laguna → La Pista → Plaza de Toros
M7_EXT_OUTBOUND="$M7_ESTACION_ROUTING;$M7_HOSPITAL_ROUTING;$M7_ANDRES_LAGUNA_ROUTING;\
$M7_LA_PISTA_ROUTING;$M7_PLAZA_TOROS_ROUTING;\
$M7_PALAZUELOS_ROUTING;$M7_TABANERA_ROUTING;$M7_TABANERA_2_ROUTING;\
$M7_SANCRIS_ROUTING;$M7_SANCRIS_IGLESIA_ROUTING;$M7_SANCRIS_ROTONDA_ROUTING;\
$M7_SONSOTO_ROUTING;$M7_SONSOTO_2_ROUTING;\
$M7_TRESCASAS_ROUTING;$M7_TRESCASAS_2_ROUTING;\
$M7_CABANILLAS_ROUTING;\
$M7_TORRECABALLEROS_ROUTING;$M7_TORRECABALLEROS_2_ROUTING;$M7_TORRECABALLEROS_3_ROUTING"

# saturday-inbound / sunday-inbound: extended route Torrecaballeros → Segovia
# Inbound Segovia cluster: Plaza de Toros → La Pista → Andrés Laguna → Jardinillos
M7_EXT_INBOUND="$M7_TORRECABALLEROS_3_ROUTING;$M7_TORRECABALLEROS_2_ROUTING;$M7_TORRECABALLEROS_ROUTING;\
$M7_CABANILLAS_ROUTING;\
$M7_TRESCASAS_2_ROUTING;$M7_TRESCASAS_ROUTING;\
$M7_SONSOTO_2_ROUTING;$M7_SONSOTO_ROUTING;\
$M7_SANCRIS_ROTONDA_ROUTING;$M7_SANCRIS_IGLESIA_ROUTING;$M7_SANCRIS_ROUTING;\
$M7_TABANERA_2_ROUTING;$M7_TABANERA_ROUTING;$M7_PALAZUELOS_ROUTING;\
$M7_PLAZA_TOROS_ROUTING;$M7_LA_PISTA_ROUTING;$M7_ANDRES_LAGUNA_ROUTING;\
$M7_JARDINILLOS_ROUTING"

save_polyline "M7-saturday-outbound" "$M7_EXT_OUTBOUND"
save_polyline "M7-saturday-inbound"  "$M7_EXT_INBOUND"
save_polyline "M7-sunday-outbound"   "$M7_EXT_OUTBOUND"
save_polyline "M7-sunday-inbound"    "$M7_EXT_INBOUND"
fi

# ---------------------------------------------------------------------------
# M8 — view IDs: "weekday-outbound/inbound", "saturday-outbound/inbound",
#                "sunday-outbound/inbound"
# All day types share the same stop sequence (polylines are identical).
# Note: F. Cristal appears before Piscinas in inbound (one-way routing through La Granja).
# ---------------------------------------------------------------------------
if [[ -z "$FILTER" || "$FILTER" == "M8" ]]; then
echo ""
echo "=== M8 ==="

# Outbound: Segovia cluster → La Granja → Valsaín
M8_OUTBOUND="$M8_ESTACION_ROUTING;$M8_IGLESIA_ROUTING;$M8_FRENTE_BAR_ROUTING;$M8_PLAZA_TOROS_ROUTING;\
$M8_CARRASCALEJO_ROUTING;$M8_PENAS_DEL_ERIZO_ROUTING;$M8_C_LA_FUENCISLA_ROUTING;\
$M8_PARQUE_ROBLEDO_ROUTING;$M8_FABRICA_CRISTAL_ROUTING;$M8_PISCINAS_ROUTING;\
$M8_PTAS_SEGOVIA_ROUTING;$M8_LA_PRADERA_ROUTING;$M8_FRONTON_ROUTING;$M8_PLAZA_VALSAIN_ROUTING"

# Inbound: Valsaín → La Granja → Segovia cluster
# F. Cristal appears before Piscinas (one-way routing through La Granja town centre)
M8_INBOUND="$M8_PLAZA_VALSAIN_ROUTING;$M8_FRONTON_ROUTING;$M8_LA_PRADERA_ROUTING;\
$M8_FABRICA_CRISTAL_ROUTING;$M8_PISCINAS_ROUTING;$M8_PTAS_SEGOVIA_ROUTING;\
$M8_PARQUE_ROBLEDO_ROUTING;$M8_C_LA_FUENCISLA_ROUTING;\
$M8_PENAS_DEL_ERIZO_ROUTING;$M8_CARRASCALEJO_ROUTING;\
$M8_PLAZA_TOROS_ROUTING;$M8_FRENTE_BAR_ROUTING;$M8_IGLESIA_ROUTING;$M8_ESTACION_ROUTING"

save_polyline "M8-weekday-outbound"  "$M8_OUTBOUND"
save_polyline "M8-weekday-inbound"   "$M8_INBOUND"
save_polyline "M8-saturday-outbound" "$M8_OUTBOUND"
save_polyline "M8-saturday-inbound"  "$M8_INBOUND"
save_polyline "M8-sunday-outbound"   "$M8_OUTBOUND"
save_polyline "M8-sunday-inbound"    "$M8_INBOUND"
fi

echo ""
echo "Done. Files written to: $OUTPUT_DIR"
echo "Run sync_polylines.sh to copy them to both platform asset directories."
