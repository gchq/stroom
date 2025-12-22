#!/usr/bin/env bash

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

# This script will replicate all files found in gh_pages_source_dir into
# a subdirectory of the gh-pages git branch. The subdirectory name will be the
# minor version, i.e. if the version is v7.1.2 then the files will be replicated
# into ./v7.1/
# This means we will have a subdirectory for each minor version and each new
# patch release will update the content for its minor version.
# The replication from the source uses rsync to add/update/delete in the
# destination directory as required to ensure the destination subdirectory
# matches the source.
# Following replication the changes are commited and pushed so that they are
# available on github.io.
set -eo pipefail
IFS=$'\n\t'

setup_echo_colours() {
  # Exit the script on any error
  set -e

  # shellcheck disable=SC2034
  if [ "${MONOCHROME}" = true ]; then
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    BLUE2=''
    DGREY=''
    NC='' # No Colour
  else
    RED='\033[1;31m'
    GREEN='\033[1;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[1;34m'
    BLUE2='\033[1;34m'
    DGREY='\e[90m'
    NC='\033[0m' # No Colour
  fi
}

debug_value() {
  local name="$1"; shift
  local value="$1"; shift

  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${name}: ${value}${NC}"
  fi
}

debug() {
  local str="$1"; shift

  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${str}${NC}"
  fi
}

main() {
  IS_DEBUG=false

  setup_echo_colours

  local repo_uri="git@github.com:${GITHUB_REPOSITORY}.git"
  local remote_name="origin"
  local gh_pages_branch="gh-pages"
  # Dir where we clone the gh-pages branch down to
  local gh_pages_clone_dir="${GITHUB_WORKSPACE}/gh-pages-clone"
  # Dir where our new gh-pages content is to be found
  local gh_pages_source_dir="${GITHUB_WORKSPACE}/gh-pages"
  local minor_version
  minor_version=$(echo "${BUILD_TAG}" | grep -oP "^v[0-9]+\.[0-9]+")
  local gh_pages_versioned_dir="${gh_pages_clone_dir}/${minor_version}"
  local version_file="${gh_pages_versioned_dir}/version.txt"

  # Start ssh-agent and add our private ssh deploy key to it
  echo -e "${GREEN}Starting ssh-agent${NC}"
  ssh-agent -a "${SSH_AUTH_SOCK}" > /dev/null

  # SSH_DEPLOY_KEY is the private ssh key that corresponds to the public key
  # that is held in the 'deploy keys' section of the stroom repo on github
  # https://github.com/gchq/stroom/settings/keys
  ssh-add - <<< "${SSH_DEPLOY_KEY}"

  # Clone the repo with just the gh-pages branch
  echo -e "${GREEN}Cloning branch ${BLUE}${gh_pages_branch}${GREEN} to" \
    "${BLUE}${gh_pages_clone_dir}${NC}"
  git clone \
    --branch "${gh_pages_branch}" \
    --single-branch \
    "${repo_uri}" \
    "${gh_pages_clone_dir}"

  pushd "${gh_pages_clone_dir}" > /dev/null

  mkdir -p "${gh_pages_versioned_dir}"

  echo -e "${GREEN}Rsyncing gh-pages content from" \
    "${BLUE}${gh_pages_source_dir}${GREEN} to" \
    "${BLUE}${gh_pages_versioned_dir}${NC}"
  rsync \
    --verbose \
    --human-readable \
    --archive \
    --delete \
    --exclude='.git/' \
    "${gh_pages_source_dir}/" \
    "${gh_pages_versioned_dir}/"

  echo -e "${GREEN}Writing version ${BLUE}${BUILD_TAG}${GREEN} to" \
    "${BLUE}${version_file}${NC}"
  echo -e "${BUILD_TAG}" > "${version_file}"

  git config user.name "$GITHUB_ACTOR"
  git config user.email "${GITHUB_ACTOR}@bots.github.com"

  echo -e "${GREEN}Adding all new/changed files${NC}"
  git add --all

  local change_count
  change_count="$(git status --porcelain | wc -l)"
  if [[ "${change_count}" -gt 0 ]]; then
    echo -e "${GREEN}Committing changes${NC}"
    git commit \
      -a \
      -m "Updated GitHub Pages"

    #git remote set-url "$remote_name" "$repo_uri" # includes access token
    echo -e "${GREEN}Pushing changes to ${BLUE}${remote_name} ${gh_pages_branch}${NC}"
    git \
      push \
      --force-with-lease \
      "${remote_name}" \
      "${gh_pages_branch}"
  else
    echo -e "${GREEN}No changes to commit${NC}"
  fi

  popd > /dev/null
}

main "$@"
