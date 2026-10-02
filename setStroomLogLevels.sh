#!/usr/bin/env bash

#
# Copyright 2016-2025 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

# Allow the caller to force the admin port, e.g. for devving
# with >1 node on a host
ADMIN_PORT_OVERRIDE=$ADMIN_PORT

# shellcheck disable=SC1091
source ./stroom-app/src/dist/config/scripts.env

ADMIN_PORT=${ADMIN_PORT_OVERRIDE:-ADMIN_PORT}

# Override jar path for dev
PATH_TO_JAR="./stroom-app/build/libs/stroom-app-all.jar" \
  ./dist/set_log_levels.sh "$@"
