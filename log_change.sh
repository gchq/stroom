#!/usr/bin/env bash

# **********************************************************************
# Copyright 2021-2026 Crown Copyright
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

##########################################################################
# Version: v0.5.3
# Date: 2026-01-21T13:42:59+00:00
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
#
# The user experience is much better if fzf and jq are installed.
# The following variables can be set for testing:
#   IS_DEBUG=true ./log_change.sh ...... # Debug logging
#   DISABLE_FZF=true ./log_change.sh ...... # Pretends fzf is not present
#   DISABLE_JQ=true ./log_change.sh ...... # Pretends jq is not present
##########################################################################

set -euo pipefail

trap on_ctrl_c INT
trap on_exit EXIT

UNRELEASED_DIR_NAME="unreleased_changes"

# File containing the configuration values for this script
TAG_RELEASE_CONFIG_FILENAME='tag_release_config.env'
TAG_RELEASE_SCRIPT_FILENAME='tag_release.sh'
ISSUE_HASH_PREFIX="#"

# e.g.
# * Issue **#1234** :
# * Issue **gchq/stroom-resources#104** :
# https://regex101.com/r/VcvbFV/1
ISSUE_LINE_REGEX_NUMBER_PART="\*\*([a-zA-Z0-9_\-.]+\/[a-zA-Z0-9_\-.]+\#[0-9]+|#[0-9]+)\*\*"

# e.g.
# 1234
# my-namespace/my-repor#1234
# foo/bar#1234
GIT_ISSUE_REGEX="^(([_.a-zA-Z0-9-]+\/[_.a-zA-Z0-9-]+\#)?[1-9][0-9]*)$"

# Lines starting with a word in the past tense
PAST_TENSE_FIRST_WORD_REGEX='^(Add|Allow|Alter|Attempt|Chang|Copi|Correct|Creat|Disabl|Extend|Fix|Import|Improv|Increas|Inherit|Introduc|Limit|Mark|Migrat|Modifi|Mov|Preferr|Recognis|Reduc|Remov|Renam|Reorder|Replac|Restor|Revert|Stopp|Supersed|Switch|Turn|Updat|Upgrad)ed[^a-z]'

on_ctrl_c () {
  debug "on_ctrl_c()"
  cleanup_on_error
  exit 1
}

on_exit() {
  exit_status=$?
  debug_value "on_ctrl_c() - exit_status" "${exit_status}"
  if [[ "${exit_status}" -ne 0 ]]; then
    cleanup_on_error
  fi
  #echo "Quitting" >&2
  debug "on_ctrl_c() - Quitting"
  exit $exit_status
}

cleanup_on_error() {
  debug "cleanup_on_error()"
  local file="${temp_file:-}"
  if [[ -n "${file}" && -f "${file}" ]]; then
    debug "Deleting file ${file}"
    rm "${file}" \
      || error "Unable to delete file ${file}"
    temp_file=""
  fi
}

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
  cleanup_on_error
  exit 1
}

debug_value() {
  local name="$1"; shift
  local value="$*"; shift

  if [ "${IS_DEBUG:-false}" = true ]; then
    echo -e "${DGREY}DEBUG ${name}: [${value}]${NC}"
  fi
}

debug() {
  local msg="$*"; shift
  if [ "${IS_DEBUG:-false}" = true ]; then
    echo -e "${DGREY}DEBUG ${msg}${NC}"
  fi
}

