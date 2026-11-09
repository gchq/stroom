#!/usr/bin/env bash

# **********************************************************************
# Copyright 2026 Crown Copyright
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

# **********************************************************************
# Script for adding change entries for dependabot PRs.
# Does the following:
# 1. Prompt to select a dependabot PR.
# 2. Prompt to select the change category.
# 3. Checkout the dependabot branch.
# 4. Create a new change entry, using log_change.sh.
# 5. Git add, commit and push the new file.
# **********************************************************************

set -euo pipefail

readonly GITHUB_REPOSITORY="gchq/stroom"
readonly UNRELEASED_CHANGES_DIR="unreleased_changes"

require_command() {
  local command_name="$1"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "ERROR: ${command_name} is required but was not found on PATH." >&2
    exit 1
  fi
}

get_dependabot_pull_requests() {
  gh pr list \
    --repo "${GITHUB_REPOSITORY}" \
    --state open \
    --app dependabot \
    --limit 1000 \
    --json number,headRefName,title \
    --jq '.[] | [.number, .headRefName, .title] | @tsv'
}

select_with_fzf() {
  local prompt="$1"
  shift

  printf '%s\n' "$@" \
    | fzf \
      --height ~40% \
      --border \
      --raw \
      --bind result:best \
      --bind enter:accept-non-empty \
      --header="${prompt} (CTRL-c or ESC to quit)"
}

select_pull_request_with_fzf() {
  local prompt="$1"
  shift

  local preview_command
  preview_command="gh pr view {1} \\
    --repo '${GITHUB_REPOSITORY}' \\
    --json body \\
    --jq '.body // \"(No description)\"'"

  printf '%s\n' "$@" \
    | fzf \
      --height ~60% \
      --border \
      --raw \
      --delimiter=$'\t' \
      --with-nth=3 \
      --bind result:best \
      --bind enter:accept-non-empty \
      --header="${prompt} (CTRL-c or ESC to quit)" \
      --preview "${preview_command}" \
      --preview-window "wrap" \
      --preview-label "PR description"
}

confirm_pull_request() {
  local pull_request_number="$1"
  local answer

  echo
  echo "Pull request ${pull_request_number} diff:"
  echo
  if ! gh pr diff "${pull_request_number}" \
    --repo "${GITHUB_REPOSITORY}" \
    --color always \
    --allow-escape-sequences; then
    echo "Unable to fetch the pull request diff." >&2
    return 2
  fi

  echo
  while true; do
    if ! read -r -p "Apply this change? [Y/N] " answer; then
      echo
      return 2
    fi

    case "${answer,,}" in
      y)
        return 0
        ;;
      n)
        return 1
        ;;
      *)
        echo "Please answer Y or N."
        ;;
    esac
  done
}

main() {
  local repo_root_dir
  repo_root_dir="$(git rev-parse --show-toplevel)"
  cd "${repo_root_dir}"

  require_command gh
  require_command fzf
  require_command git

  local pull_request_rows
  pull_request_rows="$(get_dependabot_pull_requests)"

  if [[ -z "${pull_request_rows}" ]]; then
    echo "No open pull requests authored by dependabot[bot] were found."
    exit 1
  fi

  local -a pull_requests=()
  local pull_request_row
  local pull_request_number
  local source_branch
  local pull_request_title

  while IFS= read -r pull_request_row; do
    if [[ -z "${pull_request_row}" ]]; then
      continue
    fi

    pull_requests+=("${pull_request_row}")
  done <<< "${pull_request_rows}"

  local selected_pull_request
  if ! selected_pull_request="$(select_pull_request_with_fzf "Select a Dependabot pull request:" "${pull_requests[@]}")"; then
    echo "No pull request selected."
    exit 1
  fi

  if [[ -z "${selected_pull_request}" ]]; then
    echo "No pull request selected."
    exit 1
  fi

  IFS=$'\t' read -r pull_request_number source_branch pull_request_title <<< "${selected_pull_request}"

  if [[ -z "${pull_request_number}" || -z "${source_branch}" || -z "${pull_request_title}" ]]; then
    echo "The selected pull request has incomplete metadata." >&2
    exit 1
  fi

  if confirm_pull_request "${pull_request_number}"; then
    :
  else
    local confirmation_status=$?
    if [[ "${confirmation_status}" -eq 1 ]]; then
      echo "Change not approved. Exiting."
      exit 0
    fi

    echo "Unable to confirm the change. Exiting." >&2
    exit 1
  fi

  local selected_branch="${source_branch}"

  git fetch origin \
    "refs/heads/${selected_branch}:refs/remotes/origin/${selected_branch}"

  if git show-ref --verify --quiet "refs/heads/${selected_branch}"; then
    git checkout "${selected_branch}"
  else
    git checkout --track "origin/${selected_branch}"
  fi

  local change_text
  change_text="${pull_request_title}"

  while [[ -n "${change_text}" && "${change_text: -1}" =~ [[:space:]] ]]; do
    change_text="${change_text:0:${#change_text}-1}"
  done

  if [[ -z "${change_text}" ]]; then
    echo "The selected pull request has no title." >&2
    exit 1
  fi

  if [[ "${change_text: -1}" != "." ]]; then
    change_text+="."
  fi

  local category
  if ! category="$(select_with_fzf "Select the change category:" "Dependency" "Build")"; then
    echo "No change category selected."
    exit 1
  fi

  if [[ -z "${category}" ]]; then
    echo "No change category selected."
    exit 1
  fi

  declare -A existing_change_files=()
  local change_file
  if [[ -d "${UNRELEASED_CHANGES_DIR}" ]]; then
    while IFS= read -r -d '' change_file; do
      existing_change_files["${change_file}"]=1
    done < <(find "${UNRELEASED_CHANGES_DIR}" -maxdepth 1 -type f -print0)
  fi

  ./log_change.sh "${category}" 0 "${change_text}"

  local -a new_change_files=()
  while IFS= read -r -d '' change_file; do
    if [[ -z "${existing_change_files["${change_file}"]+present}" ]]; then
      new_change_files+=("${change_file}")
    fi
  done < <(find "${UNRELEASED_CHANGES_DIR}" -maxdepth 1 -type f -print0)

  if [[ "${#new_change_files[@]}" -ne 1 ]]; then
    echo "Unable to identify the change file created by log_change.sh." >&2
    exit 1
  fi

  change_file="${new_change_files[0]}"
  echo "Adding ${change_file} to git"
  git add -- "${change_file}"
  echo "Committing changes"
  git commit --message="${change_text}"
  echo "Pushing changes"
  git push origin "${selected_branch}"
  echo "Checking out master"
  git checkout master
  echo "Complete"
}

main "$@"

# vim: set tabstop=2 shiftwidth=2 expandtab:
