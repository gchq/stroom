#!/usr/bin/env bash

set -e

# shellcheck disable=SC1091
source ./stroom-proxy/stroom-proxy-app/src/dist/config/scripts.env

# Override jar path for dev
PATH_TO_JAR="./stroom-proxy/stroom-proxy-app//build/libs/stroom-proxy-app-all.jar" \
  ./dist/set_log_levels.sh "$@"
