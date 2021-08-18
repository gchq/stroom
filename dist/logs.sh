#!/usr/bin/env bash
#
# Shows Stroom (Proxy)'s application logs

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

echo_usage() {
  echo -e "${GREEN}This script tails ${APP_NAME}'s application log file${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-h] [-m]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
}

main() {
  local script_dir
  script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" \
    >/dev/null && pwd )"

  # shellcheck disable=SC1091
  source "${script_dir}/config/scripts.env"
  # shellcheck disable=SC1091
  source "${PATH_TO_UTIL_SCRIPT}"

  local app_log_dir
  app_log_dir="$( dirname "${PATH_TO_APP_LOG}" )"

  while getopts ":mh" arg; do
    # shellcheck disable=SC2034
    case $arg in
      h ) 
        echo_usage
        exit 0
        ;;
      m )  
        MONOCHROME=true 
        ;;
      * ) 
        invalid_arguments
        ;;  # getopts already reported the illegal option
    esac
  done
  shift $((OPTIND-1)) # remove parsed options and args from $@ list

  setup_colours

  # ensure the app log dir and file exists so we can tail it
  mkdir -p "${app_log_dir}"
  touch "${PATH_TO_APP_LOG}"

  info "Tailing log file ${BLUE}${PATH_TO_APP_LOG}${NC}"

  tail -F "${PATH_TO_APP_LOG}"
}

main "$@"
# vim:sw=2:ts=2:et:
