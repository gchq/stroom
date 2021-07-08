#!/usr/bin/env bash

set -eo pipefail
shopt -s globstar

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

  if [[ $# -lt 1 ]]; then
    echo -e "${RED}ERROR: Invalid arguments.${NC}"
    echo -e "Usage: $0 dir_to_scan|file_to_convert"
    echo -e "e.g:   $0 /builder/shared"
    echo -e "e.g:   $0 /builder/shared/sequence.puml"
    exit 1
  fi

  local dir="${1%/}"

  if [[ ! -d "${dir}" ]]; then
    echo -e "${RED}ERROR: File or directory ${dir} does not exist.${NC}"
    exit 1
  fi

  echo -e "${GREEN}Converting .puml files in ${BLUE}${dir}${NC}"
  java \
    -jar ../plantuml.jar \
    -svg \
    "${dir}"/**/*.puml
}

main "$@"
