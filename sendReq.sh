#!/bin/bash

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Script to send an API request to stroom
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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


requestFile="$1"
path=$2

if [ "x" == "x${TOKEN}" ]; then
    echo -e "${RED}ERROR${NC} - TOKEN is not set, set it like '${BLUE}export TOKEN=\".....\"${NC}' where ..... is the JWT token from 'Tools->API' Keys in stroom" 
    exit 1
fi

if [ "x" == "x${requestFile}" ] || [ "x" == "x${path}" ]; then
    echo -e "${RED}ERROR${NC} - Invalid arguments"
    echo -e "Usage: ${BLUE}$0${GREEN} file path"
    echo -e "e.g:   ${BLUE}$0${GREEN} ~/req.json /api/sqlstatistics/v2/search"
    echo -e "e.g:   ${BLUE}$0${GREEN} ~/req.json /api/stroom-index/v2/search"
    exit 1
fi

cat "$requestFile" | http POST http://localhost:8080${path} "Authorization:Bearer ${TOKEN}" 
