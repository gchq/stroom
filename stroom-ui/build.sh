#!/bin/bash

# shellcheck disable=SC2034
{
    RED='\033[1;31m'
    GREEN='\033[1;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[1;34m'
    NC='\033[0m' # No Colour
}

#top -n 1 -b -o RES | head -n 40

echo -e "${GREEN}Running Yarn install${NC}"
yarn install

echo -e "${GREEN}Running Yarn build${NC}"
yarn build
