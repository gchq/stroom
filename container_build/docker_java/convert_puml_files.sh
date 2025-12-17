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

set -eo pipefail
shopt -s globstar

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

convert_file() {
  local puml_file="$1"; shift

  echo -e "\n${GREEN}Processing source file ${BLUE}${puml_file}${GREEN}"

  # TODO AT: It may be worth generating a .puml.sha1 for each .puml file.
  #   Then we can see if the .puml has changed and only regen it if there is
  #   no .puml.svg or the sha1 is different. This would speed up the process
  #   when we have lots more images in the site.

  local puml_filename
  puml_filename="$(basename "${puml_file}")"

  # Replace first match starting at end
  local generated_svg_filename="${puml_filename/%\.puml/.svg}"
  local renamed_svg_filename="${puml_filename}.svg"
  local sha1_filename="${puml_filename}.sha1"

  local puml_file_dir
  puml_file_dir="$(dirname "${puml_file}")"

  local generated_svg_file="${puml_file_dir}/${generated_svg_filename}"
  local renamed_svg_file="${puml_file_dir}/${renamed_svg_filename}"
  local sha1_file="${puml_file_dir}/${sha1_filename}"

  local is_conversion_needed=false

  if [[ -f "${renamed_svg_file}" ]]; then
    if [[ -f "${sha1_file}" ]]; then
      # Busybox version of sha1sum so -s instead of --quiet
      if sha1sum -c -s "${sha1_file}" >/dev/null 2>&1; then
        echo -e "${GREEN}PUML file has not changed since last conversion${NC}"
      else
        echo -e "${GREEN}PUML file has changed, conversion required${NC}"
        is_conversion_needed=true
      fi
    else
      # No sha1 file
      is_conversion_needed=true
    fi
  else
    # No SVG file
    is_conversion_needed=true
  fi

  if [[ "${is_conversion_needed}" = true ]]; then

    echo -e "${GREEN}Converting PUML to ${BLUE}${renamed_svg_filename}${NC}"

    local is_success=true
    # Convert the .puml to .svg with same name.
    # It is not that efficient to run up java for each puml file but as
    # the puml files don't change often this makes the error handling
    # easier
    java \
      -jar /builder/plantuml.jar \
      "${puml_file}" \
      -svg \
      || is_success=false

    if [[ "${is_success}" = "false" ]]; then
      failed_count=$(( failed_count + 1 ))
      # When it errors it seems to still create an svg so delete it if there
      rm -f "${generated_svg_file}"
    else
      # Now rename the file so we can distinguish puml generated svgs from
      # other svgs in the gitignore
      mv "${generated_svg_file}" "${renamed_svg_file}"

      # Checksum the file so we can check next time we run
      echo -e "${GREEN}Creating SHA1 checksum file ${BLUE}${sha1_filename}${NC}"
      sha1sum "${puml_file}" > "${sha1_file}"
    fi
  else
    echo -e "${GREEN}Skipping conversion${NC}"
  fi
}

main() {
  IS_DEBUG=false

  setup_echo_colours

  if [[ $# -lt 1 ]]; then
    echo -e "${RED}ERROR: Invalid arguments.${NC}"
    echo -e "Usage: $0 dir_to_scan|file_to_convert"
    echo -e "e.g:   $0 /builder/shared"
    echo -e "e.g:   $0 /builder/shared/sequence.puml"
    exit 1
  fi

  local failed_count=0

  for arg in "$@"; do
    echo
    echo -e "${GREEN}Processing argument ${BLUE}${arg}${NC}"
    # remove any trailing slash
    local file_or_dir="${arg}"; shift

    if [[ -f "${file_or_dir}" ]]; then
      echo -e "${GREEN}Path ${BLUE}${file_or_dir}${GREEN} is a file${NC}"
      local puml_file="${file_or_dir}"
      convert_file "${puml_file}"
    else
      # Not a file so assume it
      # remove any trailing slash
      local dir="${file_or_dir%/}"

      if [[ ! -d "${dir}" ]]; then
        echo -e "${RED}ERROR: File or directory ${dir} does not exist.${NC}"
        exit 1
      fi

      echo -e "${GREEN}Path ${BLUE}${file_or_dir}${GREEN} is a directory${NC}"

      if ls "${dir}"/**/*.puml > /dev/null 2>&1; then
        for puml_file in "${dir}"/**/*.puml; do
          # shellcheck disable=SC1001
          if [[ ! "${puml_file}" =~ \/_book\/ ]]; then
            convert_file "${puml_file}"
          else
            echo -e "${YELLOW}Skipping file ${BLUE}${puml_file}${NC}"
          fi
        done
      else
        echo -e "${YELLOW}No files found matching ${BLUE}${dir}/**/*.puml${NC}"
      fi
    fi
  done

  if [[ "${failed_count}" -gt 0 ]]; then
    echo -e "${RED}ERROR${NC}: Failed to convert ${failed_count} files" >&2
    exit 1
  fi
}

main "$@"
