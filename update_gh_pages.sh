#!/usr/bin/env bash
set -euo pipefail
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

  # DEPLOY_TOKEN is a github personal access token.  GITHUB_TOKEN won't work
  # for gh-pages aparantly
  # DO NOT echo this variable as it has a token in it !!!!!!!
  local repo_uri="https://x-access-token:${DEPLOY_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
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

  git config user.name "$GITHUB_ACTOR"
  git config user.email "${GITHUB_ACTOR}@bots.github.com"

  # Clone the repo with just the gh-pages branch
  echo "Cloning branch ${gh_pages_branch} to ${gh_pages_clone_dir}"
  git clone \
    --branch "${gh_pages_branch}" \
    --single-branch \
    "${repo_uri}" \
    "${gh_pages_clone_dir}"

  #git rebase "${remote_name}/${main_branch}"

  pushd "${gh_pages_clone_dir}" > /dev/null

  mkdir -p "${gh_pages_versioned_dir}"

  echo "Rsyncing gh-pages content from ${gh_pages_source_dir}" \
    "to ${gh_pages_versioned_dir}"
  rsync \
    --verbose \
    --human-readable \
    --archive \
    --delete \
    --exclude='.git/' \
    "${gh_pages_source_dir}/" \
    "${gh_pages_versioned_dir}/"

  echo "Writing version ${BUILD_TAG} to ${version_file}"
  echo "${BUILD_TAG}" > "${version_file}"

  echo "Adding all new/changed files"
  git add --all

  local change_count
  change_count="$(git status --porcelain | wc -l)"
  if [[ "${change_count}" -gt 0 ]]; then
    echo "Committing changes"
    git commit -a -m "Updated GitHub Pages"

    #git remote set-url "$remote_name" "$repo_uri" # includes access token
    echo "Pushing changes to ${remote_name} ${gh_pages_branch}"
    git \
      push \
      --force-with-lease \
      "${remote_name}" \
      "${gh_pages_branch}"
  else
    echo "No changes to commit"
  fi

  popd
}

main "$@"
