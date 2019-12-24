#!/usr/bin/env bash
#
# Starts Stroom

# trap ctrl-c and call ctrl_c()
trap ctrl_c INT

function ctrl_c() {
  # User hit ctrl-c so tidy up first
  kill_log_tailing
}

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

kill_log_tailing() {
  local cmd="tail -F ${path_to_start_log} ${PATH_TO_APP_LOG}"
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

wait_for_200_response() {
    if [[ $# -ne 1 ]]; then
        echo -e "${RED}Invalid arguments to wait_for_200_response(), expecting" \
          "a URL to wait for${NC}"
        exit 1
    fi

    local -r url=$1
    local -r maxWaitSecs=120
    echo

    n=0
    # Keep retrying for maxWaitSecs
    until [ "$n" -ge "${maxWaitSecs}" ]
    do
        # OR with true to prevent the non-zero exit code from curl from stopping our script
        responseCode=$(curl -sL -w "%{http_code}\\n" "${url}" -o /dev/null || true)
        #echo "Response code: ${responseCode}"
        if [[ "${responseCode}" = "200" ]]; then
            break
        fi

        n=$(( n + 1 ))
        sleep 1
    done
    #printf "\n"

    if [[ $n -ge ${maxWaitSecs} ]]; then
        warn "Gave up wating for stroom to start up, this may be due to the" \
          "database migration taking a long time."
    fi
}


start_stroom() {

  info "Starting ${GREEN}Stroom${NC}"
  ensure_file_exists "${path_to_start_log}" 
  ensure_file_exists "${PATH_TO_APP_LOG}" 

  # stroom and proxy both use this script and the same jar so use absolute
  # paths to distinguish the two processes when using the 'ps' command.
  # Also makes it explicit about what files are being used.
  local absolute_path_to_config
  local absoulte_path_to_jar
  if command -v realpath 1>/dev/null; then
    # realpath binary is available so use that
    absolute_path_to_config="$(realpath "${PATH_TO_CONFIG}")"
    absoulte_path_to_jar="$(realpath "${PATH_TO_JAR}")"
  elif command -v readlink 1>/dev/null; then
    # readlink binary is available
    absolute_path_to_config="$(readlink -f "${PATH_TO_CONFIG}")"
    absoulte_path_to_jar="$(readlink -f "${PATH_TO_JAR}")"
  else
    # Binaries to determine absolute path not available so fall back on just
    # using the relative path. Use of an absolute path is not critical so using
    # a relative one is preferable to the script not working.
    warn "Unable to find ${BLUE}realpath${NC} or ${BLUE}readlink${NC}" \
      "binaries.${NC}"
    warn "It is recommended to install one of these to help distinguish between" \
      "stroom and stroom-proxy processes.${NC}"
    absolute_path_to_config="${PATH_TO_CONFIG}"
    absoulte_path_to_jar="${PATH_TO_JAR}"
  fi

  # We need word splitting on JAVA_OPTS so we need to disable SC2086
  # shellcheck disable=SC2086
  nohup \
    java ${JAVA_OPTS} \
    -jar "${absoulte_path_to_jar}" \
    server \
    "${absolute_path_to_config}" \
    &> "${PATH_TO_APP_LOG}" &

  local stroom_pid="$!"
  info "Started ${GREEN}Stroom${NC} with PID ${BLUE}${stroom_pid}${NC}"
  # Write the PID to a file to prevent stroom being started multiple times
  echo "${stroom_pid}" > "${STROOM_PID_FILE}"

  # tail the log in the background
  info "Tailing log files ${BLUE}${path_to_start_log}${NC} and" \
    "${BLUE}${PATH_TO_APP_LOG}${NC}"
  tail -F "${path_to_start_log}" "${PATH_TO_APP_LOG}" 2>/dev/null &
  local tailing_pid="$!"

  wait_for_200_response "http://localhost:${STROOM_ADMIN_PORT}/admin/healthcheck"

  kill_log_tailing "${tailing_pid}"

  # display the result of the health check
  info "Checking the health of ${GREEN}Stroom${NC}"
  echo
  ./health.sh "${script_args[@]}"

  echo
  # Display the banner, URLs and login details
  ./info.sh "${script_args[@]}"
}

check_or_create_pid_file() {
  if [ ! -f "${STROOM_PID_FILE}" ]; then # If there is no pid file
    start_stroom
  else # If there is a pid file we need to deal with it
    local -r PID=$(cat "$STROOM_PID_FILE");

    if [ "${PID}" = '' ]; then # If the pid file is empty for some reason
      start_stroom
    else 
      if ps -p "${PID}" > /dev/null # Check if the PID is a running process
      then
        warn "Stroom is already running (pid: ${BLUE}$PID${NC}). Use" \
          "${BLUE}restart.sh${NC} if you want to start it."
        # echo -e "${RED}Warning:${NC} ${GREEN}Stroom${NC} is already running (pid: ${BLUE}$PID${NC}). Use ${BLUE}restart.sh${NC} if you want to start it."
      else 
        warn "There was an instance of Stroom running but it looks like"\
          "it wasn't stopped gracefully. You might want to check the logs." \
          "If you are certain it is not running delete the file" \
          "${BLUE}${STROOM_PID_FILE}${NC}"

        read -n1 -r -p \
          " - Would you like to start a new instance? (y/n)" start_new_instance
        echo -e ''
        if [ "${start_new_instance}" = 'y' ]; then
          rm "${STROOM_PID_FILE}"
          start
        else 
          info "Ok. I won't start anything."
        fi
      fi
    fi
  fi
}

main(){

  local script_args=( $@ )
  local -r path_to_start_log="./logs/start.sh.log"

  # shellcheck disable=SC1091
  source bin/utils.sh
  # shellcheck disable=SC1091
  source config/scripts.env

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

  check_is_configured

  check_or_create_pid_file
}

main "$@"
# vim:sw=2:ts=2:et:
