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

# Runs the full CI gradle build in a staged approach
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
  "-Dorg.gradle.daemon=false" # daemon no good in a container
  "-Dorg.gradle.parallel=true"
  "-Dorg.gradle.workers.max=${MAX_WORKERS:-6}"
  "-Dorg.gradle.configureondemand=true"
  "--console=plain"
)

GWT_ARGS=(
  "-PgwtCompilerWorkers=${MAX_WORKERS:-6}"
  "-PgwtCompilerMinHeap=${GWT_MIN_HEAP:-50M}"
  "-PgwtCompilerMaxHeap=${GWT_MAX_HEAP:-2G}"
)

determine_host_address() {
  if [ "$(uname)" == "Darwin" ]; then
    # Code required to find IP address is different in MacOS
    ip=$(ifconfig | grep "inet " | grep -Fv 127.0.0.1 | awk 'NR==1{print $2}')
  else
    local ip_binary
    # If ip is not on the path (as seems to be the case with ansible) then
    # try using /sbin instead.
    if command -v ip > /dev/null; then
      ip_binary="ip"
    elif command -v /sbin/ip > /dev/null; then
      ip_binary="/sbin/ip"
    else
      echo
      echo -e "${RED}ERROR${NC} Unable to locate ${BLUE}ip${NC} command." >&2
      exit 1
    fi
    ip=$( \
      "${ip_binary}" route get 1 \
      | awk 'match($0,"src [0-9\\.]+") {print substr($0,RSTART+4,RLENGTH-4)}')
  fi

  if [[ ! "${ip}" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
    echo
    echo -e "${RED}ERROR${NC} Unable to determine IP address. [${GREEN}${ip}${NC}] is not valid.${NC}" >&2
    exit 1
  fi

  echo "$ip"
}

main() {

  # We want to run from the repo root
  local local_repo_root
  local_repo_root="$(git rev-parse --show-toplevel)"
  pushd "${local_repo_root}" > /dev/null

  # When we are in a container localhost is no good for connecting to the db
  # so use the ip
  host_ip="${DOCKER_HOST_IP:-$(determine_host_address)}"
  export STROOM_JDBC_DRIVER_HOST="${host_ip}"
  echo -e "${GREEN}Setting STROOM_JDBC_DRIVER_HOST to ${BLUE}${host_ip}${NC}"

  echo "::group::Gradle clean"
  echo -e "${GREEN}Clean${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    clean
  echo "::endgroup::"

  if [[ "${SKIP_TESTS:-false}" = true ]]; then
    echo -e "${YELLOW}Skipping tests${NC}"
    test_args=( "-x" "test" )
  else
    test_args=( "test" )
  fi

  # TODO remove this block, just here to diagnose issues in proxy tests
  #echo "::group::Proxy tests build"
  #echo -e "${GREEN}Do the basic java build${NC}"
  #./gradlew \
    #"${GRADLE_ARGS[@]}" \
    #--info \
    #--scan \
    #--stacktrace \
    #-PdumpFailedTestXml=true \
    #:stroom-proxy:stroom-proxy-app:test
  #echo "::endgroup::"

  # Do the gradle build
  # Use custom gwt compile jvm settings to avoid blowing the ram limit in
  # travis. At time of writing a sudo VM in travis has 7.5gb ram.
  # Each work will chew up the maxHeap value and we have to allow for
  # our docker services as well.
  # Don't clean as this is a fresh clone and clean will wipe the cached
  # content pack zips
  # Fully qualify the shadowJar tasks as we want to run lucene553 shadowJar
  echo "::group::Basic Java build"
  echo -e "${GREEN}Do the basic java build${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    --scan \
    --stacktrace \
    -PdumpFailedTestXml=true \
    -Pversion="${BUILD_VERSION:-SNAPSHOT}" \
    build \
    "${test_args[@]}" \
    -x :stroom-app:shadowJar \
    -x :stroom-proxy:stroom-proxy-app:shadowJar \
    -x :stroom-headless:shadowJar \
    -x resolve \
    -x copyFilesForStroomDockerBuild \
    -x copyFilesForProxyDockerBuild \
    -x buildDistribution
  echo "::endgroup::"

  echo "::group::GWT UI build"
  echo -e "${GREEN}Do the GWT UI build${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    --scan \
    --stacktrace \
    "${GWT_ARGS[@]}" \
    stroom-app-gwt:gwtCompile
  echo "::endgroup::"

  echo "::group::Dashboard GWT UI build"
  echo -e "${GREEN}Do the dashboard GWT UI build${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    --scan \
    --stacktrace \
    "${GWT_ARGS[@]}" \
    stroom-dashboard-gwt:gwtCompile
  echo "::endgroup::"

  # Make the distribution.
  # Fully qualify the shadowJar tasks as have run lucene553 shadowJar
  echo "::group::Distribution build"
  echo -e "${GREEN}Build the distribution with version" \
    "${BLUE}${BUILD_VERSION:-SNAPSHOT}${NC}"
  ./gradlew \
    "${GRADLE_ARGS[@]}" \
    --scan \
    --stacktrace \
    -PdumpFailedTestXml=true \
    -Pversion="${BUILD_VERSION:-SNAPSHOT}" \
    buildDistribution \
    shadowJar \
    copyFilesForStroomDockerBuild \
    copyFilesForProxyDockerBuild \
    -x test \
    -x stroom-app-gwt:gwtCompile \
    -x stroom-dashboard-gwt:gwtCompile
  echo "::endgroup::"

  echo -e "${GREEN}Done${NC}"
}

# Get a time for the whole build
time main "$@"
