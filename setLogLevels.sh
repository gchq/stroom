#!/usr/bin/env bash

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Script to change the log level of multiple class/packages
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

set -e

#Shell Colour constants for use in 'echo -e'
#e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
# shellcheck disable=SC2034
{
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  NC='\033[0m' # No Colour 
}

readonly URL="http://127.0.0.1:8081/stroomAdmin/tasks/log-level"
readonly DEV_JAR_SUB_PATH="stroom-app/build/libs/stroom-app-all.jar"
readonly DIST_JAR_SUB_PATH="stroom-app/build/libs/stroom-app-all.jar"
# shellcheck disable=SC2155
readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
if [[ -f "${SCRIPT_DIR}/build.gradle" ]]; then
  # Looks like a dev environment
  echo -e "${RED}WARNING${NC} - Using '${DEV_JAR_SUB_PATH}' to validate/lookup classes." \
    "If not found or out of date, then run a build."
  readonly JAR_FILE="${SCRIPT_DIR}/${DEV_JAR_SUB_PATH}"
else
  readonly JAR_FILE="${SCRIPT_DIR}/${DIST_JAR_SUB_PATH}"
fi

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

  echo -e "${GREEN}Setting ${type} ${BLUE}${package_or_class}${NC} to ${YELLOW}${new_log_level}${NC}"

  curl \
    -X \
    POST \
    -d "logger=${package_or_class}&level=${new_log_level}" \
    "${URL}"
}

is_valid_package() {
  # Replace . with / as files in zip are like 
  # stroom/security/openid/api/OpenIdConfigurationResponse.class
  local package_pattern="${1//./\/}"; shift
  unzip -Z -1 "${JAR_FILE}"  \
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
  unzip -Z -1 "${JAR_FILE}" \
    | grep ".*\.class$" \
    | grep -v "package-info" \
    | sed 's#/#.#g' \
    | sed 's#\.class$##g' \
    | grep -E "(^|\.)${partial_class_name}$" \
    || true
}

main() {

  # should have an arg count that is a multiple of two
  if [ $# -eq 0 ] || [ $(( $# % 2 )) -ne 0 ]; then
    echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
    echo -e "Usage: ${BLUE}$0${GREEN} package_or_class1 new_log_level package_or_classN new_log_level ...${NC}" >&2
    echo -e "e.g:   ${BLUE}$0${GREEN} stroom.startup.App DEBUG stroom.startup.Config TRACE stroom.security debug${NC}" >&2
    echo -e "${GREEN}package_or_class${NC} can be any of the following:" >&2
    echo -e "${BLUE}stroom.security${NC} - package" >&2
    echo -e "${BLUE}App${NC} - class name (if not unique you will be required to select the desired one)" >&2
    echo -e "${BLUE}startup.App${NC} - partially qualified class name to aid uniqueness" >&2
    echo -e "${BLUE}stroom.startup.App${NC} - fully qualified class name" >&2
    exit 1
  fi

  echo -e "Using URL ${BLUE}${URL}${NC}"
  echo

  if [[ ! -f "${JAR_FILE}" ]]; then
    echo -e "${RED}WARNING${NC} - Can't find stroom jar file '${JAR_FILE}'." \
      "Partial matching and class/package validation disabled" >&2
  fi

  #loop through the pairs of args
  while [ $# -gt 0 ]; do
    package_or_class="$1"
    new_log_level="$2"
    debug_value "package_or_class" "${package_or_class}"

    if [[ ! -f "${JAR_FILE}" ]]; then
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
