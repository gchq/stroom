#!/bin/bash

# Ensure we are in the dir where this script lives
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
pushd "${SCRIPT_DIR}" > /dev/null

echo -e "\n${GREEN}Running Yarn install${NC}"
yarn install

echo -e "\n${GREEN}Running Yarn build${NC}"
yarn build
