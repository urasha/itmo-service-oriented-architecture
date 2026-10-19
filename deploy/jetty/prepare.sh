#!/bin/sh
set -eu
umask 077
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${JETTY_HOME:?Set JETTY_HOME to the unpacked pinned Jetty distribution}"
project_root=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
base=${JETTY_BASE:-"$project_root/.runtime/jetty-base"}
port=${FLATS_HTTPS_PORT:-8443}
bind_address=${FLATS_BIND_ADDRESS:-127.0.0.1}
case "$port" in *[!0-9]*|'') printf '%s\n' 'Invalid TLS port' >&2; exit 1;; esac
[ "$port" -ge 1024 ] && [ "$port" -le 65535 ] || { printf '%s\n' 'Use a port from 1024 to 65535.' >&2; exit 1; }
[ -f "$JETTY_HOME/start.jar" ] || { printf '%s\n' 'JETTY_HOME/start.jar not found.' >&2; exit 1; }
[ -f "$project_root/flats-service/target/flats-service.war" ] || { printf '%s\n' 'Run mvn clean verify first.' >&2; exit 1; }
mkdir -p "$base"
base=$(CDPATH= cd "$base" && pwd)
if [ -f "$base/start.ini" ] || [ -f "$base/start.d/http.ini" ]; then
    printf '%s\n' 'Use a dedicated Jetty base without existing HTTP configuration.' >&2
    exit 1
fi
for dir in start.d etc modules webapps lib/ext; do mkdir -p "$base/$dir"; done
for dir in start.d etc modules webapps; do cp "$project_root/deploy/jetty/$dir/"* "$base/$dir/"; done
printf '%s\n' '--module=https' "jetty.ssl.host=$bind_address" "jetty.ssl.port=$port" \
    'jetty.httpConfig.requestHeaderSize=65536' 'jetty.httpConfig.sendServerVersion=true' > "$base/start.d/https.ini"
flats_public_url=${FLATS_PUBLIC_BASE_URL:-}
agency_public_url=${AGENCY_PUBLIC_BASE_URL:-https://localhost:8444}
for public_url in "$flats_public_url" "$agency_public_url"; do
    case "$public_url" in ''|https://*) ;; *) printf '%s\n' 'Public URLs must use HTTPS' >&2; exit 1;; esac
    case "$public_url" in *'
'*) printf '%s\n' 'Public URL must be a single line' >&2; exit 1;; esac
done
printf '%s\n' "-Dsoa.flatsPublicBaseUrl=$flats_public_url" "-Dsoa.agencyPublicBaseUrl=$agency_public_url" > "$base/start.d/client.ini"
sh "$project_root/deploy/tls/generate.sh" "$base/etc"
password=$(cat "$base/etc/keystore.password")
printf 'jetty.sslContext.keyStorePassword=%s\n' "$password" > "$base/start.d/local-secrets.ini"
chmod 600 "$base/start.d/local-secrets.ini" "$base/etc/keystore.password" "$base/etc/keystore.p12"
cp "$project_root/flats-service/target/flats-service.war" "$base/webapps/ROOT.war"
cp "$project_root/flats-service/target/flats-service-jetty-errors.jar" "$base/lib/ext/"
printf 'Jetty base prepared: %s\nHTTPS port: %s\n' "$base" "$port"
