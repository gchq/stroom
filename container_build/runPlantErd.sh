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

#SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"

#determine_host_address() {
  #if [ "$(uname)" == "Darwin" ]; then
    ## Code required to find IP address is different in MacOS
    #ip=$(ifconfig | grep "inet " | grep -Fv 127.0.0.1 | awk 'NR==1{print $2}')
  #else
    #local ip_binary
    ## If ip is not on the path (as seems to be the case with ansible) then
    ## try using /sbin instead.
    #if command -v ip > /dev/null; then
      #ip_binary="ip"
    #elif command -v /sbin/ip > /dev/null; then
      #ip_binary="/sbin/ip"
    #else
      #echo
      #echo -e "${RED}ERROR${NC} Unable to locate ${BLUE}ip${NC} command." >&2
      #exit 1
    #fi
    #ip=$( \
      #"${ip_binary}" route get 1 \
      #| awk 'match($0,"src [0-9\\.]+") {print substr($0,RSTART+4,RLENGTH-4)}')
  #fi

  #if [[ ! "${ip}" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
    #echo
    #echo -e "${RED}ERROR${NC} Unable to determine IP address. [${GREEN}${ip}${NC}] is not valid.${NC}" >&2
    #exit 1
  #fi

  #echo "$ip"
#}

main() {
  local image_tag="erd-builder"

  #pushd "${SCRIPT_DIR}" > /dev/null

  local host_ip
  #host_ip="${DOCKER_HOST_IP:-$(determine_host_address)}"
  host_ip="${DOCKER_HOST_IP:-NOT_SET}"

  if [[ "${host_ip}" = "NOT_SET" ]]; then
    echo "ERROR DOCKER_HOST_IP is not set. Set it with IP of the host"
    exit 1
  fi

  # This path may be on the host or in the container depending
  # on where this script is called from
  local_repo_root="$(git rev-parse --show-toplevel)"

  host_abs_repo_dir="${HOST_REPO_DIR:-$local_repo_root}"

  user_id=
  user_id="$(id -u)"

  group_id=
  group_id="$(id -g)"

  echo -e "${GREEN}PWD ${BLUE}$(pwd)${NC}"
  echo -e "${GREEN}Host IP ${BLUE}${host_ip}${NC}"
  echo -e "${GREEN}User ID ${BLUE}${user_id}${NC}"
  echo -e "${GREEN}Group ID ${BLUE}${group_id}${NC}"
  echo -e "${GREEN}Host repo root dir ${BLUE}${host_abs_repo_dir}${NC}"

  mkdir -p ./build
  local build_dir="container_build/build"
  local builder_dir="/builder"
  local puml_output_file="${build_dir}/entity_relationships.puml"

  # Pass in the host ip so the container can see my
  echo -e "${GREEN}Building image ${BLUE}${image_tag}${NC}"
  docker build \
    --tag "${image_tag}" \
    --build-arg "DOCKER_HOST=${host_ip}" \
    "${local_repo_root}/container_build/docker_puml_erd"

  # The image will dump the puml to stdout
  # Use sed to add the puml header/footer and strip unwanted table blocks
  echo -e "${GREEN}Running image ${BLUE}${image_tag}${NC}"

  # For this to work stroom-all-dbs must be running in docker and 
  # the migration must have been run to populate it with the tables

  docker run \
    --rm \
    --tmpfs /tmp \
    --name "erd-builder" \
    "${image_tag}:latest" \
    | sed \
      -r \
      -e $'1i\\\n@startuml' \
      -e $'$a\\\n@enduml' \
      -e '/^entity (v_[a-z_]+|[a-z_]+_schema_history|docstore_history) \{/,/^\}/d' \
    > "${puml_output_file}"

  echo -e "${GREEN}ERD .puml file written to ${BLUE}${puml_output_file}${NC}"

  ls -l "${puml_output_file}"

  echo -e "${GREEN}Generating images from ${BLUE}${puml_output_file}${NC}"

  local output_formats=(
    #"pdf" # PDF doesn't seem to work
    "png"
    "svg"
  )

  for output_format in "${output_formats[@]}"; do
    echo -e "${GREEN}Converting to ${BLUE}${output_format}${GREEN} format${NC}"
    java \
      -jar "${builder_dir}/plantuml.jar" \
      "${puml_output_file}" \
      "-t${output_format}"
  done

  ls -l "${build_dir}/"
}

main "$@"
