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

#
# Displays info about the stack

echo_usage() {
  echo -e "${GREEN}This script starts ${APP_NAME}${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-h] [-m]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

show_banner() {
  # shellcheck disable=SC2086
  local banner_colour
  if [ "${MONOCHROME}" = true ]; then
    banner_colour=""
  else
    # see if the terminal supports colors...
    no_of_colours=$(tput colors)

    if test -n "${no_of_colours}" && test "${no_of_colours}" -eq 256; then
      # 256 colours so print the stroom banner in dirty orange
      #echo -en "\e[38;5;202m"
      banner_colour="\e[38;5;202m"
    else
      # No 256 colour support so fall back to blue
      #echo -en "${BLUE}"
      banner_colour="${BLUE}"
    fi
  fi

  echo -en "${banner_colour}"
  cat "${script_dir}"/bin/banner.txt
  echo -en "${NC}"
}

main() {
  local script_dir
  script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" \
    >/dev/null && pwd )"

  # shellcheck disable=SC1091
  source "${script_dir}/config/scripts.env"
  # shellcheck disable=SC1091
  source "${PATH_TO_UTIL_SCRIPT}"

  while getopts ":mh" arg; do
    # shellcheck disable=SC2034
    case $arg in
      h )
        echo_usage
        exit 0
        ;;
      m )
        MONOCHROME=true
        ;;
      * )
        invalid_arguments
        ;;  # getopts already reported the illegal option
    esac
  done
  shift $((OPTIND-1)) # remove parsed options and args from $@ list

  setup_colours

  readonly HOST_IP=$(determine_host_address)

  show_banner

  echo
  info "The local stroom is running at the following location:"
  info "${BLUE}http://localhost:<app port>/stroom/ui${NC}"
  info "The port is available in ${BLUE}config/config.yml${NC} under the" \
    "${BLUE}server.applicationConnectors.port${NC} property."
  info "If you have a gateway configured you might not be able to access it" \
    "at this address."

  echo
  info "You can access the admin page at the following location:"
  info "${BLUE}http://localhost:${ADMIN_PORT}/${ADMIN_PATH}${NC}"

  echo
  info "Data can be POSTed to ${APP_NAME} using the following URL (see README" \
    "for details)"
  info "${BLUE}https://localhost:<app port>/stroom/noauth/datafeed${NC}"
}

main "$@"
# vim:sw=2:ts=2:et:
