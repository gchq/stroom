#!/bin/bash

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
  echo -e "${@}"
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
    || error_exit "${GREEN}${binary_name}${RED} is not installed"
}

check_for_installed_binaries() {
  check_for_installed_binary "fzf"
  check_for_installed_binary "http"
  check_for_installed_binary "jq"
  check_for_installed_binary "nproc"
}

showUsage() {
    echo -e "${RED}ERROR${NC} - Invalid arguments"
    echo -e "Usage: ${BLUE}$0${GREEN} [-r] [-h hostList] [systemInfoName]${NC}"
    echo -e "e.g:   ${BLUE}$0${GREEN}${NC}"
    echo -e "e.g:   ${BLUE}$0${GREEN} stroom.dashboard.impl.ApplicationInstanceManager${NC}"
    echo -e "e.g:   ${BLUE}$0${GREEN} -r -h host1@somedomain,host2@somedomain${NC}"
    echo -e "${GREEN}-r${NC}:           Raw output of just the json, e.g. for piping to jq for further processing"
    echo -e "${GREEN}-h hostList${NC}:  A list of hosts to connect to. Will use localhost if not provided."
}

query_single_host() {
  local host="$1"; shift
  local info_path="$1"; shift
  local info_url="http://${host}:${port}${info_path}"

  debug_value "host" "${host}"
  debug_value "info_url" "${info_url}"
  debug_value "http_auth_args" "${http_auth_args}"

  if [[ "${is_raw_output}" = false ]]; then
    echo -e "${GREEN}Querying system info ${BLUE}${sys_info_name}${GREEN}" \
      "at\n${BLUE}${info_url}${NC}"
  fi

  # Call the api but wrap the returned json inside a key that is the host name
  # Just output to stdout so the result can be piped to downstream processing,
  # e.g jq
  http --body GET "${info_url}" "${http_auth_args}" \
    | jq "{ \"${host}\": .details }"
}

