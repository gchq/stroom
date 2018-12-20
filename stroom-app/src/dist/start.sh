#!/usr/bin/env bash
#
# Starts Stroom


start() {

  # We need word splitting so we need to disable SC2086
  # shellcheck disable=SC2086
  nohup \
    java ${JAVA_OPTS} -jar "${PATH_TO_JAR}" server "${PATH_TO_CONFIG}" \
    &> logs/start.sh.log &

  echo $! > "${PID_FILE}"

  # Display the banner, URLs and login details
  ./info.sh

  echo
  info "Stroom is starting up."
  echo
  info "This may take some time if the database has to be migrated."
  info "You can tail the startup logs using" \
    "'${BLUE}tail -F ./logs/start.sh.log${NC}'"
  info "You can tail the application logs using" \
    "'${BLUE}tail -F ./logs/app/app.log${NC}'"
}

main(){

  while getopts m arg; do
    # shellcheck disable=SC2034
    case $arg in
      m )  MONOCHROME=true ;;
      \? ) exit 2 ;;  # getopts already reported the illegal option
    esac
  done
  shift $((OPTIND-1)) # remove parsed options and args from $@ list

  # shellcheck disable=SC1091
  source bin/utils.sh
  # shellcheck disable=SC1091
  source config/scripts.env

  check_is_configured

  if [ ! -f "${PID_FILE}" ]; then # If there is no pid file
    start
  else # If there is a pid file we need to deal with it
    local -r PID=$(cat "$PID_FILE");

    if [ "$PID" = '' ]; then # If the pid file is empty for some reason
      start
    else 
      if ps -p "$PID" > /dev/null # Check if the PID is a running process
      then
        warn "Stroom is already running (pid: ${BLUE}$PID${NC}). Use ${BLUE}restart.sh${NC} if you want to start it."
        # echo -e "${RED}Warning:${NC} ${GREEN}Stroom${NC} is already running (pid: ${BLUE}$PID${NC}). Use ${BLUE}restart.sh${NC} if you want to start it."
      else 
        warn "There was an instance of Stroom running but it looks like"\
          "it wasn't stopped gracefully. You might want to check the logs."

        read -n1 -r -p \
          " - Would you like to start a new instance? (y/n)" start_new_instance
        echo -e ''
        if [ "$start_new_instance" = 'y' ]; then
          rm "$PID_FILE"
          start
        else 
          info "Ok. I won't start anything."
        fi
      fi
    fi
  fi
}

main "$@"
# vim:sw=2:ts=2:et:
