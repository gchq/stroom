#!/usr/bin/env bash
#
# Tells the user if stroom is running or not.

echo_usage() {
  echo -e "${GREEN}This script starts Stroom${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-h] [-m]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

show_status(){
  local -r NOT_RUNNING_MESSAGE="Stroom is not running"
  local -r RUNNING_MESSAGE="Stroom is running under PID"

  if [ ! -f "${stroom_pid_file}" ]; then # If there is no pid file
    info "${NOT_RUNNING_MESSAGE}"
  else # If there is a pid file we need to deal with it
    local stroom_pid
    stroom_pid="$(cat "${stroom_pid_file}")";

    if [ "${stroom_pid}" = '' ]; then # If the pid file is empty for some reason
      info "${NOT_RUNNING_MESSAGE}"
    else 
      if ps -p "${stroom_pid}" > /dev/null
      then
        info "${RUNNING_MESSAGE} ${BLUE}${stroom_pid}${NC}"
      else
        warn "Stroom was running as PID ${BLUE}${stroom_pid}${NC} but it looks" \
          "like it stopped. You may want to check the logs to see what happened."
      fi
    fi
  fi

}

main() {
  local script_dir
  script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" \
    >/dev/null && pwd )"

  # shellcheck disable=SC1091
  source "${script_dir}/config/scripts.env"
  # shellcheck disable=SC1091
  source "${script_dir}/${PATH_TO_UTIL_SCRIPT}"

  # shellcheck disable=SC2153
  local -r stroom_pid_file="${script_dir}/${STROOM_PID_FILE}"

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

  show_status
}

main "$@"

# vim:sw=2:ts=2:et:
