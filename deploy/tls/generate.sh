#!/bin/sh
set -eu
umask 077
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
output=${1:?Usage: generate.sh OUTPUT_DIRECTORY}
alias_name=${2:-flats}
san=${FLATS_CERT_SAN:-dns:localhost,ip:127.0.0.1}
mkdir -p "$output"
output=$(cd "$output" && pwd)
store="$output/keystore.p12"
password_file="$output/keystore.password"
certificate="$output/$alias_name.crt"
if [ -e "$store" ] || [ -e "$password_file" ]; then
    if [ ! -f "$store" ] || [ ! -f "$password_file" ] || [ ! -f "$certificate" ]; then
        printf '%s\n' 'Incomplete TLS directory. Use a new directory; keys are not overwritten.' >&2
        exit 1
    fi
    printf 'Reusing certificate: %s\n' "$certificate"
    exit 0
fi
dd if=/dev/urandom bs=32 count=1 2>/dev/null | od -A n -t x1 | tr -d ' \n' > "$password_file"
[ "$(wc -c < "$password_file" | tr -d ' ')" = 64 ] || { printf '%s\n' 'Cannot generate a secure password.' >&2; exit 1; }
if [ "$alias_name" = flats ]; then distinguished_name='CN=Flat Collection Service'; else distinguished_name="CN=$alias_name Service"; fi
"$JAVA_HOME/bin/keytool" -genkeypair -alias "$alias_name" -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 365 \
    -storetype PKCS12 -keystore "$store" -storepass:file "$password_file" -keypass:file "$password_file" \
    -dname "$distinguished_name" -ext "SAN=$san" -ext 'EKU=serverAuth' -ext 'KU=digitalSignature,keyEncipherment' -noprompt
"$JAVA_HOME/bin/keytool" -exportcert -rfc -alias "$alias_name" -keystore "$store" -storepass:file "$password_file" -file "$certificate"
printf 'Public certificate: %s\n' "$certificate"
