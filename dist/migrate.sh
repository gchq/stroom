#!/usr/bin/env bash
#
# Runs the DB migrations (if any) in the foreground then exits.
# Useful for migration testing or when you know there is a major migration to
# perform.
# Starting stroom will also perform the database migrations.

echo_usage() {
  echo -e "${GREEN}This script runs the database migrations without starting stroom.${NC}"
  echo -e "${GREEN}It runs in the foreground.${NC}"
  echo -e "Usage: ${BLUE}$0${GREEN} [-h] [-m]${NC}" >&2
  echo -e " -h:   ${GREEN}Print Help (this message) and exit${NC}"
  echo -e " -m:   ${GREEN}Monochrome. Don't use colours in terminal output.${NC}"
}

invalid_arguments() {
  echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
  echo_usage
  exit 1
}

run_migrations() {
  info "Running ${GREEN}Stroom${NC} database migrations. Logging to" \
    "${BLUE}${path_to_migration_log}${NC}"
  echo

  ensure_file_exists "${path_to_migration_log}" 

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
  # Use tee so we can see stdout and also log to a file
  # shellcheck disable=SC2086
  java \
    ${JAVA_OPTS} \
    -jar "${absoulte_path_to_jar}" \
    migrate \
    "${absolute_path_to_config}" \
    2>&1 \
    | tee -a \
      "${path_to_migration_log}"

  # Get exit status. Can't use $? due to use of '| tee'
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    exit 1
  fi
}


check_or_create_pid_file() {
  if [ ! -f "${STROOM_PID_FILE}" ]; then # If there is no pid file
    run_migrations
  else # If there is a pid file we need to deal with it
    local -r PID=$(cat "$STROOM_PID_FILE");

    if [ "${PID}" = '' ]; then # If the pid file is empty for some reason
      run_migrations
    else 
      if ps -p "${PID}" > /dev/null # Check if the PID is a running process
      then
        warn "Stroom is already running (pid: ${BLUE}$PID${NC}). Use" \
          "${BLUE}stop.sh${NC} first."
        # echo -e "${RED}Warning:${NC} ${GREEN}Stroom${NC} is already running (pid: ${BLUE}$PID${NC}). Use ${BLUE}restart.sh${NC} if you want to start it."
      else 
        warn "There was an instance of Stroom running but it looks like"\
          "it wasn't stopped gracefully. You might want to check the logs." \
          "If you are certain it is not running delete the file" \
          "${BLUE}${STROOM_PID_FILE}${NC}"

        read -n1 -r -p \
          " - Would you like to run the database migrations? (y/n)" will_run_migrations
        echo -e ''
        if [ "${will_run_migrations}" = 'y' ]; then
          rm "${STROOM_PID_FILE}"
          start
        else 
          info "Ok. I won't run anything."
        fi
      fi
    fi
  fi
}

main(){

  local -r path_to_migration_log="./logs/migration/migration.sh.log"

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
# vim:shiftwidth=2:tabstop=2:expandtab:
