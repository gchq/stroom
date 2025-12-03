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

# This script is used to run commands inside a docker container that
# has been set up as a java build environment. It will bind mount
# the root of the git repo you are currently in into the container, so
# your pwd must be somewhere inside the desired repo.
# It comes with some pre-baked commands such as ERD and GRADLE_BUILD

# Script
set -eo pipefail
IFS=$'\n\t'

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

docker_login() {
  # The username and password are configured in the travis gui
  if [[ -n "${DOCKER_USERNAME}" ]] && [[ -n "${DOCKER_PASSWORD}" ]]; then
    # Docker login stores the creds in a file so check it to
    # see if we are already logged in
    #local dockerConfigFile="${HOME}/.docker/config.json"
    #if [[ -f "${dockerConfigFile}" ]] \
      #&& grep -q "index.docker.io" "${dockerConfigFile}"; then

      #echo -e "Already logged into docker"
    #else
      echo -e "Logging in to Docker (if this fails, have you provided the" \
        "correct docker creds)"
      # Login is idempotent
      echo "${DOCKER_PASSWORD}" \
        | docker login \
          -u "${DOCKER_USERNAME}" \
          --password-stdin \
          >/dev/null 2>&1
      echo -e "Successfully logged in to docker"
    #fi
  else
    echo -e "${YELLOW}DOCKER_USERNAME and/or DOCKER_PASSWORD not set so" \
      "skipping docker login. Pulls/builds will be un-authenticated and rate" \
      "limited, pushes will fail.${NC}"
  fi
}

# We may be inside a container so the host ip may have been passed in
host_ip="${DOCKER_HOST_IP:-$(determine_host_address)}"

run_cmd=()

