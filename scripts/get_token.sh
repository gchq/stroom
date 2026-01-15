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

# ------------------------------------------------------
# Script to return an access token to stdout.
# Requires Stroom to be running as it uses Stroom's
# AuthProxy endpoint to get the token from the IDP
# ------------------------------------------------------

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
  SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

  SCHEME="http"
  PORT="8080"
  HOST="localhost"

  setup_echo_colours

  if ! command -v jq 1>/dev/null; then
    echo "jq is not installed, please install it." >&2
    exit 1
  fi

  if ! command -v yq 1>/dev/null; then
    echo "yq is not installed, please install it." >&2
    exit 1
  fi

  local config_file="${SCRIPT_DIR}/../local.yml"
  local client_id_path='.appConfig.security.authentication.openId.clientId'
  local client_secret_path='.appConfig.security.authentication.openId.clientSecret'

  local client_id
  client_id="$(yq -r "${client_id_path}" \
    < "${config_file}")"
  local client_secret
  client_secret="$(yq -r "${client_secret_path}" \
    < "${config_file}")"

  if [[ -z "${client_id}" || "${client_id}" = "null" ]]; then
    echo "'${client_id_path}' not found in ${config_file}" >&2
    exit 1
  fi

  if [[ -z "${client_secret}" || "${client_secret}" = "null" ]]; then
    echo "'${client_secret_path}' not found in ${config_file}" >&2
    exit 1
  fi

  local req_json
  req_json="$( \
    jq \
      -n \
      --arg client_id "${client_id}" \
      --arg client_secret "${client_secret}" \
      '{clientId: $client_id, clientSecret: $client_secret}')"

  #local response
  #response="$( \
    curl \
    -s \
    -k \
    --header "Content-Type: application/json" \
    --request POST \
    --data "${req_json}" \
    "${SCHEME}://${HOST}:${PORT}/api/authproxy/v1/noauth/fetchClientCredsToken"



  #local access_token
  #access_token="$( jq -r '.access_token' <<< "${RESPONSE}")"

}

main "$@"
