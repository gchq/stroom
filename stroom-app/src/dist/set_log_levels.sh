#!/bin/bash

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Script to change the log level of multiple class/packages
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

set -e


#readonly URL="http://127.0.0.1:8081/stroomAdmin/tasks/log-level"
readonly URL="http://127.0.0.1:8081/admin/tasks/log-level"
readonly CURL="curl"
readonly HTTPIE="http"
setup_colours() {
  # Shell Colour constants for use in 'echo -e'
  # e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
  # shellcheck disable=SC2034
  if [ "${MONOCHROME}" = true ]; then
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    BLUE2=''
    NC='' # No Colour
  else 
    RED='\033[1;31m'
    GREEN='\033[1;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[1;34m'
    BLUE2='\033[1;34m'
    NC='\033[0m' # No Colour
  fi
}

send_request() {
  packageOrClass=$1
  newLogLevel=$2

  echo -e "Setting ${GREEN}${packageOrClass}${NC} to ${GREEN}${newLogLevel}${NC}"
  echo

  if [ "${binary}" = "${HTTPIE}" ]; then
    local extra_httpie_args=()
    if [ "${MONOCHROME}" = true ]; then
      # Tell httpie to run in black and white
      extra_httpie_args+=( --style bw )
    fi
    ${HTTPIE} "${extra_httpie_args[@]}" --body -f POST ${URL} logger="${packageOrClass}" level="${newLogLevel}"
  else
    ${CURL} -X POST -d "logger=${packageOrClass}&level=${newLogLevel}" ${URL}
  fi
}

echo_usage() {
  echo -e "${GREEN}This script changes the log level(s) for class(es) or package(s)${NC}"
  echo -e "${GREEN}NOTE: Once a log level is set for a class/package, it can only be changed${NC}"
  echo -e "${GREEN}      and not un-set.${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-m] [-h] packageOrClass1 newLogLevel packageOrClassN newLogLevel ...${NC}"
  echo
  echo -e "e.g:   ${BLUE}$0${GREEN} stroom.startup.App TRACE stroom.security DEBUG${NC}"
  echo -e "       ${BLUE}$0${GREEN} stroom.startup.App INFO${NC}"
  echo
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

main() {
  setup_colours

  while getopts ":mh" arg; do
    # shellcheck disable=SC2034
    case $arg in
      m ) 
        MONOCHROME=true 
        ;;
      h ) 
        echo_usage
        exit 0
        ;;
      * ) 
        invalid_arguments
        ;;  # getopts already reported the illegal option
    esac
  done
  shift $((OPTIND-1)) # remove parsed options and args from $@ list

  # setup the colours again in case -m has been set
  setup_colours

  # should have an arg count that is a multiple of two
  if [ $# -eq 0 ] || [ $(( $# % 2 )) -ne 0 ]; then
    invalid_arguments
  fi

  # Check if Httpie is installed as it is preferable to curl
  if command -v "${HTTPIE}" 1>/dev/null; then 
    binary="${HTTPIE}"
  else
    #echo -e "${YELLOW}WARN${NC} - ${BLUE}httpie${NC} is not installed (see ${BLUE}https://httpie.org${NC}), falling back to ${BLUE}curl${NC}." >&2
    #echo
    binary="${CURL}"
  fi

  echo -e "Using URL ${BLUE}${URL}${NC}"
  echo

  #loop through the pairs of args
  while [ $# -gt 0 ]; do
    packageOrClass="$1"
    newLogLevel="$2"

    send_request "${packageOrClass}" "${newLogLevel}"

    #bin the two args we have just used
    shift 2
  done

  echo -e "${GREEN}Done${NC}"
}

main "$@"
# vim:sw=2:ts=2:et:
