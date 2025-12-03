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
# Common to all Stroom (Proxy) management scripts

# Exit script on any error
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

setup_colours

ask_about_logs() {
  read -n1 -r -p $'  - Press \e[94mspace\e[0m or \e[94menter\e[0m to see the logs, \e[94manything\e[0m else to return to the command line.' key

  echo -e ''

  if [ "$key" = '' ]; then
    info "Press ${BLUE}ctrl+c${NC} to stop following the logs."
    # shellcheck disable=SC1091
    source ../logs.sh
  else
    info "Run ${BLUE}./logs.sh${NC} to see the log.\n"
  fi
}

ensure_file_exists() {
  local file_path="$1"
  if [ ! -f "${file_path}" ]; then
    info "Creating empty file ${BLUE}${file_path}${NC}"
    # File doesn't exists so ensure the dir and file both exist
    local dir
    # get dir part by removing everything before last slash
    dir="${file_path%/*}"
    mkdir -p "${dir}"
    touch "${file_path}"
  fi
}

ensure_dir_exists() {
  local dir_path="$1"
  if [ ! -d "${dir_path}" ]; then
    info "Creating directory ${BLUE}${dir_path}${NC}"
    mkdir -p "${dir_path}"
  fi
}

error() {
  echo -e "${RED}Error:${NC}" "$@"
}

warn() {
  echo -e "${YELLOW}Warning:${NC}" "$@"
}

info() {
  echo -e "${GREEN}Info:${NC}" "$@"
}

#wait_for_200_response() {
  #if [[ $# -ne 1 ]]; then
    #error "Invalid arguments to wait_for_200_response(), expecting a URL to wait for."
    #exit 1
  #fi

  #local -r url=$1
  #local -r maxWaitSecs=120
  #echo

  #n=0
  ## Keep retrying for maxWaitSecs
  #until [ $n -ge ${maxWaitSecs} ]
  #do
    #check_start_is_not_erroring
    ## OR with true to prevent the non-zero exit code from curl from stopping our script
    #responseCode=$(curl -sL -w "%{http_code}\\n" "${url}" -o /dev/null || true)
    ##echo "Response code: ${responseCode}"
    #if [[ "${responseCode}" = "200" ]]; then
      #break
    #fi
    ## print a simple unbounded progress bar, increasing every 2s
    #mod=$((n%2))
    #if [[ ${mod} -eq 0 ]]; then
      #printf '.'
    #fi

    #n=$((n+1))
    ## sleep for two secs
    #sleep 1
  #done
  #printf "\n"

  #if [[ $n -ge ${maxWaitSecs} ]]; then
    #echo -e "${RED}Gave up wating for stroom to start up, check the logs: (${BLUE}./logs${NC}, or ${BLUE}logs/start.sh.log${NC}${RED})${NC}"
  #fi
#}

check_is_configured() {
  local path_to_config="${1}"
  if [ ! -f "${path_to_config}" ]; then
    error "Config file ${path_to_config} does not exist${NC}"
    exit 1
  fi

  local IP_ADDRESS_TAG="IP_ADDRESS"
  if grep -q "${IP_ADDRESS_TAG}" "${path_to_config}"; then
    echo
    error "It looks like you haven't configured IP addresses in" \
      "${BLUE}${path_to_config}${NC}.\nYou need to replace all instances" \
      "of ${BLUE}IP_ADDRESS${NC} before ${APP_NAME} can start."
    exit 1
  fi
}

determine_host_address() {
  if [ "$(uname)" == "Darwin" ]; then
    # Code required to find IP address is different in MacOS
    ip=$(ifconfig | grep "inet " | grep -Fv 127.0.0.1 | awk 'NR==1{print $2}')
  else
    ip=$(ip route get 1 \
      | awk 'match($0,"src [0-9\\.]+") {print substr($0,RSTART+4,RLENGTH-4)}')
  fi

  if [[ ! "${ip}" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
    echo
    echo -e "${RED}ERROR${NC} IP address [${GREEN}${ip}${NC}] is not valid,\n" \
      "try setting '${BLUE}STROOM_RESOURCES_ADVERTISED_HOST=x.x.x.x${NC}'" \
      "in ${BLUE}local.env${NC}" >&2
    exit 1
  fi

  echo "$ip"
}

# vim:sw=2:ts=2:et:
