#!/usr/bin/env bash

#Exit script on first error
set -e

# Copies dev.yml to local.yml and substitutes some variables, e.g. STROOM_HOST

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
SOURCE_CONF_FILE=${SCRIPT_DIR}/stroom-app/dev.yml

LOCAL_CONF_FILE_NAME=local.yml
LOCAL_2_CONF_FILE_NAME=local2.yml
LOCAL_3_CONF_FILE_NAME=local3.yml

LOCAL_CONF_FILE=${SCRIPT_DIR}/${LOCAL_CONF_FILE_NAME}
LOCAL_2_CONF_FILE=${SCRIPT_DIR}/${LOCAL_2_CONF_FILE_NAME}
LOCAL_3_CONF_FILE=${SCRIPT_DIR}/${LOCAL_3_CONF_FILE_NAME}

setIpAddress(){
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

checkSourceExists(){
  if [ ! -f "${SOURCE_CONF_FILE}" ]; then
    exit
    echo -e "${RED}Source file ${GREEN}${SOURCE_CONF_FILE}${RED}" \
      "does not exist${NC}"
          exit 1
  fi
}

backupCurrentLocalFile(){
  local backupFile="$1"; shift

  if [ -f "${LOCAL_CONF_FILE}" ]; then
    backupFile="${LOCAL_CONF_FILE}.$(date +"%Y%m%dT%H%M")"
    echo
    echo -e "Backing up ${GREEN}${LOCAL_CONF_FILE}${NC} to ${GREEN}${backupFile}${NC}"
    cp "${LOCAL_CONF_FILE}" "${backupFile}"
    echo
  fi
}

makeLocal2File() {
  echo -e "Overwriting ${GREEN}${LOCAL_2_CONF_FILE}${NC} as a node 2" \
    "variant of ${GREEN}${LOCAL_CONF_FILE}${NC}"

  sed \
    -r \
    -e 's/name: node1a/name: node2a/' \
    -e 's/(port:.*[^\d])8080/\110080/' \
    -e 's/(port:.*[^\d])8081/\110081/' \
    < "${LOCAL_CONF_FILE}" \
    > "${LOCAL_2_CONF_FILE}"
  }

makeLocal3File() {
  echo -e "Overwriting ${GREEN}${LOCAL_3_CONF_FILE}${NC} as a node 3" \
    "variant of ${GREEN}${LOCAL_CONF_FILE}${NC}"

  sed \
    -r \
    -e 's/name: node1a/name: node3a/' \
    -e 's/(port:.*[^\d])8080/\120080/' \
    -e 's/(port:.*[^\d])8081/\120081/' \
    < "${LOCAL_CONF_FILE}" \
    > "${LOCAL_3_CONF_FILE}"
  }

main(){
  setIpAddress

  #Ensure various dirs exist
  mkdir -p /tmp/stroom

  checkSourceExists

  backupFile="${LOCAL_CONF_FILE}.$(date +"%Y%m%dT%H%M")"
  backupCurrentLocalFile "${backupFile}"

  echo -e "Overwriting ${GREEN}${LOCAL_CONF_FILE}${NC} with a version" \
    "templated from ${GREEN}${SOURCE_CONF_FILE}${NC}"

  #Use '#' delimiter in HOME_DIR sed script as HOME contains '\'
  sed \
    -e "s/<<<IP_ADDRESS>>>/${ip}/g" \
    -e "s#\${HOME_DIR[^}]*}#${HOME}#g" \
    < "${SOURCE_CONF_FILE}" \
    > "${LOCAL_CONF_FILE}"

  makeLocal2File
  makeLocal3File

  if [[ "x${backupFile}" != "x" ]]; then

    if ! diff -q "${backupFile}" "${LOCAL_CONF_FILE}" > /dev/null; then
      echo
      echo -e "Run the following to see the changes made to your" \
        "${LOCAL_CONF_FILE_NAME} file"
      echo -e "${GREEN}vimdiff ${backupFile} ${LOCAL_CONF_FILE}${NC}"
    else
      echo
      echo -e "Backup file is identical to new local config file," \
        "deleting backup file ${backupFile}"
      rm "${backupFile}"
    fi
  fi

  exit 0
}

main

