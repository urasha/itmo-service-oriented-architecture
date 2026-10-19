#!/bin/sh
set -eu
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
. "$script_dir/environment.sh"
"$JAVA_HOME/bin/java" "$script_dir/Smoke.java" \
    "${FLATS_INTERNAL_URL:-https://localhost:$FLATS_HTTPS_PORT}" "${AGENCY_PROBE_URL:-https://localhost:$AGENCY_HTTPS_PORT}" \
    "$FLATS_CERTIFICATE" "$PAYARA_DOMAINS/${AGENCY_DOMAIN:-agency}-private/tls/agency.crt" "$FLATS_PUBLIC_BASE_URL"
