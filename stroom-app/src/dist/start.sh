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
  local cmd="tail -F ${path_to_start_log} ${path_to_app_log}"
  local pid
  pid="$( pgrep -fx "${cmd}" )"
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

  ensure_file_exists "${path_to_start_log}" 
  ensure_file_exists "${path_to_app_log}" 
  ensure_dir_exists "${heap_dump_dir}" 

  # stroom and proxy both use this script and the same jar so use absolute
  # paths to distinguish the two processes when using the 'ps' command.
  # Also makes it explicit about what files are being used.
  local absolute_path_to_config
  local absoulte_path_to_jar
  if command -v realpath 1>/dev/null; then
    # realpath binary is available so use that
    absolute_path_to_config="$(realpath "${path_to_config}")"
    absoulte_path_to_jar="$(realpath "${path_to_jar}")"
  elif command -v readlink 1>/dev/null; then
    # readlink binary is available
    absolute_path_to_config="$(readlink -f "${path_to_config}")"
    absoulte_path_to_jar="$(readlink -f "${path_to_jar}")"
  else
    # Binaries to determine absolute path not available so fall back on just
    # using the relative path. Use of an absolute path is not critical so using
    # a relative one is preferable to the script not working.
    warn "Unable to find ${BLUE}realpath${NC} or ${BLUE}readlink${NC}" \
      "binaries.${NC}"
    warn "It is recommended to install one of these to help distinguish between" \
      "stroom and stroom-proxy processes.${NC}"
    absolute_path_to_config="${path_to_config}"
    absoulte_path_to_jar="${path_to_jar}"
  fi

  # Set up the JVM heap dump options if not already set
  # shellcheck disable=SC2153
  local java_opts="${JAVA_OPTS}"
  echo "${java_opts}"
  if [[ ! "${java_opts}" =~ -XX:\+HeapDumpOnOutOfMemoryError ]]; then
    java_opts="${java_opts} -XX:+HeapDumpOnOutOfMemoryError"
  fi
  if [[ ! "${java_opts}" =~ -XX:HeapDumpPath ]]; then
    java_opts="${java_opts} -XX:HeapDumpPath=${heap_dump_dir}"
  fi

  # change to the script dir so java is relative to there and not where
  # we happen to be calling the script from. Can't drop into a sub shell
  # as we need to capture the pid of the java process
  pushd "${script_dir}" > /dev/null
  info "Starting ${GREEN}Stroom${NC} in directory ${BLUE}${script_dir}${NC}"
  info "Using JVM arguments ${BLUE}${java_opts}${NC}"

  # We need word splitting on java_opts so we need to disable SC2086
  # We redirect stdout to path_to_start_log so that anything that is written
  # to stdout before logback is initialised can be seen, e.g. if the log
  # file path is bad.
  # shellcheck disable=SC2086
  nohup \
    java ${java_opts} \
    -jar "${absoulte_path_to_jar}" \
    server \
    "${absolute_path_to_config}" \
    &> "${path_to_start_log}" &

  local stroom_pid="$!"

  popd > /dev/null

  info "Started ${GREEN}Stroom${NC} with PID ${BLUE}${stroom_pid}${NC}"
  # Write the PID to a file to prevent stroom being started multiple times
  echo "${stroom_pid}" > "${stroom_pid_file}"

  # tail the logs in the background
  info "Tailing log files ${BLUE}${path_to_start_log}${NC} and" \
    "${BLUE}${path_to_app_log}${NC}"
  info "Tailing will terminate when a 200 response is received from Stroom's" \
    "health check page, or a ${maxWaitSecs}s timeout is reached."
  tail -F "${path_to_start_log}" "${path_to_app_log}" 2>/dev/null &
  local tailing_pid="$!"

  wait_for_200_response \
    "http://localhost:${STROOM_ADMIN_PORT}/stroomAdmin/healthcheck"

  kill_log_tailing "${tailing_pid}"

  # display the result of the health check
  info "Checking the health of ${GREEN}Stroom${NC}"
  echo
  "${script_dir}/health.sh" "${script_args[@]}"

  echo
  # Display the banner, URLs and login details
  "${script_dir}/info.sh" "${script_args[@]}"
}

check_or_create_pid_file() {
  if [ ! -f "${stroom_pid_file}" ]; then # If there is no pid file
    start_stroom
  else # If there is a pid file we need to deal with it
    local -r PID=$(cat "$stroom_pid_file");

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
          "${BLUE}${stroom_pid_file}${NC}"

        read -n1 -r -p \
          " - Would you like to start a new instance? (y/n)" start_new_instance
        echo -e ''
        if [ "${start_new_instance}" = 'y' ]; then
          rm "${stroom_pid_file}"
          start_stroom
        else 
          info "Ok. I won't start anything."
        fi
      fi
    fi
  fi
}

main() {

  local script_dir
  script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" \
    >/dev/null && pwd )"
  local script_args=( $@ )

  # shellcheck disable=SC1091
  source "${script_dir}/config/scripts.env"
  # shellcheck disable=SC1091
  source "${script_dir}/${PATH_TO_UTIL_SCRIPT}"

  # UPPERCASE vars defined in scripts.env as relative paths so
  # assign them to local variables as children of script_dir

  # shellcheck disable=SC2153
  local -r path_to_start_log="${script_dir}/${PATH_TO_START_LOG}"
  # shellcheck disable=SC2153
  local -r path_to_app_log="${script_dir}/${PATH_TO_APP_LOG}"
  # shellcheck disable=SC2153
  local -r stroom_pid_file="${script_dir}/${STROOM_PID_FILE}"
  # shellcheck disable=SC2153
  local -r path_to_config="${script_dir}/${PATH_TO_CONFIG}"
  # shellcheck disable=SC2153
  local -r path_to_conf_file="${script_dir}/${PATH_TO_CONF_FILE}"
  # shellcheck disable=SC2153
  local -r path_to_jar="${script_dir}/${PATH_TO_JAR}"
  # shellcheck disable=SC2153
  local -r heap_dump_dir="${script_dir}/${HEAP_DUMP_DIR}"

  local -r maxWaitSecs=240

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

  check_is_configured "${path_to_conf_file}"

  check_or_create_pid_file
}

main "$@"
# vim:sw=2:ts=2:et:
