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
# Stops Stroom (Proxy)

# trap ctrl-c and call ctrl_c()
trap ctrl_c INT

function ctrl_c() {
  kill_log_tailing
}

echo_usage() {
  echo -e "${GREEN}This script stops ${APP_NAME} gracefully and tails the logs${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-f] [-h] [-m] [-q]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -f:   ${GREEN}Forces an immediate shutdown by issuing a SIGKILL${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
  echo -e " -q:   ${GREEN}Quiet. Don't tail the logs or wait for stroom to" \
    "complete its shutdown${NC}"
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
    # kill -0 returns true if the process is still alive
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
    local force_stop=true
  fi

  if [ ! -f "${STROOM_PID_FILE}" ]; then # If there is no pid file
    warn "${NOT_RUNNING_MESSAGE}"
  else # If there is a pid file we need to deal with it
    local stroom_pid
    stroom_pid=$(cat "${STROOM_PID_FILE}");

    if [ "${stroom_pid}" = '' ]; then # If the pid file is empty for some reason
      warn "${NOT_RUNNING_MESSAGE}"
    else
      if ps -p "${stroom_pid}" > /dev/null
      then
        stroom_pid=$(cat "${STROOM_PID_FILE}");
        if [ "${force_stop}" = true ]; then
          info "Killing ${GREEN}${APP_NAME}${NC}"
          kill -9 "${stroom_pid}";

          wait_for_pid_to_die "${stroom_pid}"

          info "Killed ${GREEN}${APP_NAME}${NC}"
        else
          info "Stopping ${GREEN}${APP_NAME}${NC} gracefully."

          if [[ "${do_tailing}" = "true" ]]; then
            # tail the log in the background
            info "Tailing log file ${BLUE}${PATH_TO_APP_LOG}${NC}"
            info "Press CTRL-C to terminate log tailing only."
            ensure_file_exists "${PATH_TO_APP_LOG}"
            tail -F "${PATH_TO_APP_LOG}" 2>/dev/null &
          fi

          # issue the kill
          kill "${stroom_pid}";

          if [[ "${do_tailing}" = "true" ]]; then
            wait_for_pid_to_die "${stroom_pid}"

            kill_log_tailing

            info "Stopped ${GREEN}${APP_NAME}${NC} gracefully."
          else
            info "${GREEN}${APP_NAME}${NC} is stopping in the background."
            info "See log file ${BLUE}${PATH_TO_APP_LOG}${NC}"
          fi
        fi

        # Removing the pid file if we haven't waited for the shutdown is not
        # ideal
        rm "${STROOM_PID_FILE}"
        # ask_about_logs
      else
        warn "There was an instance of ${APP_NAME} running with process ID" \
          "${BLUE}${stroom_pid}${NC} but it looks like it" \
          "wasn't stopped gracefully.\nYou might want to check the logs.\nIf" \
          "you are certain it is not running delete the file" \
          "${BLUE}${STROOM_PID_FILE}${NC}"
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
  source "${PATH_TO_UTIL_SCRIPT}"

  readonly NOT_RUNNING_MESSAGE="This deployment of ${APP_NAME} is not running!"

  local stop_stroom_args=()

  local do_tailing=true

  while getopts ":fhmq" arg; do
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

  stop_stroom "${stop_stroom_args[@]}"
}

main "$@"

# vim:sw=2:ts=2:et:
