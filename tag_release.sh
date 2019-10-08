#!/usr/bin/env bash

# This script creates and pushes a git annotated tag with a commit message 
# taken from the appropriate versioned section of the changelog
# The changelog should look something like this:

# -----------------------------------------------------
# ## [v6.0-beta.29] - 2019-02-21
# 
# * Change Travis build to generate sha256 hashes for release zip/jars.
# 
# * Uplift the visualisations content pack to v3.2.1
# 
# * Issue **#1100** : Fix incorrect sort direction being sent to visualisations.
# 
# 
# ## [v6.0-beta.28] - 2019-02-20
# 
# * Add guard against race condition
# -----------------------------------------------------

# And have a section at the bottom like this:

# -----------------------------------------------------
# [Unreleased]: https://github.com/<namespace>/<repo>/compare/v6.0-beta.28...6.0
# [v6.0-beta.28]: https://github.com/<namespace>/<repo>/compare/v6.0-beta.27...v6.0-beta.28
# [v6.0-beta.27]: https://github.com/<namespace>/<repo>/compare/v6.0-beta.26...v6.0-beta.27
# -----------------------------------------------------


# CHANGELOG for tag_release.sh
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#
# 2019-10-04 - Check if determined version has been tagged
# 2019-10-04 - Refactor to use tag_release_config.env


set -e

# File containing the configuration values for this script
TAG_RELEASE_CONFIG_FILENAME='tag_release_config.env'


# Configure the following for your github repository
# ----------------------------------------------------------
# Git tags should match this regex to be a release tag
RELEASE_VERSION_REGEX='^v[0-9]+\.[0-9]+.*$'
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
# The URL format for a github compare request
COMPARE_URL_EXAMPLE="https://github.com/${GITHUB_NAMESPACE}/${GITHUB_REPO}/compare/${PREVIOUS_TAG_EXAMPLE}...${TAG_EXAMPLE}"
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

error_exit() {
  error "$@"
  exit 1
}

show_usage() {
  {
    error "Missing version argument${NC}"
    echo -e "${GREEN}Usage: ${BLUE}./tag_release.sh version${NC}"
    echo -e "${GREEN}e.g:   ${BLUE}./tag_release.sh ${TAG_EXAMPLE}${NC}"
    echo
    echo -e "${GREEN}If the version argument is not supplied it will try to determine the version to release.${NC}"
    echo
    echo -e "${GREEN}This script will extract the changes from the" \
      "${BLUE}${changelog_file}${GREEN} file for the passed${NC}"
    echo -e "${GREEN}version tag and create an annotated git commit with it." \
      "The tag commit will be pushed${NC}"
    echo -e "${GREEN}to the origin.${NC}"
  } >&2
}

do_tagging() {
  echo
  echo -e "${GREEN}Tagging the current commit${NC}"
  echo -e "${commit_msg}" | git tag -a --file - "${version}"

  echo -e "${GREEN}Pushing the new tag${NC}"
  git push origin "${version}"

  echo -e "${GREEN}Done.${NC}"
  echo
}

do_release() {
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

  echo -e "${GREEN}You are about to create the git tag ${BLUE}${version}${GREEN}" \
    "with the following commit message.${NC}"
  echo -e "${GREEN}If there isn't anything between these lines then you should" \
    "probably add some entries to the ${BLUE}${CHANGELOG_FILENAME}${GREEN} first.${NC}"
  echo -e "${DGREY}------------------------------------------------------------------------${NC}"
  echo -e "${YELLOW}${commit_msg}${NC}"
  echo -e "${DGREY}------------------------------------------------------------------------${NC}"

  read -rsp $'Press "y" to continue, any other key to cancel.\n' -n1 keyPressed

  if [ "$keyPressed" = 'y' ] || [ "$keyPressed" = 'Y' ]; then
    do_tagging
  else
    echo
    echo -e "${GREEN}Exiting without tagging a commit${NC}"
    echo
    exit 0
  fi
}

validate_version_string() {
  if [[ ! "${version}" =~ ${RELEASE_VERSION_REGEX} ]]; then
    error_exit "Version [${BLUE}${version}${GREEN}] does not match the release" \
      "version regex ${BLUE}${RELEASE_VERSION_REGEX}${NC}"
  fi
}

validate_changelog_exists() {
  if [ ! -f "${changelog_file}" ]; then
    error_exit "The file ${BLUE}${changelog_file}${GREEN} does not exist in the" \
      "current directory.${NC}"
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
    error_exit "This repository has already been tagged with" \
      "[${BLUE}${version}${GREEN}].${NC}"
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
    echo -e "${GREEN}e.g.:${NC}"
    echo -e "${BLUE}[${TAG_EXAMPLE}]: ${COMPARE_URL_EXAMPLE}${NC}"
    echo
    exit 1
  fi
}

validate_for_uncommitted_work() {
  if [ "$(git status --porcelain 2>/dev/null | wc -l)" -ne 0 ]; then
    error_exit "There are uncommitted changes or untracked files." \
      "Commit them before tagging.${NC}"
  fi
}

apply_custom_validation() {
  # this can be overridden in tag_release_config.env, : is a no-op
  :
}

do_validation() {
  apply_custom_validation
  validate_version_string
  validate_in_git_repo
  validate_for_duplicate_tag
  validate_version_in_changelog
  validate_release_date
  validate_compare_link_exists
  validate_for_uncommitted_work
}

determine_version_to_release() {

  echo -e "${GREEN}Release version argument not supplied so we will try to" \
    "work it out from ${BLUE}${CHANGELOG_FILENAME}${NC}"
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

  if [ -n "${determined_version}" ]; then
    # Found a version so seek confirmation

    # Extract the date from the version heading
    local release_date
    release_date="$( \
      grep -oP \
        "(?<=##\s\[${determined_version}\]\s-\s)\d{4}-\d{2}-\d{2}" \
        "${changelog_file}"
    )"

    echo -e "${GREEN}Determined release to be" \
      "[${BLUE}${determined_version}${GREEN}] with date" \
      "[${BLUE}${release_date}${GREEN}]${NC}"
    echo

    read -rsp $'If this is correct press "y" to continue or any other key to cancel.\n' -n1 keyPressed

    if [ ! "$keyPressed" = 'y' ] && [ ! "$keyPressed" = 'Y' ]; then
      show_usage
      exit 1
    fi
    echo
  fi
}

main() {
  setup_echo_colours
  echo

  local repo_root
  repo_root="$(git rev-parse --show-toplevel)"

  local tag_release_config_file="${repo_root}/${TAG_RELEASE_CONFIG_FILENAME}"

  # Source any repo specific config
  source "${tag_release_config_file}"
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

  validate_changelog_exists

  local version

  if [ $# -ne 1 ]; then
    determine_version_to_release
  fi

  if [ -n "${determined_version}" ]; then
    version="${determined_version}"
  else
    if [ $# -ne 1 ]; then
      # no arg supplied and we couldn't determine the version so bomb out
      show_usage
      exit 1
    fi
    version="$1"
  fi

  local curr_date
  curr_date="$(date +%Y-%m-%d)"

  do_validation

  do_release
}

main "$@"
