#!/bin/sh
set -eu
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
. "$script_dir/environment.sh"
umask 077
sh "$project_root/deploy/jetty/prepare.sh"
sh "$project_root/deploy/payara/prepare.sh"
printf '%s\n' 'Both runtimes prepared. Private keys remain in runtime directories.'
