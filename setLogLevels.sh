#!/bin/bash

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Script to change the log level of multiple class/packages
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

set -e

#Shell Colour constants for use in 'echo -e'
#e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
NC='\033[0m' # No Colour 

#IMPORTANT - This script requires HTTPie so please install it.

if ! [ -x "$(command -v http)" ]; then
  echo -e "${RED}ERROR${NC} - httpie is not installed." >&2
  exit 1
fi

if [ $(( $# % 2 )) -ne 0 ]; then
    echo -e "${RED}ERROR${NC} - Invalid arguments" >&2
    echo -e "Usage: ${BLUE}$0${GREEN} packageOrClass1 newLogLevel packageOrClassN newLogLevel ...${NC}" >&2
    echo -e "e.g:   ${BLUE}$0${GREEN} stroom.startup.App DEBUG stroom.startup.Config TRACE${NC}" >&2
    exit 1
fi

#loop through the pairs of args
while [ $# -gt 0 ]; do
    packageOrClass="$1"
    newLogLevel="$2"

    echo -e "Setting ${GREEN}${packageOrClass}${NC} to ${GREEN}${newLogLevel}${NC}"
    echo

    http --headers -f POST http://127.0.0.1:8081/admin/tasks/log-level logger="${packageOrClass}" level="${newLogLevel}"

    #bin the two args we have just used
    shift 2
done

