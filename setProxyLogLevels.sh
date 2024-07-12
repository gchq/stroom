#!/usr/bin/env bash

set -e

# Allow the caller to force the admin port, e.g. for devving
# with >1 node on a host
ADMIN_PORT_OVERRIDE=$ADMIN_PORT

# shellcheck disable=SC1091
source ./stroom-proxy/stroom-proxy-app/src/dist/config/scripts.env

ADMIN_PORT=${ADMIN_PORT_OVERRIDE:-ADMIN_PORT}

# Override jar path for dev
PATH_TO_JAR="./stroom-proxy/stroom-proxy-app//build/libs/stroom-proxy-app-all.jar" \
  ./dist/set_log_levels.sh "$@"
