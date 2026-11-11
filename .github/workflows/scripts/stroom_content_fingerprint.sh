#!/usr/bin/env bash

#
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
#

# Prints a fingerprint of the downloaded stroom content in a stroom user dir
# (~/.stroom), so CI only saves its cache of them when they have changed.
# The content pack zips are named with their versions and the git repo clones
# with the hash of their remote's branches and tags, so the names are enough.
# Prints nothing if there is no content.
#
# Usage: ./stroom_content_fingerprint.sh <stroom user dir>

set -eo pipefail

main() {
  if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <stroom user dir>" >&2
    exit 1
  fi
  local -r stroom_user_dir="$1"

  local content
  content="$(
    if [[ -d "${stroom_user_dir}/contentPackDownload" ]]; then
      find "${stroom_user_dir}/contentPackDownload" \
        -mindepth 1 -maxdepth 1 -type f -name '*.zip' -printf 'zip %f\n'
    fi
    # e.g. git-repo-cache/gchq/stroom-content/<hash>, but not unfinished
    # .tmp-* clones
    if [[ -d "${stroom_user_dir}/git-repo-cache" ]]; then
      find "${stroom_user_dir}/git-repo-cache" \
        -mindepth 3 -maxdepth 3 -type d ! -name '.tmp-*' -printf 'repo %P\n'
    fi
  )"

  # Print nothing if there is no content, so there is nothing to save
  if [[ -n "${content}" ]]; then
    LC_ALL=C sort <<< "${content}" \
      | sha256sum \
      | cut -c1-16
  fi
}

main "$@"
