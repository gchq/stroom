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

# Usage: ./build.sh
#        MAX_WORKERS=6 SKIP_TESTS=true ./build.sh

set -euo pipefail

# Shell Colour constants for use in 'echo -e'
# e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
# shellcheck disable=SC2034
{
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  NC='\033[0m' # No Colour
}

# -Dorg.gradle.caching=true
GRADLE_ARGS=(
  "-Dorg.gradle.daemon=true"
  "-Dorg.gradle.parallel=true"
  "-Dorg.gradle.workers.max=${MAX_WORKERS:-24}"
  "-Dorg.gradle.configureondemand=true"
)

GWT_ARGS=(
  "-PgwtCompilerWorkers=5"
  "-PgwtCompilerMinHeap=50M"
  "-PgwtCompilerMaxHeap=4G"
)

main() {

  echo -e "${GREEN}Clean${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    clean

  if [[ "${SKIP_TESTS:-false}" = true ]]; then
    echo -e "${YELLOW}Skipping tests${NC}"
    test_args=( "-x" "test" )
  else
    test_args=( "test" )
  fi

  # Do the gradle build
  # Use custom gwt compile jvm settings to avoid blowing the ram limit in
  # travis. At time of writing a sudo VM in travis has 7.5gb ram.
  # Each work will chew up the maxHeap value and we have to allow for
  # our docker services as well.
  # Don't clean as this is a fresh clone and clean will wipe the cached
  # content pack zips
  echo -e "${GREEN}Do the basic java build${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    --scan \
    --stacktrace \
    -PdumpFailedTestXml=true \
    -Pversion="${TRAVIS_TAG:-SNAPSHOT}" \
    build \
    "${test_args[@]}" \
    -x shadowJar \
    -x resolve \
    -x copyFilesForStroomDockerBuild \
    -x copyFilesForProxyDockerBuild \
    -x buildDistribution

  echo -e "${GREEN}Do the UI build${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    --scan \
    --stacktrace \
    "${GWT_ARGS[@]}" \
    stroom-app-gwt:gwtCompile \
    stroom-dashboard-gwt:gwtCompile

  ## Compile the application GWT UI
  #echo -e "${GREEN}Do the GWT app compile${NC}"
  #./gradlew \
  #  "${GRADLE_ARGS[@]}" \
  #  --scan \
  #  --stacktrace \
  #  "${GWT_ARGS[@]}" \
  #  stroom-app-gwt:gwtCompile
  #
  ## Compile the dashboard GWT UI
  #echo -e "${GREEN}Do the GWT dashboard compile${NC}"
  #./gradlew \
  #  "${GRADLE_ARGS[@]}" \
  #  --scan \
  #  --stacktrace \
  #  "${GWT_ARGS[@]}" \
  #  stroom-dashboard-gwt:gwtCompile

  # Make the distribution.
  echo -e "${GREEN}Build the distribution${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    --scan \
    --stacktrace \
    -PdumpFailedTestXml=true \
    -Pversion="${TRAVIS_TAG:-SNAPSHOT}" \
    shadowJar \
    buildDistribution \
    copyFilesForStroomDockerBuild \
    copyFilesForProxyDockerBuild \
    -x test \
    -x stroom-app-gwt:gwtCompile \
    -x stroom-dashboard-gwt:gwtCompile

  echo -e "${GREEN}Done${NC}"
}

# Get a time for the whole build
time main "$@"
