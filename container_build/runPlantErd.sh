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

run_java_build() {

  echo -e "${GREEN}Running java build to create app jar file${NC}"

  # Don't need any ui stuff, just the app fat jar
  ./gradlew \
    --no-daemon \
    shadowJar \
    -x test \
    -x gwtCompile

  echo -e "${GREEN}Java build complete${NC}"
}

run_db_migration() {
  echo -e "${GREEN}Running database migration${NC}"

  # DB is in a sibling container so need to force it to use the IP instead of localhost
  export STROOM_JDBC_DRIVER_URL="jdbc:mysql://${host_ip}:3307/stroom?useUnicode=yes&characterEncoding=UTF-8"
  java \
    -jar \
    ./stroom-app/build/libs/stroom-app-all.jar \
    migrate \
    ./local.yml
  echo -e "${GREEN}Migration complete${NC}"
}

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
  echo -e "${GREEN}Build version ${BLUE}${BUILD_VERSION}${NC}"

  local build_dir="container_build/build"
  local builder_dir="/builder"
  local puml_output_file="${build_dir}/entity-relationships-${BUILD_VERSION:-SNAPSHOT}.puml"
  # The app-all jar is not versioned even if you pass -Pversion to gradle
  local app_jar_file="stroom-app/build/libs/stroom-app-all.jar"

  mkdir -p "${build_dir}"

  if [ -f "${app_jar_file}" ]; then
    echo -e "${GREEN}Found stroom app jar file ${BLUE}${app_jar_file}${GREEN}" \
      "so won't run java build${NC}"
  else
    echo -e "${GREEN}Could not find stroom app jar file" \
      "${BLUE}${app_jar_file}${GREEN} so run java build${NC}"
    run_java_build
  fi

  run_db_migration

  # Pass in the host ip so the container can see my
  echo -e "${GREEN}Building image ${BLUE}${image_tag}${NC}"
  docker build \
    --tag "${image_tag}" \
    --build-arg "DOCKER_HOST=${host_ip}" \
    "${local_repo_root}/container_build/docker_puml_erd"

  echo -e "${GREEN}Running image ${BLUE}${image_tag}${NC}"

  # For this to work stroom-all-dbs must be running in docker and
  # the migration must have been run to populate it with the tables

  # The image will dump the puml to stdout
  # Use sed to add the puml header/footer and strip unwanted table blocks
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

  if [ ! -s "${puml_output_file}" ]; then
    echo -e "${RED}ERROR${NC}: Generated file ${puml_output_file} is empty.\nIs the DB empty?${NC}"
    exit 1
  fi

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

  echo -e "${GREEN}Generated files:${NC}"
  ls -l "${build_dir}/"
  echo -e "${GREEN}Generation complete${NC}"
}

main "$@"
