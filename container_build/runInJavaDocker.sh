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

host_ip="$(determine_host_address)"

run_cmd=()

if [[ $# -ne 1 ]]; then
  echo -e "${RED}ERROR: Invalid arguments.${NC}"
  echo -e "Usage: $0 bash_command"
  echo -e "e.g:   $0 \"./some_path/a_script.sh arg1 arg2\""
  echo -e "or:    $0 bash  # for a bash prompt"
  echo -e "Commands are relative to the repo root."
  echo -e "Commands/scripts with args must be quoted as a whole."
  exit 1
else
  if [[ $# -eq 1 ]] && [[ "$1" = "bash" ]]; then
    run_cmd=( "bash" )
  elif [[ $# -eq 1 ]] && [[ "$1" = "ERD" ]]; then
    run_cmd=( "bash" "-c"  "./container_build/runPlantErd.sh" )
  elif [[ $# -eq 1 ]] && [[ "$1" = "GRADLE_BUILD" ]]; then
    run_cmd=( "bash" "-c"  "SKIP_TESTS=\"${SKIP_TESTS:-false}\" MAX_WORKERS=\"${MAX_WORKERS:-6}\" ./container_build/gradleBuild.sh" )
  elif [[ $# -eq 1 ]] && [[ "$1" = "MIGRATE" ]]; then
    # DB is in a sibling container so need to force it to use the IP instead of localhost
    run_cmd=( "bash" "-c"  "pwd; export STROOM_JDBC_DRIVER_URL=\"jdbc:mysql://${host_ip}:3307/stroom?useUnicode=yes&characterEncoding=UTF-8\"; java -jar ./stroom-app/build/libs/stroom-app-all.jar migrate ./local.yml" )
  else
    run_cmd=( "bash" "-c" "$1" )
  fi
fi

user_id=
user_id="$(id -u)"

group_id=
group_id="$(id -g)"

image_tag="stroom-builder"

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

docker_group_id="$(stat -c '%g' /var/run/docker.sock)"

echo -e "${GREEN}Host IP ${BLUE}${host_ip}${NC}"
echo -e "${GREEN}User ID ${BLUE}${user_id}${NC}"
echo -e "${GREEN}Group ID ${BLUE}${group_id}${NC}"
echo -e "${GREEN}Host repo root dir ${BLUE}${host_abs_repo_dir}${NC}"
echo -e "${GREEN}Docker group id ${BLUE}${docker_group_id}${NC}"

# Create a persistent vol for the home dir, idempotent
docker volume create builder-home-dir-vol

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
  --name "stroom-builder" \
  --env "BUILD_VERSION=${BUILD_VERSION:-SNAPSHOT}" \
  "${image_tag}" \
  "${run_cmd[@]}"

  #bash -c "pwd; SKIP_TESTS=\"${SKIP_TESTS:-false}\" MAX_WORKERS=\"${MAX_WORKERS:-6}\" ./container_build/gradleBuild.sh"

  #bash
  #bash -c 'echo $PWD; nvm --version; node --version; npm --version; npx --version; yarn --version; ./yarnBuild.sh'


  #--mount "type=bind,src=$HOME/.gradle,dst=/home/builder/.gradle" \
  #--mount "type=bind,src=$HOME/.m2,dst=/home/builder/.m2" \
