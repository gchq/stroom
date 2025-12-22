#!/bin/bash

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

######################################################################################
#
# Script to fetch a list of the System Info names so that you can
# select one of the names to then fetch the named System Info
#
######################################################################################

set -e -o pipefail

#Shell Colour constants for use in 'echo -e'
#shellcheck disable=SC2034
{
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  LGREY='\e[37m'
  DGREY='\e[90m'
  NC='\033[0m' # No Color
}

error_exit() {
  echo -e "${RED}ERROR${NC}: ${*}"
  exit 1
}

debug_value() {
  local name="$1"; shift
  local value="$1"; shift

  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${name}: [${value}]${NC}" >&2
  fi
}

debug() {
  local str="$1"; shift

  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${str}${NC}" >&2
  fi
}

check_for_installed_binary() {
  local -r binary_name=$1
  command -v "${binary_name}" 1>/dev/null \
    || error_exit "Binary ${binary_name} is not installed"
}

check_for_installed_binaries() {
  check_for_installed_binary "curl"
  check_for_installed_binary "jq"
  check_for_installed_binary "nproc"
}

show_usage() {
  echo -e "A script to query Stroom's System Info providers on one or more hosts."
  echo -e "Usage: ${BLUE}$0${GREEN} [-h] [-s] [-l hostList] [systemInfoName] [[param_key=param_val]...]${NC}"
  echo -e "e.g:   ${BLUE}$0${GREEN}${NC}"
  echo -e "         # Select a provider from a list of names and query it on localhost.${NC}"
  echo -e "e.g:   ${BLUE}$0 stroom.dashboard.impl.ApplicationInstanceManager${NC}"
  echo -e "         # Query the named provider on localhost"
  echo -e "e.g:   ${BLUE}$0 -l \"host1,host2\" stroom.dashboard.impl.ApplicationInstanceManager${NC}"
  echo -e "         # Query the named provider on the listed hosts"
  echo -e "e.g:   ${BLUE}$0 -s -l host1.somedomain,host2.somedomain${NC}"
  echo -e "         # Select a provider from a list of names and query it on the listed hosts.${NC}"
  echo -e "Arguments:"
  echo -e "${GREEN}-h${NC}:             Show this help"
  echo -e "${GREEN}-s${NC}:             Silent. Only output result. Useful for piping to other processes."
  echo -e "${GREEN}-l hostList${NC}:    A list of comma delimited hosts to connect to. Will use localhost if not provided."
  echo -e "${GREEN}systemInfoName${NC}: The name of the provider to query. If not provided you will be asked to select one."
  echo -e "${GREEN}params${NC}:         Query parameter key=value paris, space delimited."
}

query_single_host() {
  local host_port="$1"; shift
  local info_path="$1"; shift
  local info_url="http://${host_port}${info_path}"


  debug_value "host_port" "${host_port}"
  debug_value "info_url" "${info_url}"
  debug_value "http_auth_args" "${http_auth_args}"

  if [[ "${is_silent}" = false ]]; then
    echo -e "${GREEN}Querying system info ${BLUE}${sys_info_name}${GREEN}" \
      "at\n${BLUE}${info_url}${NC}"
  fi

  local query_param_args=()
  for param in "${params[@]}"; do
    query_param_args+=( "--data-urlencode" "${param}" )
  done

  debug_value "query_param_args" "${query_param_args[*]}"

  # Call the api using curl
  local sys_info_json
  sys_info_json="$( \
    curl \
      --get \
      --silent \
      --request GET \
      --header "${http_auth_args}" \
      "${query_param_args[@]}" \
      "${info_url}" )"

  # Call the api but wrap the returned json inside a key that is the host name
  # Just output to stdout so the result can be piped to downstream processing,
  # e.g jq
  jq "{ \"${host_port}\": .details }" \
    <<< "${sys_info_json}"
}

