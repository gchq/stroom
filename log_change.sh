#!/usr/bin/env bash

##########################################################################
# Version: v0.3.0
# Date: 2021-11-04T10:17:38+00:00
#
# Script to record changelog entries in individual files to get around
# the issue of merge conflicts on the CHANGELOG file when doing PRs.
#
# Credit for this idea goes to 
# https://about.gitlab.com/blog/2018/07/03/solving-gitlabs-changelog-conflict-crisis/
# 
# Change log entries are stored in files in <repo_root>/unreleased_changes
# This script is used in conjunction with tag_release.sh which adds the
# change entries to the CHANGELOG at release time.
##########################################################################

set -euo pipefail

IS_DEBUG=${IS_DEBUG:-false}
UNRELEASED_DIR_NAME="unreleased_changes"

# File containing the configuration values for this script
TAG_RELEASE_CONFIG_FILENAME='tag_release_config.env'
TAG_RELEASE_SCRIPT_FILENAME='tag_release.sh'

# e.g
# * Fix bug
# Used to look for lines that might be a change entry
ISSUE_LINE_SIMPLE_PREFIX_REGEX="^\* [A-Z]"

# e.g.
# * Issue **#1234** : 
# * Issue **gchq/stroom-resources#104** : 
# https://regex101.com/r/VcvbFV/1
ISSUE_LINE_NUMBERED_PREFIX_REGEX="^\* (Issue \*\*([a-zA-Z0-9_\-.]+\/[a-zA-Z0-9_\-.]+\#[0-9]+|#[0-9]+)\*\* : )"

# https://regex101.com/r/Pgvckt/1
ISSUE_LINE_TEXT_REGEX="^[A-Z].+\.$"

# Lines starting with a word in the past tense
PAST_TENSE_FIRST_WORD_REGEX='^(Add|Allow|Alter|Attempt|Chang|Copi|Correct|Creat|Disabl|Extend|Fix|Import|Improv|Increas|Inherit|Introduc|Limit|Mark|Migrat|Modifi|Mov|Preferr|Recognis|Reduc|Remov|Renam|Reorder|Replac|Restor|Revert|Stopp|Supersed|Switch|Turn|Updat|Upgrad)ed[^a-z]'

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
}

error() {
  echo -e "${RED}ERROR${NC}: $*${NC}" >&2
}

error_exit() {
  error "$@"
  exit 1
}

debug_value() {
  local name="$1"; shift
  local value="$1"; shift
  
  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${name}: [${value}]${NC}"
  fi
}

debug() {
  local str="$1"; shift
  
  if [ "${IS_DEBUG}" = true ]; then
    echo -e "${DGREY}DEBUG ${str}${NC}"
  fi
}

get_git_issue_from_branch() {

  local current_branch
  current_branch="$(git rev-parse --abbrev-ref HEAD)"

  local git_issue_from_branch
  git_issue_from_branch="$( \
    echo "${current_branch}" \
    | grep \
      --only-matching \
      --perl-regexp '(?<=^gh-)[1-9][0-9]*' \
      )"

  if [[ -z "${git_issue_from_branch}" ]]; then
    error_exit "Unable to establish GitHub issue number from" \
      "branch ${BLUE}${current_branch}${NC}"
  fi
  echo "${git_issue_from_branch}"
}

