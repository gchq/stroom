#!/usr/bin/env bash

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
  
  local puml_filename
  puml_filename="$(basename "${puml_file}")"

  # Replace first match starting at end
  local generated_svg_filename="${puml_filename/%\.puml/.svg}"
  local renamed_svg_filename="${puml_filename}.svg"

  local puml_file_dir
  puml_file_dir="$(dirname "${puml_file}")"

  local generated_svg_file="${puml_file_dir}/${generated_svg_filename}"
  local renamed_svg_file="${puml_file_dir}/${renamed_svg_filename}"

  echo -e "${GREEN}Converting file ${BLUE}${puml_file}${GREEN}" \
    "to ${BLUE}${renamed_svg_filename}${NC}"

  local is_success=true
  # convert the .puml to .svg with same name
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

  # remove any trailing slash
  local file_or_dir="$1"; shift
  local failed_count=0

  if [[ -f "${file_or_dir}" ]]; then
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

    for puml_file in "${dir}"/**/*.puml; do
      # shellcheck disable=SC1001
      if [[ ! "${puml_file}" =~ \/_book\/ ]]; then
        convert_file "${puml_file}"
      else
        echo -e "${YELLOW}Skipping file ${BLUE}${puml_file}${NC}"
      fi
    done
  fi

  if [[ "${failed_count}" -gt 0 ]]; then
    echo -e "${RED}ERROR${NC}: Failed to convert ${failed_count} files" >&2
    exit 1
  fi
}

main "$@"