if [[ $# -lt 1 ]]; then
  echo -e "${RED}ERROR: Invalid arguments.${NC}"
  echo -e "Usage: $0 bash_command"
  echo -e "e.g:   $0 \"./some_path/a_script.sh arg1 arg2\""
  echo -e "or:    $0 bash  # for a bash prompt in the container"
  echo -e "or:    $0 ERD  # To run the entity relationship diagram build"
  echo -e "or:    $0 GRADLE_BUILD  # To run the full gradle build"
  echo -e "or:    $0 MIGRATE  # To run the db migration"
  echo -e "or:    $0 SVG  # To convert all .puml files to .puml.svg"
  echo -e "Commands are relative to the repo root."
  echo -e "Commands/scripts with args must be quoted as a whole."
  exit 1
else
  if [[ $# -eq 1 ]] && [[ "$1" = "bash" ]]; then

    run_cmd=( "bash" )
  elif [[ $# -eq 1 ]] && [[ "$1" = "ERD" ]]; then
    # Generate an entity relationship diagram from an existing DB
    run_cmd=( \
      "bash" \
      "-c"  \
      "./container_build/runPlantErd.sh" \
    )
  elif [[ $# -eq 1 ]] && [[ "$1" = "GRADLE_BUILD" ]]; then
    # Run the full CI gradle build
    run_cmd=( \
      "bash" \
      "-c"  \
      "SKIP_TESTS=\"${SKIP_TESTS:-false}\" MAX_WORKERS=\"${MAX_WORKERS:-6}\" GWT_MIN_HEAP=\"${GWT_MIN_HEAP:-50M}\" GWT_MAX_HEAP=\"${GWT_MAX_HEAP:-2G}\" ./container_build/gradleBuild.sh" \
    )
  elif [[ $# -eq 1 ]] && [[ "$1" = "GRADLE_COMPILE" ]]; then
    # Run the full CI gradle build
    run_cmd=( \
      "bash" \
      "-c"  \
      "MAX_WORKERS=\"${MAX_WORKERS:-6}\" ./container_build/gradleCompile.sh" \
    )
  elif [[ $# -eq 1 ]] && [[ "$1" = "MIGRATE" ]]; then
    # Run the db migration against a running db instance
    # DB is in a sibling container so need to force it to use the IP instead of localhost
    run_cmd=( \
      "bash" \
      "-c"  \
      "pwd; export STROOM_JDBC_DRIVER_URL=\"jdbc:mysql://${host_ip}:3307/stroom?useUnicode=yes&characterEncoding=UTF-8\"; java -jar ./stroom-app/build/libs/stroom-app-all.jar migrate ./local.yml" \
    )
  elif [[ $# -ge 1 ]] && [[ "$1" = "SVG" ]]; then
    run_cmd=( \
      "bash" \
      "-c"  \
      "/builder/convert_puml_files.sh ${2:-/builder/shared}" \
    )
  else
    run_cmd=( \
      "bash" \
      "-c" \
      "$1" \
    )
  fi
fi

user_id=
user_id="$(id -u)"

group_id=
group_id="$(id -g)"

image_tag="java-build-env"

# This path may be on the host or in the container depending
# on where this script is called from
local_repo_root="$(git rev-parse --show-toplevel)"

# This script may be running inside a container so first check if
# the env var has been set in the container
host_abs_repo_dir="${HOST_REPO_DIR:-$local_repo_root}"

dest_dir="/builder/shared"

docker_group_id="$(stat -c '%g' /var/run/docker.sock)"

echo -e "${GREEN}HOME ${BLUE}${HOME}${NC}"
echo -e "${GREEN}Host IP ${BLUE}${host_ip}${NC}"
echo -e "${GREEN}User ID ${BLUE}${user_id}${NC}"
echo -e "${GREEN}Group ID ${BLUE}${group_id}${NC}"
echo -e "${GREEN}Host repo root dir ${BLUE}${host_abs_repo_dir}${NC}"
echo -e "${GREEN}Docker group id ${BLUE}${docker_group_id}${NC}"

# Create a persistent vol for the home dir, idempotent
docker volume create builder-home-dir-vol

# So we are not rate limited, login before doing the build as this
# will pull images
docker_login

# TODO consider pushing the built image to dockerhub so we can
# reuse it for better performance.  See here
# https://github.com/i3/i3/blob/42f5a6ce479968a8f95dd5a827524865094d6a5c/.travis.yml
# https://github.com/i3/i3/blob/42f5a6ce479968a8f95dd5a827524865094d6a5c/travis/ha.sh
# for an example of how to hash the build context so we can pull or push
# depending on whether there is already an image for the hash.

echo -e "${GREEN}Building image ${BLUE}${image_tag}${NC}"
docker build \
  --tag "${image_tag}" \
  --build-arg "USER_ID=${user_id}" \
  --build-arg "GROUP_ID=${group_id}" \
  --build-arg "HOST_REPO_DIR=${host_abs_repo_dir}" \
  --build-arg "DOCKER_HOST_IP=${host_ip}" \
  --build-arg "DOCKER_GROUP_ID=${docker_group_id}" \
  "${local_repo_root}/container_build/docker_java"


  #--workdir "${dest_dir}" \

if [ -t 1 ]; then
  # In a terminal
  tty_args=( "--tty" "--interactive" )
else
  tty_args=()
fi

# Mount the whole repo into the container so we can run the build
# The mount src is on the host file system
# group-add gives the permission to interact with the docker cli
# docker.sock allows use to interact with the docker cli
# Need :exec on /tmp else LMDB complains with link errors
# Need to pass in docker creds in case the container needs to do authenticated
# pulls/pushes with dockerhub
# shellcheck disable=SC2145
echo -e "${GREEN}Running image ${BLUE}${image_tag}${NC} with command" \
  "${BLUE}${run_cmd[@]}${NC}"

docker run \
  "${tty_args[@]+"${tty_args[@]}"}" \
  --rm \
  --tmpfs /tmp:exec \
  --mount "type=bind,src=${host_abs_repo_dir},dst=${dest_dir}" \
  --volume builder-home-dir-vol:/home/builder \
  --group-add "${docker_group_id}" \
  --volume /var/run/docker.sock:/var/run/docker.sock \
  --read-only \
  --name "java-build-env" \
  --env "BUILD_VERSION=${BUILD_VERSION:-SNAPSHOT}" \
  --env "DOCKER_USERNAME=${DOCKER_USERNAME}" \
  --env "DOCKER_PASSWORD=${DOCKER_PASSWORD}" \
  "${image_tag}" \
  "${run_cmd[@]}"

