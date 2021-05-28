#!/usr/bin/env bash
set -euo pipefail
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

docker_login() {
  # The username and password are configured in the travis gui
  if [[ ! -n "${LOCAL_BUILD}" ]]; then
    # Docker login stores the creds in a file so check it to
    # see if we are already logged in
    local dockerConfigFile="${HOME}/.docker/config.json"
    if [[ -f "${dockerConfigFile}" ]] \
      && grep -q "index.docker.io" "${dockerConfigFile}"; then

      echo -e "Already logged into docker"
    else
      echo -e "Logging in to Docker"
      echo "$DOCKER_PASSWORD" \
        | docker login -u "$DOCKER_USERNAME" --password-stdin >/dev/null 2>&1
    fi
  else
    echo -e "${YELLOW}LOCAL_BUILD set so skipping docker login${NC}"
  fi
}

if [ "$#" -ne 1 ]; then
  echo -e "${RED}ERROR: Invalid arguments.${NC}"
  echo -e "Usage: $0 bash_command"
  echo -e "e.g:   $0 ./path/to/script.sh arg1 arg2"
  echo -e "or:    $0 bash  # for a bash prompt"
  echo -e "Commands are relative to the repo root."
  echo -e "Commands/scripts with args must be quoted as a whole."
  exit 1
fi

bash_cmd="$1"

if [ "${bash_cmd}" = "bash" ]; then
  run_cmd=( "bash" )
else
  run_cmd=( "bash" "-c" "${bash_cmd[*]}" )
fi

user_id=
user_id="$(id -u)"

group_id=
group_id="$(id -g)"

image_tag="stroom-ui-builder"

# This path may be on the host or in the container depending
# on where this script is called from
local_repo_root="$(git rev-parse --show-toplevel)"

# This script may be running inside a container so first check if
# the env var has been set in the container
#if [ -n "${HOST_REPO_DIR}" ]; then
  #host_abs_repo_dir="${HOST_REPO_DIR}"
#else
  #host_abs_repo_dir="${local_repo_root}"
#fi
host_abs_repo_dir="${HOST_REPO_DIR:-$local_repo_root}"

dest_dir="/builder/shared"

echo -e "${GREEN}HOME ${BLUE}${HOME}${NC}"
echo -e "${GREEN}User ID ${BLUE}${user_id}${NC}"
echo -e "${GREEN}Group ID ${BLUE}${group_id}${NC}"
echo -e "${GREEN}Host repo root dir ${BLUE}${host_abs_repo_dir}${NC}"

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

# Pass in the location of the repo root on the docker host
# which may have been passed down to us or we have determined
echo -e "${GREEN}Building image ${BLUE}${image_tag}${NC}"
docker build \
  --tag "${image_tag}" \
  --build-arg "USER_ID=${user_id}" \
  --build-arg "GROUP_ID=${group_id}" \
  --build-arg "HOST_REPO_DIR=${host_abs_repo_dir}" \
  "${local_repo_root}/container_build/docker_node"

  #-interactive \
  #-tty \
  #--rm \


if [ -t 1 ]; then 
  # In a terminal
  tty_args=( "--tty" "--interactive" )
else
  tty_args=()
fi

# Mount the whole repo into the container so we can run the build
# The mount src is on the host file system
# "${tty_args[@]+"${tty_args[@]}"}" The + thing is so it does complain
# of being unbound when set -u is on
# Need to pass in docker creds in case the container needs to do authenticated
# pulls/pushes with dockerhub
# shellcheck disable=SC2145
echo -e "${GREEN}Running image ${BLUE}${image_tag}${NC} with command" \
  "${BLUE}${run_cmd[@]}${NC}"
docker run \
  "${tty_args[@]+"${tty_args[@]}"}" \
  --rm \
  --tmpfs /tmp \
  --mount "type=bind,src=${host_abs_repo_dir},dst=${dest_dir}" \
  --volume builder-home-dir-vol:/home/node \
  --workdir "${dest_dir}" \
  --name "node-build-env" \
  --env "BUILD_VERSION=${BUILD_VERSION:-SNAPSHOT}" \
  --env "DOCKER_USERNAME=${DOCKER_USERNAME}" \
  --env "DOCKER_PASSWORD=${DOCKER_PASSWORD}" \
  "${image_tag}" \
  "${run_cmd[@]}"
  #bash
  #bash -c 'echo $PWD; nvm --version; node --version; npm --version; npx --version; yarn --version; ./yarnBuild.sh'


#  --mount "type=bind,src=$(pwd),dst=${dest_dir}" \