query_multiple_hosts() {
  local host_list="$1"; shift
  local info_path="$1"; shift
  debug_value "host_list" "${host_list}"

  # TODO validate host_list to ensure it only contains [a-zA-Z.0-9\-] as
  # we are about to pass it to bash -c

  # Determine how many threads we can use with xargs
  local max_proc_count
  max_proc_count="$(nproc)"
  debug_value "max_proc_count" "${max_proc_count}"

  temp_dir=
  temp_dir="$(mktemp -d --suffix=_stroom_system_info)"
  debug_value "temp_dir" "${temp_dir}"

  param_args_str=""
  for param in "${params[@]}"; do
    # shellcheck disable=SC2089
    param_args_str+="\"${param}\" "
  done
  debug_value "param_args_str" "${param_args_str}"

  # Need to export these so they are visible in bash -c
  export temp_dir
  export http_auth_args
  export port
  export SCRIPT_DIR
  export SCRIPT_NAME
  export sys_info_name
  # shellcheck disable=SC2090
  export param_args_str

  # convert , to \n then use xargs to process each host in parallel
  # writing output to a temp file
  # We first use xargs to craft a bash command using bash -c 'echo ...'
  # so that we can get access to the variables, then we pass each command string
  # to another xargs bash -c to actually execute it.
  # shellcheck disable=SC2016
  tr ',' '\n' <<< "${host_list}" \
    | sort \
    | uniq \
    | xargs \
      -I'{}' \
      --max-procs="${max_proc_count}" \
      bash -c 'echo "\"${SCRIPT_DIR}/${SCRIPT_NAME}\" -s -l \"{}\" \"${sys_info_name}\" ${param_args_str} > \"${temp_dir}/{}.json\""' \
    | xargs \
      -I'{}' \
      --max-procs="${max_proc_count}" \
      bash -c '{}'

  if [ "${IS_DEBUG}" = true ]; then
    for file in "${temp_dir}"/*.json; do
      echo
      echo -e "${DGREY}DEBUG ${file} contents:${NC}"
      echo -e "${DGREY}-START------------------------------------------------------${NC}"
      local file_content
      file_content="$(<"${file}")"
      echo -e "${DGREY}${file_content}${NC}"
      echo -e "${DGREY}-END--------------------------------------------------------${NC}"
    done
  fi

  # Merge all the files into one json object, which each host's content as a top
  # level key. This is output to stdout for onward piping if needs be
  jq --slurp add "${temp_dir}"/*.json

  # No longer need the temp files so bin them
  debug "Deleting temporary dir ${temp_dir}"
  rm -rf "${temp_dir}"
}

# The host list is passed to bash -c to execute so make sure it only
# containts safe chars
validate_host_list() {
  local host_list="$1"; shift
  local host_list_pattern="^([-.0-9a-zA-Z]+(:[0-9]+)?,?)+$"
  if [[ ! "${host_list}" =~ ${host_list_pattern} ]]; then
    error_exit "Host list [${host_list}] does not match pattern ${host_list_pattern}"
  fi
}

# The name is passed to bash -c to execute so make sure it only
# containts safe chars
validate_sys_info_name() {
  local sys_info_name="$1"; shift
  local sys_info_name_pattern="^[.0-9a-zA-Z]+$"
  debug_value "sys_info_name" "${sys_info_name}"
  if [[ ! "${sys_info_name}" =~ ${sys_info_name_pattern} ]]; then
    error_exit "System info provider name [${sys_info_name}] does not" \
      "match pattern ${sys_info_name_pattern}"
  fi
}

capture_params() {
  local sys_info_name="$1"; shift
  local params_url="${params_url_base}/${sys_info_name}"

  debug "Querying params for ${sys_info_name}"
  param_details="$( \
    curl \
        --silent \
        --request GET \
        --header "${http_auth_args}" \
        "${params_url}" \
      | jq -r '. | sort_by(.paramType, .name)[] | .name + " (" + .paramType + ") - " + .description' \
    )"

  if [[ -n "${param_details}" ]]; then
    debug "Found some params"

    # Get just the keys
    local param_keys
    param_keys="$( \
      curl \
          --silent \
          --request GET \
          --header "${http_auth_args}" \
          "${params_url}" \
          | jq -r '.[] | .name' )"
    param_keys_arr=()
    while IFS= read -r line; do
      param_keys_arr+=("$line")
    done <<< "${param_keys}"

    local mandatory_param_details
    mandatory_param_details="$( \
      grep "MANDATORY" <<<"${param_details}" || echo "" )"

    if [[ -n "${mandatory_param_details}" ]]; then
      echo -e "${GREEN}Mandatory query parametes are required for this system info provider${NC}"
      echo -e "${YELLOW}${param_details}${NC}"

      for param_key in "${param_keys_arr[@]}"; do
        echo -n "Enter the value for parameter ${param_key} (or hit enter for no value): "
        read -r param_value
        debug_value "param_value" "${param_value}"
        if [[ -n "${param_value}" ]]; then
          params+=( "${param_key}=${param_value}" )
        fi
      done

      debug_value "params" "${params[*]}"
    fi
  fi
}

main() {
  check_for_installed_binaries

  #optspec="gu:rih:"
  local is_silent=false
  # If no host_list is supplied we just use localhost
  local host_list="localhost"
  optspec="shl:"
  while getopts "$optspec" optchar; do
    #echo "Parsing $optchar"
    case "${optchar}" in
      s)
        is_silent=true
        ;;
      h)
        show_usage
        exit 0
        ;;
      l)
        if [[ -z "${OPTARG}" ]]; then
          echo -e "${RED}-h argument requires a comma delimted list of hosts (hostname, FQDN or IP) to be specified, e.g. '${GREEN}-h host1@somedomain,host2@somedomain${NC}'${NC}" >&2
          echo
          show_usage
          exit 1
        fi
        host_list="${OPTARG}"
        ;;
      *)
        echo -e "${RED}ERROR${NC} Unknown argument: '-${OPTARG}'" >&2
        echo
        show_usage
        exit 1
        ;;
    esac
  done

  #discard the args parsed so far
  shift $((OPTIND -1))
  #echo "Remaining args [${@}]"

  local name_arg
  if [ $# -gt 0 ]; then
    name_arg="$1"; shift
  fi

  local params=()
  #loop through the pairs of args
  while [ $# -gt 0 ]; do
    params+=( "$1" )
    shift
  done

  SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
  SCRIPT_NAME="$0"
  SCRIPT_NAME="$(basename "${SCRIPT_NAME}")"

  debug_value "is_silent" "${is_silent}"
  debug_value "host_list" "${host_list}"
  debug_value "name_arg" "${name_arg}"
  debug_value "SCRIPT_DIR" "${SCRIPT_DIR}"
  debug_value "SCRIPT_NAME" "${SCRIPT_NAME}"
  debug_value "params" "${params[*]}"

  validate_host_list "${host_list}"

  local api_token
  if [ -n "${TOKEN}" ]; then
    if [[ "${is_silent}" = false ]]; then
      echo -e "${GREEN}Using token from ${BLUE}\${TOKEN}${NC}"
    fi
    api_token="${TOKEN}"
  else
    # Hard coded token that works with the hard coded default open id creds
    # for use in dev only. Expires on 2030-08-18T13:53:50.000Z
    if [[ "${is_silent}" = false ]]; then
      echo -e "${GREEN}Using hard coded token, export ${BLUE}\${TOKEN}${GREEN} to override.${NC}"
    fi
    local api_token='eyJhbGciOiJSUzI1NiIsImtpZCI6ImYzNzQyZTBlLWQ2ZTQtNDZlYS04MmM0LTBmZGE0MjE5ZTk5MiJ9.eyJleHAiOjIzMDIxMDc3NzksInN1YiI6ImRlZmF1bHQtdGVzdC1vbmx5LWFwaS1rZXktdXNlciIsImlzcyI6ImRlZmF1bHQtdGVzdC1vbmx5LWlzc3VlciIsImF1ZCI6IkJ5VXVYUEVVQndxVmZBbTladHI5SzEyTmhxV3lNRnoxVG1YU2xXeVAuY2xpZW50LWlkLmFwcHMuc3Ryb29tLWlkcCJ9.pvP3ojlYnbjpRdvuRcj7R9gbTgb-pirCDwGwPTWrDa4MTb30IS0DRNi6wdBL6K4FQJVwOwkRsc3xBIhF2cRAxLgZ1qWr7Zeh0bGB25y1womshnDvFQQFsl6vM-taxSRXrZ2b8by7KbDu9-PitxIaLssD9ZDe9hoi5vZlWlVUe6CI3EfUCQGcyqbVPrkwPpWg_tQa6Mvx7UxUf_GslYt6YVibF8FVelM5ISVZEZRM8GRUTeoKZGSySiFR18CzUn7XsRpffEtmwpo8JuEoOprvgPk-n8y6nQfZnPCdr45bthTi4tzARPTCLaK0XUiiAP58_LfaeldjMhSdYnefgT3gXg'
  fi

  local default_port="8080"
  local default_host_port="localhost:8080"
  local base_path="/api/systemInfo/v1"
  local names_path_part="/names"
  local params_path_part="/params"
  local http_auth_args="Authorization:Bearer ${api_token}"

  if [[ "${is_silent}" = false ]]; then
    echo -e "${GREEN}Using names URL ${BLUE}${names_url}${NC}"
    echo
  fi

  local host
  if [[ -n "${host_list}" ]]; then
    # Get the first host in the list
    host="${host_list%%,*}"
  else
    host="${default_host_port}"
  fi
  local host_port="${host}"
  if [[ ! "${host}" =~ :[0-9]+ ]]; then
    host_port="${host}:${default_port}"
  fi

  local names_url="http://${host_port}${names_path_part}"
  debug_value "names_url" "${names_url}"
  local base_url="http://${host_port}${base_path}"
  local names_url="${base_url}${names_path_part}"
  local params_url_base="${base_url}${params_path_part}"
  debug_value "names_url" "${names_url}"
  debug_value "params_url_base" "${params_url_base}"

  local sys_info_name

  if [[ -n "${name_arg}" ]]; then
    sys_info_name="${name_arg}"
  else
    # Use fzf to get a system info name
    debug "Qurying for system info provider names"
    local sys_info_names_json
    sys_info_names_json="$( \
      curl \
        --silent \
        --request GET \
        --header "${http_auth_args}" \
        "${names_url}" )"

    debug_value "sys_info_names_json" "${sys_info_names_json}"

    # Extract a list of names from the json
    sys_info_names="$( \
        jq -r '.[]' \
          <<< "${sys_info_names_json}" \
        | sort )"

    if command -v "fzf" 1>/dev/null; then
      # Let the user fuzzy find the name they want using fzf
      sys_info_name="$( \
        fzf \
          --border \
          --header="Select the System Info provider to query" \
          --height=15 \
        <<< "${sys_info_names}" )"
    else
      # User doesn't have FZF, bless, so fall back to a simple bash selection menu

      echo -e "${YELLOW}We recommend you install FZF to improve this menu" \
        "selection process${NC}"
      echo -e "${GREEN}Select the system info provider to query${NC}"

      # Build the menu items
      local menu_item_arr=()
      while IFS= read -r name; do
        menu_item_arr+=( "${name}" )
      done <<< "${sys_info_names}"

      # Present the user with a menu of options in a single column
      COLUMNS=1
      select user_input in "${menu_item_arr[@]}"; do
        if [[ -n "${user_input}" ]]; then
          sys_info_name="${user_input}"
          break
        else
          echo "Invalid option. Try another one."
          continue
        fi
      done
    fi

    # shellcheck disable=SC2181
    [ $? -eq 0 ] || error_exit "Something went wrong looking up the sys_info_name"
  fi

  validate_sys_info_name "${sys_info_name}"

  local param_details=()
  if [[ "${is_silent}" = false ]] && [[ "${#params[@]}" -eq 0 ]]; then
    # No param args used, so see if the sys info provider has any params
    # and if so prompt user for their values.
    capture_params "${sys_info_name}"
  fi

  # Construct the url path base on the sys info provider name
  local info_path="${base_path}/${sys_info_name}"

  if [[ -n "${host_list}" ]] && [[ "${host_list}" =~ , ]]; then
    debug "More than one host to send to"
    query_multiple_hosts "${host_list}" "${info_path}"
  else
    local host="${host_list:-${default_host}}"
    debug "Single host"
    debug_value "host_port" "${host_port}"

    query_single_host "${host_port}" "${info_path}"
  fi
}

main "$@"
