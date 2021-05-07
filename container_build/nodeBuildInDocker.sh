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

if [ "$#" -ne 1 ]; then
  echo "ERROR: Invalid arguments"
  echo "Usage: $0 \"path/to/script.sh\""
  exit 1
fi

bash_cmd="$1"

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

echo -e "${GREEN}User ID ${user_id}${NC}"
echo -e "${GREEN}Group ID ${group_id}${NC}"
echo -e "${GREEN}Host repo root dir ${host_abs_repo_dir}${NC}"

# Create a persistent vol for the home dir, idempotent
docker volume create builder-home-dir-vol

# Pass in the location of the repo root on the docker host
# which may have been passed down to us or we have determined
docker build \
  --tag "${image_tag}" \
  --build-arg "USER_ID=${user_id}" \
  --build-arg "GROUP_ID=${group_id}" \
  --build-arg "HOST_REPO_DIR=${host_abs_repo_dir}" \
  "${local_repo_root}/container_build/docker_node"

  #-interactive \
  #-tty \
  #--rm \

# Mount the whole repo into the container so we can run the build
# The mount src is on the host file system
docker run \
  --rm \
  --mount "type=bind,src=${host_abs_repo_dir},dst=${dest_dir}" \
  --volume builder-home-dir-vol:/home/node \
  --workdir "${dest_dir}" \
  "${image_tag}" \
  bash -c "${bash_cmd}"
  #bash
  #bash -c 'echo $PWD; nvm --version; node --version; npm --version; npx --version; yarn --version; ./yarnBuild.sh'


#  --mount "type=bind,src=$(pwd),dst=${dest_dir}" \
