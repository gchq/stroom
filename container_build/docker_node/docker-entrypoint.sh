#!/bin/bash
#
# Copyright 2016-2025 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

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

# Re-set permission to the `stroom` user if current user is root
# This avoids permission denied if the data volume is mounted by root
#if [ "$1" = 'stroom' -a "$(id -u)" = '0' ]; then
if [ "$(id -u)" = '0' ]; then
  user="${CONTAINER_USER_ID}"

  echo "Using user id ${user}"

  # This is a bit of a cludge to get round "Text file in use" errors
  # See: https://github.com/moby/moby/issues/9547
  # sync ensures all disk writes are persisted
  #sync

  #su-exec is the alpine equivalent of gosu
  #runs all args as user stroom, rather than as root
  exec su-exec "${user}" "$@"
fi

exec "$@"
