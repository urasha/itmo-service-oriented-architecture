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
# Some Helios JVM builds reject the CLI's remote HTTP requests. The local REST
# administration API uses the same domain credentials and stays on loopback.
if [ "${PAYARA_ADMIN_TRANSPORT:-cli}" = rest ]; then
    rest_command() {
        admin_password=$(sed -n 's/^AS_ADMIN_PASSWORD=//p' "$password_file")
        case "$admin_password" in ''|*[!a-f0-9]*) printf '%s\n' 'Expected the generated admin password.' >&2; return 1;; esac
        result=$(printf 'user = "admin:%s"\n' "$admin_password" | curl --config - --noproxy '*' \
            --fail --silent --show-error --connect-timeout 5 --max-time 120 \
            -H 'Accept: application/json' -H 'X-Requested-By: soa-deploy' "$@")
        unset admin_password
        printf '%s\n' "$result"
        printf '%s' "$result" | grep -Eq '"exit_code"[[:space:]]*:[[:space:]]*"SUCCESS"'
    }
    admin_url="http://127.0.0.1:${AGENCY_ADMIN_PORT:-18048}/management/domain"
    case "$action" in
        deploy)
            rest_command -F "id=@$root/agency-service/target/agency-service.war" -F name=agency-service \
                -F contextroot=/ -F virtualservers=server -F force=true "$admin_url/applications/application"
            exit $?;;
        stop)
            pid_file="$PAYARA_DOMAINS/$domain_name/config/pid"
            [ -f "$pid_file" ] || exit 0
            pid=$(cat "$pid_file")
            case "$pid" in ''|*[!0-9]*) printf '%s\n' 'Invalid Payara PID.' >&2; exit 1;; esac
            kill -0 "$pid" 2>/dev/null || exit 0
            rest_command --data force=true "$admin_url/stop"
            attempts=0
            while kill -0 "$pid" 2>/dev/null; do
                attempts=$((attempts + 1))
                [ "$attempts" -lt 30 ] || { printf '%s\n' 'Payara is still stopping.' >&2; exit 1; }
                sleep 1
            done
            exit 0;;
    esac
fi
case "$action" in
    start) exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" --passwordfile "$password_file" start-domain --domaindir "$PAYARA_DOMAINS" "$domain_name";;
    stop) exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" --passwordfile "$password_file" stop-domain --domaindir "$PAYARA_DOMAINS" "$domain_name";;
    deploy) exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" --host 127.0.0.1 --port "${AGENCY_ADMIN_PORT:-18048}" --user admin --passwordfile "$password_file" \
        deploy --force true --name agency-service --contextroot / --virtualservers server "$root/agency-service/target/agency-service.war";;
    version) exec "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -jar "$cli" version --local true;;
    *) printf 'Unknown action: %s\n' "$action" >&2; exit 1;;
esac
