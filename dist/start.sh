#!/usr/bin/env bash
#
# Copyright 2016-2025 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# Starts Stroom (Proxy)

# trap ctrl-c and call ctrl_c()
trap ctrl_c INT

function ctrl_c() {
  # User hit ctrl-c so tidy up first
  kill_log_tailing
}

echo_usage() {
  echo -e "${GREEN}This script starts ${APP_NAME}${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-h] [-m] [-q]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
  echo -e " -q:   ${GREEN}Quiet. Don't tail the logs or wait for stroom to" \
    "complete its startup${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

kill_log_tailing() {
  local cmd="tail -F ${PATH_TO_START_LOG} ${PATH_TO_APP_LOG}"
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
    # kill -0 returns true if the process is still alive
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

  # Ensure files and dirs exist for later tailing
  ensure_file_exists "${PATH_TO_START_LOG}"

  if [[ ! "${APP_NAME}" =~ [Pp]roxy ]]; then
    ensure_file_exists "${PATH_TO_MIGRATION_LOG}"
  fi

  ensure_file_exists "${PATH_TO_APP_LOG}"
  # Ensure dir exists for an OOM heap dumps to go into
  ensure_dir_exists "${HEAP_DUMP_DIR}"

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

  # Set up the JVM heap dump options if not already set
  # shellcheck disable=SC2153
  local java_opts="${JAVA_OPTS:- -Xms50m -Xmx2g}"
  if [[ ! "${java_opts}" =~ -XX:\+HeapDumpOnOutOfMemoryError ]]; then
    java_opts="${java_opts} -XX:+HeapDumpOnOutOfMemoryError"
  fi
  if [[ ! "${java_opts}" =~ -XX:HeapDumpPath ]]; then
    java_opts="${java_opts} -XX:HeapDumpPath=${HEAP_DUMP_DIR}"
  fi

  # Open some packages to the classpath.
  java_opts="${java_opts} --add-opens java.base/java.nio=ALL-UNNAMED"
  java_opts="${java_opts} --add-opens java.base/sun.nio.ch=ALL-UNNAMED"
  java_opts="${java_opts} --add-opens java.base/java.lang=ALL-UNNAMED"

  # change to the script dir so java is relative to there and not where
  # we happen to be calling the script from. Can't drop into a sub shell
  # as we need to capture the pid of the java process
  pushd "${script_dir}" > /dev/null

  info "Starting ${GREEN}${APP_NAME}${NC} in directory ${BLUE}${script_dir}${NC}"
  info "Using JVM arguments ${BLUE}${java_opts}${NC}"

  if [[ "${APP_NAME}" =~ [Pp]roxy ]]; then
    # Export STROOM_PROXY_HOME so we can use it for env var substitution in the
    # dropwizard yaml file to avoid defining it in multiple places.
    info "Setting ${YELLOW}STROOM_PROXY_HOME${NC} to" \
      "${BLUE}${STROOM_PROXY_HOME}${NC}"
    export STROOM_PROXY_HOME
  else
    # Export STROOM_HOME so we can use it for env var substitution in the
    # dropwizard yaml file to avoid defining it in multiple places.
    info "Setting ${YELLOW}STROOM_HOME${NC} to ${BLUE}${STROOM_HOME}${NC}"
    export STROOM_HOME
  fi

  # We need word splitting on java_opts so we need to disable SC2086
  # We redirect stdout to path_to_start_log so that anything that is written
  # to stdout before logback is initialised can be seen, e.g. if the log
  # file path is bad.
  # shellcheck disable=SC2086
  nohup \
    java \
    ${java_opts} \
    -jar "${absoulte_path_to_jar}" \
    server \
    "${absolute_path_to_config}" \
    &> "${PATH_TO_START_LOG}" &

  local stroom_pid="$!"

  popd > /dev/null

  info "Started ${GREEN}${APP_NAME}${NC} in the background with PID" \
    "${BLUE}${stroom_pid}${NC}."
  # Write the PID to a file to prevent stroom being started multiple times
  echo "${stroom_pid}" > "${STROOM_PID_FILE}"

  if [[ "${do_tailing}" = "true" ]]; then
    info "Tailing log files:" \
      "\n  ${BLUE}${PATH_TO_START_LOG}${NC}" \
      "\n  ${BLUE}${PATH_TO_APP_LOG}${NC}"
    info "Tailing will terminate when a 200 response is received from ${APP_NAME}'s" \
      "health check page, or a ${maxWaitSecs}s timeout is reached."
    info "Press CTRL-C to terminate log tailing only."

    # Tail the log files in the background
    tail -F "${PATH_TO_START_LOG}" "${PATH_TO_APP_LOG}" 2>/dev/null &

    # ADMIN_(PORT|PATH) are exported in scripts.env
    wait_for_200_response \
      "http://localhost:${ADMIN_PORT}/${ADMIN_PATH}/healthcheck"

    kill_log_tailing

    # display the result of the health check
    info "Checking the health of ${GREEN}${APP_NAME}${NC}"
    echo
    "${script_dir}/health.sh" "${script_args[@]}"

    echo
    # Display the banner, URLs and login details
    "${script_dir}/info.sh" "${script_args[@]}"
  else
    info "See log files:" \
      "\n  ${BLUE}${PATH_TO_START_LOG}${NC}" \
      "\n  ${BLUE}${PATH_TO_APP_LOG}${NC}"
  fi
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
        warn "${APP_NAME} is already running (pid: ${BLUE}$PID${NC}). Use" \
          "${BLUE}restart.sh${NC} if you want to start it."
        # echo -e "${RED}Warning:${NC} ${GREEN}${APP_NAME}${NC} is already running (pid: ${BLUE}$PID${NC}). Use ${BLUE}restart.sh${NC} if you want to start it."
      else
        warn "There was an instance of ${APP_NAME} running but it looks like"\
          "it wasn't stopped gracefully.\nYou might want to check the logs" \
          "in ${BLUE}${PATH_TO_APP_LOG}${NC}.\nIf you are certain it is" \
          "not running delete the file ${BLUE}${STROOM_PID_FILE}${NC}"
        echo
        read -n1 -r -p \
          "Would you like to start a new instance? (y/n)" start_new_instance
        echo -e ''
        if [ "${start_new_instance}" = 'y' ]; then
          rm "${STROOM_PID_FILE}"
          start_stroom
        else
          info "${APP_NAME} will not be started."
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
  source "${PATH_TO_UTIL_SCRIPT}"

  # UPPERCASE vars defined in config/scripts.env as relative paths so
  # assign them to local variables as children of script_dir

  local -r maxWaitSecs=240
  local do_tailing=true

  while getopts ":mhq" arg; do
    # shellcheck disable=SC2034
    case $arg in
      h )
        echo_usage
        exit 0
        ;;
      m )
        MONOCHROME=true
        ;;
      q )
        do_tailing=false
        ;;
      * )
        invalid_arguments
        ;;  # getopts already reported the illegal option
    esac
  done
  shift $((OPTIND-1)) # remove parsed options and args from $@ list

  setup_colours

  check_or_create_pid_file
}

main "$@"

# vim: set tabstop=2 shiftwidth=2 expandtab:
