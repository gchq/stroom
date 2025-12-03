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

set -e -o pipefail

showUsage() {
    echo -e "Usage: ${BLUE}$0 [OPTION]... [UUID]...${NC}"
    echo -e "OPTIONs:"
    echo -e "  ${GREEN}-d${NC} - The data file to read from (or '-' for stdin)"
    echo -e "  ${GREEN}-u${NC} - The base URL to use. If not set, uses http://localhost:8080"
    echo -e "  ${GREEN}-o${NC} - The file path to export to. If not set it will be written to the current directory with the resource name"
    echo -e "UUID:"
    echo -e "  Each UUID is of the form '<UUID>,<Type>', e.g. '18d92d74-a5aa-4a81-83be-46fb6d84a60e,Pipeline'"
    echo -e "e.g.: ${BLUE}$0 -d - ${NC}    - Exports all UUIDs in std into a file in this directory"
    echo
}

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
  #SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

  setup_echo_colours

  local uuids_arr=()
  local url_base
  local data_filename
  local output_filename

  if [[ -z "${TOKEN}" ]]; then
    echo -e "${RED}Environment variable TOKEN must be set with an API key or an OAuth token${NC}" >&2
    exit 1
  fi

  while [ ${OPTIND} -le "$#" ]; do
      if getopts d:ho:u: option
      then
        case ${option} in
          h)
            showUsage
            ;;
          u)
            if [[ -z "${OPTARG}" ]]; then
              echo -e "${RED}-u argument requires a base URL to be specified${NC}" >&2
              echo
              showUsage
              exit 1
            fi
            url_base="${OPTARG}"
            ;;
          d)
            if [[ -z "${OPTARG}" ]]; then
              echo -e "${RED}-d argument requires a filename or '-' to read from stdin${NC}" >&2
              echo
              showUsage
              exit 1
            fi
            data_filename="${OPTARG}"
            ;;
          o)
            if [[ -z "${OPTARG}" ]]; then
              echo -e "${RED}-o argument requires a file path to output to${NC}" >&2
              echo
              showUsage
              exit 1
            fi
            output_filename="${OPTARG}"
            ;;
          esac
      else
          uuids_arr+=("${!OPTIND}")
          ((OPTIND++))
      fi
  done

  # Add all the uuids from file/stdin
  if [[ -n "${data_filename}" ]]; then
    if [[ "${data_filename}" = "-" ]]; then
      data_filename="/dev/stdin"
    fi

    while read -r line; do
      uuids_arr+=( "${line}" )
    done < "${data_filename}"
  fi

  if [[ "${#uuids_arr[@]}" -eq 0 ]]; then
    echo "No UUIDs to export" >&2
    exit 1
  fi


  local uuids_str
  uuids_str=$( printf "%s\n" "${uuids_arr[@]}" )
  local req_json
  req_json=$( \
    jq -R -s '{
      docRefs: [
        split("\n")[]
        | select(length > 0)
        | split(",")
        | {uuid: .[0], type: .[1]}
      ]
    }' <<< "${uuids_str}" )

  #echo -e "${req_json}"

  if [[ -z "${url_base}" ]]; then
    url_base="http://localhost:8080"
  fi

  local export_url="${url_base}/api/content/v1/export"
  local resource_url="${url_base}/resourcestore/"

  local resource_key_json
  resource_key_json=$( \
    echo -e "${req_json}" | curl \
      -s \
      --request POST \
      -H "Content-Type:application/json" \
      -H "Authorization:Bearer ${TOKEN}" \
      -d @- \
      "${export_url}" )

  #echo -e "${resource_key_json}"

  local key
  local name
  key=$( jq -r '.resourceKey.key' <<< "${resource_key_json}" )
  name=$( jq -r '.resourceKey.name' <<< "${resource_key_json}" )
  if [[ -z "${output_filename}" ]]; then
    output_filename="${name}"
  fi

  echo -e "${GREEN}Downloading resource ${YELLOW}${key}${GREEN} to file ${BLUE}${output_filename}${NC}"
  resource_url="${resource_url}?uuid=${key}"
  #echo -e "${resource_url}"

  curl \
    -s \
    --output "${name}" \
    -H "Authorization: Bearer $TOKEN" \
    "${resource_url}"


  #http POST http://localhost:8080/api/content/v1/export "Authorization:Bearer ${TOKEN}"
  #| jq -r '.resourceKey.key') && curl --output content.zip -H "Authorization: Bearer $TOKEN" http://localhost:8080/resourcestore/\?uuid\=$key

}

main "$@"
