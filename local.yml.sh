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

#Exit script on first error
set -e

# Copies dev.yml to local.yml and substitutes some variables, e.g. STROOM_HOST
# Also copies proxy-dev.yml to proxy-local.yml in the same way

#Shell Colour constants for use in 'echo -e'
# shellcheck disable=SC2034
{
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  NC='\033[0m' # No Color
}

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
STROOM_SOURCE_CONF_FILE="${SCRIPT_DIR}/stroom-app/dev.yml"
PROXY_SOURCE_CONF_FILE="${SCRIPT_DIR}/stroom-proxy/stroom-proxy-app/proxy-dev.yml"

STROOM_LOCAL_CONF_FILE_NAME=local.yml
STROOM_LOCAL_2_CONF_FILE_NAME=local2.yml
STROOM_LOCAL_3_CONF_FILE_NAME=local3.yml
PROXY_LOCAL_CONF_FILE_NAME=proxy-local.yml
PROXY_REMOTE_CONF_FILE_NAME=proxy-remote.yml

STROOM_LOCAL_CONF_FILE=${SCRIPT_DIR}/${STROOM_LOCAL_CONF_FILE_NAME}
STROOM_LOCAL_2_CONF_FILE=${SCRIPT_DIR}/${STROOM_LOCAL_2_CONF_FILE_NAME}
STROOM_LOCAL_3_CONF_FILE=${SCRIPT_DIR}/${STROOM_LOCAL_3_CONF_FILE_NAME}
PROXY_LOCAL_CONF_FILE=${SCRIPT_DIR}/${PROXY_LOCAL_CONF_FILE_NAME}
PROXY_REMOTE_CONF_FILE=${SCRIPT_DIR}/${PROXY_REMOTE_CONF_FILE_NAME}

set_ip_address(){
  if [ "$(uname)" == "Darwin" ]; then
    # Code required to find IP address is different in MacOS
    ip=$(ifconfig | grep "inet " | grep -Fv 127.0.0.1 | awk 'NR==1{print $2}')
  else
    local ip_binary
    # If ip is not on the path (as seems to be the case with ansible) then
    # try using /sbin instead.
    if command -v ip > /dev/null; then
      ip_binary="ip"
    elif command -v /sbin/ip > /dev/null; then
      ip_binary="/sbin/ip"
    else
      echo
      echo -e "${RED}ERROR${NC} Unable to locate ${BLUE}ip${NC} command." >&2
      exit 1
    fi
    ip=$( \
      "${ip_binary}" route get 1 \
      | awk 'match($0,"src [0-9\\.]+") {print substr($0,RSTART+4,RLENGTH-4)}')
  fi
  echo
  echo -e "Using IP ${GREEN}${ip}${NC} as the IP, as determined from" \
    "the operating system"
  }

check_source_exists() {
  local source="$1"; shift

  if [ ! -f "${source}" ]; then
    echo -e "${RED}Source file ${GREEN}${source}${RED}" \
      "does not exist${NC}"
    exit 1
  fi
}

backup_current_local_file() {
  local source="$1"; shift
  local backup_file
  backup_file="${source}.$(date +"%Y%m%dT%H%M")"

  if [ -f "${source}" ]; then
    backup_file="${source}.$(date +"%Y%m%dT%H%M")"
    echo -e "Backing up ${GREEN}${source}${NC} to ${GREEN}${backup_file}${NC}"
    cp "${source}" "${backup_file}"
  fi
}

make_local2_file() {
  echo -e "Overwriting ${GREEN}${STROOM_LOCAL_2_CONF_FILE}${NC} as a node 2" \
    "variant of ${GREEN}${STROOM_LOCAL_CONF_FILE}${NC}"

  sed \
    -r \
    -e 's/node1a/node2a/' \
    -e 's/(port:.*[^\d])8080/\110080/' \
    -e 's/(port:.*[^\d])8081/\110081/' \
    < "${STROOM_LOCAL_CONF_FILE}" \
    > "${STROOM_LOCAL_2_CONF_FILE}"
}

