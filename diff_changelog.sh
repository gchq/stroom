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

######################################################
#  Gets all the issues in one CHANGELOG that are not #
#  also in a previous CHNAGELOG.                     #
#  Useful if you want to see what changes are in a   #
#  release branch without merged up changes.         #
######################################################

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

  if [ "${IS_DEBUG:=false}" = true ]; then
    echo -e "${DGREY}DEBUG ${name}: ${value}${NC}"
  fi
}

debug() {
  local str="$1"; shift

  if [ "${IS_DEBUG:=false}" = true ]; then
    echo -e "${DGREY}DEBUG ${str}${NC}"
  fi
}

main() {
  #SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

  setup_echo_colours

  if [ "$#" -ne 2 ]; then
    echo -e "${RED}ERROR${NC}: Invalid arguments"
    echo -e "Usage: ${BLUE}$0 from_branch to_branch${NC}"
    echo -e "E.g:   ${BLUE}$0 7.5 7.6${NC}"
    exit 1
  fi

  local temp_dir
  temp_dir="$(mktemp -d)"

  pushd "${temp_dir}" > /dev/null

  local URL_BASE="https://raw.githubusercontent.com/gchq/stroom"
  local FILE_NAME="CHANGELOG.md"

  local from_branch="$1"; shift
  local to_branch="$1"; shift

  local from_url="${URL_BASE}/${from_branch}/${FILE_NAME}"
  local to_url="${URL_BASE}/${to_branch}/${FILE_NAME}"

  local from_file="${from_branch}.md"
  local to_file="${to_branch}.md"

  # Redirect to stderr so only the change entires go to stdout
  # so we have the option to pipe into something else.
  #echo -e "${GREEN}Fetching ${BLUE}${from_url}${NC}" > /dev/stderr
  wget -q -O "${from_file}" "${from_url}"
  #echo -e "${GREEN}Fetching ${BLUE}${to_url}${NC}" > /dev/stderr
  wget -q -O "${to_file}" "${to_url}"

  #echo > /dev/stderr
  #echo -e "${GREEN}Change entries in ${BLUE}${to_branch}${GREEN}" \
    #"but not in ${BLUE}${from_branch}${NC}" > /dev/stderr
  # Get only issue lines from from_file.
  # Get all lines from to_file not matching lines in the output of above.
  # Of that output get only the issue lines.
  grep "^\* " "${from_file}" \
    | grep \
      --invert-match \
      --fixed-strings \
      --file - \
      "${to_file}" \
    | grep "^\* "

  rm -f "${from_file}"
  rm -f "${to_file}"
  rmdir "${temp_dir}"

  popd > /dev/null
}

main "$@"
