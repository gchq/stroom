#!/usr/bin/env bash

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

echo -e "Overwriting ${GREEN}~/.stroom.conf${NC} with a version templated from ${GREEN}./stroom.conf.template${NC}"
sed "s/IP_ADDRESS/$ip/g" stroom.conf.template > ~/.stroom/stroom.conf