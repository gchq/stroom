#!/bin/bash

######################################################################################
# 
# Script to fetch a list of the System Info names so that you can
# select one of the names to then fetch the named System Info
#
######################################################################################

set -e

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

check_for_installed_binary() {
    local -r binary_name=$1
    command -v "${binary_name}" 1>/dev/null \
      || error_exit "${GREEN}${binary_name}${RED} is not installed"
}

check_for_installed_binaries() {
    check_for_installed_binary "fzf"
    check_for_installed_binary "http"
    check_for_installed_binary "jq"
}

main(){
    check_for_installed_binaries

    if [ ! -n "${TOKEN}" ]; then
      error_exit "${RED}TOKEN not defined!${NC}" \
        "TOKEN env var must be set to your API key." \
        "e.g.\n${BLUE}export TOKEN=<your api key>${NC}"
    fi

    http_auth_args="Authorization:Bearer ${TOKEN}"
    base_url="http://localhost:8080/api/systemInfo/v1"
    names_url="${base_url}/names"

    echo -e "Using URL ${BLUE}${names_url}${NC}"
    echo

    # Use fzf to get a system info name
    local sys_info_name
    sys_info_name="$( \
        http --body "${names_url}" "${http_auth_args}" | 
        jq -r '.[]' | 
        sort |
        fzf --border --header="Select the System Info set to view" --height=15)"

    [ $? -eq 0 ] || error_exit "Something went wrong looking up the sys_info_name"

    info_url="${base_url}/${sys_info_name}"

    echo -e "Querying system info ${BLUE}${sys_info_name}${NC}"

    http --body "${info_url}" "${http_auth_args}" |
      jq '.details'
}

main "$@"