make_local3_file() {
  echo -e "Overwriting ${GREEN}${STROOM_LOCAL_3_CONF_FILE}${NC} as a node 3" \
    "variant of ${GREEN}${STROOM_LOCAL_CONF_FILE}${NC}"

  sed \
    -r \
    -e 's/node1a/node3a/' \
    -e 's/(port:.*[^\d])8080/\120080/' \
    -e 's/(port:.*[^\d])8081/\120081/' \
    < "${STROOM_LOCAL_CONF_FILE}" \
    > "${STROOM_LOCAL_3_CONF_FILE}"
}

create_local_file() {
  local source="$1"; shift
  local dest="$1"; shift

  echo -e "Overwriting ${GREEN}${dest}${NC} with a version" \
    "templated from ${GREEN}${source}${NC}"

  #Use '#' delimiter in HOME_DIR sed script as HOME contains '\'
  sed \
    -e "s/<<<IP_ADDRESS>>>/${ip}/g" \
    -e "s#\${HOME_DIR[^}]*}#${HOME}#g" \
    < "${source}" \
    > "${dest}"
}

replace_in_file() {
  local file="$1"; shift
  local str="$1"; shift
  local replacement="$1"; shift

  echo -e "Changing port ${GREEN}${str}${NC} to ${GREEN}${replacement}${NC}" \
    "in file ${GREEN}${source}${NC}"

  sed \
    -i \
    -e "s/${str}/${replacement}/g" \
    "${file}"
}

diff_against_backup() {
  local file="$1"; shift
  local backup_file
  backup_file="${file}.$(date +"%Y%m%dT%H%M")"

  if ! diff -q "${backup_file}" "${file}" > /dev/null; then
    echo
    echo -e "Run the following to see the changes made to your" \
      "${file} file"
    echo -e "${GREEN}vimdiff ${backup_file} ${file}${NC}"
  else
    echo -e "Backup file is identical to new local config file," \
      "deleting backup file ${backup_file}"
    rm "${backup_file}"
  fi
}

main(){
  set_ip_address

  #Ensure various dirs exist
  mkdir -p /tmp/stroom

  check_source_exists "${STROOM_SOURCE_CONF_FILE}"
  check_source_exists "${PROXY_SOURCE_CONF_FILE}"

  backup_current_local_file "${STROOM_LOCAL_CONF_FILE}"
  backup_current_local_file "${STROOM_LOCAL_2_CONF_FILE}"
  backup_current_local_file "${STROOM_LOCAL_3_CONF_FILE}"
  backup_current_local_file "${PROXY_LOCAL_CONF_FILE}"
  backup_current_local_file "${PROXY_REMOTE_CONF_FILE}"

  create_local_file "${STROOM_SOURCE_CONF_FILE}" "${STROOM_LOCAL_CONF_FILE}"

  # Make files for nodes 2 and 3 based on a copy of node 1
  make_local2_file
  make_local3_file

  create_local_file "${PROXY_SOURCE_CONF_FILE}" "${PROXY_LOCAL_CONF_FILE}"
  create_local_file "${PROXY_SOURCE_CONF_FILE}" "${PROXY_REMOTE_CONF_FILE}"

  # Change the app+admin server ports
  replace_in_file "${PROXY_REMOTE_CONF_FILE}" 8090 9090
  replace_in_file "${PROXY_REMOTE_CONF_FILE}" 8091 9091
  # Change the ports on the downstream urls
  replace_in_file "${PROXY_REMOTE_CONF_FILE}" 8080 8090
  # Change the home/tmp paths
  replace_in_file "${PROXY_REMOTE_CONF_FILE}" stroom-proxy-local stroom-proxy-remote

  diff_against_backup "${STROOM_LOCAL_CONF_FILE}"
  diff_against_backup "${STROOM_LOCAL_2_CONF_FILE}"
  diff_against_backup "${STROOM_LOCAL_3_CONF_FILE}"
  diff_against_backup "${PROXY_LOCAL_CONF_FILE}"
  diff_against_backup "${PROXY_REMOTE_CONF_FILE}"

  exit 0
}

main "$@"

