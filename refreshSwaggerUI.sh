#!/usr/bin/env bash

set -e

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

  setup_echo_colours

  if [ "$#" -ne 1 ]; then
    echo -e "${RED}ERROR${NC}: Invalid arguments${NC}"
    echo -e "${NC}Usage${NC}:  $0 version_number${NC}"
    exit 1
  fi

  local version="$1"; shift

  local url="https://github.com/swagger-api/swagger-ui/archive/v${version}.tar.gz"
  local swagger_ui_dir="./stroom-app/src/main/resources/ui/swagger-ui"
  
  # The OR to deal with fruit based devices
  temp_dir=$(mktemp -d 2>/dev/null || mktemp -d -t 'swaggerui')

  pushd "${temp_dir}" >/dev/null

  echo -e "${GREEN}Downloading Swagger UI distribution from ${BLUE}${url}${NC}"
  wget "${url}"

  echo -e "${GREEN}Extracting distribution to ${BLUE}${temp_dir}${NC}"
  tar -xf "v${version}.tar.gz"

  popd >/dev/null

  if [ ! -d "${swagger_ui_dir}" ]; then
    echo -e "${RED}ERROR${NC}: Unable to find ${swagger_ui_dir}${NC}"
    exit 1
  fi

  echo -e "${GREEN}Deleting existing swagger UI from ${BLUE}${swagger_ui_dir}${NC}"
  rm -rf "${swagger_ui_dir}"

  mkdir -p "${swagger_ui_dir}"

  echo -e "${GREEN}Copying swagger UI to ${BLUE}${swagger_ui_dir}${NC}"
  cp "${temp_dir}/swagger-ui-${version}/dist/"* "${swagger_ui_dir}/"

  # replace the default swagger spec url in swagger UI
  sed \
    -i \
    's#url: ".*"#url: "https://localhost:8080/stroom/ui/swagger.json"#g' \
    "${swagger_ui_dir}/index.html"

  rm -rf "${temp_dir}"
  
}

main "$@"