show_help_and_exit() {
  local msgs=()
  if [[ $# -eq 0 ]]; then
    msgs=( "Invalid arguments" )
  else
    msgs=( "$@" )
  fi

  debug_value "msgs" "${msgs[@]}"
  error "${msgs[@]}"
  echo -e -n "${DGREY}"
  show_help
  echo -e -n "${NC}"
  cleanup_on_error
  exit 1
}

show_help() {
  echo -e "log_change.sh creates/edits change entry files for unreleased changes." >&2
  echo -e "Usage: ${script_name} [command] [category] [github_issue] [change_text]" >&2
  echo -e "command     - One of: [help list edit link]:" >&2
  echo -e "              help: Show this help" >&2
  echo -e "              list: List all unreleased changes." >&2
  echo -e "              edit: Edit an existing change entry file." >&2
  echo -e "              link: Open an issue in Github." >&2
  echo -e "category    - The change category, one of [${CHANGE_CATEGORIES[*]}]" >&2
  echo -e "git_issue   - GitHub issue number in one of the following formats:" >&2
  echo -e "              0 - No issue exists for this change" >&2
  echo -e "              n - Just the issue number." >&2
  echo -e "              namespace/repo#n - Issue number on another repo." >&2
  echo -e "              auto - Will derive the issue number from the current branch." >&2
  echo -e "change_text - The change text in github markdown format. This will be appended to" >&2
  echo -e "              change log entry" >&2
  echo -e "If no arguments are supplied it will try to derive the issue number" >&2
  echo -e "from the current branch name and obtain the category from the GitHub issue." >&2

  echo -e "E.g: ${script_name}   # Fully interactive mode" >&2
  echo -e "E.g: ${script_name} Bug 1234 \"Fix nasty bug\"" >&2
  echo -e "E.g: ${script_name} Bug gchq/stroom#1234 \"Fix nasty bug\"" >&2
  echo -e "E.g: ${script_name} 1234" >&2
  echo -e "E.g: ${script_name} Bug auto \"Fix nasty bug\"" >&2
  echo -e "E.g: ${script_name} Bug 0 \"Fix something without an issue number\"" >&2
  echo -e "E.g: ${script_name} list" >&2
  echo -e "E.g: ${script_name} edit" >&2
  echo -e "E.g: ${script_name} edit auto" >&2
}

# Parse the git issue number from the branch into git_issue variable,
# if possible
get_git_issue_from_branch() {
  local current_branch
  current_branch="$(git rev-parse --abbrev-ref HEAD)"
  debug_value "current_branch" "${current_branch}"

  # Examples of branches that will give us an issue number:
  # 1234
  # gh-1234
  # gh-1234-some-text
  # gh-1234_some-text
  # foo_1234_bar
  # xxx/1234
  # xxx/1234/yyy
  git_issue="$( \
    grep \
      --only-matching \
      --perl-regexp \
      '(^[1-9][0-9]*$|(((?<=[\/_\-])|^)[1-9][0-9]*((?=[\/_\-])|$)))' \
      <<< "${current_branch}" \
      || true \
  )"

  debug_value "git_issue (from branch)" "${git_issue}"
}

parse_git_issue() {
  local git_issue="$1";
  debug_value "git_issue" "${git_issue}"
  if [[ -n "${git_issue}" ]]; then
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
  fi
}

fetch_git_issue_info() {
  local git_issue="$1";
  debug_value "git_issue" "${git_issue}"

  parse_git_issue "${git_issue}"

  local github_issue_api_url="https://api.github.com/repos/${issue_namespace}/${issue_repo}/issues/${issue_number}"

  debug_value "Calling GH using github_issue_api_url" "${github_issue_api_url}"

  local curl_return_code=0
  # Turn off exit on error so we can get the curl return code in the subshell
  set +e

  local response_json
  response_json="$( \
    curl \
      --silent \
      --fail \
      "${github_issue_api_url}" \
  )"
  curl_return_code=$?
  set -e

  if [[ "${has_jq}" = true ]]; then
    # jq is available so use it
    # '// empty' ensure we get an empty string back if not found, rather than
    # 'null', see https://jqlang.org/manual/#alternative-operator
    issue_title="$( \
      jq \
        --raw-output \
        '.title // empty' \
        <<< "${response_json}"
    )"
    issue_type="$( \
      jq \
        --raw-output \
        '.type.name // empty' \
        <<< "${response_json}"
    )"
    issue_tags="$( \
      jq \
        --raw-output \
        '[.labels[].name] | sort | join(", ") // empty' \
        <<< "${response_json}"
    )"
  else
    # No jq so fall back to grep, very dirty
    issue_title="$( \
      grep \
        --only-matching \
        --prl-regexp \
        '(?<="title": ").*(?=",)' \
        <<< "${response_json}"
    )"
  fi

  debug_value "curl_return_code" "${curl_return_code}"
  debug_value "issue_title" "${issue_title}"
  debug_value "issue_type" "${issue_type}"
  debug_value "issue_tags" "${issue_tags}"

  if [[ -n "${change_category_from_arg}" && -n "${issue_type}" ]]; then
    if [[ "${change_category_from_arg}" != "${issue_type}" ]]; then
      error_exit "The provided change category ${BLUE}${change_category_from_arg}${NC}" \
        "does not match the issue type ${BLUE}${issue_type}${NC} of issue ${BLUE}${git_issue}${NC}."
    fi
  fi

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
    info "Issue number: ${BLUE}${git_issue}${NC}"
    info "Issue type:   ${BLUE}${issue_type}${NC}"
    info "Issue title:  ${BLUE}${issue_title}${NC}"
    info "Issue tags:   ${BLUE}${issue_tags}${NC}"
  fi
}

validate_in_git_repo() {
  if ! git rev-parse --show-toplevel > /dev/null 2>&1; then
    error_exit "You are not in a git repository. This script should be run from" \
      "inside a repository.${NC}"
  fi
}

# Return a 0 status if valid
is_validate_change_text() {
  local change_text="$1"; shift

  if [[ ! "${change_text}" =~ ^[A-Z].*\.$ ]]; then
    error "The change entry text must start with a capital letter and end with a full stop."
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo -e "${YELLOW}${change_text}${NC}"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    return 1
  fi

  if ! validate_tense "${change_text}"; then
    error "The change entry text should be in the imperitive mood" \
      "\ni.e. \"Fix nasty bug\" rather than \"Fixed nasty bug\"."
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    echo -e "${YELLOW}${change_text}${NC}"
    echo -e "${DGREY}------------------------------------------------------------------------${NC}"
    return 1
  fi

  return 0
}

validate_tense() {
  debug "validate_tense()"
  local change_text="$1"; shift
  debug_value "change_text" "${change_text}"

  if [[ "${IS_TENSE_VALIDATED:-true}" = true ]]; then
    if [[ "${change_text}" =~ ${PAST_TENSE_FIRST_WORD_REGEX} ]]; then
      debug "Found past tense first word"
      return 1
    else
      debug "Tense validated ok"
    fi
  else
    debug "Tense validation disabled"
  fi
  return 0
}

