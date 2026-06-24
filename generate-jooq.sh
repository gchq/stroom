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

# Automatically discovers every directory whose name ends in "db-jooq"
# (searching from the repo root, two levels deep) and runs generateJooq
# in each one.
#
# Usage: ./generate-jooq.sh [--dry-run]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DRY_RUN=false

if [[ "${1:-}" == "--from-migrations" ]]; then
  echo "Running per-module regenerateJooq tasks (fresh DB + Flyway + codegen)..."
  # Find all -impl-db modules that have a build.gradle with regenerateJooq
  mapfile -t DB_DIRS < <(
    find "${SCRIPT_DIR}" \
      -mindepth 2 -maxdepth 3 \
      -type d \
      -name '*-impl-db' \
      | sort
  )
  FAILED=()
  for dir in "${DB_DIRS[@]}"; do
    rel="${dir#"${SCRIPT_DIR}/"}"
    # Convert dir path to Gradle project path, e.g.
    # stroom-activity/stroom-activity-impl-db -> :stroom-activity:stroom-activity-impl-db
    gradle_path=":$(echo "${rel}" | sed 's|/|:|g')"
    echo "  regenerateJooq → ${gradle_path}"
    if "${SCRIPT_DIR}/gradlew" "${gradle_path}:regenerateJooq"; then
      echo "  ✓ done"
    else
      echo "  ✗ FAILED" >&2
      FAILED+=("${gradle_path}")
    fi
  done
  if [[ ${#FAILED[@]} -gt 0 ]]; then
    echo "The following modules failed:" >&2
    for f in "${FAILED[@]}"; do
      echo "  ✗ ${f}" >&2
    done
    exit 1
  fi
  echo "All regenerateJooq tasks completed successfully."
  exit 0
fi

if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
  echo "DRY RUN — no gradle tasks will be executed"
fi
# Find all JOOQ modules. Two discovery methods:
#   1. Migrated modules: contain a jooq-codegen.xml file
#   2. Legacy modules: directory name matches *db-jooq
mapfile -t JOOQ_DIRS < <(
  {
    # Migrated: find directories containing jooq-codegen.xml
    find "${SCRIPT_DIR}" \
      -mindepth 3 -maxdepth 4 \
      -name 'jooq-codegen.xml' \
      -exec dirname {} \;

    # Legacy: find directories matching *db-jooq
    find "${SCRIPT_DIR}" \
      -mindepth 2 -maxdepth 3 \
      -type d \
      -name '*db-jooq'
  } | sort -u
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
