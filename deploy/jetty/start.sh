#!/bin/sh
set -eu
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17+}"
: "${JETTY_HOME:?Set JETTY_HOME}"
project_root=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
base=${JETTY_BASE:-"$project_root/.runtime/jetty-base"}
jetty_home=$(CDPATH= cd "$JETTY_HOME" && pwd)
base=$(CDPATH= cd "$base" && pwd)
cd "$base"
exec "$JAVA_HOME/bin/java" -Xms64m -Xmx256m -Dfile.encoding=UTF-8 "-Djetty.base=$base" -jar "$jetty_home/start.jar"
