#!/bin/sh
set -eu
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
. "$script_dir/environment.sh"
umask 077
mkdir -p "$JETTY_BASE/logs"
pid_file="$JETTY_BASE/jetty.pid"
if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    printf '%s\n' 'The recorded Jetty PID is alive; inspect it before starting another instance.' >&2; exit 1
fi
nohup sh "$project_root/deploy/jetty/start.sh" > "$JETTY_BASE/logs/stdout.log" 2>&1 < /dev/null &
printf '%s\n' "$!" > "$pid_file"
sh "$project_root/deploy/payara/manage.sh" start
sh "$project_root/deploy/payara/manage.sh" deploy
sh "$script_dir/smoke.sh"
