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
if [ -d "${NVM_DIR}" ]; then
  [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"  # This loads nvm

  # Looks in .nvmrc for the version to use, see also version in .travis.yml
  nvm use
fi

echo_version() {
  local cmd="$1"; shift
  local version
  if command -v "${cmd}" > /dev/null; then
    version="$(${cmd} --version)"
  else
    version="${RED}NOT INSTALLED${NC}"
  fi
  echo -e "${GREEN}${cmd} version: ${YELLOW}${version}${NC}"
}

echo 
echo_version nvm
echo_version node
echo_version npm
echo_version npx
echo_version yarn

echo -e "\n${GREEN}Running Yarn install${NC}"
yarn install

echo -e "\n${GREEN}Running Yarn build${NC}"
yarn build
