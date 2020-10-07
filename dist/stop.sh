#!/usr/bin/env bash
#
# Stops Stroom

readonly NOT_RUNNING_MESSAGE="This deployment of Stroom is not running!"

# trap ctrl-c and call ctrl_c()
trap ctrl_c INT

function ctrl_c() {
  kill_log_tailing
}

echo_usage() {
  echo -e "${GREEN}This script stops Stroom gracefully and tails the logs${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-f] [-h] [-m]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -f:   ${GREEN}Forces an immediate shutdown by issuing a SIGKILL${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

wait_for_pid_to_die() {
  local pid=$1
  # wait for the kill to finish
  # have to temporarily not stop script on errors
  set +e
  while kill -0 "${pid}" >/dev/null 2>&1; do 
    sleep 1
  done
  set -e
}

kill_log_tailing() {
  local cmd="tail -F ${PATH_TO_APP_LOG}"
  local pid
  pid="$(pgrep -fx "${cmd}")"
  # kill the log tailing
  # The bit in quotes must match the command + args used to start the
  # tailing in the first place
  info "Terminating log tailing"
  pkill -fx "${cmd}"

  # We have a pid for the tail process so wait for it to die
  if [ -n "${pid}" ]; then
    set +e
    while kill -0 "${pid}" >/dev/null 2>&1; do 
      sleep 1
    done
    set -e
    sleep 1
    info "Log tailing terminated"
  fi
}

stop_stroom() {
  if [ "$1" = "force" ]; then
    FORCE_STOP=true
  fi

  if [ ! -f "${STROOM_PID_FILE}" ]; then # If there is no pid file
    warn "${NOT_RUNNING_MESSAGE}"
  else # If there is a pid file we need to deal with it
    PID=$(cat "${STROOM_PID_FILE}");

    if [ "${PID}" = '' ]; then # If the pid file is empty for some reason
      warn "${NOT_RUNNING_MESSAGE}"
    else 
      if ps -p "${PID}" > /dev/null
      then
        PID=$(cat "${STROOM_PID_FILE}");
        if [ "${FORCE_STOP}" = true ]; then
          info "Killing ${GREEN}Stroom${NC}"
          kill -9 "${PID}";

          wait_for_pid_to_die "${PID}"

          info "Killed ${GREEN}Stroom${NC}"
        else
          info "Stopping ${GREEN}Stroom${NC} gracefully."

          # tail the log in the background
          info "Tailing log file ${BLUE}${PATH_TO_APP_LOG}${NC}"
          ensure_file_exists "${PATH_TO_APP_LOG}" 
          tail -F "${PATH_TO_APP_LOG}" 2>/dev/null &

          # issue the kill
          kill "${PID}";

          wait_for_pid_to_die "${PID}"

          kill_log_tailing

          info "Stopped ${GREEN}Stroom${NC} gracefully."
        fi

        rm "${STROOM_PID_FILE}"
        # ask_about_logs
      else 
        warn "There was an instance of Stroom running but it looks like it wasn't stopped gracefully. You might want to check the logs. If you are certain it is not running delete the file ${BLUE}${STROOM_PID_FILE}${NC}"
      fi
    fi
  fi
}

main() {
  # shellcheck disable=SC1091
  source bin/utils.sh
  # shellcheck disable=SC1091
  source config/scripts.env

  local stop_stroom_args=()

  while getopts ":mfh" arg; do
    # shellcheck disable=SC2034
    case $arg in
      f )  
        stop_stroom_args+=( "force" )
        ;;
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

  stop_stroom "${stop_stroom_args[@]}"
}

main "$@"

# vim:sw=2:ts=2:et:
