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

# Script to import a single Stroom content pack ZIP file into Stroom.
# Requires that the environment variable TOKEN is set with either a
# valid API Key or an OAuth token.
# Usage:
# import_content.sh CONTENT_ZIP_FILE [URL_BASE]
# e.g. import_content.sh /some/path/StroomConfig.zip
# e.g. import_content.sh /some/path/StroomConfig.zip https://stroom.some.domain

set -e -o pipefail

showUsage() {
    echo -e "Usage: ${BLUE}$0 [OPTION]... FILE${NC}"
    echo -e "OPTIONs:"
    echo -e "  ${GREEN}-d${NC} - The data file to read from (or '-' for stdin)"
    echo -e "  ${GREEN}-u${NC} - The base URL to use. If not set, uses http://localhost:8080"
    echo -e "  ${GREEN}-o${NC} - The file path to export to. If not set it will be written to the current directory with the resource name"
    echo -e "FILE: The file to import"
    echo -e "e.g.: ${BLUE}$0 content.zip${NC}"
    echo -e "e.g.: ${BLUE}$0 -u content.zip${NC}"
    echo
}

main() {
  if [[ -z "${TOKEN}" ]]; then
    echo -e "${RED}Environment variable TOKEN must be set with an API key or an OAuth token${NC}" >&2
    exit 1
  fi

  while getopts hu: option; do
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
    esac
  done

  # shellcheck disable=SC2124
  local content_zip_file="${@:$OPTIND:1}"; shift
  if [[ -z "${content_zip_file}" ]]; then
    echo -e "${RED}content_zip_file argument must be provided${NC}" >&2
    exit 1
  fi

  if [[ -z "${url_base}" ]]; then
    url_base="http://localhost:8080"
  fi

  echo -e "${GREEN}Uploading file '${BLUE}${content_zip_file}${GREEN}' to a temporary file${NC}"

  local response1
  response1="$( \
    curl \
      --silent \
      --header "Authorization: Bearer ${TOKEN}" \
      --form "encoding=multipart/form-data" \
      --form fileUpload="@${content_zip_file}" \
      --request POST \
      "${url_base}/importfile.rpc" \
  )"

  echo -e "${GREEN}File uploaded${NC}"

  echo -e "${response1}"
  # response1 looks like
  # '#PM#success=true name=StroomConfig.zip key=e015e5d4-8a6a-4454-8d81-913e6c13cca5#PM#'

  local key
  key="$( \
    grep \
      --only-matching \
      --perl-regexp \
      '(?<=key=)[^ #]+' \
      <<< "${response1}"
  )"

  # name is only really used for logging purposes in stroom, but provide
  # it anyway
  local name
  name="$( \
    grep \
      --only-matching \
      --perl-regexp \
      '(?<=name=)[^ #]+' \
      <<< "${response1}"
  )"

  local import_request
  import_request="{ \"confirmList\": [], \"importSettings\": { \"importMode\": \"IGNORE_CONFIRMATION\" }, \"resourceKey\": { \"key\": \"${key}\", \"name\": \"${name}\" }  }"

  echo "Importing content"

  # Not interested in the response
  curl \
    --silent \
    --request POST \
    --header "Authorization:Bearer ${TOKEN}" \
    --header 'Content-Type: application/json' \
    --data "${import_request}" \
    "${url_base}/api/content/v1/import" \
    > /dev/null

  echo "Content imported successfully"
  echo "Done!"
}

main "$@"