select_file_from_list() {
  local file_list=( "$@" );

  # No point listing them if user has fzf, as it can show the preview
  if [[ "${has_fzf}" = false ]]; then
    list_unreleased_changes "${git_issue_str:-}"
    echo
  fi

  # Build the menu options
  local menu_item_arr=()
  local open_prefix=""
  if [[ "${mode}" != "edit" ]]; then
    echo "Do you want to create a new change file for the issue or open an existing one?"
    echo "If it is a different change tied to the same issue then you should create a new"
    echo "file to avoid merge conflicts."

    menu_item_arr+=( "Create new file" )
    open_prefix="Open "
  fi

  for filename in "${file_list[@]}"; do
    menu_item_arr+=( "${open_prefix}${filename}" )
  done

  if [[ "${has_fzf}" = true ]]; then

    local fzf_header
    if [[ "${mode}" != "edit" ]]; then
      fzf_header="Select the action you want to perform: (CTRL-c or ESC to quit)"
    else
      fzf_header="Select the change file to open: (CTRL-c or ESC to quit)"
    fi

    # Do the -f test to handle the 'Create new file' entry
    # {} is the selected item
    local fzf_preview_cmd="file={}; file=\"${unreleased_dir}/\${file#Open }\"; [[ -f \"\${file}\" ]] && head -n1 \"\${file}\""
    debug_value "fzf_preview_cmd" "${fzf_preview_cmd}"

    # Show the top line of the file in the preview pane
    if ! user_input="$( \
      printf "%s\n" "${menu_item_arr[@]}" \
        | fzf \
          --height ~40% \
          --border \
          --raw  \
          --bind result:best \
          --bind enter:accept-non-empty \
          --header="${fzf_header}" \
          --preview "${fzf_preview_cmd}" \
          --preview-window "wrap" \
          --preview-label "Change entry" \
    )"; then
      cleanup_on_error
      exit 1
    fi

  else
    # Present the user with a menu of options in a single column
    COLUMNS=1
    if [[ "${mode}" != "edit" ]]; then
      PS3="Enter the number of the action you want to perform:"
    else
      PS3="Enter the number of the file you want to edit:"
    fi
    select user_input in "${menu_item_arr[@]}"; do
      if [[ -n "${user_input}" ]]; then
        break
      else
        echo "Invalid option. Try another one."
        continue
      fi
    done
  fi

  if [[ "${user_input}" = "Create new file" ]]; then
    write_change_entry "${change_category}" "${git_issue}" "${change_text:-}"
    # If the user didn't provide the change text as an arg, open the file
    if [[ -z "${change_text}" ]]; then
      open_file_in_editor "${change_file}" "${git_issue}"
    fi
  elif [[ "${user_input}" =~ ^${open_prefix} ]]; then
    local chosen_file_name="${user_input#Open }"
    local chosen_file="${unreleased_dir}/${chosen_file_name}"
    debug_value "chosen_file_name" "${chosen_file_name}"
    debug_value "chosen_file" "${chosen_file}"
    if [[ -z "${git_issue}" ]]; then
      # If we have come here via the edit mode, then we may not have
      # a git_issue, so try to get it from the file/filename
      read_git_issue_from_change_file "${chosen_file}"
    fi
    open_file_in_editor "${chosen_file}" "${git_issue}"
  else
    error_exit "Unknown user_input ${user_input}"
  fi
}

read_git_issue_from_change_file() {
  debug_value "read_git_issue_from_change_file()" "${@}"
  local file="$1"; shift
  if [[ ! -f "${file}" ]]; then
    error_exit "File ${file} not found."
  fi

  if [[ "${file}" =~ __0.md$ ]]; then
    # No associated issue
    git_issue=0
    debug "No associated issue, setting git_issue to 0"
  else
    # This regex needs to look for the line added in write_change_entry()
    # Examples:
    # # Issue number: 1234
    # # Issue number: some-user/some-repo#1234
    git_issue="$( \
      grep \
        --only-matching \
        --perl-regexp \
        '(?<=^# Issue number: )([a-zA-Z0-9_\-.]+\/[a-zA-Z0-9_\-.]+\#[0-9]+|[0-9]+)$' \
        "${file}" \
        || true
    )"
    debug_value "git_issue" "${git_issue}"

    # In case this is an old change file without the '# Issue number:' line
    # attempt to get it from the change entry line
    if [[ -z "${git_issue}" ]]; then
      local change_entry_line
      change_entry_line="$( head -n 1 "${file}" )"
      debug_value "change_entry_line" "${change_entry_line}"
      if [[ -n "${change_entry_line}" ]]; then
        # Examples:
        # * XXXXX : Fix bug with...
        # * XXXXX **#456** : Fix bug with...
        # * XXXXX **namespace/other-repo#456** : Fix bug with...
        local pattern='^\* [^\*]+ \*{2}([^\*]+)\*\*'
        if [[ "${change_entry_line}" =~ ${pattern} ]]; then
          git_issue="${BASH_REMATCH[1]}"
          # '#1234' => '1234'
          git_issue="${git_issue#"${ISSUE_HASH_PREFIX}"}"
          debug_value "git_issue" "${git_issue}"
        fi
      fi

      if [[ -z "${git_issue}" ]]; then
        error_exit "Unable to extract git_issue from file ${file}".
      fi
    fi
  fi
}

