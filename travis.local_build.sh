#!/usr/bin/env bash

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

  # shellcheck disable=SC2034
  {
    TRAVIS_BRANCH="7.0" # Needs to be a proper brach as we git clone this
    TRAVIS_BUILD_DIR="/tmp/travis_build"
    TRAVIS_BUILD_NUMBER="12345"
    TRAVIS_COMMIT="dummy_commit_hash"
    TRAVIS_EVENT_TYPE="push"
    TRAVIS_PULL_REQUEST="false"
    TRAVIS_TAG="v7.0-dummy" # Gets parsed and needs to be set to trigger aspects of the build
    STROOM_RESOURCES_GIT_REF="stroom-stacks-v7.0-beta.118"
  }

  # IMPORTANT - Stops us trying to push builds to dockerhub
  export LOCAL_BUILD=true

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

  mkdir -p "${TRAVIS_BUILD_DIR}"

  pushd "${TRAVIS_BUILD_DIR}" > /dev/null

  echo -e "${GREEN}Cloning strrom repo into ${TRAVIS_BUILD_DIR}${NC}"

  # Don't need any history
  git clone \
    --depth=1 \
    --branch "${TRAVIS_BRANCH}" \
    --single-branch \
    https://github.com/gchq/stroom.git \
    "${TRAVIS_BUILD_DIR}"

  echo -e "${GREEN}Running ${BLUE}travis.before_script.sh${NC}"
  ./travis.before_script.sh

}

main "$@"
