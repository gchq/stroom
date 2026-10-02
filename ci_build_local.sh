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

# This script aims to mimic the github build on a local machine, with
# the exception of releasing artefacts to github and pushing to dockerhub.
# It does the following:
# - Sets up various env vars that the build scripts expect
# - Creates a build dir in /tmp
# - Clones the stroom repo on your current branch into the build dir.
#   NOTE: You MUST have committed and pushed any changes you want to
#   the remote as it is doing a fresh clone from the remote.
# - Runs the ci_build.sh script that would be run by GitHub Actions
#
# This script and the ci_build script run on this host but all parts
# of the build that need anything more than bash and standard shell tools
# are executed in docker containers.

set -eo pipefail

setup_echo_colours() {
  # Exit the script on any error
  set -e

  # shellcheck disable=SC2034
  if [ "${MONOCHROME}" = true ]; then
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    BLUE2=''
    DGREY=''
    NC='' # No Colour
  else
    RED='\033[1;31m'
    GREEN='\033[1;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[1;34m'
    BLUE2='\033[1;34m'
    DGREY='\e[90m'
    NC='\033[0m' # No Colour
  fi
}

debug_value() {
  local name="$1"; shift
  local value="$1"; shift

  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${name}: ${value}${NC}"
  fi
}

debug() {
  local str="$1"; shift

  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${str}${NC}"
  fi
}

main() {
  IS_DEBUG=false
  local runWithNonEmptyBuildDir
  if [[ "${1}" = "force" ]]; then
    runWithNonEmptyBuildDir=true
  else
    runWithNonEmptyBuildDir=false
  fi

  setup_echo_colours


  local stroom_clone_branch
  # get the current branch
  stroom_clone_branch="$(git rev-parse --abbrev-ref HEAD)"

  # shellcheck disable=SC2034
  {
    # IMPORTANT - Stops us trying to push builds to dockerhub
    export LOCAL_BUILD=true

    export BUILD_BRANCH="${stroom_clone_branch}" # Needs to be a proper brach as we git clone this
    export BUILD_DIR="/tmp/stroom_ci_build"
    export BUILD_COMMIT="dummy_commit_hash" # Uses in docker imagge
    export BUILD_IS_PULL_REQUEST="false" # ensures we do docker builds
    # To run with no tag use BUILD_TAG= ./travis.local_build.sh
    export BUILD_TAG="${BUILD_TAG=v7.0-dummy}" # Gets parsed and needs to be set to trigger aspects of the build

    # Settings to run the build according for your local hardware
    export SKIP_TESTS="${SKIP_TESTS:-false}"
    export MAX_WORKERS="${MAX_WORKERS:-6}"
    export GWT_MIN_HEAP="${GWT_MIN_HEAP:-50M}"
    export GWT_MAX_HEAP="${GWT_MAX_HEAP:-4G}"
  }

  if [[ ! -n "${BUILD_TAG}" ]]; then
    echo -e "${YELLOW}WARNING:${NC} BUILD_TAG unset so won't run release" \
      "parts of build${NC}"
  fi

  if [[ -d "${BUILD_DIR}" ]]; then
    if [[ "${runWithNonEmptyBuildDir}" = true ]]; then
      echo -e "${YELLOW}WARNING:${NC} BUILD_DIR ${BLUE}${BUILD_DIR}${NC}" \
        "already exists, running anyway${NC}"
    else
      echo -e "${RED}ERROR:${NC} BUILD_DIR ${BLUE}${BUILD_DIR}${NC}" \
        "already exists, delete or use 'force' argument${NC}"
      exit 1
    fi
  fi

  local stroom_all_dbs_container_id
  stroom_all_dbs_container_id="$( \
    docker ps \
      --quiet \
      --filter "name=stroom-all-dbs")"

  if [[ -n "${stroom_all_dbs_container_id}" ]]; then
      echo -e "${RED}ERROR:${NC} stroom-all-dbs container exists." \
        "Delete it and its volumes before running this script"
      exit 1
  fi

  mkdir -p "${BUILD_DIR}"

  # Make sure we start in the travis build dir
  pushd "${BUILD_DIR}" > /dev/null

  echo -e "${GREEN}Cloning branch ${BLUE}${stroom_clone_branch}${NC}" \
    "into ${BUILD_DIR}${NC}"

  # Clone stroom like travis would
  # Speed up clone with no depth and one branch
  git clone \
    --depth=1 \
    --branch "${stroom_clone_branch}" \
    --single-branch \
    https://github.com/gchq/stroom.git \
    "${BUILD_DIR}"

  echo -e "${GREEN}Running ${BLUE}ci_build.sh${NC}"
  ${BUILD_DIR}/ci_build.sh

  echo -e "${GREEN}Done local travis build${NC}"
}

main "$@"
