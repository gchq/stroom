#!/usr/bin/env bash

# Script to update the Copyright date in all applicable java files.
# If it can't find the Copyright line it adds the full ASL2.0 header.
# The Copyright date is set to the year of the first commit of the file.

set -euo pipefail

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" || "${1:-}" == "-d" ]]; then
  DRY_RUN=true
  echo "=== DRY RUN MODE: Displaying planned changes only ==="
  echo ""
fi

# RipGrep be faster than grep, hopefully
USE_RIP_GREP=false
if command -v rg 1>/dev/null; then
  USE_RIP_GREP=true
fi

echo "This will take a while"

# Track how many files need updates
#changed_count=0

git ls-files '*.java' | while IFS= read -r file; do

  # Check for various files to ignore

  if [[ "${file}" =~ /db/jooq/ ]]; then
    echo "${file} - SKIPPING (Jooq Generated)"
    continue
  fi

  if [[ "${file}" =~ /edu/ycp/cs/dh/acegwt/client/ace/ ]]; then
    echo "${file} - SKIPPING (Ace Editor)"
    continue
  fi

  if [[ "${file}" =~ /com/google/gwt/ ]]; then
    echo "${file} - SKIPPING (GWT)"
    continue
  fi

  if [[ "${USE_RIP_GREP}" = true ]]; then
    if rg -qF --no-messages "@Generated(" "${file}"; then
      echo "${file} - SKIPPING (@Generated)"
      continue
    fi
  else
    if grep -sF "^@Generated(" "${file}"; then
      echo "${file} - SKIPPING (@Generated)"
      continue
    fi
  fi

  # Get the year of the first commit for the file
  first_year=$(git log --follow --format="%ad" --date=format:"%Y" -- "$file" | tail -n 1)

  # Fall back to current year if untracked/no commit history
  if [ -z "$first_year" ]; then
    first_year=$(date +%Y)
  fi

  # Find the existing copyright line matching the target pattern
  # Looking for something like 
  # * Copyright 2016-2025 Crown Copyright
  # * Copyright 2016 Crown Copyright
  existing_line=$(perl -ne 'print if /\s+\*\s+Copyright\s+\d{4}.*\s+Crown Copyright/' "$file" | head -n 1)

  # Extract the existing year or year range string
  existing_year="${existing_line}"
  existing_year="${existing_year#[[:space:]]*[[:space:]]Copyright[[:space:]]}"
  existing_year="${existing_year%%[[:space:]]*Crown Copyright}"

  tmp_file=""

  if [ -n "$existing_line" ]; then
    # Generate the expected target line
    target_line=" * Copyright ${first_year} Crown Copyright"

    # Check if the file needs an update
    if [ "$existing_line" != "$target_line" ]; then
      echo "$file - ${existing_year} => ${first_year}"
      if [ "$DRY_RUN" = false ]; then
        perl -pi -e "s/\s+\*\s+Copyright\s+\d{4}.*\s+Crown Copyright/${target_line}/g" "$file"
      fi
    fi
  else
    echo "$file - MISSING => ${first_year}"

    if [ "$DRY_RUN" = false ]; then
      if [[ -z "${tmp_file}" ]]; then
        tmp_file="$(mktemp)"
      fi

      # Add the header to the file, via a temp file
      if cat - "$file" >"$tmp_file" <<EOF
/*
 * Copyright ${first_year} Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

EOF
        then
          cp "${tmp_file}" "${file}"
      fi
    fi
  fi
done
