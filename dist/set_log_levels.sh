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

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Script to change the log level of multiple class/packages
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

set -e

setup_colours() {
  # Shell Colour constants for use in 'echo -e'
  # e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
  # shellcheck disable=SC2034
  if [ "${MONOCHROME}" = true ]; then
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    BLUE2=''
    NC='' # No Colour
  else
    RED='\033[1;31m'
    GREEN='\033[1;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[1;34m'
    BLUE2='\033[1;34m'
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

send_request() {
  local package_or_class="$1"
  # Convert to uppercase
  local new_log_level="${2^^}"
  local type="$3"
  local extra_args=()
  if [[ "${URL}" == https* ]]; then
    # Use insecure mode if stroom/proxy is running the admin port on https
    extra_args+=("-k")
  fi
  local data="logger=${package_or_class}&level=${new_log_level}"
  local exit_status=0

  echo -e "${GREEN}Setting ${type} ${BLUE}${package_or_class}${NC} to ${YELLOW}${new_log_level}${NC}"
  curl \
    -s \
    -X \
    POST \
    -d "${data}" \
    "${extra_args[@]}" \
    "${URL}" \
    || exit_status=$?

  if [[ "${exit_status}" -ne 0 ]]; then
    echo -e "${RED}ERROR${NC} - Failed to set log level using data ${BLUE}'${data}'${NC}," \
      "retrying with verbose output." >&2
    echo
    curl \
      -s \
      -v \
      -X \
      POST \
      -d "${data}" \
      "${extra_args[@]}" \
      "${URL}" \
      || true
    exit 1
  fi
}

is_valid_package() {
  # Replace . with / as files in zip are like
  # stroom/security/openid/api/OpenIdConfigurationResponse.class
  local package_pattern="${1//./\/}"; shift
  unzip -Z -1 "${PATH_TO_JAR}"  \
    | grep "\.class$" \
    | grep -v "package-info" \
    | grep -q -E "^${package_pattern}(/[a-z]+)*(/|/[A-Z])"
}

find_matching_classes() {
  local partial_class_name="$1"; shift
  # Files in the zip are like
  # stroom/security/openid/api/OpenIdConfigurationResponse.class
  # Convert them to
  # stroom.security.openid.api.OpenIdConfigurationResponse
  unzip -Z -1 "${PATH_TO_JAR}" \
    | grep ".*\.class$" \
    | grep -v "package-info" \
    | sed 's#/#.#g' \
    | sed 's#\.class$##g' \
    | grep -E "(^|\.)${partial_class_name}$" \
    || true
}

echo_usage() {
  echo -e "${GREEN}This script changes the log level(s) for class(es) or" \
    "package(s)${NC}"
  echo -e "${GREEN}NOTE: Once a log level is set for a class/package, it" \
    "can only be changed${NC}"
  echo -e "${GREEN}      and not un-set.${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-m] [-h] packageOrClass1 newLogLevel" \
    "packageOrClassN newLogLevel ...${NC}"
  echo
  echo -e "e.g:   ${BLUE}$0${GREEN} DbUtil DEBUG startup.Config TRACE stroom.security debug${NC}"
  echo -e "${BLUE}stroom.security${NC} - package"
  echo -e "${BLUE}App${NC} - class name (if not unique you will be required to select the desired one)"
  echo -e "${BLUE}startup.App${NC} - partially qualified class name to aid uniqueness"
  echo -e "${BLUE}stroom.startup.App${NC} - fully qualified class name"
  echo
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

main() {

  setup_colours

  # shellcheck disable=SC2155
  readonly script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

  while getopts ":mh" arg; do
    # shellcheck disable=SC2034
    case $arg in
      m )
        MONOCHROME=true
        ;;
      h )
        echo_usage
        exit 0
        ;;
      * )
        invalid_arguments
        ;;  # getopts already reported the illegal option
    esac
  done
  shift $((OPTIND-1)) # remove parsed options and args from $@ list
  #
  # setup the colours again in case -m has been set
  setup_colours

  # should have an arg count that is a multiple of two
  if [ $# -eq 0 ] || [ $(( $# % 2 )) -ne 0 ]; then
    echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
    echo -e "Usage: ${BLUE}$0${GREEN} package_or_class1 new_log_level package_or_classN new_log_level ...${NC}" >&2
    echo -e "e.g:   ${BLUE}$0${GREEN} DbUtil DEBUG startup.Config TRACE stroom.security debug${NC}" >&2
    echo -e "${GREEN}package_or_class${NC} can be any of the following:" >&2
    echo -e "${BLUE}stroom.security${NC} - package" >&2
    echo -e "${BLUE}App${NC} - class name (if not unique you will be required to select the desired one)" >&2
    echo -e "${BLUE}startup.App${NC} - partially qualified class name to aid uniqueness" >&2
    echo -e "${BLUE}stroom.startup.App${NC} - fully qualified class name" >&2
    exit 1
  fi

  local scripts_env_file="${script_dir}/config/scripts.env"
  # Won't exist in dev environment, but that is fine as we can source the
  # appropriate one externally to this script
  if [[ -f "${scripts_env_file}" ]]; then
    # shellcheck disable=SC1090
    source "${scripts_env_file}"
  fi

  readonly URL="${ADMIN_SCHEME:-http}://127.0.0.1:${ADMIN_PORT:-8081}/${ADMIN_PATH:-stroomAdmin}/tasks/log-level"

  echo -e "Using URL ${BLUE}${URL}${NC}"
  echo -e "Using JAR file ${BLUE}${PATH_TO_JAR}${NC}"
  echo

  if [[ ! -f "${PATH_TO_JAR}" ]]; then
    echo -e "${RED}WARNING${NC} - Can't find stroom jar file '${PATH_TO_JAR}'." \
      "Partial matching and class/package validation disabled" >&2
  fi

  #loop through the pairs of args
  while [ $# -gt 0 ]; do
    package_or_class="$1"
    new_log_level="$2"
    debug_value "package_or_class" "${package_or_class}"

    if [[ ! -f "${PATH_TO_JAR}" ]]; then
      # No jar file so just send what we have
      send_request "${package_or_class}" "${new_log_level}" "package"
    elif [[ "${package_or_class}" =~ ^[a-z.]+$ ]]; then
      # is a package
      debug "Is a package"
      if is_valid_package "${package_or_class}"; then
        send_request "${package_or_class}" "${new_log_level}" "package"
      else
        echo -e "\n${RED}ERROR${NC} - No package found matching '${package_or_class}'" >&2
        exit 1
      fi
    else
      # is a class
      debug "Is a class"
      # . => \.
      local pattern="${package_or_class//./\.}"
      # $ => \$
      pattern="${pattern//\$/\\$}"
      # .a.b => a.b
      pattern="${pattern#.}"
      local matching_classes
      matching_classes="$(find_matching_classes "${pattern}" )"

      debug "matching_classes:\n${matching_classes}"

      if [[ -z "${matching_classes}" ]]; then
        echo -e "\n${RED}ERROR${NC} - No classes found matching pattern '(^|\.)${pattern}$'" >&2
        exit 1
      fi

      local class_count
      class_count="$(echo "${matching_classes}" | wc -l )"

      if [[ "${class_count}" -eq 1 ]]; then
        send_request "${matching_classes}" "${new_log_level}" "class"
      else
        local matches_arr=()
        mapfile -t matches_arr <<< "${matching_classes}"

        echo -e "\n${GREEN}Found multiple matching classes. Select the required class${NC}"
        COLUMNS=1
        select user_input in "${matches_arr[@]}"; do
          echo
          send_request "${user_input}" "${new_log_level}" "class"
          break
        done
      fi
    fi

    #bin the two args we have just used
    shift 2
  done

  echo -e "${GREEN}Done${NC}"
}

main "$@"
