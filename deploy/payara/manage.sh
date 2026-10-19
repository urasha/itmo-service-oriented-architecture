#!/bin/sh
set -eu
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${PAYARA_HOME:?Set PAYARA_HOME}"
: "${PAYARA_DOMAINS:?Set PAYARA_DOMAINS}"
action=${1:?Usage: manage.sh start|deploy|stop|version}
domain_name=${AGENCY_DOMAIN:-agency}
cli="$PAYARA_HOME/glassfish/lib/client/appserver-cli.jar"
password_file="$PAYARA_DOMAINS/$domain_name-private/asadmin.password"
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
root=$(CDPATH= cd "$script_dir/../.." && pwd)
case "$action" in
    start) exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" --passwordfile "$password_file" start-domain --domaindir "$PAYARA_DOMAINS" "$domain_name";;
    stop) exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" --passwordfile "$password_file" stop-domain --domaindir "$PAYARA_DOMAINS" "$domain_name";;
    deploy) exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" --host 127.0.0.1 --port "${AGENCY_ADMIN_PORT:-18048}" --user admin --passwordfile "$password_file" \
        deploy --force true --name agency-service --contextroot / --virtualservers server "$root/agency-service/target/agency-service.war";;
    version) exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" version --local true;;
    *) printf 'Unknown action: %s\n' "$action" >&2; exit 1;;
esac
