#!/usr/bin/env bash
#
# Displays info about the stack

# We shouldn't use a lib function (e.g. in shell_utils.sh) because it will
# give the directory relative to the lib script, not this script.
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

main() {
  while getopts m arg; do
    # shellcheck disable=SC2034
    case $arg in
      m )  MONOCHROME=true ;;
      \? ) exit 2 ;;  # getopts already reported the illegal option
    esac
  done
  shift $((OPTIND-1)) # remove parsed options and args from $@ list

  # shellcheck disable=SC1090
  source "$DIR"/bin/utils.sh
  # shellcheck disable=SC1090
  source "${DIR}"/config/scripts.env
  readonly HOST_IP=$(determine_host_address)

  # see if the terminal supports colors...
  no_of_colours=$(tput colors)

  # shellcheck disable=SC2086
  local banner_colour
  if [ "${MONOCHROME}" = true ]; then
    banner_colour=""
  elif test -n "${no_of_colours}" && test "${no_of_colours}" -eq 256; then
    # 256 colours so print the stroom banner in dirty orange
    #echo -en "\e[38;5;202m"
    banner_colour="\e[38;5;202m"
  else
    # No 256 colour support so fall back to blue
    #echo -en "${BLUE}"
    banner_colour="${BLUE}"
  fi
  echo -en "${banner_colour}"
  cat "${DIR}"/bin/banner.txt
  echo -en "${NC}"

  echo
  info "The local stroom is running at the following location:" 
  info "${BLUE}http://localhost:<app port>/stroom/ui${NC}"
  info "The port is available in ${BLUE}config/config.yml${NC} under the ${BLUE}server.applicationConnectors.port${NC} property."
  info "If you have a gateway configured you might not be able to access it at this address."

  echo
  info "You can access the admin page at the following location:"
  info "${BLUE}http://localhost:${STROOM_ADMIN_PORT}/stroomAdmin${NC}"

  echo
  info "Data can be POSTed to Stroom using the following URL (see README for details)"
  info "${BLUE}https://localhost:<app port>/stroom/datafeed${NC}"
}


main "$@"
# vim:sw=2:ts=2:et:
