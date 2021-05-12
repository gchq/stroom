#!/bin/bash

# Shell Colour constants for use in 'echo -e'
# e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
# shellcheck disable=SC2034
{
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  NC='\033[0m' # No Colour
}

NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"  # This loads nvm

# Looks in .nvmrc for the version to use, see also version in .travis.yml
nvm use

echo 
echo -e "${GREEN}node version: ${YELLOW}$(node --version)${NC}"
echo -e "${GREEN}nvm version: ${YELLOW}$(nvm --version)${NC}"
echo -e "${GREEN}npm version: ${YELLOW}$(npm --version)${NC}"
echo -e "${GREEN}npx version: ${YELLOW}$(npx --version)${NC}"
echo -e "${GREEN}yarn version: ${YELLOW}$(yarn --version)${NC}"

echo -e "\n${GREEN}Running Yarn install${NC}"
yarn install

echo -e "\n${GREEN}Running Yarn build${NC}"
yarn build
