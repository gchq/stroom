#!/usr/bin/env bash

#Exit script on first error
set -e 

# Exports config environment variables, overriding stroom.conf. This allows us to specify IP addresses more easily

#Shell Colour constants for use in 'echo -e'
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
NC='\033[0m' # No Color

if [ "$(uname)" == "Darwin" ]; then
    # Code required to find IP address is different in MacOS
    ip=$(ifconfig | grep "inet " | grep -Fv 127.0.0.1 | awk '{print $2}')
else
    ip=$(ip route get 1 |awk 'match($0,"src [0-9\\.]+") {print substr($0,RSTART+4,RLENGTH-4)}')
fi
echo
echo -e "Using IP ${GREEN}${ip}${NC} as the IP, as determined from the operating system"

stroomDir=~/.stroom
confFile=${stroomDir}/stroom.conf
templateFile=stroom.conf.template

#Ensure various dirs exist
mkdir -p ${stroomDir}
mkdir -p ${stroomDir}/plugins
mkdir -p /tmp/stroom

if [ -f $confFile ]; then
    backupFile="${stroomDir}/stroom.conf.$(date +"%Y%m%dT%H%M")"
    echo -e "Backing up ${GREEN}~/stroom.conf${NC} to ${GREEN}${backupFile}${NC}"
    cp ${confFile} ${backupFile}
    echo
fi

echo -e "Overwriting ${GREEN}${confFile}${NC} with a version templated from ${GREEN}${templateFile}${NC}"
#Use '#' delimiter in HOME_DIR sed script as HOME contains '\'
cat ${templateFile} | sed "s/IP_ADDRESS/${ip}/g" | sed "s#HOME_DIR#${HOME}#g" > ${confFile}

if [[ "x${backupFile}" != "x" ]]; then
    echo
    echo -e "Run the following to see the changes made to your stroom.conf"
    echo -e "${GREEN}diff ${backupFile} ${confFile}${NC}"
fi
