#!/usr/bin/env bash
#
# Copyright 2016-2025 Crown Copyright
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

# Automatically discovers every module containing a jooq-codegen.xml file
# and runs generateJooq in each one.  The Gradle task creates a fresh temp DB,
# runs Flyway migrations, generates JOOQ code, then drops the DB.
#
# Usage: ./generate-jooq.sh [--dry-run]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DRY_RUN=false


if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
  echo "DRY RUN — no gradle tasks will be executed"
fi
# Find all JOOQ modules: discover directories containing a jooq-codegen.xml file
mapfile -t JOOQ_DIRS < <(
  find "${SCRIPT_DIR}" \
    -mindepth 3 -maxdepth 4 \
    -name 'jooq-codegen.xml' \
    -exec dirname {} \; \
  | sort -u
)

if [[ ${#JOOQ_DIRS[@]} -eq 0 ]]; then
  echo "No JOOQ modules found under ${SCRIPT_DIR}" >&2
  exit 1
fi

echo "Found ${#JOOQ_DIRS[@]} jooq module(s):"
for dir in "${JOOQ_DIRS[@]}"; do
  echo "  ${dir#"${SCRIPT_DIR}/"}"
done
echo

FAILED=()

for dir in "${JOOQ_DIRS[@]}"; do
  rel="${dir#"${SCRIPT_DIR}/"}"
  # Convert dir path to Gradle project path, e.g.
  # stroom-activity/stroom-activity-impl-db -> :stroom-activity:stroom-activity-impl-db
  gradle_path=":$(echo "${rel}" | sed 's|/|:|g')"

  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  generateJooq  →  ${gradle_path}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  if [[ "${DRY_RUN}" == true ]]; then
    echo "  (skipped — dry run)"
    continue
  fi

  if "${SCRIPT_DIR}/gradlew" "${gradle_path}:generateJooq"; then
    echo "  ✓ done"
  else
    echo "  ✗ FAILED" >&2
    FAILED+=("${gradle_path}")
  fi
  echo
done

if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo "The following modules failed:" >&2
  for f in "${FAILED[@]}"; do
    echo "  ✗ ${f}" >&2
  done
  exit 1
fi

echo "All generateJooq tasks completed successfully."
