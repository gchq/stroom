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
# [Unreleased]: https://github.com/gchq/stroom/compare/v6.0-beta.28...6.0
# [v6.0-beta.28]: https://github.com/gchq/stroom/compare/v6.0-beta.27...v6.0-beta.28
# [v6.0-beta.27]: https://github.com/gchq/stroom/compare/v6.0-beta.26...v6.0-beta.27
# -----------------------------------------------------

set -e

# Configure the following for your github repository
# ----------------------------------------------------------
# Git tags should match this regex to be a release tag
readonly RELEASE_VERSION_REGEX='^v[0-9]+\.[0-9]+.*$'
# Example git tag for use in help text
readonly TAG_EXAMPLE='v6.0-beta.19'
# Example of a tag that is older than TAG_EXAMPLE, for use in help text
readonly PREVIOUS_TAG_EXAMPLE="${TAG_EXAMPLE//9/8}"
# The location of the change log relative to the repo root
readonly CHANGELOG_FILE='CHANGELOG.md'
readonly GITHUB_NAMESPACE='gchq'
readonly GITHUB_REPO='stroom'
readonly COMPARE_URL_EXAMPLE="https://github.com/${GITHUB_NAMESPACE}/${GITHUB_REPO}/compare/${PREVIOUS_TAG_EXAMPLE}...${TAG_EXAMPLE}"
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
    echo -e "${GREEN}This script will extract the changes from the" \
      "${BLUE}${CHANGELOG_FILE}${GREEN} file for the passed${NC}"
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
  commit_msg="$(sed "1,/^\s*##\s*\[${version}\]/d;/## \[/Q" "${CHANGELOG_FILE}")"

  # Add the release version as the top line of the commit msg, followed by
  # two new lines
  commit_msg="${version}\n\n${commit_msg}"

  # Remove any repeated blank lines with cat -s
  commit_msg="$(echo -e "${commit_msg}" | cat -s)"

  echo -e "${GREEN}You are about to create the git tag ${BLUE}${version}${GREEN}" \
    "with the following commit message.${NC}"
  echo -e "${GREEN}If there isn't anything between these lines then you should" \
    "probably add some entries to the ${BLUE}${CHANGELOG_FILE}${GREEN} first.${NC}"
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
  if [ ! -f "${CHANGELOG_FILE}" ]; then
    error_exit "The file ${BLUE}${CHANGELOG_FILE}${GREEN} does not exist in the" \
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
  if ! grep -q "^\s*##\s*\[${version}\]" "${CHANGELOG_FILE}"; then
    error_exit "Version [${BLUE}${version}${GREEN}] is not in the file" \
      "${BLUE}${CHANGELOG_FILE}${GREEN}.${NC}"
  fi
}

validate_release_date() {
  if ! grep -q "^\s*##\s*\[${version}\] - ${curr_date}" "${CHANGELOG_FILE}"; then
    error_exit "Cannot find a heading with today's date" \
      "[${BLUE}## [${version}] - ${curr_date}${GREEN}] in" \
      "${BLUE}${CHANGELOG_FILE}${GREEN}.${NC}"
  fi
}

validate_compare_link_exists() {
  if ! grep -q "^\[${version}\]:" "${CHANGELOG_FILE}"; then
    error "Version [${BLUE}${version}${GREEN}] does not have a link entry at" \
      "the bottom of the ${BLUE}${CHANGELOG_FILE}${GREEN}.${NC}"
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

do_validation() {
  validate_version_string
  validate_changelog_exists
  validate_in_git_repo
  validate_for_duplicate_tag
  validate_version_in_changelog
  validate_release_date
  validate_compare_link_exists
  validate_for_uncommitted_work
}

main() {
  setup_echo_colours
  echo

  if [ $# -ne 1 ]; then
    show_usage
    exit 1
  fi

  local version="$1"
  local curr_date
  curr_date="$(date +%Y-%m-%d)"

  do_validation

  do_release
}

main "$@"
