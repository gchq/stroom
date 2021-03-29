#!/usr/bin/env bash
#
# Restarts Stroom (Proxy)

echo_usage() {
  echo -e "${GREEN}This script restarts ${APP_NAME}${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-h] [-m]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -f:   ${GREEN}Forces an immediate shutdown by issuing a SIGKILL${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

main() {
  local script_dir
  script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" \
    >/dev/null && pwd )"

  local start_args=()
  local stop_args=()

  while getopts ":mfh" arg; do
    # shellcheck disable=SC2034
    case $arg in
      f )  
        stop_args+=( "-f" )
        ;;
      h ) 
        echo_usage
        exit 0
        ;;
      m )  
        MONOCHROME=true 
        start_args+=( "-m" )
        stop_args+=( "-m" )
        ;;
      * ) 
        invalid_arguments
        ;;  # getopts already reported the illegal option
    esac
  done
  shift $((OPTIND-1)) # remove parsed options and args from $@ list

  setup_colours

  source "${script_dir}/stop.sh" "${stop_args[@]}"
  source "${script_dir}/start.sh" "${start_args[@]}"
}

main "$@"
# vim:sw=2:ts=2:et:
