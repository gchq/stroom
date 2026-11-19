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

# Copies the test results of every module into one directory, keeping their
# paths in the repo, so the upload doesn't have to search the whole workspace
# for them, which is slow. find skips the directories that can't hold any.
#
# Usage: ./collect_test_results.sh <results dir>
# Run from the repo root.

set -eo pipefail

main() {
  if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <results dir>" >&2
    exit 1
  fi
  local -r results_dir="$1"

  local -r start_ms="$(date +%s%3N)"
  mkdir -p "${results_dir}"

  find . \
    -type d \( -name .git -o -name node_modules -o -name .gradle \) -prune \
    -o -type f -path '*/build/test-results/*' -name '*.xml' -print0 \
    | xargs -0 -r cp --parents -t "${results_dir}"

  local file_count
  file_count="$(find "${results_dir}" -type f | wc -l)"
  echo "Collected ${file_count} test result files in" \
    "$(( $(date +%s%3N) - start_ms ))ms"
}

main "$@"