edit_change_file_if_present() {
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
    info "Change file(s) already exist for this issue:"
    echo
    select_file_from_list "${existing_files[@]}"
    return 0
  fi
}

edit_all_files() {
  local existing_files=()

  for existing_file in "${unreleased_dir}"/*__*.md; do
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
    info "There are no change files to edit."
  else
    select_file_from_list "${existing_files[@]}"
  fi
}

# Writes the formatted git issue to stdout,
# therefore don't echo anything else to stdout
format_git_issue_for_filename() {
  local git_issue="$1"; shift

  local git_issue_str
  if [[ "${git_issue}" = "0" ]]; then
    git_issue_str="0"
  else
    # replace / and # with _
    git_issue_str="${git_issue//[\#\/]/_}"
    debug_value "git_issue_str" "${git_issue_str}"
  fi
  echo "${git_issue_str}"
}

write_change_entry() {
  local change_category="$1"; shift
  local git_issue="$1"; shift
  local change_text="$1"; shift

  local date_str
  date_str="$(date --utc +%Y%m%d_%H%M%S_%3N)"

  local git_issue_str
  git_issue_str="$(format_git_issue_for_filename "${git_issue}")"

  # Use two underscores to help distinguish the date from the issue part
  # which may itself contain underscores.
  local filename="${date_str}__${git_issue_str}.md"
  # Will make a file like /tmp/<date part>__<git issue part>.md.QkL5v43Cx1
  temp_file="$( mktemp -t "${filename}.XXXXXXXXXX" )"
  # set change_file so the rest of the script can work on this temp file
  change_file="${temp_file}"
  # Record what we want the ultimate new file to be if successful
  new_file="${unreleased_dir}/${filename}"

  debug_value "temp_file" "${temp_file}"
  debug_value "change_file" "${change_file}"
  debug_value "new_file" "${new_file}"

  local line_prefix="* "
  local category_part="${change_category}"
  local issue_prefix="**"
  local issue_suffix="**"

  # Examples:
  # * Bug **#1234** : Some text
  # * Feature **#1234** : Some text
  # * Feature **user/repo#1234** : Some text
  # * Refactor : Some text

  local issue_part
  if [[ "${git_issue}" = "0" ]]; then
    issue_part=": "
  elif [[ "${git_issue}" =~ ^[0-9]+$ ]]; then
    # * Issue **#1234** : My change text
    issue_part="${issue_prefix}#${git_issue}${issue_suffix} : "
  else
    # * Issue **gchq/stroom#1234** :
    issue_part="${issue_prefix}${git_issue}${issue_suffix} : "
  fi

  local empty_change_text="< Enter the change entry description here >."
  local change_entry_line="${line_prefix}${category_part} ${issue_part}${change_text:-"${empty_change_text}"}"
  debug_value "change_entry_line" "${change_entry_line}"

  # Craft the content of the file
  # shellcheck disable=SC2016
  {
    echo "${change_entry_line}"
    echo
    echo
    echo '```sh'
    if [[ -n "${issue_title:-}" ]]; then
      local github_issue_url="https://github.com/${issue_namespace}/${issue_repo}/issues/${issue_number}"
      echo "# ********************************************************************************"
      echo "# Issue number: ${git_issue}"
      echo "# Issue title:  ${issue_title}"
      echo "# Issue tags:   ${issue_tags}"
      echo "# Issue link:   ${github_issue_url}"
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
    echo "# * Bug **#123** : Fix bug with an associated GitHub issue in this repository."
    echo "#"
    echo "# * Bug **namespace/other-repo#456** : Fix bug with an associated GitHub issue in another repository."
    echo "#"
    echo "# * Feature **#789** : Add new feature X."
    echo "#"
    echo "# * Bug : Fix bug with no associated GitHub issue."
    echo "#"
    echo "#"
    echo "# Note: The line must start '* XXX ', where 'XXX' is a valid category,"
    echo "#       one of [${CHANGE_CATEGORIES[*]}]."
    echo
    echo
    echo "# --------------------------------------------------------------------------------"
    echo "# The following is random text to make this file unique for git's change detection"
    # Print 30 lines of 80 random chars to std out to make the file very unique for git
    # shellcheck disable=SC2034
    for ignored in {1..30}; do
      echo -n "# "
      tr -dc A-Za-z0-9 </dev/urandom \
        | head -c 80 \
        || true
      # Add the line break
      echo
    done
    echo "# --------------------------------------------------------------------------------"
    echo
    echo '```'
  } > "${temp_file}"
}

# Return zero if the file was changed, else non-zero
open_file_in_editor() {
  local file_to_open="$1"; shift
  local git_issue="$1"; shift

  local editor
  editor="${VISUAL:-${EDITOR:-vi}}"
  debug_value "editor" "${editor}"

  local is_first_pass=true

  info "Opening file ${BLUE}${file_to_open}${GREEN} in editor" \
    "(${BLUE}${editor}${GREEN})${NC}"

  # Loop until the user saves a file that is valid
  while true; do
    if [[ "${is_first_pass}" = true ]]; then
      read -n 1 -s -r -p "Press any key to open the file"
    else
      read -n 1 -s -r -p "File failed validation. Press any key to re-open the file or CTRL-c to quit"
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

get_line_count_by_regex() {
  local change_file="$1"; shift
  local regex="$1"; shift
  if [[ -z "${regex}" ]]; then
    error_exit "regex argument not set"
  fi

  match_count="$( \
    grep \
      --count \
      --perl-regexp \
      "${regex}" \
      "${change_file}" \
    || true
    )"
  match_count="${match_count:-0}"
}

validate_issue_line_in_file() {
  debug "validate_issue_line_in_file ($*)"
  local change_file="$1"; shift
  local git_issue="$1"; shift

  debug "Validating file ${change_file}"
  debug_value "git_issue" "${git_issue}"

  # Example change lines:
  # * Bug **#1234** : Some text
  # * Feature **#1234** : Some text
  # * Feature **user/repo#1234** : Some text
  # * Refactor : Some text

  local match_count
  get_line_count_by_regex "${change_file}" "^\*"
  if [[ "${match_count}" -gt 1 ]]; then
    error "Found multiple lines starting with '*'." \
      "Only the top line should start with a '*'."
    return 1
  elif [[ "${match_count}" -eq 0 ]]; then
    error "No change entry line found could be found."
    return 1
  fi

  local issue_line; issue_line="$( \
    head -n 1 "${change_file}" \
  )"
  debug_value "issue_line" "${issue_line}"

  if [[ -z "${issue_line}" ]]; then
    error "The top line of the the file is empty. It much contain the change entry."
    return 1
  fi
  if [[ ! "${issue_line}" =~ ^\* ]]; then
    error "The top line of the the file must be the change entry and start with '* '."
    return 1
  fi

  # Bash regex can't do non-capturing groups so we have to have two capturing
  # groups for the issue part.
  local split_pattern='^\* ([^ ]+) (([^\ ]+) )?: (.*)$'
  # * <category> <issue with markdown> : <change text>
  if [[ "${issue_line}" =~ ${split_pattern} ]]; then
    local part_count="${#BASH_REMATCH[@]}"
    debug_value "part_count" "${part_count}"

    local category_part="${BASH_REMATCH[1]}"
    local text_part="${BASH_REMATCH[-1]}" # Always last part
    debug_value "category_part" "${category_part}"
    debug_value "text_part" "${text_part}"

    if ! is_change_category "${category_part}" true; then
      error "Category '${category_part}' must match one of [${CHANGE_CATEGORIES[*]}]."
      return 1
    fi

    if [[ "${part_count}" -eq 4 ]]; then
      # Group 2 includes the space which we don't want
      local issue_part="${BASH_REMATCH[3]}"
      debug_value "issue_part" "${issue_part}"

      if [[ ! "${issue_part}" =~ ${ISSUE_LINE_REGEX_NUMBER_PART} ]]; then
        error "The GitHub issue part '${issue_part}' (if applicable) must be of the form:"
        error "  **#123**"
        error "  **namespace/other-repo#456**"
        return 1
      fi
    fi

    if ! is_validate_change_text "${text_part}"; then
      return 1
    fi
  else
    error "Expecting the top line to match pattern '${split_pattern}'."
    return 1
  fi
}

list_unreleased_changes() {
  # if no git issue is provided use a wildcard so we can get all issues
  local git_issue_str="${1:-*}"; shift
  debug_value "git_issue_str" "${git_issue_str}"
  local found_change_files=false
  local list_output=""

  local format_cmds=()
  if command -v fmt >/dev/null 2>&1; then
    format_cmds=( "fmt" "--width=80" )
  else
    # No fmt cmd so use tee to just send stdin to stdout
    format_cmds=( "tee" )
  fi

  debug_value "format_cmds" "${format_cmds[*]}"

  # git_issue_str may be '*' so we must not quote it else the
  # globbing won't work
  # shellcheck disable=SC2231
  for file in "${unreleased_dir}/"*__${git_issue_str}.md; do
    if [[ -f "${file}" ]]; then
      local filename
      local change_entry_line

      found_change_files=true
      filename="$(basename "${file}" )"

      # Get first line of the file and word wrap it
      change_entry_line="$( \
        head \
          -n1 \
          "${file}" \
        | "${format_cmds[@]}" )"
      list_output+="${BLUE}${filename}${NC}:\n${change_entry_line}\n\n"
    fi
  done

  if [[ "${found_change_files}" = true ]]; then
    # Remove the trailing blank lines
    list_output="$(echo -e "${list_output}" | head -n-2 )"
    echo -e "${list_output}"
  else
    info "There are no unreleased changes"
  fi
}

validate_bash_version() {
  if (( BASH_VERSINFO[0] < 4 )); then
    error_exit "This script requires Bash version 4 or greater." \
      "Please install it."
  fi
}

validate_env() {
  if [[ -z "${GITHUB_NAMESPACE}" ]]; then
    error_exit "Variable ${YELLOW}GITHUB_NAMESPACE${NC} must be set in the " \
      "${BLUE}${TAG_RELEASE_CONFIG_FILENAME}${NC} file."
  fi
  if [[ -z "${GITHUB_REPO}" ]]; then
    error_exit "Variable ${YELLOW}GITHUB_REPO${NC} must be set in the " \
      "${BLUE}${TAG_RELEASE_CONFIG_FILENAME}${NC} file."
  fi
  if [[ -z "${CHANGE_CATEGORIES}" ]]; then
    error_exit "Variable ${YELLOW}CHANGE_CATEGORIES${NC} must be set in the " \
      "${BLUE}${TAG_RELEASE_CONFIG_FILENAME}${NC} file."
  fi
  if ! command -v basename >/dev/null 2>&1; then
    error_exit "${BLUE}basename${NC} is not installed." \
      "Please install it via the GNU coreutils package."
  fi
}

build_categories_regex() {
  change_categories_regex="("
  local idx=0
  for category in "${CHANGE_CATEGORIES[@]}"; do

    if [[ "${idx}" -ne 0 ]]; then
      change_categories_regex+="|"
    fi
    change_categories_regex+="${category}"
    (( idx++ )) || true
  done
  change_categories_regex+=")"
}

is_change_category() {
  local text="${1:-}"
  local is_case_sensitive="${2:-false}"
  if [[ -z "${text}" ]]; then
    return 1
  elif [[ ${#CHANGE_CATEGORIES[@]} -eq 0 ]]; then
    # Empty arr, no change categories
    return 1
  else
    if [[ "${is_case_sensitive}" = true ]]; then
      for category in "${CHANGE_CATEGORIES[@]}"; do
        if [[ "${text}" = "${category}" ]]; then
          return 0
        fi
      done
    else
      local lower_text="${1,,}"
      for category in "${CHANGE_CATEGORIES[@]}"; do
        local lower_category="${category,,}"
        if [[ "${lower_text}" = "${lower_category}" ]]; then
          return 0
        fi
      done
    fi
    # Not found
    return 1
  fi
}

# Update change_category variable to match the case from the env file
normalise_change_category() {
  if [[ -z "${change_category}" ]]; then
    error_exit "change_category is unset in call to normalise_change_category"
  fi
  debug_value "change_category" "${change_category}"

  for category in "${CHANGE_CATEGORIES[@]}"; do
    debug_value "category" "${category}"
    if [[ "${change_category,,}" = "${category,,}" ]]; then
      # Set it to the case from the env file
      change_category="${category}"
      return 0
    fi
  done
  # If we get here, it wasn't found.
  error_exit "Category '${change_category}' not found in [${CHANGE_CATEGORIES[*]}]"
}

# Make sure it has not been configured with any categories that
# will get confused with other special args.
validate_categories() {
  for category in "${CHANGE_CATEGORIES[@]}"; do
    local lower_category="${category,,}"
    if [[ "${lower_category}" =~ (list|edit|auto) ]]; then
      error_exit "'${category}' is a reserved word so cannot be used" \
        "as a category"
    fi
  done
}

parse_args() {
  if [[ $# -gt 3 ]]; then
    show_help_and_exit "Too many arguments."
  fi

  if [[ $# -eq 0 ]]; then
    mode="interactive"
    # Try to extract the GH issue from the branch name
    get_git_issue_from_branch
    # If we are successful, then hit the api to get its details
    # so we can infer the category
    if [[ "${git_issue}" =~ $GIT_ISSUE_REGEX ]]; then
      fetch_git_issue_info "${git_issue}"
    else
      git_issue=""
    fi
    return 0
  fi

  # Special case for creating an entry for a specific issue
  # e.g. ./log_change.sh 0
  # e.g. ./log_change.sh 1234
  if [[ $# -eq 1 ]] && [[ "$1" = "0" || "$1" =~ ${GIT_ISSUE_REGEX} ]]; then
    mode="interactive"
    git_issue="$1"
    if [[ "${git_issue}" != "0" ]]; then
      fetch_git_issue_info "${git_issue}"
    fi
    return 0
  fi

  # Show help
  if [[ "$1" = "help" || "$1" = "-h" ]] ; then
    show_help
    exit 0
  fi

  # List existing unreleased changes
  if [[ "$1" = "list" ]] ; then
    if [[ $# -eq 1 ]]; then
      mode="list"
      return 0
    else
      show_help_and_exit "Invalid arguments. 'list' should be the only" \
        "argument if you want to list the unreleased changes."
    fi
  fi

  # Open issue in GitHub
  if [[ "$1" = "link" ]] ; then
    mode="link"
    if [[ $# -gt 2 ]]; then
      show_help_and_exit "Invalid arguments. 'link' should be the only" \
        "argument or followed by the git issue number."
    elif [[ $# -eq 1 ]]; then
      # 'link' with no git issue, so drop out
      return 0
    fi
  # Edit an existing unrleased change entry
  elif [[ "$1" = "edit" ]] ; then
    mode="edit"
    if [[ $# -gt 2 ]]; then
      show_help_and_exit "Invalid arguments. 'edit' should be the only" \
        "argument or followed by the git issue number."
    elif [[ $# -eq 1 ]]; then
      # 'edit' with no git issue, so drop out
      return 0
    fi
  fi

  # log is the default mode
  if [[ "${mode}" = "log" ]]; then
    change_category="${1:-}"
    if is_change_category "$1"; then
      normalise_change_category
      debug_value "change_category" "${change_category}"
    else
      show_help_and_exit "Invalid arguments. Expecting the first argument" \
        "${change_category} to be a change" \
        "category [${CHANGE_CATEGORIES[*]}]."
    fi
  fi

  git_issue="${2:-}"
  debug_value "git_issue" "${git_issue}"
  if [[ "${git_issue}" != "0" ]]; then
    if [[ "${git_issue}" = "auto" ]]; then
      # Extract the GH issue from the branch name
      get_git_issue_from_branch

      if [[ -z "${git_issue}" ]]; then
        error_exit "Unable to establish GitHub issue number from branch ${BLUE}${current_branch}${NC}"
      fi
    fi

    if [[ "${git_issue}" =~ $GIT_ISSUE_REGEX ]]; then
      change_category_from_arg="${change_category}"
      fetch_git_issue_info "${git_issue}"
    else
      show_help_and_exit "Invalid arguments. Expecting second argument" \
        "'${git_issue}' to be" \
        "a GitHub issue number (e.g. 1234 or" \
        "some-user/some-repo#1234) or 'auto' to" \
        "determine the issue number from the branch."
    fi
  fi

  if [[ $# -gt 2 ]]; then
    change_text="$3"
    debug_value "change_text" "${change_text}"
    if [[ -n "${change_text}" ]]; then
      if ! is_validate_change_text "${change_text}"; then
        cleanup_on_error
        exit 1
      fi
    fi
  fi
}

capture_git_issue() {
  # TODO we could hit the api and use fzf to pick an issue, but that is
  # probably too much faff for too little gain.
  while true; do
    echo -e "Enter one of the following:"
    echo -e "  * The GitHub issue number (e.g. ${BLUE}1234${NC})."
    echo -e "  * A fully qualified issue (e.g. ${BLUE}user/repo#1234${NC})."
    echo -e "  * '0' to not associate the change entry with an issue."

    read -e -r user_input

    if [[ "${user_input}" = "0" || "${user_input}" =~ ${GIT_ISSUE_REGEX} ]]; then
      git_issue="${user_input}"
      break
    else
      echo
      echo -e "Invalid GitHub issue ${BLUE}${user_input}${NC}"
      echo
      # Go round again
    fi
  done
}

capture_category() {
  local msg="Select the appropriate category for this change:"

  if [[ "${has_fzf}" = true ]]; then
    echo "${msg}"

    # Use FZF to get the category
    # Printf to convert space delim to line delim for FZF
    user_input="$( \
      printf "%s\n" "${CHANGE_CATEGORIES[@]}" \
        | fzf \
          --height ~40% \
          --border \
          --raw  \
          --bind result:best \
          --bind enter:accept-non-empty \
          --header="${msg} (CTRL-c or ESC to quit)" \
    )"

    if [[ -z "${user_input}" ]]; then
      # User must have done a ctrl-c
      error_exit "No category selected, quitting."
    else
      change_category="${user_input}"
    fi
  else
    echo "${msg}"
    COLUMNS=1
    PS3="Enter the number of the category for this change:"
    select user_input in "${CHANGE_CATEGORIES[@]}"; do
      if [[ -n "${user_input}" ]]; then
        change_category="${user_input}"
        break
      fi
    done
  fi
}

# Prompt the user to get the category, issue and change text
# depending on the mode
prompt_user_for_remaining_args() {
  if [[ "${mode}" != "edit" && -z "${change_category:-}" ]]; then
    infer_category_from_issue_type
    if [[ -z "${change_category}" ]]; then
      capture_category
    fi
  fi

  if [[ -z "${git_issue}" ]]; then
    # Have a stab at getting the git issue number from the branch first
    if [[ -z "${git_issue}" ]]; then
      get_git_issue_from_branch
    fi
    capture_git_issue
  fi
}

# If we have a issue_type from the GH api call and it matches
# one of our categories, then use that category.
infer_category_from_issue_type() {
  if [[ -n "${issue_type}" && -z "${change_category}" ]]; then
    for category in "${CHANGE_CATEGORIES[@]}"; do
      # ',,' to compare in  lower case
      if [[ "${issue_type,,}" = "${category,,}" ]]; then
        change_category="${category}"
        #echo -e "Inferred change category '${BLUE}${change_category}${NC}'" \
          #"from the GitHub issue."
        break
      fi
    done
  fi
}

check_for_fzf() {
  has_fzf=false
  # DISABLE_FZF allows us to pretend fzf is not present for testing
  # DISABLE_FZF=true ./log_change.sh ......
  if [[ "${DISABLE_FZF:-false}" != true ]] && command -v fzf > /dev/null; then
    has_fzf=true
  else
    echo -e "${DGREY}log_chang.sh works better if fzf" \
      "(command line fuzzy finder) is installed." \
      "See https://junegunn.github.io/fzf/${NC}"
  fi
}

check_for_jq() {
  has_jq=false
  # DISABLE_JQ allows us to pretend jq is not present for testing
  # DISABLE_JQ=true ./log_change.sh ......
  if [[ "${DISABLE_JQ:-false}" != true ]] && command -v jq > /dev/null; then
    has_jq=true
  else
    echo -e "${DGREY}log_chang.sh works better if jq" \
      "(command line JSON processor) is installed." \
      "See https://jqlang.org${NC}"
  fi
}

source_env_file() {
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
}

open_issue_in_github() {
  if ! command -v xdg-open >/dev/null; then
    error_exit "xdg-open is not installed. Can't open issue in GitHub."
  fi

  if [[ -z "${git_issue}" || "${git_issue}" = "auto" ]]; then
    get_git_issue_from_branch
  fi

  if [[ -n "${git_issue}" ]]; then
    parse_git_issue "${git_issue}"

    local issue_url="https://github.com/${issue_namespace}/${issue_repo}/issues/${issue_number}"
    echo -e "${GREEN}Issue link:${NC}   ${BLUE}${issue_url}${NC}"
    read -rsp $'Press "y" to open the issue in the browser, any other key to cancel.\n' -n1 keyPressed

    if [ "$keyPressed" = 'y' ] || [ "$keyPressed" = 'Y' ]; then
      xdg-open "${issue_url}"
    fi
  else
    error_exit "No git issue number"
  fi
}

write_new_file() {
  debug_value "new_file" "${new_file}"
  debug_value "temp_file" "${temp_file}"
  if [[ -n "${new_file}" && -n "${temp_file}" && -f "${temp_file}" ]]; then
    local change_entry_line

    if [[ -e "${new_file}" ]]; then
      error_exit "Unable to write new file, ${BLUE}${new_file}${NC} already exists"
    fi
    mv "${temp_file}" "${new_file}"

    change_entry_line="$( head -n1 "${new_file}" )"
    info "Created file ${BLUE}${new_file}${GREEN}:"
    info "${DGREY}------------------------------------------------------------------------${NC}"
    info "${YELLOW}${change_entry_line}${NC}"
    info "${DGREY}------------------------------------------------------------------------${NC}"
  fi
}

main() {
  #local SCRIPT_DIR
  #SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
  #debug_value "SCRIPT_DIR" "${SCRIPT_DIR}"
  local script_name="$0"
  local has_fzf=false

  setup_echo_colours
  validate_bash_version

  # Get the root of this git repo
  local repo_root_dir
  repo_root_dir="$(git rev-parse --show-toplevel)"

  source_env_file
  validate_env
  validate_in_git_repo
  check_for_fzf
  check_for_jq

  # Ensure change_categories is set to an empty arr if unset
  #local -a change_categories=${CHANGE_CATEGORIES:-( )}
  local change_categories_regex
  local change_category=""
  local change_category_from_arg=""
  local change_file=""
  local git_issue=""
  local change_text=""
  local mode="log"
  local issue_title=""
  local issue_tags=""
  local issue_namespace=""
  local issue_type=""
  local issue_repo=""
  local issue_number=""
  local unreleased_dir="${repo_root_dir}/${UNRELEASED_DIR_NAME}"
  local new_file=""
  local temp_file=""
  mkdir -p "${unreleased_dir}"

  build_categories_regex
  parse_args "$@"

  debug_value "Parsed arguments" "$*"
  debug_value "mode" "${mode}"
  debug_value "CHANGE_CATEGORIES" "${CHANGE_CATEGORIES[*]}"
  debug_value "change_category" "${change_category:-}"
  debug_value "git_issue" "${git_issue:-}"
  debug_value "change_text" "${change_text:-}"
  debug_value "GITHUB_NAMESPACE" "${GITHUB_NAMESPACE}"
  debug_value "GITHUB_REPO" "${GITHUB_REPO}"

  if [[ "${mode}" = "list" ]]; then
    list_unreleased_changes ""
  elif [[ "${mode}" = "link" ]]; then
    open_issue_in_github
  elif [[ "${mode}" = "edit" ]]; then
    if [[ -n "${git_issue}" ]]; then
      edit_change_file_if_present "${git_issue}" "" || true
    else
      edit_all_files
    fi
  elif [[ "${mode}" = "log" || "${mode}" = "interactive" ]]; then
    # Capture the category or git issue if required
    prompt_user_for_remaining_args

    if [[ "${git_issue}" = "0" ]] \
      || ! edit_change_file_if_present "${git_issue}" "${change_text:-}"; then

      write_change_entry "${change_category}" "${git_issue}" "${change_text:-}"

      # If the user didn't provide the change text as an arg, open the file
      if [[ -z "${change_text}" ]]; then
        open_file_in_editor "${change_file}" "${git_issue}"
      fi
    fi

    debug_value "new_file" "${new_file}"
    if [[ -n "${new_file}" ]]; then
      write_new_file
    fi
  else
    error_exit "Unexpected mode ${mode}"
  fi
}

main "$@"

# vim: set tabstop=2 shiftwidth=2 expandtab:
