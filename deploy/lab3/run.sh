#!/bin/sh
set -eu
umask 077
root=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
set -a
. "${SOA_ENV_FILE:-$root/deploy/local.env}"
JETTY_BASE="$SOA_RUNTIME/jetty-base"
AGENCY_TLS_DIR="$SOA_RUNTIME/agency-tls"
FLATS_INTERNAL_URL="https://127.0.0.1:$FLATS_HTTPS_PORT"
FLATS_CERTIFICATE="$JETTY_BASE/etc/flats.crt"
EJB_PROVIDER_URL="remote+http://127.0.0.1:$((8080 + WILDFLY_PORT_OFFSET))"
CONSUL_HOST=127.0.0.1
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.net.preferIPv4Stack=true"
set +a
exec python3 "$root/deploy/lab3/stack.py" "$@"
