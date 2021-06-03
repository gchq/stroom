#!/usr/bin/env bash

# Simple script to echo pairs of variable names and their values,
# e.g. ./echo_variables.sh HOME $HOME "Docker version" "$(docker --version)"
# results in:
#   HOME:                                      [/home/dev]
#   Docker version:                            [Docker version 20.10.6, build 370c289]

set -eo pipefail
IFS=$'\n\t'

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

  local max_padding_str='                                        '

  while (( "$#" >= 2 )); do
    local name="${1}"
    local value="${2}"
    shift 2
    printf \
      "${GREEN}%s${NC}: %s [${BLUE}%s${NC}]\n" \
      "${name}" \
      "${max_padding_str:${#name}}" \
      "${value}"
  done
}

main "$@"
