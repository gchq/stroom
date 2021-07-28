#!/usr/bin/env bash
#
# Runs the DB migrations (if any) in the foreground then exits.
# Useful for migration testing or when you know there is a major migration to
# perform.
# Starting stroom will also perform the database migrations.

# trap ctrl-c and call ctrl_c()
trap ctrl_c INT

function ctrl_c() {
  # User hit ctrl-c so tidy up first
  echo 
  kill_log_tailing
  info "The migration may still be running in the background"
}

echo_usage() {
  echo -e "${GREEN}This script runs the database migrations without starting stroom.${NC}"
  echo -e "${GREEN}The migrations run in the background but the logs will be tailed${NC}"
  echo -e "${GREEN}until it completes. CTRL-C will only terminate the log tailing.${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-h] [-m] [-q]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
  echo -e " -q:   ${GREEN}Quiet. Don't tail the logs and exit as soon as the" \
    "migration is started.${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

run_migrations() {

  ensure_file_exists "${path_to_migrate_log}" 
  ensure_file_exists "${path_to_migration_log}" 

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
  local java_opts="${JAVA_OPTS:- -Xms50m -Xmx2g}"
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

  # We need word splitting on java_opts so we need to disable SC2086
  # Redirect all output to the log file which we are already tailing
  # Run it in the background with nohup as migrations can take many hours
  # Also we want ctrl-c to only kill tailing, not the migration
  # shellcheck disable=SC2086
  nohup \
    java \
    ${java_opts} \
    -jar "${absoulte_path_to_jar}" \
    migrate \
    "${absolute_path_to_config}" \
    &> "${path_to_migrate_log}" &

  local migrate_pid="$!"

  info "Running ${GREEN}${APP_NAME}${NC} database migrations in the background" \
    "with PID ${BLUE}${migrate_pid}${NC}."
  info "Logging to:" \
    "\n  ${BLUE}${path_to_migrate_log}${NC}" \
    "\n  ${BLUE}${path_to_migration_log}${NC}" \
    "\n  ${BLUE}${path_to_app_log}${NC}"
  echo
  info "Using JVM arguments ${BLUE}${java_opts}${NC}"

  if [[ "${do_tailing}" = "true" ]]; then
    info "Tailing log files:" \
      "\n  ${BLUE}${path_to_migrate_log}${NC}" \
      "\n  ${BLUE}${path_to_app_log}${NC}"
    info "Tailing will terminate when the migrate is complete."
    info "Press CTRL-C to terminate log tailing only."

    # Tail the log files in the background
    # The app log will show basic progress of the migration
    tail -F "${path_to_migrate_log}" "${path_to_app_log}" 2>/dev/null &

    # Now wait for the migration to complete
    if [ -n "${migrate_pid}" ]; then
      set +e
      # kill -0 returns true if the process is still alive
      while kill -0 "${migrate_pid}" >/dev/null 2>&1; do 
        sleep 1
      done
      set -e
      sleep 1

      info "Migrations completed, check for any errors"
    fi

    kill_log_tailing

    info "For detailed logging of the migration SQL see" \
      "${BLUE}${path_to_migration_log}${NC}"
  fi

  popd > /dev/null
}

check_or_create_pid_file() {
  if [ ! -f "${stroom_pid_file}" ]; then # If there is no pid file
    run_migrations
  else # If there is a pid file we need to deal with it
    local -r stroom_pid=$(cat "$stroom_pid_file");

    if [ "${stroom_pid}" = '' ]; then # If the pid file is empty for some reason
      run_migrations
    else 
      if ps -p "${stroom_pid}" > /dev/null # Check if the PID is a running process
      then
        warn "${APP_NAME} is already running (pid: ${BLUE}${stroom_pid}${NC}). Use" \
          "${BLUE}stop.sh${NC} first."
        # echo -e "${RED}Warning:${NC} ${GREEN}Stroom${NC} is already running (pid: ${BLUE}$PID${NC}). Use ${BLUE}restart.sh${NC} if you want to start it."
      else 
        warn "There was an instance of ${APP_NAME} running but it looks like"\
          "it wasn't stopped gracefully. You might want to check the logs." \
          "If you are certain it is not running delete the file" \
          "${BLUE}${stroom_pid_file}${NC}"

        read -n1 -r -p \
          " - Would you like to run the database migrations? (y/n)" will_run_migrations
        echo -e ''
        if [ "${will_run_migrations}" = 'y' ]; then
          rm "${stroom_pid_file}"
          start
        else 
          info "Ok. I won't run anything."
        fi
      fi
    fi
  fi
}

kill_log_tailing() {
  local cmd="tail -F ${path_to_migrate_log} ${path_to_app_log}"
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

main() {

  local script_dir
  script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" \
    >/dev/null && pwd )"

  # shellcheck disable=SC1091
  source "${script_dir}/config/scripts.env"
  # shellcheck disable=SC1091
  source "${script_dir}/${PATH_TO_UTIL_SCRIPT}"

  # UPPERCASE vars defined in config/scripts.env as relative paths so
  # assign them to local variables as children of script_dir

  # shellcheck disable=SC2153
  local -r path_to_migrate_log="${script_dir}/${PATH_TO_MIGRATE_LOG}"
  # shellcheck disable=SC2153
  local -r path_to_migration_log="${script_dir}/${PATH_TO_MIGRATION_LOG}"
  # shellcheck disable=SC2153
  local -r path_to_app_log="${script_dir}/${PATH_TO_APP_LOG}"
  # shellcheck disable=SC2153
  local -r stroom_pid_file="${script_dir}/${STROOM_PID_FILE}"
  # shellcheck disable=SC2153
  local -r path_to_jar="${script_dir}/${PATH_TO_JAR}"
  # shellcheck disable=SC2153
  local -r heap_dump_dir="${script_dir}/${HEAP_DUMP_DIR}"
  # shellcheck disable=SC2153
  local -r path_to_config="${script_dir}/${PATH_TO_CONFIG}"

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

  check_is_configured "${path_to_config}"

  check_or_create_pid_file
}

main "$@"
# vim:shiftwidth=2:tabstop=2:expandtab:
