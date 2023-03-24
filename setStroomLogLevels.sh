#!/usr/bin/env bash

set -e

# shellcheck disable=SC1091
source ./stroom-app/src/dist/config/scripts.env

# Override jar path for dev
PATH_TO_JAR="./stroom-app/build/libs/stroom-app-all.jar" \
  ./dist/set_log_levels.sh "$@"
