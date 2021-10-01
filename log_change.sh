#!/usr/bin/env bash

set -euo pipefail

setup_echo_colours() {
  # Exit the script on any error
  set -e

  # shellcheck disable=SC2034
  if [ "${MONOCHROME:-false}" = true ]; then
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

info() {
  echo -e "${GREEN}$*${NC}"
}

warn() {
  echo -e "${YELLOW}WARNING${NC}: $*${NC}" >&2
  echo
}

error() {
  echo -e "${RED}ERROR${NC}: $*${NC}" >&2
  echo
}

error_exit() {
  error "$@"
  exit 1
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


get_git_issue_from_branch() {
  # 'origin https://github.com/gchq/stroom.git (fetch)' => 'gchq stroom'
  # read the space delimited values into an array so we can split them
  local namespace_and_repo=()
  namespace_and_repo=(
    $(git remote -v \
      | grep "(fetch)" \
      | sed -r 's#.*[/:]([^/]+)/(.*)\.git \(fetch\)#\1 \2#'))

  debug_value "namespace_and_repo" "${namespace_and_repo[*]}"

  local git_namespace=""
  local git_repo=""

  if [[ "${#namespace_and_repo[@]}" -ne 2 ]]; then
    warn "Unable to parse git namespace and repo from the remote URL."
  else
    git_namespace="${namespace_and_repo[0]}"
    git_repo="${namespace_and_repo[1]}"

    debug_value "git_namespace" "${git_namespace}"
    debug_value "git_repo" "${git_repo}"
  fi

}

validate_git_issue() {
  local git_issue="$1"; shift
  debug "Validating [${git_issue}]"
  
  if [[ ! "${git_issue}" =~ ^([a-zA-Z0-9_-.]+\/[a-zA-Z0-9_-.]+\#[0-9]+|[0-9]+)$ ]]; then
    error_exit "Invalid github issue number ${BLUE}${git_issue}${NC}." \
      "Should be of the form ${BLUE}1234${NC}," \
      "${BLUE}namespace/repo#1234${NC}, ${BLUE}0${NC} or ${BLUE}AUTO${NC}."
  fi

  # TODO:  <30-09-21, AT> # hit github api to see if issue exists
}

validate_in_git_repo() {
  if ! git rev-parse --show-toplevel > /dev/null 2>&1; then
    error_exit "You are not in a git repository. This script should be run from" \
      "inside a repository.${NC}"
  fi
}

is_existing_change_file_present() {
  local git_issue="$1"; shift
  local change_text="$1"; shift


  local git_issue_str
  git_issue_str="$(format_git_issue_for_filename "${git_issue}")"

  local existing_file
  existing_file="$( \
    find \
      /home/dev/git_work/v7stroom/unreleased_changes/ \
      -maxdepth 1 \
      -name '*__1234' \
      -print \
      -quit)"

  debug_value "existing_file" "${existing_file}"

  if [[ -f "${existing_file}" ]]; then
    # File exists for this issue so open it
    info "A change entry file already exists for this issue"

    open_file_in_editor "${existing_file}"

    return 0
  else
    debug "File does not exist"
    return 1
  fi
}

format_git_issue_for_filename() {
  local git_issue="$1"; shift

  local git_issue_str
  if [[ "${git_issue}" = "0" ]]; then
    git_issue_str="NO_ISSUE"
  else
    git_issue_str="${git_issue/\#/_}"
    #debug_value "git_issue_str" "${git_issue_str}"
    git_issue_str="${git_issue_str/\//_}"
    #debug_value "git_issue_str" "${git_issue_str}"
  fi
  echo "${git_issue_str}"
}

write_change_entry() {
  local git_issue="$1"; shift
  local change_text="$1"; shift

  local date_str
  date_str="$(date --utc +%Y%m%d_%H%M%S_%3N)"

  local git_issue_str
  git_issue_str="$(format_git_issue_for_filename "${git_issue}")"
  
  # Use two underscores to help distinguish the date from the issue part
  # which may itself contain underscores.
  local filename="${date_str}__${git_issue_str}"
  local change_file="${unreleased_dir}/${filename}"

  debug_value "change_file" "${change_file}"

  if [[ -e "${change_file}" ]]; then
    error_exit "File ${BLUE}${change_file}${NC} already exists"
  fi

  local issue_prefix="Issue **"
  local issue_suffix="** : "

  local line_prefix="* "

  local issue_part
  if [[ "${git_issue}" = "0" ]]; then
    issue_part=""
  elif [[ "${git_issue}" =~ ^[0-9]+$ ]]; then
    # * Issue **#1234** : My change text
    issue_part="${issue_prefix}#${git_issue}${issue_suffix}"
  else
    # * Issue **gchq/stroom#1234** : 
    issue_part="${issue_prefix}${git_issue}${issue_suffix}"
  fi

  local line="${line_prefix}${issue_part}${change_text}"
  info "Writing file ${BLUE}${change_file}${GREEN} with content:"
  info "--------------------------------------------------------------------------------"
  info "${YELLOW}${line}${NC}"
  info "--------------------------------------------------------------------------------"

  echo "${line}" > "${change_file}"

  if [[ ! -n "${change_text}" ]]; then

    read -n 1 -s -r -p "Press any key to continue"

    # No change text so open the user's preferred editor or vi/vim if not set
    if ! open_file_in_editor "${change_file}"; then
      #rm "${change_file}"
      error_exit "Edit aborted by user. Deleting file ${BLUE}${change_file}${NC}"
    fi
  fi
}

# Return zero if the file was changed, else non-zero
open_file_in_editor() {
  local file_to_open="$1"; shift
  
  local return_code=0
  local md5_before
  md5_before="$(md5sum "${file_to_open}" | cut -d' ' -f1)"

  local editor
  editor="${VISUAL:-${EDITOR:-vi}}"

  info "Opening file ${BLUE}${file_to_open}${GREEN} in editor" \
    "(${BLUE}${editor}${GREEN})${NC}"

  read -n 1 -s -r -p "Press any key to continue"
  echo

  # Open the user's preferred editor or vi/vim if not set
  "${editor}" "${file_to_open}"

  local md5_after
  md5_after="$(md5sum "${file_to_open}" | cut -d' ' -f1)"

  if [[ "${md5_before}" = "${md5_after}" ]]; then
    debug "File unchanged"
    return 1
  else
    debug "File changed"
    return 0
  fi

  debug_value "return_code" "${return_code}" 
}

main() {
  local IS_DEBUG=true
  #local SCRIPT_DIR
  #SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
  #debug_value "SCRIPT_DIR" "${SCRIPT_DIR}"
  local UNRELEASED_DIR_NAME="unreleased_changes"

  setup_echo_colours

  if [[ $# -eq 0 ]]; then
    error "Invalid arguments"
    echo -e "Usage: $0 github_issue [change_text]" >&2
    echo -e "git_issue - GitHub issue number in one of the following formats:" >&2
    echo -e "            n - Just the issue number." >&2
    echo -e "            namespace/repo#n - Issue number on another repo." >&2
    echo -e "            AUTO - Will derive the issue number from the current branch." >&2
    echo -e "            0 - No issue exists for this change" >&2
    echo -e "change_text - The change text in github markdown format. This will be appended to" >&2
    echo -e "              change log entry" >&2
    echo -e "E.g:   $0 1234 \"Fix nasty bug\"" >&2
    echo -e "E.g:   $0 gchq/stroom#1234 \"Fix nasty bug\"" >&2
    echo -e "E.g:   $0 1234" >&2
    echo -e "E.g:   $0 AUTO \"Fix nasty bug\"" >&2
    echo -e "E.g:   $0 0 \"Fix something without an issue number\"" >&2
    exit 1
  fi

  local git_issue="$1"; shift
  local change_text="${1:-}"

  debug_value "git_issue" "${git_issue}"
  debug_value "change_text" "${change_text}"

  validate_in_git_repo
  
  if [[ "${git_issue}" = "AUTO" ]]; then
    git_issue="$(get_git_issue_from_branch)"
    debug_value "git_issue" "${git_issue}"
  else
    validate_git_issue "${git_issue}"
  fi

  local repo_root_dir
  repo_root_dir="$(git rev-parse --show-toplevel)"
  local unreleased_dir="${repo_root_dir}/${UNRELEASED_DIR_NAME}"

  mkdir -p "${unreleased_dir}"

  if ! is_existing_change_file_present "${git_issue}" "${change_text:-}"; then
    write_change_entry "${git_issue}" "${change_text:-}"
  fi
}

main "$@"

# vim: set tabstop=2 shiftwidth=2 expandtab:
