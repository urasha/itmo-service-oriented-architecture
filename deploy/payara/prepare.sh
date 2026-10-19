#!/bin/sh
set -eu
umask 077
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${PAYARA_HOME:?Set PAYARA_HOME to Payara Server 6.2025.10}"
: "${PAYARA_DOMAINS:?Set PAYARA_DOMAINS to a private dedicated domains directory}"
: "${FLATS_CERTIFICATE:?Set FLATS_CERTIFICATE to the Jetty public certificate}"
: "${FLATS_INTERNAL_URL:?Set the HTTPS URL accessible from Payara}"
: "${FLATS_PUBLIC_URL:?Set the HTTPS URL accessible from the browser}"
: "${AGENCY_ALLOWED_ORIGIN:?Set the HTTPS origin of the future web client}"
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
root=$(CDPATH= cd "$script_dir/../.." && pwd)
domain_name=${AGENCY_DOMAIN:-agency}
case "$domain_name" in ''|*[!a-zA-Z0-9_-]*) printf '%s\n' 'Invalid domain name' >&2; exit 1;; esac
port_base=${AGENCY_PORT_BASE:-18000}
https_port=${AGENCY_HTTPS_PORT:-8444}
bind_address=${AGENCY_BIND_ADDRESS:-127.0.0.1}
mkdir -p "$PAYARA_DOMAINS"
PAYARA_DOMAINS=$(CDPATH= cd "$PAYARA_DOMAINS" && pwd)
private_dir="$PAYARA_DOMAINS/$domain_name-private"
mkdir -p "$private_dir"
chmod 700 "$private_dir"
password_file="$private_dir/asadmin.password"
master_file="$private_dir/master.password"
random_password() { dd if=/dev/urandom bs=32 count=1 2>/dev/null | od -A n -t x1 | tr -d ' \n'; }
if [ ! -f "$password_file" ]; then
    master=$(random_password)
    admin=$(random_password)
    [ "${#master}" = 64 ] && [ "${#admin}" = 64 ] || exit 1
    printf '%s' "$master" > "$master_file"
    printf 'AS_ADMIN_PASSWORD=%s\nAS_ADMIN_MASTERPASSWORD=%s\n' "$admin" "$master" > "$password_file"
    unset master admin
fi
cli="$PAYARA_HOME/glassfish/lib/client/appserver-cli.jar"
domain="$PAYARA_DOMAINS/$domain_name"
if [ -f "$domain/config/pid" ] && kill -0 "$(cat "$domain/config/pid")" 2>/dev/null; then
    printf '%s\n' 'Stop the domain before preparation.' >&2; exit 1
fi
if [ ! -f "$domain/config/domain.xml" ]; then
    "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" --user admin --passwordfile "$password_file" create-domain \
        --domaindir "$PAYARA_DOMAINS" --portbase "$port_base" --usemasterpassword true --savemasterpassword true --savelogin false "$domain_name"
fi
chmod 700 "$domain"
tls="$private_dir/tls"
FLATS_CERT_SAN=${AGENCY_CERT_SAN:-dns:localhost,ip:127.0.0.1} sh "$root/deploy/tls/generate.sh" "$tls" agency
app_config="$private_dir/agency.properties"
# Avoid Properties escape syntax and line injection; paths and URLs are single-line values.
for value in "$tls" "$FLATS_INTERNAL_URL" "$FLATS_PUBLIC_URL" "$AGENCY_ALLOWED_ORIGIN"; do
    case "$value" in *\\*|*'
'*) printf '%s\n' 'Backslashes and newlines are not supported in configuration values' >&2; exit 1;; esac
done
printf 'flats.internalBaseUrl=%s\nflats.publicBaseUrl=%s\nflats.trustStore=%s/flats-truststore.p12\nflats.trustStorePasswordFile=%s/flats-truststore.password\nflats.connectTimeoutMillis=3000\nflats.requestTimeoutMillis=5000\ncors.allowedOrigin=%s\n' \
    "$FLATS_INTERNAL_URL" "$FLATS_PUBLIC_URL" "$tls" "$tls" "$AGENCY_ALLOWED_ORIGIN" > "$app_config"
"$JAVA_HOME/bin/java" "$script_dir/ConfigureDomain.java" "$domain" "$tls" "$master_file" "$https_port" "$bind_address" "$FLATS_CERTIFICATE" "$app_config"
printf 'Prepared domain: %s\nPublic certificate: %s/agency.crt\n' "$domain" "$tls"
