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

# ***********************************************************************
# A script to merge changes up the chain of release brances.
# It will checkout the first branch in the chain and ensure commits
# are pushed then checkout the next branch and merge the previous one
# in, and so on until it reaches the last branch.
# ***********************************************************************

set -e

setup_echo_colours() {
  # Exit the script on any error

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

error() {
    echo -e "${RED}ERROR${NC} $*${NC}" >&2
}

error_exit() {
    echo -e "${RED}ERROR${NC} $*${NC}" >&2
    exit 1
}

populate_branches_arr() {
  local start_branch="$1"

  # Ensure we have an accurate picture of branches available
  # on the default remote
  # --prune to delete any local branches tracking the remote where the remote
  # branch no longer exists.
  git fetch --prune

  # Add the branches that come before the release branches
  local all_branches=()
  all_branches+=( "${TAIL_BRANCHES[@]}" )

  local release_branches
  # Sort them by major then minor part
  release_branches="$( \
    git branch \
    | sed 's/..//' \
    | grep -E "^[0-9]+\.[0-9]+$" \
    | sort -t . -k 1,1n -k 2,2n )"

  for branch in ${release_branches}; do
    all_branches+=( "${branch}" )
  done

  # Add the branches that come after the release branches
  all_branches+=( "${HEAD_BRANCHES[@]}" )

  local found_start_branch=true
  if [[ -n "${start_branch}" ]]; then
    found_start_branch=false
  fi

  for branch in "${all_branches[@]}"; do
    debug_value "branch" "${branch}"
    debug_value "found_start_branch" "${found_start_branch}"

    check_branch_exists "${branch}"

    if [[ -n "${start_branch}" && "${branch}" = "${start_branch}" ]]; then
      found_start_branch=true
    fi

    # Only add branches >= the start branch
    if [[ "${found_start_branch}" = "true" ]]; then
      branches+=( "${branch}" )
    fi
  done

  if [[ "${found_start_branch}" = "false" ]]; then
    error_exit "Start branch ${start_branch} not found in list" \
      "of all valid branches [${all_branches[*]}]"
  fi

  if [[ "${#branches[@]}" -lt 2 ]]; then
    error_exit "Need at least two branches in the chain to continue."
  fi
}

validate_inside_git_repo() {
  if ! git rev-parse --is-inside-work-tree > /dev/null; then
    error "Not inside a git repository"
    exit 1
  fi
}

check_branch_exists() {
  local branch_name="$1"; shift

  if ! git show-ref --quiet "refs/heads/${branch_name}"; then
    error "Branch ${branch_name} does not exist"
    exit 1
  fi

  if ! git show-branch "remotes/${REMOTE_NAME}/${branch_name}" > /dev/null; then
    error "Branch ${branch_name} does not exist on remote ${REMOTE_NAME}"
    exit 1
  fi
}

validate_for_uncommitted_work() {
  debug "validate_for_uncommitted_work() called"
  if [ "$(git status --porcelain 2>/dev/null | wc -l)" -ne 0 ]; then
    error "There are uncommitted changes or untracked files."
    exit 1
  fi
}

merge_branch_up() {
  local source_branch="${1:?source_branch not set}"; shift
  local dest_branch="${1:?dest_branch not set}"; shift

  echo
  echo -e "${GREEN}--------------------------------------------------${NC}"
  echo -e "${GREEN}Merging up from ${BLUE}${source_branch}${GREEN}" \
    "to ${BLUE}${dest_branch}${NC}"

  checkout_branch "${dest_branch}"

  git pull \
    || error_exit "Pulling on branch ${dest_branch}"

  local is_merge_success=true
  git merge --no-edit "${source_branch}" \
    || is_merge_success=false

  if [[ "${is_merge_success}" = "true" ]]; then
    echo -e "${GREEN}Merge completed successfully${NC}"
    push_if_needed
    echo -e "${GREEN}--------------------------------------------------${NC}"
  else
    error_exit "Merge has conflicts. Fix and push the conflicts and try again."
  fi
}

checkout_branch() {
  local branch_name="${1:?branch_name not set}"; shift
    echo -e "${GREEN}Checking out branch ${BLUE}${branch_name}${NC}"
    git checkout "${branch_name}" \
      || error_exit "Checking out ${branch_name}"
}

push_if_needed() {
  local unpushed_commit_count
  unpushed_commit_count="$( \
    git cherry -v \
    | wc -l )"

  if [[ "${unpushed_commit_count}" -gt 0 ]]; then
    echo -e "${GREEN}Pushing branch ${BLUE}${curr_branch}${NC}"
    git push \
      || error_exit "Error pushing on branch ${curr_branch}"
  fi
}

confirm_branches() {

  echo -e "${GREEN}Merge_up will merge changes up this chain of branches:${NC}"
  if [[ -z "${start_branch}" ]]; then
    echo -e "${GREEN}To start at specific branch do" \
      "${BLUE}./merge_up.sh <start branch>${NC}"
  fi

  local branch_chain=""
  for branch in "${branches[@]}"; do
    branch_chain="${branch_chain}${BLUE}${branch}${GREEN} -> "
  done
  branch_chain="${branch_chain% -> }"

  echo
  echo -e "${GREEN}${branch_chain}${NC}"
  echo
  echo -e "${GREEN}It will stop if there are merge conflicts.${NC}"

  read -rsp $'Press [y|Y] to continue, or ctrl-c to exit...\n' -n1 keyPressed

  if [[ "${keyPressed}X" =~ ^(y|Y)?X$ ]] ; then
    echo
  else
    echo "Exiting"
    exit 1
  fi
}

main() {
  #SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

  setup_echo_colours

  local start_branch="$1"

  local REMOTE_NAME="origin"
  local TAIL_BRANCHES=()
  local HEAD_BRANCHES=( \
    "master" )

  local branches=()

  validate_inside_git_repo
  validate_for_uncommitted_work
  populate_branches_arr "${start_branch}"

  debug_value "branches" "${branches[*]}"

  confirm_branches

  local initial_branch
  initial_branch="$( \
    git symbolic-ref --short HEAD )"
  debug_value "initial_branch" "${initial_branch}"

  local prev_branch
  local curr_branch

  for branch in "${branches[@]}"; do
    curr_branch="${branch}"

    if [[ -n "${prev_branch}" ]]; then
      merge_branch_up "${prev_branch}" "${curr_branch}"
    else
      debug "No prev_branch, curr_branch: ${curr_branch}"
      checkout_branch "${curr_branch}"
      push_if_needed
    fi

    prev_branch="${curr_branch}"
  done

  echo
  echo -e "${GREEN}Returning to original branch${NC}"
  checkout_branch "${initial_branch}"
  echo
  echo -e "${GREEN}Done${NC}"
}

main "$@"
