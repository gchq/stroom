#!/bin/bash

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Script to send an API request to stroom
#
# To work with the json request most recently downloaded from a stroom dashboard you can do stuff like:
#
# #Edit the file in vim
# vim "$(ls -1tr ~/Downloads/DashboardQuery* | tail -n 1 | grep -oP '/home/.*\.json')"
# 
# #Send the request to stroom
# ./sendReq.sh "$(ls -1tr ~/Downloads/DashboardQuery* | tail -n 1 | grep -oP '/home/.*\.json')" /api/stroom-index/v2/search
# 
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

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
  echo -e "${RED}ERROR${NC} - ${GREEN}httpie${NC} is not installed, please install it." >&2
  exit 1
fi

if ! [ -x "$(command -v jq)" ]; then
  echo -e "${RED}ERROR${NC} - ${GREEN}jq${NC} is not installed, please install it." >&2
  exit 1
fi

if [ "x" == "x${TOKEN}" ]; then
    echo -e "${RED}ERROR${NC} - TOKEN is not set, set it like '${BLUE}export TOKEN=\".....\"${NC}' where ..... is the JWT token from 'Tools->API' Keys in stroom" 
    exit 1
fi

showUsage() {
    echo -e "${RED}ERROR${NC} - Invalid arguments"
    echo -e "Usage: ${BLUE}$0${GREEN} [-g] [-u UUID] [-i] [-h baseUrl] requestFile urlPath"
    echo -e "e.g:   ${BLUE}$0${GREEN} -g ~/req.json /api/sqlstatistics/v2/search"
    echo -e "e.g:   ${BLUE}$0${GREEN} -u query-123456 -i -h http://some.domain:8080 ~/req.json /api/stroom-index/v2/search"
    echo -e "${GREEN}-g${NC}:           Replace key.uuid with an auto-generated uuid using ${BLUE}uuidgen${NC}"
    echo -e "${GREEN}-u UUID${NC}:      Replace key.uuid with the user supplied UUID string"
    echo -e "${GREEN}-i${NC}:           Show info (uuid used, request content, file name, etc)"
    echo -e "${GREEN}-h baseUrl${NC}:   Override base URL with supplied baseUrl (e.g. \"http://some.domain:8080\")"
}

uuid=""
showInfo=false
urlBase="http://localhost:8080"

optspec="gu:ih:"
while getopts "$optspec" optchar; do
    #echo "Parsing $optchar"
    case "${optchar}" in
        g)
            uuid="$(uuidgen)"
            ;;
        u)
            if [ "x${OPTARG}" = "x" ]; then
                echo -e "${RED}-u argument requires a uuid to be specified, e.g. '${GREEN}-u 382765e6-8aaf-4c44-97a7-40372050ba45${NC}'${NC}" >&2
                echo
                showUsage
                exit 1
            fi
            uuid="${OPTARG}"
            ;;
        i)
            showInfo=true
            ;;
        h)
            if [ "x${OPTARG}" = "x" ]; then
                echo -e "${RED}-h argument requires a URL base to be specified, e.g. '${GREEN}-h http://some.domain:8080${NC}'${NC}" >&2
                echo
                showUsage
                exit 1
            fi
            urlBase="${OPTARG}"
            ;;
        *)
            echo -e "${RED}ERROR${NC} Unknown argument: '-${OPTARG}'" >&2
            echo
            showUsage
            exit 1
            ;;
    esac
done

#discard the args parsed so far
shift $((OPTIND -1))
#echo "Remaining args [${@}]"

requestFile="$1"
path=$2

if [ "x" == "x${requestFile}" ] || [ "x" == "x${path}" ]; then
    showUsage
    exit 1
fi

if [ "x" == "x${uuid}" ]; then
    uuid="$(cat "${requestFile}" | jq -r '.key.uuid')"
fi
req="$(cat "$requestFile" | jq ".key.uuid = \"${uuid}\"")"
fullUrl="${urlBase}${path}"

if ${showInfo}; then
    echo -e "Using uuid [${GREEN}${uuid}${NC}] for the request"
    echo -e "Request file [${GREEN}${requestFile}${NC}]"
    echo -e "URL [${GREEN}${fullUrl}${NC}]"

    echo -e "Request content:"
    #cat "$requestFile" | jq ".key.uuid = \"${uuid}\"" 
    #Use jq to it is sytax highlighted and pretty printed
    echo -e "${req}" | jq '.'
    echo
    echo -e "Response content:"
fi

# Disable certificate verification to cope with stroom's self-signed cert
if [[ "${fullUrl}" =~ ^https.* ]]; then
    extraHttpieArgs="${extraHttpieArgs} --verify=no"
fi

echo -e "${req}" | jq -r ".key.uuid = \"${uuid}\"" | http ${extraHttpieArgs} POST ${fullUrl} "Authorization:Bearer ${TOKEN}" 