validate_git_issue() {
  local git_issue="$1"; shift
  debug "Validating [${git_issue}]"
  
  if [[ ! "${git_issue}" =~ ^([_.a-zA-Z0-9-]+\/[_.a-zA-Z0-9-]+\#[0-9]+|[0-9]+)$ ]]; then
    error_exit "Invalid github issue number ${BLUE}${git_issue}${NC}." \
      "Should be of the form ${BLUE}1234${NC}," \
      "${BLUE}namespace/repo#1234${NC}, ${BLUE}0${NC} or ${BLUE}auto${NC}."
  fi

  # global scope
  issue_title=""

  if [[ ! "${git_issue}" = "0" ]]; then
    if [[ "${git_issue}" =~ ^[1-9][0-9]*$ ]]; then
      # Issue in this repo so use the values we got from the local repo
      issue_namespace="${GITHUB_NAMESPACE}"
      issue_repo="${GITHUB_REPO}"
      issue_number="${git_issue}"
    else
      # Fully qualified issue so extract the parts by replacing / and # with
      # space then reading into an array which will split on space
      local parts=()
      IFS=" " read -r -a parts <<< "${git_issue//[\#\/]/ }"
      issue_namespace="${parts[0]}"
      issue_repo="${parts[1]}"
      issue_number="${parts[2]}"
    fi

    debug_value "issue_namespace" "${issue_namespace}"
    debug_value "issue_repo" "${issue_repo}"
    debug_value "issue_number" "${issue_number}"

    local github_issue_api_url="https://api.github.com/repos/${issue_namespace}/${issue_repo}/issues/${issue_number}"

    debug_value "github_issue_api_url" "${github_issue_api_url}"

    local curl_return_code=0
    # Turn off exit on error so we can get the curl return code in the subshell
    set +e 

    if command -v jq >/dev/null 2>&1; then
      # jq is available so use it
      issue_title="$( \
        curl \
          --silent \
          --fail \
          "${github_issue_api_url}" \
        | jq \
          --raw-output \
          '.title' \
      )"
      curl_return_code=$?
    else
      # no jq so fall back to grep
      issue_title="$( \
        curl \
          --silent \
          --fail \
          "${github_issue_api_url}" \
        | grep \
          --only-matching \
          --prl-regexp \
          '(?<="title": ").*(?=",)' \
      )"
      curl_return_code=$?
    fi
    set -e

    debug_value "curl_return_code" "${curl_return_code}"

    # curl_return_code is NOT the http status, just sucess/fail
    if [[ "${curl_return_code}" -ne 0 ]]; then
      # curl failed so check to see what the status code was
      local http_status_code
      http_status_code="$( \
        curl \
          --silent \
          --output /dev/null \
          --write-out "%{http_code}" \
          "${github_issue_api_url}"\
      )"
      debug_value "http_status_code" "${http_status_code}"

      if [[ "${http_status_code}" = "404" ]]; then
        error_exit "Issue ${BLUE}${git_issue}${NC} does not exist on" \
          "${BLUE}github.com/(${issue_namespace}/${issue_repo}${NC}"
      else
        warn "Unable to obtain issue title for issue ${BLUE}${issue_number}${NC}" \
          "from ${BLUE}github.com/(${issue_namespace}/${issue_repo}${NC}" \
          "(HTTP status: ${BLUE}${http_status_code}${NC})"
        issue_title=""
      fi
    else
      info "Issue title: ${BLUE}${issue_title}${NC}"
    fi
  fi
}

validate_in_git_repo() {
  if ! git rev-parse --show-toplevel > /dev/null 2>&1; then
    error_exit "You are not in a git repository. This script should be run from" \
      "inside a repository.${NC}"
  fi
}

validate_change_text_arg() {
  local change_text="$1"; shift

  if ! grep --quiet --perl-regexp "${ISSUE_LINE_TEXT_REGEX}" <<< "${change_text}"; then
    error "The change entry text is not valid"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo -e "${YELLOW}${change_text}${NC}"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo -e "Validation regex: ${BLUE}${ISSUE_LINE_TEXT_REGEX}${NC}"
    exit 1
  fi

  if ! validate_tense "${change_text}"; then
    error "The change entry text should be in the imperitive mood" \
      "\ni.e. \"Fix nasty bug\" rather than \"Fixed nasty bug\""
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo -e "${YELLOW}${change_text}${NC}"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    exit 1
  fi
}

validate_tense() {
  debug "validate_tense()"
  local change_text="$1"; shift
  debug_value "change_text" "${change_text}"

  if [[ "${IS_TENSE_VALIDATED:-true}" = true ]]; then
    if echo "${change_text}" | grep --quiet --perl-regexp "${PAST_TENSE_FIRST_WORD_REGEX}"; then
      debug "Found past tense first word"
      return 1
    else
      debug "Tense validated ok"
      return 0
    fi
  else
    debug "Tense validation disabled"
    return 0
  fi
}

is_existing_change_file_present() {
  local git_issue="$1"; shift
  local change_text="$1"; shift

  local git_issue_str
  git_issue_str="$(format_git_issue_for_filename "${git_issue}")"

  local existing_files=()

  for existing_file in "${unreleased_dir}"/*__"${git_issue_str}".md; do
    if [[ -f "${existing_file}" ]]; then
      debug_value "existing_file" "${existing_file}"
      local filename
      filename="$(basename "${existing_file}" )"
      existing_files+=( "${filename}" )
    fi
  done

  debug_value "existing_files" "${existing_files[@]:-()}"

  local existing_file_count="${#existing_files[@]}"
  debug_value "existing_file_count" "${existing_file_count}"

  if [[ "${existing_file_count}" -eq 0 ]]; then
    debug "File does not exist"
    return 1
  else 
    # Multiple files exist for this 
    debug "${existing_file_count} files exist"

    info "Change file(s) already exist for this issue:"
    echo

    list_unreleased_changes "${git_issue_str}"

    echo
    echo "Do you want to create a new change file for the issue or open an existing one?"
    echo "If it is a different change tied to the same issue then you should create a new"
    echo "file to avoid merge conflicts."

    # Build the menu options
    local menu_item_arr=()
    menu_item_arr+=( "Create new file" )
    for filename in "${existing_files[@]}"; do
      menu_item_arr+=( "Open ${filename}" )
    done

    # Present the user with a menu of options in a single column
    COLUMNS=1
    select user_input in "${menu_item_arr[@]}"; do
      if [[ "${user_input}" = "Create new file" ]]; then
        write_change_entry "${git_issue}" "${change_text:-}"
        break
      elif [[ "${user_input}" =~ ^Open ]]; then
        local chosen_file_name="${user_input#Open }"
        debug_value "chosen_file_name" "${chosen_file_name}"
        open_file_in_editor "${unreleased_dir}/${chosen_file_name}" "${git_issue}"
        break
      else
        echo "Invalid option. Try another one."
        continue
      fi
    done

    return 0
  fi
}

format_git_issue_for_filename() {
  local git_issue="$1"; shift

  local git_issue_str
  if [[ "${git_issue}" = "0" ]]; then
    git_issue_str="0"
  else
    # replace / and # with _
    git_issue_str="${git_issue//[\#\/]/_}"
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
  local filename="${date_str}__${git_issue_str}.md"
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


  local change_entry_line="${line_prefix}${issue_part}${change_text}"
  local all_content

  # Craft the content of the file
  # shellcheck disable=SC2016
  all_content="$( \
    echo "${change_entry_line}" 
    echo
    echo
    echo '```sh'
    if [[ -n "${issue_title:-}" ]]; then
      local github_issue_url="https://github.com/${issue_namespace}/${issue_repo}/issues/${issue_number}"
      echo "# ********************************************************************************"
      echo "# Issue title: ${issue_title}"
      echo "# Issue link:  ${github_issue_url}"
      echo "# ********************************************************************************"
      echo
    fi
    echo "# ONLY the top line will be included as a change entry in the CHANGELOG."
    echo "# The entry should be in GitHub flavour markdown and should be written on a SINGLE"
    echo "# line with no hard breaks. You can have multiple change files for a single GitHub issue."
    echo "# The  entry should be written in the imperative mood, i.e. 'Fix nasty bug' rather than"
    echo "# 'Fixed nasty bug'."
    echo "#"
    echo "# Examples of acceptable entries are:"
    echo "#"
    echo "#"
    echo "# * Issue **123** : Fix bug with an associated GitHub issue in this repository"
    echo "#"
    echo "# * Issue **namespace/other-repo#456** : Fix bug with an associated GitHub issue in another repository"
    echo "#"
    echo "# * Fix bug with no associated GitHub issue."
    echo '```'
  )"

  info "Writing file ${BLUE}${change_file}${GREEN}:"
  info "${DGREY}------------------------------------------------------------------------${NC}"
  info "${YELLOW}${change_entry_line}${NC}"
  info "${DGREY}------------------------------------------------------------------------${NC}"

  echo -e "${all_content}" > "${change_file}"

  if [[ -z "${change_text}" ]]; then
    open_file_in_editor "${change_file}" "${git_issue}"
  fi
}

# Return zero if the file was changed, else non-zero
open_file_in_editor() {
  local file_to_open="$1"; shift
  local git_issue="$1"; shift
  
  local editor
  editor="${VISUAL:-${EDITOR:-vi}}"

  local is_first_pass=true

  info "Opening file ${BLUE}${file_to_open}${GREEN} in editor" \
    "(${BLUE}${editor}${GREEN})${NC}"

  while true; do
    if [[ "${is_first_pass}" = true ]]; then
      read -n 1 -s -r -p "Press any key to open the file"
    else
      read -n 1 -s -r -p "Press any key to re-open the file"
      # Extra line break for subsequent passes to separate them
      echo
    fi

    echo
    echo

    # Open the user's preferred editor or vi/vim if not set
    "${editor}" "${file_to_open}"

    if validate_issue_line_in_file "${file_to_open}" "${git_issue}"; then
      # Happy with the file so break out of loop
      info "File passed validation"
      break;
    fi
    is_first_pass=false
  done
}

validate_issue_line_in_file() {
  debug "validate_issue_line_in_file ($*)"
  local change_file="$1"; shift
  local git_issue="$1"; shift

  debug "Validating file ${change_file}"
  debug_value "git_issue" "${git_issue}"

  local issue_line_prefix_regex
  if [[ "${git_issue}" = "0" ]]; then
    issue_line_prefix_regex="${ISSUE_LINE_SIMPLE_PREFIX_REGEX}"
  else
    issue_line_prefix_regex="${ISSUE_LINE_NUMBERED_PREFIX_REGEX}"
  fi
  debug_value "issue_line_prefix_regex" "${issue_line_prefix_regex}"

  local issue_line_count
  issue_line_count="$( \
    grep \
      --count \
      --perl-regexp \
      "${issue_line_prefix_regex}" \
      "${change_file}" \
    || true
    )"

  debug_value "issue_line_count" "${issue_line_count}"

  if [[ "${issue_line_count}" -eq 0 ]]; then
    error "No change entry line found in ${BLUE}${change_file}${NC}"
    echo -e "Line prefix regex: ${BLUE}${issue_line_prefix_regex}${NC}"
    return 1
  elif [[ "${issue_line_count}" -gt 1 ]]; then
    local matching_change_lines
    matching_change_lines="$(grep --perl-regexp "${issue_line_prefix_regex}" "${change_file}" )"
    error "More than one entry lines found in ${BLUE}${change_file}${NC}:"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo -e "${YELLOW}${matching_change_lines}${NC}"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo -e "Line prefix regex: ${BLUE}${issue_line_prefix_regex}${NC}"
    return 1
  else
    # Found one issue line which should be on the top line so validate it
    local issue_line
    issue_line="$(head -n1 "${change_file}")"
    local issue_line_text

    if [[ "${git_issue}" = "0" ]]; then
      # Line should look like
      # * Fix something.
      # Delete the prefix part
      issue_line_text="${issue_line#* }"
    else
      # Line should look like one of
      # * Issue **#1234** : Fix something.
      # * Issue **gchq/stroom-resources#104** : Fix something.
      # Delete the prefix part
      issue_line_text="${issue_line#*: }"
    fi

    debug_value "issue_line" "${issue_line}"
    debug_value "issue_line_text" "${issue_line_text}"

    if ! echo "${issue_line_text}" | grep --quiet --perl-regexp "${ISSUE_LINE_TEXT_REGEX}"; then
      error "The change entry text is not valid in ${BLUE}${change_file}${NC}:"
      echo -e "${DGREY}------------------------------------------------------------------------${NC}"
      echo -e "${YELLOW}${issue_line_text}${NC}"
      echo -e "${DGREY}------------------------------------------------------------------------${NC}"
      echo -e "Validation regex: ${BLUE}${ISSUE_LINE_TEXT_REGEX}${NC}"
      return 1
    fi

    if ! validate_tense "${issue_line_text}"; then
      error "The change entry text should be in the imperitive mood" \
        "\ni.e. \"Fix nasty bug\" rather than \"Fixed nasty bug\""
      echo -e "${DGREY}------------------------------------------------------------------------${NC}"
      echo -e "${YELLOW}${issue_line_text}${NC}"
      echo -e "${DGREY}------------------------------------------------------------------------${NC}"
      return 1
    fi
  fi
}

list_unreleased_changes() {

  # if no git issue is provided use a wildcard so we can get all issues
  local git_issue_str="${1:-*}"; shift
  debug_value "git_issue_str" "${git_issue_str}"
  local found_change_files=false
  local list_output=""

  for file in "${unreleased_dir}/"*__${git_issue_str}.md; do
    if [[ -f "${file}" ]]; then
      local filename
      local change_entry_line

      found_change_files=true
      filename="$(basename "${file}" )"

      change_entry_line="$( \
        head \
          -n1 \
          "${file}" \
      )"
      list_output+="${BLUE}${filename}${NC}:\n${change_entry_line}\n\n"
    fi
  done

  #if [[ "${#entry_map[@]}" -gt 0 ]]; then
  if [[ "${found_change_files}" = true ]]; then
    #for filename in "${!MYMAP[@]}"; do echo $K; done

    # Remove the trailing blank lines
    list_output="$(echo -e "${list_output}" | head -n-2 )"

    echo -e "${list_output}"
  else
    info "There are no unreleased changes"
  fi
}

main() {
  #local SCRIPT_DIR
  #SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
  #debug_value "SCRIPT_DIR" "${SCRIPT_DIR}"

  setup_echo_colours

  if [[ $# -eq 0 ]]; then
    error "Invalid arguments"
    echo -e "Usage: $0 github_issue [change_text]" >&2
    echo -e "git_issue - GitHub issue number in one of the following formats:" >&2
    echo -e "            0 - No issue exists for this change" >&2
    echo -e "            n - Just the issue number." >&2
    echo -e "            namespace/repo#n - Issue number on another repo." >&2
    echo -e "            auto - Will derive the issue number from the current branch." >&2
    echo -e "            list - List all unreleased issues." >&2
    echo -e "change_text - The change text in github markdown format. This will be appended to" >&2
    echo -e "              change log entry" >&2
    echo -e "E.g:   $0 1234 \"Fix nasty bug\"" >&2
    echo -e "E.g:   $0 gchq/stroom#1234 \"Fix nasty bug\"" >&2
    echo -e "E.g:   $0 1234" >&2
    echo -e "E.g:   $0 auto \"Fix nasty bug\"" >&2
    echo -e "E.g:   $0 0 \"Fix something without an issue number\"" >&2
    echo -e "E.g:   $0 list" >&2
    exit 1
  fi

  local git_issue="$1"; shift
  local change_text="${1:-}"

  debug_value "git_issue" "${git_issue}"
  debug_value "change_text" "${change_text}"

  # TODO validate change_text against the text part of issue_line_regex if
  # it is set

  validate_in_git_repo

  if [[ -n "${change_text}" ]]; then
    validate_change_text_arg "${change_text}"
  fi

  local repo_root_dir
  repo_root_dir="$(git rev-parse --show-toplevel)"

  local tag_release_config_file="${repo_root_dir}/${TAG_RELEASE_CONFIG_FILENAME}"
  if [[ -f "${tag_release_config_file}" ]]; then
    # Source any repo specific config
    # shellcheck disable=SC1090
    source "${tag_release_config_file}"
  else
    error_exit "Config file ${BLUE}${tag_release_config_file}${NC}" \
      "doesn't exist. Run ${BLUE}./${TAG_RELEASE_SCRIPT_FILENAME}${NC}" \
      "to generate it."
  fi

  debug_value "GITHUB_NAMESPACE" "${GITHUB_NAMESPACE}"
  debug_value "GITHUB_REPO" "${GITHUB_REPO}"

  local unreleased_dir="${repo_root_dir}/${UNRELEASED_DIR_NAME}"
  mkdir -p "${unreleased_dir}"

  if [[ "${git_issue}" = "list" ]]; then
    list_unreleased_changes ""
  else
    if [[ "${git_issue}" = "auto" ]]; then
      git_issue="$(get_git_issue_from_branch)"
    fi

    validate_git_issue "${git_issue}"

    if [[ "${git_issue}" = "0" ]] \
      || ! is_existing_change_file_present "${git_issue}" "${change_text:-}"; then

      write_change_entry "${git_issue}" "${change_text:-}"
    fi
  fi
}

main "$@"

# vim: set tabstop=2 shiftwidth=2 expandtab:
