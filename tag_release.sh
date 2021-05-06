#!/usr/bin/env bash

# **********************************************************************
# Copyright 2021 Crown Copyright
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
# **********************************************************************


# This script is for tagging a git repository for the purpose of driving a
# separate release process from that tagged commit. It also updates the 
# changelog with details of the release.
#
# Assuming there are unreleased changes in the changelog, there are no
# untracked/staged files and the last release was v1.0.0 then the following
# will happen when run with no arguments:
#
# 1. Prompt the user for the release version, v1.0.1 will be suggested.
# 2. Validate the provided version against a regex and against existing tags.
# 3. Add a heading to the changlog for the version and add/modify the 
#    compare links.
# 4. git add, commit, push the changelog changes.
# 5. Prompt the user for conformation of the releasing tagging.
# 6. Create an annotated tag and push it to the remote.
#
# The script can be configured to some degree by means of the 
# tag_release_config.env file.
#
# The script is interactive and intended to be run by a human that can
# make decisions about what the release version number is.
#
# Usage: ./tag_release.sh [version]
# version: Only used when you want to state up front what the version will be.
#          If omitted the user will be prompted for the version with a
#          suggestion based on the last released version. 
#
# The changelog (CHANGELOG.md) should look something like this:

#   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#   |## [Unreleased]
#   |
#   |* Fix typo
#   |
#   |
#   |## [v6.0-beta.29] - 2019-02-21
#   |
#   |* Change Travis build to generate sha256 hashes for release zip/jars.
#   |
#   |* Uplift the visualisations content pack to v3.2.1
#   |
#   |* Issue **#1100** : Fix incorrect sort direction being sent to visualisations.
#   |
#   |
#   |## [v6.0-beta.28] - 2019-02-20
#   |
#   |* Add guard against race condition
#   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# And have a section at the bottom like this:

#   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#   |[Unreleased]: https://github.com/<namespace>/<repo>/compare/v6.0-beta.28...master
#   |[v6.0-beta.28]: https://github.com/<namespace>/<repo>/compare/v6.0-beta.27...v6.0-beta.28
#   |[v6.0-beta.27]: https://github.com/<namespace>/<repo>/compare/v6.0-beta.26...v6.0-beta.27
#   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# The format of the headings in compare links are critical to the parsing of
# the file.

# CHANGELOG for tag_release.sh
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#
# v1.0 2019-10-04 Check if determined version has been tagged
# v1.1 2019-10-04 Refactor to use tag_release_config.env
# v1.2 2021-05-05 Add changelog updating

set -euo pipefail

# File containing the configuration values for this script
TAG_RELEASE_CONFIG_FILENAME='tag_release_config.env'

# Configure the following for your github repository
# ----------------------------------------------------------
# Git tags should match this regex to be a release tag
RELEASE_VERSION_REGEX='^v[0-9]+\.[0-9]+.*$'
# Matches any level 2 heading
HEADING_REGEX='^## \[(.*)\]'
# Matches the [Unreleased] heading
UNRELEASED_HEADING_REGEX='^## \[Unreleased\]'
# Matches the [Unreleased]: link
UNRELEASED_LINK_REGEX='^\[Unreleased\]:'
# Matches an issue line [* ......]
ISSUE_LINE_REGEX='^\* .*'
# Finds version part but only in a '## [v1.2.3xxxxx]' heading
RELEASE_VERSION_IN_HEADING_REGEX="(?<=## \[)v[0-9]+\.[0-9]+[^\]]*(?=\])" 
# Example git tag for use in help text
TAG_EXAMPLE='v6.0-beta.19'
# Example of a tag that is older than TAG_EXAMPLE, for use in help text
PREVIOUS_TAG_EXAMPLE="${TAG_EXAMPLE//9/8}"
# The location of the change log relative to the repo root
CHANGELOG_FILENAME='CHANGELOG.md'
# The namespace/usser on github, i.e. github.com/<namespace>, should be set in tag_release_config.env
GITHUB_NAMESPACE=
# The name of the git repository on github, should be set in tag_release_config.env
GITHUB_REPO=
# The name of the git remote repo that the branch is tracking against.
GIT_REMOTE_NAME='origin'
# ----------------------------------------------------------

setup_echo_colours() {
  #Shell Colour constants for use in 'echo -e'
  # shellcheck disable=SC2034
  {
    RED='\033[1;31m'
    GREEN='\033[1;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[1;34m'
    LGREY='\e[37m'
    DGREY='\e[90m'
    NC='\033[0m' # No Color
  }
}

error() {
  echo -e "${RED}ERROR${GREEN}: $*${NC}" >&2
  echo
}

warn() {
  echo -e "${YELLOW}WARNING${GREEN}: $*${NC}" >&2
  echo
}

error_exit() {
  error "$@"
  exit 1
}

debug() {
  # To run with debug logging do;
  # IS_DEBUG_ENABLED=true ./tag_release.sh
  if [ "${IS_DEBUG_ENABLED:-false}" = true ]; then
    echo -e "${DGREY}DEBUG: $*${NC}"
  fi
}
info() {

  echo -e "${GREEN}$*${NC}"
}

show_usage() {
  {
    error "Missing version argument${NC}"
    info "Usage: ${BLUE}./tag_release.sh version"
    info "e.g:   ${BLUE}./tag_release.sh ${TAG_EXAMPLE}$"
    echo
    info "If the version argument is not supplied it will try to determine the version to release."
    echo
    info "This script will extract the changes from the" \
      "${BLUE}${changelog_file}${GREEN} file for the passed"
    info "version tag and create an annotated git commit with it." \
      "The tag commit will be pushed"
    info "to the origin."
  } >&2
}

do_tagging() {
  echo
  info "Creating annotated tag [${BLUE}${version}${GREEN}]" \
    "for the current commit"
  echo -e "${commit_msg}" | git tag -a --file - "${version}"

  info "Pushing the new tag [${BLUE}${version}${GREEN}] to origin"
  git push origin "${version}"

  info "Done"
  echo
}

do_release() {
  local last_release_tag
  last_release_tag="$( \
    grep -Po "(?<=## \[)v[^\]]+(?=\])" CHANGELOG.md \
    | head -1)"

  debug "Previous release version: ${last_release_tag}"

  local commit_msg
  # delete all lines up to and including the desired version header
  # then output all lines until quitting when you hit the next 
  # version header
  commit_msg="$(sed "1,/^\s*##\s*\[${version}\]/d;/## \[/Q" "${changelog_file}")"

  # Add the release version as the top line of the commit msg, followed by
  # two new lines
  commit_msg="${version}\n\n${commit_msg}"

  # Remove any repeated blank lines with cat -s
  commit_msg="$(echo -e "${commit_msg}" | cat -s)"

  info "\nYou are about to create the git tag ${BLUE}${version}${GREEN}" \
    "with the following commit message."
  info "If there isn't anything between these lines then you should" \
    "probably add some entries to the ${BLUE}${CHANGELOG_FILENAME}${GREEN} first."
  echo -e "${DGREY}------------------------------------------------------------------------${NC}"
  echo -e "${YELLOW}${commit_msg}${NC}"
  echo -e "${DGREY}------------------------------------------------------------------------${NC}"

  read -rsp $'Press "y" to continue, any other key to cancel.\n' -n1 keyPressed

  if [ "$keyPressed" = 'y' ] || [ "$keyPressed" = 'Y' ]; then
    do_tagging
  else
    echo
    info "Exiting without tagging a commit"
    echo
    exit 0
  fi
}

validate_version_string() {
  if [[ ! "${version}" =~ ${RELEASE_VERSION_REGEX} ]]; then
      local msgs=("Version [${BLUE}${version}${GREEN}] does not match the release"
        "version regex ${BLUE}${RELEASE_VERSION_REGEX}${NC}")
    if [[ $# -eq 1 && "${1}" = false ]]; then
      error "${msgs[@]}"
      return 1
    else
      error_exit "${msgs[@]}"
    fi
  else
    return 0
  fi
}

validate_changelog_exists() {
  if [ ! -f "${changelog_file}" ]; then
    error_exit "The file ${BLUE}${changelog_file}${GREEN} does not exist in the" \
      "current directory.${NC}"
  fi
}

validate_unreleased_heading_in_changelog() {
  if ! grep -q "${UNRELEASED_HEADING_REGEX}" "${changelog_file}"; then
    error_exit "The changelog is missing the" \
      "following heading.\n${YELLOW}## [Unreleased]${NC}"
  fi
}

validate_in_git_repo() {
  if ! git rev-parse --show-toplevel > /dev/null 2>&1; then
    error_exit "You are not in a git repository. This script should be run from" \
      "the root of a repository.${NC}"
  fi
}

validate_for_duplicate_tag() {
  if git tag | grep -q "^${version}$"; then
    local msgs=("This repository has already been tagged with"
      "[${BLUE}${version}${GREEN}].${NC}")
    if [[ $# -eq 1 && "${1}" = false ]]; then
      error "${msgs[@]}"
      return 1
    else
      error_exit "${msgs[@]}"
    fi
  else
    return 0
  fi
}

validate_version_in_changelog() {
  if ! grep -q "^\s*##\s*\[${version}\]" "${changelog_file}"; then
    error_exit "Version [${BLUE}${version}${GREEN}] is not in the file" \
      "${BLUE}${CHANGELOG_FILENAME}${GREEN}.${NC}"
  fi
}

validate_release_date() {
  if ! grep -q "^\s*##\s*\[${version}\] - ${curr_date}" "${changelog_file}"; then
    error_exit "Cannot find a heading with today's date" \
      "[${BLUE}## [${version}] - ${curr_date}${GREEN}] in" \
      "${BLUE}${CHANGELOG_FILENAME}${GREEN}.${NC}"
  fi
}

validate_compare_link_exists() {
  if ! grep -q "^\[${version}\]:" "${changelog_file}"; then
    error "Version [${BLUE}${version}${GREEN}] does not have a link entry at" \
      "the bottom of the ${BLUE}${CHANGELOG_FILENAME}${GREEN}.${NC}"
    info "e.g.:"
    echo -e "${BLUE}[${TAG_EXAMPLE}]: ${COMPARE_URL_EXAMPLE}${NC}"
    echo
    exit 1
  fi
}

validate_for_uncommitted_work() {
  if [ "$(git status --porcelain 2>/dev/null | wc -l)" -ne 0 ]; then
    error_exit "There are uncommitted changes or untracked files." \
      "Commit them before running this script.${NC}"
  fi
}

apply_custom_validation() {
  # this can be overridden in tag_release_config.env, : is a no-op
  :
}

# See if the local repo differs from the remote
validate_local_vs_remote() {
  local branch_name
  branch_name="$(git rev-parse --abbrev-ref HEAD)"

  if ! git diff --quiet "${GIT_REMOTE_NAME}/${branch_name}"; then

    warn "The local repository differs from ${GIT_REMOTE_NAME}/${branch_name}." \
      "\nTo see the differences run 'git diff ${GIT_REMOTE_NAME}/${branch_name}'"

    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    git \
      --no-pager \
      diff \
      --stat \
      "${GIT_REMOTE_NAME}/${branch_name}"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"

    read -rsp $'Press "y" to continue anyway, any other key to cancel.\n' -n1 keyPressed

    if [ "$keyPressed" = 'y' ] || [ "$keyPressed" = 'Y' ]; then
      do_tagging
    else
      echo
      info "Exiting without tagging a commit"
      echo
      exit 0
    fi
  fi
}

do_validation() {
  apply_custom_validation
  validate_version_string
  validate_for_duplicate_tag
  validate_version_in_changelog
  validate_release_date
  validate_compare_link_exists
}

determine_version_to_release() {

  info "Release version argument not supplied so we will try to" \
    "work it out from ${BLUE}${CHANGELOG_FILENAME}"
  echo

  # Find the first mastching version or return an empty string if no matches
  determined_version="$( \
    grep -oP "${RELEASE_VERSION_IN_HEADING_REGEX}" "${changelog_file}" \
    | head -n1 || echo ""
  )"

  if git tag | grep -q "^${determined_version}$"; then
    error_exit "${GREEN}The latest version in ${BLUE}${CHANGELOG_FILENAME}${GREEN}" \
      "[${BLUE}${determined_version}${GREEN}] has already been tagged in git.${NC}"
    determined_version=
  fi


  if [ -z "${determined_version}" ]; then
    info "Unable to determine the version to release from" \
      "from the changelog."
  fi

  prompt_user_for_version "${determined_version}"
}

commit_changelog() {
  local next_release_version="$1"; shift

  local changed_file_count
  changed_file_count="$(git status --porcelain | wc -l)"

  if [ "${changed_file_count}" -gt 1 ]; then
    echo 
    error "Expecting only ${BLUE}${CHANGELOG_FILENAME}${GREEN} to have" \
      "changed in git status.\nThe following uncommitted changes exist:"

    echo 
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    git status --porcelain 
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    exit 1
  fi

  if ! git diff --quiet; then
    info "The following changes have been made to the changelog:"

    echo
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    # Remove the header bits from the diff so we just have the content
    # (delete everything that is not from the matched line to the end)
    git --no-pager diff --color=always "${changelog_file}" \
      | sed '/@@/,$!d'
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo

    read -rsp $'If these are correct press "y" to continue or any other key to cancel.\n' -n1 keyPressed

    if [ ! "$keyPressed" = 'y' ] && [ ! "$keyPressed" = 'Y' ]; then
      echo -e "${RED}Aborted${NC}"
      exit 1
    fi
  fi

  info "Adding ${BLUE}${CHANGELOG_FILENAME}${GREEN} to the git index."
  git add "${changelog_file}"

  info "Committing the staged changes"
  git commit -m "Update CHANGELOG for release ${next_release_version}"

  info "Pushing the changelog changes to the remote repository"
  git push
}

modify_changelog() {
  local prev_release_version="$1"; shift
  local next_release_version="$1"; shift

  info "Adding version [${BLUE}${next_release_version}${GREEN}]" \
    "to the changelog file [${BLUE}${CHANGELOG_FILENAME}${GREEN}]"

  local new_heading
  new_heading="## [${next_release_version}] - ${curr_date}"

  # Add the new release heading after the [Unreleased] heading
  # plus some new lines \\\n\n seems to provide two new lines
  sed \
    -i'' \
    "/${UNRELEASED_HEADING_REGEX}/a \\\n\n${new_heading}" \
    "${changelog_file}"

  local compare_regex="^(\[Unreleased\]: https:\/\/github\.com\/${GITHUB_NAMESPACE}\/${GITHUB_REPO}\/compare\/)(.*)\.{3}(.*)$"

  if grep -q "^\[Unreleased\]:" "${changelog_file}" ; then
    # There is an unreleased link so modify it
    # Change the from version in the [Unreleased] link
    debug "Modifying unreleased link"
    sed \
      -E \
      -i'' \
      "s/${compare_regex}/\1${next_release_version}...\3/" \
      "${changelog_file}"
  else
    # No link so add one to the end
    debug "Appending unreleased link"
    echo -e "\n[Unreleased]: ${GITHUB_URL_BASE}/compare/${next_release_version}...master" \
      >> "${changelog_file}"
  fi

  if [ -n "${prev_release_version}" ]; then
    # We have a prev release to compare to so add in the compare link for 
    # prev release to next release
    new_link_line="[${next_release_version}]: ${GITHUB_URL_BASE}/compare/${prev_release_version}...${next_release_version}"
    debug "Appending compare link"
    sed \
      -i'' \
      "/${UNRELEASED_LINK_REGEX}/a ${new_link_line}" \
      "${changelog_file}"
  fi

  commit_changelog "${next_release_version}"
}

prompt_user_for_version() {
  local suggested_version="$1"; shift
  
  # Ask the user what version tag they want and validate what
  # they provide
  local is_valid_version_str=false
  while [[ "${is_valid_version_str}" = false ]]; do
    read \
      -e \
      -p "$(echo -e "${GREEN}Enter the tag/version for this release:${NC}")"$'\n' \
      -i "${suggested_version}" version

    if validate_version_string false && validate_for_duplicate_tag false; then
      is_valid_version_str=true
    fi
  done
}

prepare_changelog_for_release() {
  local prev_release_version="$1"; shift
  local next_release_version=""
  local next_release_version_guess=""

  info "There are unrelased changes in the changelog:\n"

  for line in "${unreleased_changes[@]}"; do
    echo -e "  ${YELLOW}${line}${NC}"
  done

  info "\nThe changelog needs to be modified for a new release" \
    "version."

  if [ -n "${prev_release_version}" ]; then
    info "\nThe last release tag/version was:" \
      "${BLUE}${prev_release_version}"


    if [[ "${prev_release_version}" =~ \.([0-9]+)$ ]]; then
      local prev_patch_part="${BASH_REMATCH[1]}"
      local next_patch_part=$((prev_patch_part + 1))

      next_release_version_guess="$( echo "${prev_release_version}" \
        | sed -E "s/\.[0-9]+$/\.${next_patch_part}/" )"

      debug "next_release_version_guess: ${next_release_version_guess}"
    fi
  fi


  if [ -n "${requested_version}" ]; then
    # User gave us the version via the arg so no need to prompt
    version="${requested_version}"
    validate_version_string
    validate_for_duplicate_tag
  else
    # Ask the user what version tag they want and validate what
    # they provide
    prompt_user_for_version "${next_release_version_guess}"
  fi

  modify_changelog "${prev_release_version}" "${version}"
}

parse_changelog() {
  local seen_unreleased_heading=false

  # Read each line of the changelog to find out what state it is in
  while read -r line; do
    #debug "line: ${line}"

    if [[ "${line}" =~ ${UNRELEASED_HEADING_REGEX} ]]; then
      #debug "line: ${line}"
      seen_unreleased_heading=true
    fi

    if [[ "${seen_unreleased_heading}" = true \
      && -z "${most_recent_release_version}" \
      && "${line}" =~ ${ISSUE_LINE_REGEX} ]]; then
      #debug "line: ${line}"
      are_unreleased_issues=true
      unreleased_changes+=( "${line}" )
    fi

    if [[ "${seen_unreleased_heading}" = true \
      && ! "${line}" =~ ${UNRELEASED_HEADING_REGEX}
      && "${line}" =~ ${HEADING_REGEX} \
      && -z "${most_recent_release_version}" ]]; then
      #debug "line: ${line}"

      # HEADING_REGEX captures the content of the heading as the first group
      most_recent_release_version="${BASH_REMATCH[1]}"
      # Got all we need so break out now
      break
    fi

  done < "${changelog_file}"

  debug "are_unreleased_issues: ${are_unreleased_issues}"
  debug "most_recent_release_version: ${most_recent_release_version}"
  debug "seen_unreleased_heading: ${seen_unreleased_heading}"
}

create_config_file() {
  info "Config file ${BLUE}${tag_release_config_file}${GREEN} does not" \
    "exist so it will be created"

  # 'EOF' quoted to avoid any expansion/substitution
  cat <<'EOF' > "${tag_release_config_file}"
# This file provides the repository specific config for the
# tag_release.sh script

# shellcheck disable=2034
{
  # The namespace/usser on github, i.e. github.com/<namespace>
  GITHUB_NAMESPACE='gchq'
  # The name of the git repository on github
  GITHUB_REPO='stroom-test-data'

  # Git tags should match this regex to be a release tag
  #RELEASE_VERSION_REGEX='^v[0-9]+\.[0-9]+.*$'

  # Finds version part but only in a '## [v1.2.3xxxxx]' heading
  #RELEASE_VERSION_IN_HEADING_REGEX="(?<=## \[)v[0-9]+\.[0-9]+[^\]]*(?=\])" 

  # Example git tag for use in help text
  #TAG_EXAMPLE='v6.0-beta.19'

  # Example of a tag that is older than TAG_EXAMPLE, for use in help text
  #PREVIOUS_TAG_EXAMPLE="${TAG_EXAMPLE//9/8}"

  # The location of the change log relative to the repo root
  #CHANGELOG_FILENAME='CHANGELOG.md'

  # If you want to run any validation that is specific to this repo then uncomment
  # this function and implement some validation
  #apply_custom_validation() {
    #echo -e "${GREEN}Applying custom validation${NC}"
    #echo
  #}

}

EOF

  # 'origin https://github.com/gchq/stroom.git (fetch)' => 'gchq stroom'
  # read the space delimited values into an array so we can split them
  local namespace_and_repo=()
  namespace_and_repo=(
    $(git remote -v \
      | grep "(fetch)" \
      | sed -r 's#.*[/:]([^/]+)/(.*)\.git \(fetch\)#\1 \2#'))

  debug "namespace_and_repo: ${namespace_and_repo[*]}"

  local git_namespace=""
  local git_repo=""

  if [[ "${#namespace_and_repo[@]}" -ne 2 ]]; then
    warn "Unable to parse git namespace and repo from the remote URL." \
      "\nYou will have to see them manually in ${BLUE}${tag_release_config_file}"
  else
    git_namespace="${namespace_and_repo[0]}"
    git_repo="${namespace_and_repo[1]}"
    info "Customising config file ${BLUE}${tag_release_config_file}"

    info "  Setting ${YELLOW}GITHUB_NAMESPACE='${git_namespace}'"
    sed \
      -i'' \
      -r \
      "s/(GITHUB_NAMESPACE=).*/\1'${git_namespace}'/" \
      "${tag_release_config_file}"

    info "  Setting ${YELLOW}GITHUB_REPO='${git_repo}'"
    sed \
      -i'' \
      -r \
      "s/(GITHUB_REPO=).*/\1'${git_repo}'/" \
      "${tag_release_config_file}"
  fi

  info "Confirm the values in the generated config file are appropriate, then" \
    "commit them to git and then finally re-run this script."
}

main() {
  setup_echo_colours

  local repo_root
  repo_root="$(git rev-parse --show-toplevel)"
  # Switch to the repo root so all git commands are run from there
  pushd "${repo_root}" > /dev/null

  local tag_release_config_file="${repo_root}/${TAG_RELEASE_CONFIG_FILENAME}"
  if [[ -f "${tag_release_config_file}" ]]; then
    # Source any repo specific config
    source "${tag_release_config_file}"
  else
    create_config_file
    exit 0
  fi

  # Need to define these here as they depend on the config file having
  # been sourced.
  # The URL format for a github compare request
  GITHUB_URL_BASE="https://github.com/${GITHUB_NAMESPACE}/${GITHUB_REPO}"
  COMPARE_URL_EXAMPLE="${GITHUB_URL_BASE}/compare/${PREVIOUS_TAG_EXAMPLE}...${TAG_EXAMPLE}"

  local changelog_file="${repo_root}/${CHANGELOG_FILENAME}"

  if [ ! -f "${tag_release_config_file}" ]; then
    error_exit "Can't find file ${BLUE}${tag_release_config_file}${NC}"
  fi

  if [ -z "${GITHUB_REPO}" ]; then
    error_exit "Variable ${BLUE}GITHUB_REPO${GREEN} has not been set" \
      "in ${BLUE}${tag_release_config_file}${NC}"
  fi

  if [ -z "${GITHUB_NAMESPACE}" ]; then
    error_exit "Variable ${BLUE}GITHUB_NAMESPACE${GREEN} has not been set" \
      "in ${BLUE}${tag_release_config_file}${NC}"
  fi

  # Ensure we have all the tags from the remote
  info "Fetching tags"
  git fetch --tags

  # Initial validation before we start modifying the changelog
  validate_changelog_exists
  validate_unreleased_heading_in_changelog
  validate_for_uncommitted_work
  validate_local_vs_remote
  validate_in_git_repo

  local most_recent_release_version=""
  local requested_version=""
  local version=""
  local unreleased_changes=()
  local are_unreleased_issues=false
  local curr_date
  curr_date="$(date +%Y-%m-%d)"

  if [ $# -gt 0 ]; then
    # version passed as argument
    requested_version="$1"
  fi

  parse_changelog

  if [[ "${are_unreleased_issues}" = true ]]; then
    # Changelog contains changes that are unreleased so need to
    # set up the new release heading in it.
    prepare_changelog_for_release "${most_recent_release_version}"
  else
    info "There are no unreleased changes in the changelog so" \
      "assuming the changelog has been prepared for a release."
    if [[ -n "${requested_version}" ]]; then
      version="${requested_version}"
    else
      determine_version_to_release
    fi
  fi

  do_validation

  do_release
}

main "$@"
