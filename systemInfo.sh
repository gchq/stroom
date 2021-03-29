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

    local name_arg="$1"

    local api_token
    if [ -n "${TOKEN}" ]; then
      echo -e "${GREEN}Using token from ${BLUE}\${TOKEN}${NC}"
      api_token="${TOKEN}"
    else
      # Hard coded token that works with the hard coded default open id creds
      # for use in dev only
      echo -e "${GREEN}Using hard coded token, export ${BLUE}\${TOKEN}${GREEN} to override.${NC}"
      local api_token="eyJhbGciOiJSUzI1NiIsImtpZCI6IjhhM2I1OGNhLTk2ZTctNGFhNC05ZjA3LTQ0MDBhYWVkMTQ3MSJ9.eyJleHAiOjE2MzY0NDQ1NzcsInN1YiI6ImFkbWluIiwiaXNzIjoic3Ryb29tIiwiYXVkIjoiTlhDbXJyTGpQR2VBMVN4NWNEZkF6OUV2ODdXaTNucHRUbzZSdzVmTC5jbGllbnQtaWQuYXBwcy5zdHJvb20taWRwIn0.YFcNS0W8-whP9F0ANSHoe8MNBUUs3VAhyqB06jF40bPEvdq_gAhQR8KVMdgYaYxK-NcQH3Pi062ve7cbwYJ2p1t6J-PRUyY7SwdMf4z-LgoOQXyyoaFBe5Jy100mXOzrEtlQ28DRtzes8X6A7NDzdtbghG5cpfjSqB29WgLWjHoqQiKYv_s_29xajkooUBQXOvKCDR8se3hA9SKwPoO-qyYHC0sZyvRJ9KgdXA6DkHJPuqjnYY-kjkeDOdI5ufTteTsAaQV0ot44XR2SF9yenD4Bzk7IyfN5H31Ly5Bh30S4E0dRiUghvnGeiD0mKB1zPEV_6ZDss6tOXdwQML4Wfw"
    fi

    http_auth_args="Authorization:Bearer ${api_token}"
    base_url="http://localhost:8080/api/systemInfo/v1"
    names_url="${base_url}/names"

    echo -e "${GREEN}Using names URL ${BLUE}${names_url}${NC}"
    echo

    local sys_info_name

    if [ -n "${name_arg}" ]; then
      sys_info_name="${name_arg}"
    else
      # Use fzf to get a system info name
      sys_info_name="$( \
        http --body "${names_url}" "${http_auth_args}" | 
        jq -r '.[]' | 
        sort |
        fzf --border --header="Select the System Info set to view" --height=15)"

      [ $? -eq 0 ] || error_exit "Something went wrong looking up the sys_info_name"
    fi

    info_url="${base_url}/${sys_info_name}"

    echo -e "${GREEN}Querying system info ${BLUE}${sys_info_name}${GREEN}" \
      "at\n${BLUE}${info_url}${NC}"

    http --body "${info_url}" "${http_auth_args}" |
      jq '.details'
}

main "$@"
