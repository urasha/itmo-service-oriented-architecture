#!/bin/sh
set -eu
uname -a
uname -m
if [ -n "${JAVA_HOME:-}" ]; then "$JAVA_HOME/bin/java" -version; else java -version; fi
ulimit -a
printf '%s\n' 'Also confirm your assigned ports, disk/memory quota, browser access and certificate SAN before deployment.'
