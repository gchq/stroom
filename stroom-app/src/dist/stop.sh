#!/usr/bin/env bash
#
# Stops Stroom

readonly NOT_RUNNING_MESSAGE="This deployment of Stroom is not running!"

stop() {
  if [ ! -f "${PID_FILE}" ]; then # If there is no pid file
    warn "${NOT_RUNNING_MESSAGE}"
  else # If there is a pid file we need to deal with it
    PID=$(cat "${PID_FILE}");

    if [ "${PID}" = '' ]; then # If the pid file is empty for some reason
      warn "${NOT_RUNNING_MESSAGE}"
    else 
      if ps -p "${PID}" > /dev/null
      then
        PID=$(cat "${PID_FILE}");
        kill "${PID}";
        info "Stopping ${GREEN}Stroom${NC} gracefully."
        rm "${PID_FILE}"
        # ask_about_logs
      else 
        warn "There was an instance of Stroom running but it looks like it wasn't stopped gracefully. You might want to check the logs."
      fi
    fi
  fi
}

main() {
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

  stop
}

main "$@"

# vim:sw=2:ts=2:et:
