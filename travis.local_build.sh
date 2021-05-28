#!/usr/bin/env bash

# This script aims to mimic the travis build on a local machine, with
# the exception of releasing artefacts to github and pushing to dockerhub.
# It does the following:
# - sets up various env vars that the build scripts expect
# - creates a build dir in /tmp
# - clones the stroom repo on your current branch into the build dir
# - runs the travis.before_script.sh script
# - runs the travis.script.sh script
#
# This script and the two travis scripts run on this host but all parts
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

  # IMPORTANT - Stops us trying to push builds to dockerhub
  export LOCAL_BUILD=true

  local stroom_clone_branch
  # get the current branch
  stroom_clone_branch="$(git rev-parse --abbrev-ref HEAD)"

  # shellcheck disable=SC2034
  {
    export TRAVIS_BRANCH="${stroom_clone_branch}" # Needs to be a proper brach as we git clone this
    export TRAVIS_BUILD_DIR="/tmp/travis_build"
    export TRAVIS_BUILD_NUMBER="12345" # Only echoed
    export TRAVIS_COMMIT="dummy_commit_hash" # Only echoed
    export TRAVIS_EVENT_TYPE="push" # Only echoed
    export TRAVIS_PULL_REQUEST="false" # ensures we do docker builds
    # To run with no tag use TRAVIS_TAG= ./travis.local_build.sh
    export TRAVIS_TAG="${TRAVIS_TAG=v7.0-dummy}" # Gets parsed and needs to be set to trigger aspects of the build
    export STROOM_RESOURCES_GIT_TAG="stroom-stacks-v7.0-beta.118"
    export SKIP_TESTS="${SKIP_TESTS:-false}"
    export MAX_WORKERS="${MAX_WORKERS:-6}"
  }

  if [[ ! -n "${TRAVIS_TAG}" ]]; then
    echo -e "${YELLOW}WARNING:${NC} TRAVIS_TAG unset so won't run release parts of build${NC}"
  fi

  if [[ -d "${TRAVIS_BUILD_DIR}" ]]; then
    if [[ "${runWithNonEmptyBuildDir}" = true ]]; then
      echo -e "${YELLOW}WARNING:${NC} TRAVIS_BUILD_DIR ${BLUE}${TRAVIS_BUILD_DIR}${NC}" \
        "already exists, running anyway${NC}"
    else
      echo -e "${RED}ERROR:${NC} TRAVIS_BUILD_DIR ${BLUE}${TRAVIS_BUILD_DIR}${NC}" \
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

  mkdir -p "${TRAVIS_BUILD_DIR}"

  # Make sure we start in the travis build dir
  pushd "${TRAVIS_BUILD_DIR}" > /dev/null

  echo -e "${GREEN}Cloning branch ${BLUE}${stroom_clone_branch}${NC}" \
    "into ${TRAVIS_BUILD_DIR}${NC}"

  # Clone stroom like travis would
  # Speed up clone with no depth and one branch
  git clone \
    --depth=1 \
    --branch "${stroom_clone_branch}" \
    --single-branch \
    https://github.com/gchq/stroom.git \
    "${TRAVIS_BUILD_DIR}"

  # This is in the newly cloned repo
  #./container_build/runInJavaDocker.sh \
    #"./travis.before_script.sh && ./travis.script.sh"

  echo -e "${GREEN}Running ${BLUE}travis.script.sh${NC}"
  ${TRAVIS_BUILD_DIR}/travis.script.sh

  echo -e "${GREEN}Done local travis build${NC}"
}

main "$@"
