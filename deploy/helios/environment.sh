#!/bin/sh
# Sourced by the entry points. local.env is trusted shell configuration owned by you.
set -eu
project_root=$(CDPATH= cd "$(dirname "$0")/../.." && pwd)
env_file=${SOA_ENV_FILE:-"$project_root/deploy/local.env"}
[ -f "$env_file" ] || { printf 'Copy deploy/env.example to %s and edit it first.\n' "$env_file" >&2; exit 1; }
set -a
. "$env_file"
set +a
: "${JAVA_HOME:?Set JAVA_HOME}"
: "${JETTY_HOME:?Set JETTY_HOME}"
: "${JETTY_BASE:?Set JETTY_BASE}"
: "${PAYARA_HOME:?Set PAYARA_HOME}"
: "${PAYARA_DOMAINS:?Set PAYARA_DOMAINS}"
: "${FLATS_HTTPS_PORT:?Set FLATS_HTTPS_PORT}"
: "${AGENCY_HTTPS_PORT:?Set AGENCY_HTTPS_PORT}"
: "${AGENCY_ADMIN_PORT:?Set AGENCY_ADMIN_PORT}"
: "${FLATS_PUBLIC_BASE_URL:?Set FLATS_PUBLIC_BASE_URL}"
: "${AGENCY_PUBLIC_BASE_URL:?Set AGENCY_PUBLIC_BASE_URL}"
export JAVA_HOME JETTY_HOME JETTY_BASE PAYARA_HOME PAYARA_DOMAINS