query_multiple_hosts() {
  local host_list="$1"; shift
  local info_path="$1"; shift
  
  # TODO validate host_list to ensure it only contains [a-zA-Z.0-9\-] as
  # we are about to pass it to bash -c

  # Determine how many threads we can use with xargs
  local max_proc_count
  max_proc_count="$(nproc)"
  debug_value "max_proc_count" "${max_proc_count}"

  temp_dir=
  temp_dir="$(mktemp -d --suffix=_stroom_system_info)"
  debug_value "temp_dir" "${temp_dir}"

  # Need to export these so they are visible in bash -c
  export temp_dir
  export http_auth_args
  export port
  export SCRIPT_DIR
  export SCRIPT_NAME
  export sys_info_name

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
      bash -c 'echo "\"${SCRIPT_DIR}/${SCRIPT_NAME}\" -r -h \"{}\" \"${sys_info_name}\" > \"${temp_dir}/{}.json\""' \
    | xargs \
      -I'{}' \
      --max-procs="${max_proc_count}" \
      bash -c '{}'

  ls -l "${temp_dir}"
  cat "${temp_dir}/"*.json

  # Merge all the files into one json object, which each host's content as a top
  # level key. This is output to stdout for onward piping if needs be
  jq --slurp add "${temp_dir}"/*.json

  # No longer need the temp files so bin them
  rm -rf "${temp_dir}"
}

main(){
  check_for_installed_binaries

  #optspec="gu:rih:"
  local is_raw_output=false
  # If no host_list is supplied we just use localhost
  local host_list="localhost"
  optspec="rh:"
  while getopts "$optspec" optchar; do
    #echo "Parsing $optchar"
    case "${optchar}" in
      r)
        is_raw_output=true
        ;;
      h)
        if [[ -z "${OPTARG}" ]]; then
          echo -e "${RED}-h argument requires a comma delimted list of hosts (hostname, FQDN or IP) to be specified, e.g. '${GREEN}-h host1@somedomain,host2@somedomain${NC}'${NC}" >&2
          echo
          showUsage
          exit 1
        fi
        host_list="${OPTARG}"
        ;;
      *)
        echo -e "${RED}ERROR${NC} Unknown argument: '-${OPTARG}'" >&2
        echo
        showUsage
        exit 1
        ;;
    esac
  done

  #discard the args parsed so far
  shift $((OPTIND -1))
  #echo "Remaining args [${@}]"

  local name_arg="$1"

  SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
  SCRIPT_NAME="$0"
  SCRIPT_NAME="$(basename "${SCRIPT_NAME}")"

  debug_value "is_raw_output" "${is_raw_output}"
  debug_value "host_list" "${host_list}"
  debug_value "name_arg" "${name_arg}"
  debug_value "SCRIPT_DIR" "${SCRIPT_DIR}"
  debug_value "SCRIPT_NAME" "${SCRIPT_NAME}"

  local api_token
  if [ -n "${TOKEN}" ]; then
    if [[ "${is_raw_output}" = false ]]; then
      echo -e "${GREEN}Using token from ${BLUE}\${TOKEN}${NC}"
    fi
    api_token="${TOKEN}"
  else
    # Hard coded token that works with the hard coded default open id creds
    # for use in dev only. Expires on 2030-08-18T13:53:50.000Z
    if [[ "${is_raw_output}" = false ]]; then
      echo -e "${GREEN}Using hard coded token, export ${BLUE}\${TOKEN}${GREEN} to override.${NC}"
    fi
    local api_token='eyJhbGciOiJSUzI1NiIsImtpZCI6IjhhM2I1OGNhLTk2ZTctNGFhNC05ZjA3LTQ0MDBhYWVkMTQ3MSJ9.eyJleHAiOjE2Njg3NzczNjcsInN1YiI6ImFkbWluIiwiaXNzIjoic3Ryb29tIiwiYXVkIjoiTlhDbXJyTGpQR2VBMVN4NWNEZkF6OUV2ODdXaTNucHRUbzZSdzVmTC5jbGllbnQtaWQuYXBwcy5zdHJvb20taWRwIn0.YhhRQKF29CQm7IWHP2sh-i70qBicuWKJLhH5UmSRxeyHJ2T38RmHVEcIjC9tv71-gJR4z3bY9tRq_r6cf5hG2G9DKVfPoVZTN-MhK-pU5eD3VbVc2HBEm0Xk02LL7vKRS2mKLolaI-DC_5TuYclZ-CGKmmqhh8Bb5evqAXnknccILAGsl3xDFzsKdfuX5iZ5wjizxvgyMjvLCfaM6P-Ut5kUWYHmxpdNextE3p35Kajw_iEbHvZ3_CobX09l5QQo2R7Hgzk3lIFrGxUqQ7jyC_DmmxRlcT-pyJApl7_TFlFJm153R_9gDXIwphI1mg1vsAojkWncCl8ODY227t3-Lw'
  fi

  local port=8080
  local default_host="localhost"
  local base_path="/api/systemInfo/v1"
  local names_path="${base_path}/names"

  local http_auth_args="Authorization:Bearer ${api_token}"
  local base_url="http://localhost:8080/api/systemInfo/v1"
  local names_url="${base_url}/names"

  if [[ "${is_raw_output}" = false ]]; then
    echo -e "${GREEN}Using names URL ${BLUE}${names_url}${NC}"
    echo
  fi

  local sys_info_name

  if [[ -n "${name_arg}" ]]; then
    sys_info_name="${name_arg}"
  else
    # Use fzf to get a system info name
    local host
    if [[ -n "${host_list}" ]]; then
      # Get the first host in the list
      host="${host_list%%,*}"
    else
      host="${default_host}"
    fi

    local names_url="http://${host}:${port}${names_path}"
    debug_value "names_url" "${names_url}"

    sys_info_names="$( \
      http --body "${names_url}" "${http_auth_args}" | 
      jq -r '.[]' | 
      sort )"

    sys_info_name="$( \
      fzf \
        --border \
        --header="Select the System Info set to view" \
        --height=15 \
        <<< "${sys_info_names}" )"

    # shellcheck disable=SC2181
    [ $? -eq 0 ] || error_exit "Something went wrong looking up the sys_info_name"
  fi

  # Construct the url path base on the sys info provider name
  local info_path="${base_path}/${sys_info_name}"

  if [[ -n "${host_list}" ]] && [[ "${host_list}" =~ , ]]; then
    debug "More than one host to send to"
    query_multiple_hosts "${host_list}" "${info_path}"
  else
    local host="${host_list:-${default_host}}"
    debug "Single host"
    debug_value "host" "${host}"

    query_single_host "${host}" "${info_path}"
  fi
}

main "$@"
