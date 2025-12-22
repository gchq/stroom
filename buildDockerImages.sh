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

# This script is all you need to build a local image of stroom and stroom-proxy
# for development purposes.
# Releases to Dockerhub will be done by travis

set -e

#shellcheck disable=SC2034
{
  #Shell Colour constants for use in 'echo -e'
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  LGREY='\e[37m'
  DGREY='\e[90m'
  NC='\033[0m' # No Color
}

MODE_SKIP_GRADLE="skipGradle"
MODE_SKIP_GWT="skipGwt"

show_usage() {
  echo -e "Usage: ${BLUE}$0 [${MODE_SKIP_GRADLE}|${MODE_SKIP_GWT}]${NC}"
}

do_gradle_build() {
  local gradle_args=(
    "clean"
    "build"
    "-x" "test"
  )

  if [ "$mode" = "skipGwt" ]; then
    echo -e "${GREEN}Skipping GWT compile${NC}"
    gradle_args+=( "-x" "gwtCompile" )
  fi

  echo -e "${GREEN}Gradle arguments: ${BLUE}${gradle_args[*]}${NC}"
  # Exclude tests because we want this to be fast.
  # I guess you'd better test the build before releasing.
  ./gradlew "${gradle_args[@]}"
}

do_docker_builds() {
  DOCKER_IMAGE_TAG="local-SNAPSHOT"
  CURRENT_GIT_COMMIT="$(git rev-parse HEAD)"

  echo -e "${GREEN}Building stroom docker image" \
    "${BLUE}gchq/stroom:${DOCKER_IMAGE_TAG}${NC} for commit" \
    "${BLUE}${CURRENT_GIT_COMMIT}${NC}"

  docker build \
    --tag gchq/stroom:${DOCKER_IMAGE_TAG} \
    --build-arg GIT_COMMIT="${CURRENT_GIT_COMMIT}" \
    --build-arg GIT_TAG="${DOCKER_IMAGE_TAG}" \
    ./stroom-app/docker

  echo -e "${GREEN}Building stroom-proxy docker image" \
    "${BLUE}gchq/stroom-proxy:${DOCKER_IMAGE_TAG}${NC} for commit" \
    "${BLUE}${CURRENT_GIT_COMMIT}${NC}"

  docker build \
    --tag gchq/stroom-proxy:${DOCKER_IMAGE_TAG} \
    --build-arg GIT_COMMIT="${CURRENT_GIT_COMMIT}" \
    --build-arg GIT_TAG="${DOCKER_IMAGE_TAG}" \
    ./stroom-proxy/stroom-proxy-app/docker
}

main() {

  if [ "$#" -gt 1 ]; then
    echo -e "${RED}Error${NC}: Invalid arguments"
    show_usage
    exit 1
  elif [ "$#" -eq 1 ] \
    && [[ ! "$1" =~ (${MODE_SKIP_GRADLE}|${MODE_SKIP_GWT}) ]]; then
    echo -e "${RED}Error${NC}: Invalid mode"
    show_usage
    exit 1
  fi

  local mode="$1"

  if [ "$mode" = "skipGradle" ]; then
    echo -e "${GREEN}Skipping gradle build${NC}"
  else
    do_gradle_build
  fi

  if [ $? -ne 0 ]; then
    exit 1
  fi

  do_docker_builds

  echo -e "${GREEN}Done${NC}"
}

main "$@"
