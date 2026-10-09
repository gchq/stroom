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
# Merges a round's shards (out/<round>-*, walked by ./lane.sh) into the evidence (out/crawl), then
# rebuilds what is judged from it, in the order each step's input needs:
#
#   capability specs (reads the crawls) -> coverage (writes node-presenters.json and coverage.md)
#   -> door preconditions (reads coverage.md) -> handler inventory (reads node-presenters.json)
#
#   ./merge.sh <round>
#
# A shard whose walk didn't finish still has a checkpointed crawl-*.json, which is merged too; its
# walk.log says how far it got. Shards merged before under the same round are replaced.

set -euo pipefail

SUITE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [[ $# -ne 1 ]]; then
  sed -n '17,27p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
fi

round=$1
merged=0
for dir in "${SUITE}/out/${round}"-*/; do
  for src in "${dir}"crawl-*.json; do
    [[ -e "${src}" ]] || continue
    base=$(basename "${src}" .json)
    cp "${src}" "${SUITE}/out/crawl/${base}-${round}.json"
    echo "merged $(basename "${dir}") -> out/crawl/${base}-${round}.json"
    merged=$((merged + 1))
  done
done
if [[ ${merged} -eq 0 ]]; then
  echo "no crawl-*.json in ${SUITE}/out/${round}-*" >&2
  exit 1
fi

node "${SUITE}/tools/build-capability-specs.mjs"
node "${SUITE}/coverage.mjs"
node "${SUITE}/tools/build-door-preconditions.mjs"
node "${SUITE}/tools/build-handler-inventory.mjs"
