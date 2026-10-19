#!/bin/sh
set -eu
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
. "$script_dir/environment.sh"
payara_status=0
sh "$project_root/deploy/payara/manage.sh" stop || payara_status=$?
pid_file="$JETTY_BASE/jetty.pid"
if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file")
    case "$pid" in ''|*[!0-9]*) printf '%s\n' 'Invalid Jetty PID file' >&2; exit 1;; esac
    if kill -0 "$pid" 2>/dev/null; then
        command_line=$(ps -p "$pid" -o args=)
        process_owner=$(ps -p "$pid" -o user= | tr -d ' ')
        [ "$process_owner" = "$(id -un)" ] || { printf '%s\n' 'Recorded PID belongs to another user; refusing to stop it.' >&2; exit 1; }
        case "$command_line" in
            *"-Djetty.base=$JETTY_BASE"*"start.jar"*) kill -TERM "$pid";;
            *) printf '%s\n' 'Recorded PID is not this Jetty instance; refusing to stop it.' >&2; exit 1;;
        esac
        attempts=0
        while kill -0 "$pid" 2>/dev/null; do
            attempts=$((attempts + 1))
            [ "$attempts" -lt 30 ] || { printf '%s\n' 'Jetty is still stopping; inspect its log.' >&2; exit 1; }
            sleep 1
        done
    fi
    rm -f "$pid_file"
fi
exit "$payara_status"
